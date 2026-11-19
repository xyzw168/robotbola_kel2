#include <cmath>
#include <memory>

#include "rclcpp/rclcpp.hpp"
#include "std_msgs/msg/float64_multi_array.hpp"
#include "std_msgs/msg/int16.hpp"

constexpr double kPi = 3.14159265358979323846;

class GripCommandAdapter : public rclcpp::Node
{
public:
  GripCommandAdapter() : Node("grip_command_adapter")
  {
    publisher_ = create_publisher<std_msgs::msg::Float64MultiArray>(
      "/gripper_controller/commands", 10);
    subscription_ = create_subscription<std_msgs::msg::Int16>(
      "/grip/command", 10,
      [this](const std_msgs::msg::Int16::SharedPtr msg) {
        if (msg->data < 0 || msg->data > 180) {
          RCLCPP_WARN(get_logger(), "Ignoring gripper angle %d: expected 0..180 degrees", msg->data);
          return;
        }
        std_msgs::msg::Float64MultiArray command;
        command.data = {static_cast<double>(msg->data) * kPi / 180.0};
        publisher_->publish(command);
      });
    RCLCPP_INFO(get_logger(), "Converting /grip/command degrees (Int16) to gripper joint radians");
  }

private:
  rclcpp::Publisher<std_msgs::msg::Float64MultiArray>::SharedPtr publisher_;
  rclcpp::Subscription<std_msgs::msg::Int16>::SharedPtr subscription_;
};

int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  rclcpp::spin(std::make_shared<GripCommandAdapter>());
  rclcpp::shutdown();
  return 0;
}
