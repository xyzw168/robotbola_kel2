import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import socket

class UdpBridgeNode(Node):
    def __init__(self):
        super().__init__('udp_bridge_node')

        self.publisher_ = self.create_publisher(String, '/perintah_android', 10)

        self.udp_ip = "0.0.0.0"
        self.udp_port = 5556

        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind((self.udp_ip, self.udp_port))
        self.sock.setblocking(False)  # Non-blocking biar nggak bikin ROS2 macet

        self.timer = self.create_timer(0.01, self.terima_udp)
        self.get_logger().info(f"UDP Server siap dengerin controller di port {self.udp_port}...")

    def terima_udp(self):
        try:
            data, addr = self.sock.recvfrom(1024)
            pesan = data.decode('utf-8').strip()
            if pesan:
                self.get_logger().info(f"UDP masuk dari {addr[0]}: {pesan}")
                msg = String()
                msg.data = pesan
                self.publisher_.publish(msg)
        except BlockingIOError:
            # Normal kalau di milidetik ini belum ada paket UDP masuk
            pass
        except Exception as e:
            self.get_logger().error(f"Error baca UDP: {e}")

def main(args=None):
    rclpy.init(args=args)
    node = UdpBridgeNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.sock.close()
        rclpy.shutdown()

if __name__ == '__main__':
    main()