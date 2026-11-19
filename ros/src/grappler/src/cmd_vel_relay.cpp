#include <memory>

#include "geometry_msgs/msg/twist.hpp"
#include "rclcpp/rclcpp.hpp"

class CmdVelRelay : public rclcpp::Node
{
public:
  CmdVelRelay() : Node("cmd_vel_relay")
  {
    publisher_ = create_publisher<geometry_msgs::msg::Twist>(
      "/mecanum_drive_controller/reference_unstamped", 10);
    subscription_ = create_subscription<geometry_msgs::msg::Twist>(
      "/cmd_vel", 10,
      [this](const geometry_msgs::msg::Twist::SharedPtr msg) {
        publisher_->publish(*msg);
      });
    RCLCPP_INFO(get_logger(), "Relaying /cmd_vel to /mecanum_drive_controller/reference_unstamped");
  }

private:
  rclcpp::Publisher<geometry_msgs::msg::Twist>::SharedPtr publisher_;
  rclcpp::Subscription<geometry_msgs::msg::Twist>::SharedPtr subscription_;
};

int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  rclcpp::spin(std::make_shared<CmdVelRelay>());
  rclcpp::shutdown();
  return 0;
}
