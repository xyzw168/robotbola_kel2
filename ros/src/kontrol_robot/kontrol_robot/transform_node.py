import rclpy
from rclpy.node import Node
from std_msgs.msg import String 



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
    
