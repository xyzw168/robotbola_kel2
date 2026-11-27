import rclpy
from rclpy.node import Node
from geometry_msgs.msg import Twist
from std_msgs.msg import Float32MultiArray
from kontrol_robot.omni_kinematics import OmniKinematics3 

wheel_deg = [120, -120, 0]  # arah dorong roda, derajat (asumsi)
wheel_radius   = 0.15 # meter, radius roda 
robot_radius    = 0.20 # meter, jarak pusat robot ke roda (asumsi)
max_speed        = 10.47 #rad/s   


class KinematicsNode(Node):
    def __init__(self):
        super().__init__('kinematics_node')
        self.cmd_vel_subscriber = self.create_subscription(
            Twist, '/cmd_vel', self.on_cmd_vel, 10
        )
        
        self.ik = OmniKinematics3(wheel_deg, wheel_radius, robot_radius, max_speed)
        self.wheel_speeds_publisher_ = self.create_publisher(Float32MultiArray, '/wheel_speeds', 10)
                
    def on_cmd_vel(self, msg):
        vx = msg.linear.x
        vy = msg.linear.y
        omega = msg.angular.z

        speeds = self.ik.wheel_speeds(vx, vy, omega)
        normalize_speeds = self.ik.normalize_speeds(speeds)

        out = Float32MultiArray()
        out.data =  normalize_speeds       
        self.wheel_speeds_publisher_.publish(out)
        speeds_rounded = [round(speed, 2) for speed in normalize_speeds]
        self.get_logger().info(
            f"w1 front-right: {speeds_rounded[0]} rad/s, "
            f"w2 front-left: {speeds_rounded[1]} rad/s, "
            f"w3 rear: {speeds_rounded[2]} rad/s"
        )           
def main(args=None):
    rclpy.init(args=args)
    node = KinematicsNode()
    rclpy.spin(node)
    node.destroy_node()
    rclpy.shutdown()