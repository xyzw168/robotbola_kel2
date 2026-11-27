import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import socket


class UdpBridgeNode(Node):
    def __init__(self):
        super().__init__('udp_bridge_node')

        self.publisher = self.create_publisher(
            String, '/perintah_android', 10
        )

        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(('0.0.0.0', 5556))
        self.sock.setblocking(False)

        self.timer = self.create_timer(0.01, self.terima_udp)

        self.get_logger().info('UDP Server berjalan di port 5556')

    def terima_udp(self):
        try:
            data, addr = self.sock.recvfrom(1024)
            pesan = data.decode('utf-8').strip()

            if pesan:
                msg = String()
                msg.data = pesan
                self.publisher.publish(msg)

                self.get_logger().info(f'Pesan dari {addr[0]}: {pesan}')

        except BlockingIOError:
            pass

        except Exception as e:
            self.get_logger().error(f'Error: {e}')


def main(args=None):
    rclpy.init(args=args)
    node = UdpBridgeNode()

    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.sock.close()
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()