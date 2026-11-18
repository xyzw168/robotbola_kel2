import rclpy
from rclpy.node import Node
from std_msgs.msg import String 

RENTANG_IN = 100
TANDA = (1,1,1,1)


def parse_pesan_android(pesan):
    try:
        parts = pesan.split(',')
        if len(parts) != 4:
            raise ValueError("Pesan harus memiliki 4 nilai yang dipisahkan oleh koma.")
        
        nilai_x = int(parts[0].strip())
        nilai_y = int(parts[1].strip())
        nilai_z = int(parts[2].strip())
        nilai_w = int(parts[3].strip())

        return nilai_x, nilai_y, nilai_z, nilai_w
    except ValueError as e:
        None
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
        self.get_logger().info("get data dari HP...")

    def cek_pesan_android(self, msg):
        pesan = msg.data.strip()
        if not pesan:
            self.get_logger().warning("Pesan Android kosong, diabaikan.")
            return

        perintah = String()
        perintah.data = pesan
        self.get_logger().info(f"diterima: {pesan}")

def main(args=None):
        rclpy.init(args=args)
        node = TransformNode()
        rclpy.spin(node)
        rclpy.shutdown()
    
