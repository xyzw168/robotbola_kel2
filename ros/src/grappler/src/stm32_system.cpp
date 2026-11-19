#include "stm32_ros2_control_hardware/stm32_system.hpp"

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iomanip>
#include <sstream>
#include <termios.h>
#include <unistd.h>

#include "hardware_interface/types/hardware_interface_type_values.hpp"
#include "pluginlib/class_list_macros.hpp"
#include "rclcpp/logging.hpp"

namespace stm32_ros2_control_hardware
{
namespace
{
constexpr std::size_t kWheelCount = 4;
constexpr std::size_t kFeedbackValueCount = 5;  
constexpr double kPi = 3.14159265358979323846;

std::vector<std::string> split_csv(const std::string & text)
{
  std::vector<std::string> fields;
  std::stringstream stream(text);
  std::string field;
  while (std::getline(stream, field, ',')) {
    const auto first = field.find_first_not_of(" \t\r");
    if (first == std::string::npos) {
      fields.emplace_back("");
    } else {
      const auto last = field.find_last_not_of(" \t\r");
      fields.emplace_back(field.substr(first, last - first + 1));
    }
  }
  return fields;
}

bool parse_double(const std::string & text, double & value)
{
  try {
    std::size_t used = 0;
    value = std::stod(text, &used);
    return used == text.size() && std::isfinite(value);
  } catch (const std::exception &) {
    return false;
  }
}

bool parse_bool(const std::string & text, bool default_value)
{
  if (text.empty()) {
    return default_value;
  }
  return text == "true" || text == "True" || text == "1" || text == "yes";
}

speed_t baud_to_termios(int baud_rate)
{
  switch (baud_rate) {
    case 9600: return B9600;
    case 19200: return B19200;
    case 38400: return B38400;
    case 57600: return B57600;
    case 115200: return B115200;
#ifdef B230400
    case 230400: return B230400;
#endif
    default: return static_cast<speed_t>(0);
  }
}

std::vector<std::string> split_names(const std::string & text)
{
  std::vector<std::string> names;
  std::stringstream stream(text);
  std::string item;
  while (std::getline(stream, item, ',')) {
    const auto first = item.find_first_not_of(" \t");
    if (first == std::string::npos) {
      continue;
    }
    const auto last = item.find_last_not_of(" \t");
    names.emplace_back(item.substr(first, last - first + 1));
  }
  return names;
}
}  

STM32System::CallbackReturn STM32System::on_init(
  const hardware_interface::HardwareInfo & info)
{
  if (hardware_interface::SystemInterface::on_init(info) != CallbackReturn::SUCCESS) {
    return CallbackReturn::ERROR;
  }

  const auto & params = info_.hardware_parameters;
  const auto get_param = [&params](const std::string & key, const std::string & fallback) {
      const auto it = params.find(key);
      return it == params.end() ? fallback : it->second;
    };

  device_ = get_param("device", "/dev/ttyACM0");
  try {
    baud_rate_ = std::stoi(get_param("baud_rate", "115200"));
    feedback_timeout_ms_ = std::stoi(get_param("feedback_timeout_ms", "1000"));
  } catch (const std::exception &) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Invalid baud_rate or feedback_timeout_ms parameter");
    return CallbackReturn::ERROR;
  }
  require_feedback_ = parse_bool(get_param("require_feedback", "false"), false);
  gripper_joint_name_ = get_param("gripper_joint_name", "gripper_joint");
  wheel_joint_names_ = split_names(get_param(
      "wheel_joint_names",
      "front_left_wheel_joint,front_right_wheel_joint,rear_left_wheel_joint,rear_right_wheel_joint"));

  if (baud_to_termios(baud_rate_) == 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Unsupported baud_rate: %d", baud_rate_);
    return CallbackReturn::ERROR;
  }
  if (feedback_timeout_ms_ <= 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "feedback_timeout_ms must be > 0");
    return CallbackReturn::ERROR;
  }
  if (wheel_joint_names_.size() != kWheelCount) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "wheel_joint_names must contain exactly four comma-separated names");
    return CallbackReturn::ERROR;
  }
  if (info_.joints.size() != kWheelCount + 1) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Expected exactly four wheel joints and one gripper joint in <ros2_control>");
    return CallbackReturn::ERROR;
  }

  hw_commands_.assign(info_.joints.size(), 0.0);
  hw_states_.assign(info_.joints.size(), 0.0);
  wheel_joint_indices_.clear();

  for (const auto & wheel_name : wheel_joint_names_) {
    const std::size_t index = find_joint_index(wheel_name);
    if (index >= info_.joints.size()) {
      RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Wheel joint '%s' not found in URDF", wheel_name.c_str());
      return CallbackReturn::ERROR;
    }
    const auto & joint = info_.joints[index];
    if (joint.command_interfaces.size() != 1 ||
      joint.command_interfaces[0].name != hardware_interface::HW_IF_VELOCITY ||
      joint.state_interfaces.size() != 1 ||
      joint.state_interfaces[0].name != hardware_interface::HW_IF_VELOCITY)
    {
      RCLCPP_ERROR(rclcpp::get_logger("STM32System"),
        "Wheel '%s' must expose velocity command and velocity state interfaces", wheel_name.c_str());
      return CallbackReturn::ERROR;
    }
    wheel_joint_indices_.push_back(index);
  }

  gripper_joint_index_ = find_joint_index(gripper_joint_name_);
  if (gripper_joint_index_ >= info_.joints.size()) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Gripper joint '%s' not found in URDF", gripper_joint_name_.c_str());
    return CallbackReturn::ERROR;
  }
  const auto & gripper = info_.joints[gripper_joint_index_];
  if (gripper.command_interfaces.size() != 1 ||
    gripper.command_interfaces[0].name != hardware_interface::HW_IF_POSITION ||
    gripper.state_interfaces.size() != 1 ||
    gripper.state_interfaces[0].name != hardware_interface::HW_IF_POSITION)
  {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"),
      "Gripper '%s' must expose position command and position state interfaces", gripper_joint_name_.c_str());
    return CallbackReturn::ERROR;
  }

  RCLCPP_INFO(rclcpp::get_logger("STM32System"),
    "Initialized hardware description. Serial device=%s baud=%d", device_.c_str(), baud_rate_);
  return CallbackReturn::SUCCESS;
}

STM32System::CallbackReturn STM32System::on_configure(
  const rclcpp_lifecycle::State & )
{
  if (!open_serial()) {
    return CallbackReturn::ERROR;
  }
  received_feedback_ = false;
  feedback_timeout_logged_ = false;
  rx_buffer_.clear();
  tx_buffer_.clear();
  tx_offset_ = 0;
  configure_time_ = std::chrono::steady_clock::now();
  RCLCPP_INFO(rclcpp::get_logger("STM32System"),
    "Serial port opened: %s at %d baud. Waiting for STM32 feedback frame.", device_.c_str(), baud_rate_);
  return CallbackReturn::SUCCESS;
}

STM32System::CallbackReturn STM32System::on_cleanup(
  const rclcpp_lifecycle::State & )
{
  close_serial();
  return CallbackReturn::SUCCESS;
}

STM32System::CallbackReturn STM32System::on_activate(
  const rclcpp_lifecycle::State & )
{
  if (serial_fd_ < 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Cannot activate: serial port is not open");
    return CallbackReturn::ERROR;
  }
  std::fill(hw_commands_.begin(), hw_commands_.end(), 0.0);
  send_zero_velocity_frame();
  RCLCPP_INFO(rclcpp::get_logger("STM32System"), "STM32 hardware interface activated");
  return CallbackReturn::SUCCESS;
}

STM32System::CallbackReturn STM32System::on_deactivate(
  const rclcpp_lifecycle::State & )
{
  send_zero_velocity_frame();
  RCLCPP_INFO(rclcpp::get_logger("STM32System"), "STM32 hardware interface deactivated; zero wheel command sent");
  return CallbackReturn::SUCCESS;
}

STM32System::CallbackReturn STM32System::on_shutdown(
  const rclcpp_lifecycle::State & )
{
  close_serial();
  return CallbackReturn::SUCCESS;
}

STM32System::CallbackReturn STM32System::on_error(
  const rclcpp_lifecycle::State & )
{
  close_serial();
  return CallbackReturn::SUCCESS;
}

std::vector<hardware_interface::StateInterface> STM32System::export_state_interfaces()
{
  std::vector<hardware_interface::StateInterface> state_interfaces;
  state_interfaces.reserve(kWheelCount + 1);
  for (std::size_t index : wheel_joint_indices_) {
    state_interfaces.emplace_back(
      info_.joints[index].name, hardware_interface::HW_IF_VELOCITY, &hw_states_[index]);
  }
  state_interfaces.emplace_back(
    gripper_joint_name_, hardware_interface::HW_IF_POSITION, &hw_states_[gripper_joint_index_]);
  return state_interfaces;
}

std::vector<hardware_interface::CommandInterface> STM32System::export_command_interfaces()
{
  std::vector<hardware_interface::CommandInterface> command_interfaces;
  command_interfaces.reserve(kWheelCount + 1);
  for (std::size_t index : wheel_joint_indices_) {
    command_interfaces.emplace_back(
      info_.joints[index].name, hardware_interface::HW_IF_VELOCITY, &hw_commands_[index]);
  }
  command_interfaces.emplace_back(
    gripper_joint_name_, hardware_interface::HW_IF_POSITION, &hw_commands_[gripper_joint_index_]);
  return command_interfaces;
}

hardware_interface::return_type STM32System::read(
  const rclcpp::Time & , const rclcpp::Duration & )
{
  if (serial_fd_ < 0) {
    return hardware_interface::return_type::ERROR;
  }

  char buffer[256];
  while (true) {
    const ssize_t count = ::read(serial_fd_, buffer, sizeof(buffer));
    if (count > 0) {
      rx_buffer_.append(buffer, static_cast<std::size_t>(count));
      if (rx_buffer_.size() > 4096) {
        RCLCPP_WARN(rclcpp::get_logger("STM32System"), "Serial receive buffer overflow; clearing incomplete data");
        rx_buffer_.clear();
      }
      std::size_t newline_pos = std::string::npos;
      while ((newline_pos = rx_buffer_.find('\n')) != std::string::npos) {
        std::string line = rx_buffer_.substr(0, newline_pos);
        rx_buffer_.erase(0, newline_pos + 1);
        if (!line.empty() && line.back() == '\r') {
          line.pop_back();
        }
        parse_feedback_line(line);
      }
      continue;
    }
    if (count == 0) {
      break;
    }
    if (errno == EAGAIN || errno == EWOULDBLOCK) {
      break;
    }
    if (errno == EINTR) {
      continue;
    }
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Serial read failed: %s", std::strerror(errno));
    return hardware_interface::return_type::ERROR;
  }

  if (require_feedback_) {
    const auto now = std::chrono::steady_clock::now();
    const auto reference_time = received_feedback_ ? last_feedback_time_ : configure_time_;
    const auto age_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - reference_time).count();
    if (age_ms > feedback_timeout_ms_) {
      if (!feedback_timeout_logged_) {
        RCLCPP_ERROR(rclcpp::get_logger("STM32System"),
          "No valid STM32 feedback frame for %ld ms (timeout=%d ms)", age_ms, feedback_timeout_ms_);
        feedback_timeout_logged_ = true;
      }
      return hardware_interface::return_type::ERROR;
    }
  }
  return hardware_interface::return_type::OK;
}

hardware_interface::return_type STM32System::write(
  const rclcpp::Time & , const rclcpp::Duration & )
{
  if (serial_fd_ < 0) {
    return hardware_interface::return_type::ERROR;
  }

  if (tx_offset_ >= tx_buffer_.size()) {
    tx_buffer_ = make_command_frame();
    tx_offset_ = 0;
  }

  const char * data = tx_buffer_.data() + tx_offset_;
  const std::size_t remaining = tx_buffer_.size() - tx_offset_;
  const ssize_t written = ::write(serial_fd_, data, remaining);
  if (written > 0) {
    tx_offset_ += static_cast<std::size_t>(written);
    return hardware_interface::return_type::OK;
  }
  if (written < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)) {
    return hardware_interface::return_type::OK;
  }
  RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Serial write failed: %s", std::strerror(errno));
  return hardware_interface::return_type::ERROR;
}

bool STM32System::open_serial()
{
  close_serial();
  serial_fd_ = ::open(device_.c_str(), O_RDWR | O_NOCTTY | O_NONBLOCK);
  if (serial_fd_ < 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"),
      "Cannot open serial device '%s': %s. Check port name and dialout permission.",
      device_.c_str(), std::strerror(errno));
    return false;
  }
  if (!configure_serial(serial_fd_, baud_rate_)) {
    close_serial();
    return false;
  }
  return true;
}

void STM32System::close_serial()
{
  if (serial_fd_ >= 0) {
    ::close(serial_fd_);
    serial_fd_ = -1;
  }
  tx_buffer_.clear();
  tx_offset_ = 0;
  rx_buffer_.clear();
}

bool STM32System::configure_serial(int fd, int baud_rate)
{
  struct termios tty {};
  if (tcgetattr(fd, &tty) != 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "tcgetattr failed: %s", std::strerror(errno));
    return false;
  }
  cfmakeraw(&tty);
  const speed_t speed = baud_to_termios(baud_rate);
  if (speed == 0 || cfsetispeed(&tty, speed) != 0 || cfsetospeed(&tty, speed) != 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Failed to set baud rate %d", baud_rate);
    return false;
  }
  tty.c_cflag |= (CLOCAL | CREAD);
  tty.c_cflag &= ~PARENB;
  tty.c_cflag &= ~CSTOPB;
  tty.c_cflag &= ~CSIZE;
  tty.c_cflag |= CS8;
#ifdef CRTSCTS
  tty.c_cflag &= ~CRTSCTS;
#endif
  tty.c_cc[VMIN] = 0;
  tty.c_cc[VTIME] = 0;
  if (tcsetattr(fd, TCSANOW, &tty) != 0) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "tcsetattr failed: %s", std::strerror(errno));
    return false;
  }
  tcflush(fd, TCIOFLUSH);
  return true;
}

bool STM32System::parse_feedback_line(const std::string & line)
{
  const auto fields = split_csv(line);
  if (fields.size() != kFeedbackValueCount + 1 || fields[0] != "FB") {
    return false;
  }

  std::vector<double> values(kFeedbackValueCount, 0.0);
  for (std::size_t i = 0; i < kFeedbackValueCount; ++i) {
    if (!parse_double(fields[i + 1], values[i])) {
      RCLCPP_WARN(rclcpp::get_logger("STM32System"), "Invalid numeric value in feedback frame: '%s'", line.c_str());
      return false;
    }
  }

  for (std::size_t i = 0; i < kWheelCount; ++i) {
    hw_states_[wheel_joint_indices_[i]] = values[i];
  }
  hw_states_[gripper_joint_index_] = values[4];
  received_feedback_ = true;
  feedback_timeout_logged_ = false;
  last_feedback_time_ = std::chrono::steady_clock::now();
  return true;
}

bool STM32System::send_zero_velocity_frame()
{
  if (serial_fd_ < 0) {
    return false;
  }
  const double gripper_position = hw_states_[gripper_joint_index_];
  std::ostringstream frame;
  frame << "CMD,0,0,0,0," << std::fixed << std::setprecision(6)
        << (gripper_position * 180.0 / kPi) << '\n';
  tx_buffer_ = frame.str();
  tx_offset_ = 0;
  const ssize_t written = ::write(serial_fd_, tx_buffer_.data(), tx_buffer_.size());
  if (written > 0) {
    tx_offset_ = static_cast<std::size_t>(written);
  }
  if (written < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) {
    RCLCPP_ERROR(rclcpp::get_logger("STM32System"), "Failed to send zero-velocity frame: %s", std::strerror(errno));
    return false;
  }
  return tx_offset_ >= tx_buffer_.size();
}

std::string STM32System::make_command_frame() const
{
  std::ostringstream frame;
  frame << "CMD" << std::fixed << std::setprecision(6);
  for (std::size_t index : wheel_joint_indices_) {
    frame << ',' << hw_commands_[index];  
  }
  frame << ',' << (hw_commands_[gripper_joint_index_] * 180.0 / kPi);  
  frame << '\n';
  return frame.str();
}

std::size_t STM32System::find_joint_index(const std::string & joint_name) const
{
  for (std::size_t i = 0; i < info_.joints.size(); ++i) {
    if (info_.joints[i].name == joint_name) {
      return i;
    }
  }
  return info_.joints.size();
}

} 

PLUGINLIB_EXPORT_CLASS(
  stm32_ros2_control_hardware::STM32System,
  hardware_interface::SystemInterface)
