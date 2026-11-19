#ifndef STM32_ROS2_CONTROL_HARDWARE__STM32_SYSTEM_HPP_
#define STM32_ROS2_CONTROL_HARDWARE__STM32_SYSTEM_HPP_

#include <chrono>
#include <cstddef>
#include <string>
#include <vector>

#include "hardware_interface/system_interface.hpp"
#include "hardware_interface/types/hardware_interface_return_values.hpp"
#include "rclcpp_lifecycle/state.hpp"

namespace stm32_ros2_control_hardware
{

class STM32System : public hardware_interface::SystemInterface
{
public:
  using CallbackReturn = rclcpp_lifecycle::node_interfaces::LifecycleNodeInterface::CallbackReturn;

  CallbackReturn on_init(const hardware_interface::HardwareInfo & info) override;
  CallbackReturn on_configure(const rclcpp_lifecycle::State & previous_state) override;
  CallbackReturn on_cleanup(const rclcpp_lifecycle::State & previous_state) override;
  CallbackReturn on_activate(const rclcpp_lifecycle::State & previous_state) override;
  CallbackReturn on_deactivate(const rclcpp_lifecycle::State & previous_state) override;
  CallbackReturn on_shutdown(const rclcpp_lifecycle::State & previous_state) override;
  CallbackReturn on_error(const rclcpp_lifecycle::State & previous_state) override;

  std::vector<hardware_interface::StateInterface> export_state_interfaces() override;
  std::vector<hardware_interface::CommandInterface> export_command_interfaces() override;

  hardware_interface::return_type read(
    const rclcpp::Time & time, const rclcpp::Duration & period) override;
  hardware_interface::return_type write(
    const rclcpp::Time & time, const rclcpp::Duration & period) override;

private:
  bool open_serial();
  void close_serial();
  bool configure_serial(int fd, int baud_rate);
  bool parse_feedback_line(const std::string & line);
  bool send_zero_velocity_frame();
  std::string make_command_frame() const;
  std::size_t find_joint_index(const std::string & joint_name) const;

  int serial_fd_{-1};
  std::string device_{"/dev/ttyACM0"};
  int baud_rate_{115200};
  bool require_feedback_{false};
  int feedback_timeout_ms_{1000};

  std::vector<std::string> wheel_joint_names_;
  std::string gripper_joint_name_{"gripper_joint"};
  std::vector<std::size_t> wheel_joint_indices_;
  std::size_t gripper_joint_index_{0};

  std::vector<double> hw_commands_;
  std::vector<double> hw_states_;

  std::string rx_buffer_;
  std::string tx_buffer_;
  std::size_t tx_offset_{0};

  bool received_feedback_{false};
  bool feedback_timeout_logged_{false};
  std::chrono::steady_clock::time_point last_feedback_time_{};
  std::chrono::steady_clock::time_point configure_time_{};
};

}  // namespace stm32_ros2_control_hardware

#endif  // STM32_ROS2_CONTROL_HARDWARE__STM32_SYSTEM_HPP_
