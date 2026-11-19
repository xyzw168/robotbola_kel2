import rclpy
from rclpy.node import Node
from std_msgs.msg import String 
from geometry_msgs.msg import Twist
from std_msgs.msg import Float32

RENTANG_IN = 100  #ini berapa ya harusnya 100 apa 180 jir
TANDA = (1,1,1,1)


def parse_pesan_android(pesan):
    try:
        parts = pesan.split(',')
        if len(parts) != 4:
            raise ValueError("Pesan harus memiliki 4 nilai yang dipisahkan oleh koma.")
        
        nilai_x = int(parts[0].strip()) #kan kata evan 123 = xyz trus 4 = grip
        nilai_y = int(parts[1].strip())
        nilai_z = int(parts[2].strip())
        nilai_w = int(parts[3].strip())

        return nilai_x, nilai_y, nilai_z, nilai_w
    except ValueError as e:
        return None
def ambang_nilai(nilai, ambang):
    if abs(nilai) < ambang:
        return 0
    return nilai
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
        cmd.linear.x = skala(ambang_nilai(x, 10), RENTANG_IN, 0.5)
        cmd.linear.y = skala(ambang_nilai(y, 10), RENTANG_IN, 0.5)
        cmd.angular.z = skala(ambang_nilai(z, 10), RENTANG_IN, 0.5)
    
        grip = Float32()
        grip.data =  float(w)

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
    
