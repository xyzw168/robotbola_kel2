import rclpy
from rclpy.node import Node
from std_msgs.msg import String 
from geometry_msgs.msg import Twist
from std_msgs.msg import Float32

RENTANG_IN = 100 #--> satuannya persen


def parse_pesan_android(pesan):
    try:
        parts = pesan.split(',')
        if len(parts) != 4:
            raise ValueError("Pesan harus memiliki 4 nilai yang dipisahkan oleh koma.")
        
        nilai_x = float(parts[0].strip()) #kan kata evan 123 = xyz trus 4 = grip
        nilai_y = float(parts[1].strip())
        nilai_z = float(parts[2].strip())
        nilai_w = float(parts[3].strip())

        return nilai_x, nilai_y, nilai_z, nilai_w
    except ValueError:
        return None
    
def skala(nilai, rentang_in, maks):
    return float((nilai / rentang_in) * maks)
    

class TransformNode(Node):
    def __init__(self):
        super().__init__('transform_node')
        self.pose_subscriber_ = self.create_subscription(
            String, '/perintah_android', self.cek_pesan_android, 10
        )
        self.cmd_vel_publisher_ = self.create_publisher(Twist, '/cmd_vel', 10) #publish buat ngirim ke cmd_vel 
        self.grip_publisher_ = self.create_publisher(Float32, '/grip/command', 10) #publish buat ngirim ke grip/command
        self.get_logger().info("get data dari HP...")

    def cek_pesan_android(self, msg):
        pesan = msg.data.strip()
        if not pesan:
            self.get_logger().warning("kosong")
            return
        
        x, y, z, w = parse_pesan_android(pesan)
        if x is None or y is None or z is None or w is None:
            return None
        
        cmd = Twist()
        cmd.linear.x = skala(x, RENTANG_IN, 100) 
        cmd.linear.y = skala(y, RENTANG_IN, 100)
        cmd.angular.z = skala(z, RENTANG_IN, 100)

        grip = Float32()
        grip.data = float(w)

        perintah = String()
        perintah.data = pesan
        self.get_logger().info(f"diterima: {pesan} -> x: {cmd.linear.x}, y: {cmd.linear.y}, z: {cmd.angular.z}, grip: {grip.data}")
        self.cmd_vel_publisher_.publish(cmd)
        self.grip_publisher_.publish(grip)

def main(args=None):
        rclpy.init(args=args)
        node = TransformNode()
        rclpy.spin(node)
        rclpy.shutdown()