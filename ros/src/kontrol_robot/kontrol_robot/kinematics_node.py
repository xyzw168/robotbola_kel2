import rclpy
from rclpy.node import Node
from std_msgs.msg import String 
from geometry_msgs.msg import Twist
from std_msgs.msg import Float32MultiArray
from kontrol_robot.omni_kinematics import OmniKinematics3 

class KinematicsNode(Node):
    def __init__(self):
        super().__init__('kinematics_node')
        self.cmd_vel_subscriber = self.create_subscription(
            Twist, '/cmd_vel', self.on_cmd_vel, 100
        )
        self.declare_parameter('wheel_angles_deg', [0.0, 120.0, 240.0])
        self.declare_parameter('wheel_radius', 0.075)
        self.declare_parameter('robot_radius', 0.20)
        self.declare_parameter('max_speed', 8.0)


        sudut = self.get_parameter('wheel_angles_deg').value
        r     = self.get_parameter('wheel_radius').value
        R     = self.get_parameter('robot_radius').value
        batas = self.get_parameter('max_speed').value
        
        self.ik = OmniKinematics3(sudut, r, R, batas)
        self.wheel_speeds_publisher_ = self.create_publisher(Float32MultiArray, '/wheel_speeds', 10)
                
    def on_cmd_vel(self, msg):
        vx = msg.linear.x
        vy = msg.linear.y
        omega = msg.angular.z

        speeds = self.ik.wheel_speeds(vx, vy, omega)
        normalized_speeds = self.ik.normalize_speeds(speeds)

        out = Float32MultiArray()
        out.data = normalized_speeds           
        self.wheel_speeds_publisher_.publish(out)
        self.get_logger().info(f"wheel speeds: {[round(s, 2) for s in normalized_speeds]}")
        
def main(args=None):
    rclpy.init(args=args)
    node = KinematicsNode()
    rclpy.spin(node)
    node.destroy_node()
    rclpy.shutdown()