import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import serial


class SerialNode(Node):
    def __init__(self):
        super().__init__('serial_node')

        self.perintah_subscriber = self.create_subscription(
            String, '/perintah_android', self.on_perintah, 10
        )

        self.status_publisher_ = self.create_publisher(
            String, '/status_stm32', 10
        )

        self.ser = serial.Serial('/dev/ttyUSB0', 115200, timeout=0.1)
        self.timer = self.create_timer(0.1, self.baca_status_stm32)

        self.get_logger().info("Serial terkoneksi ke STM32")

    def on_perintah(self, msg):
        perintah = msg.data
        self.ser.write((perintah + '\n').encode('utf-8'))

        self.get_logger().info(f"Kirim ke STM32: {perintah}")

    def baca_status_stm32(self):
        if self.ser.in_waiting:
            data = self.ser.readline().decode('utf-8').strip()

            if data:
                status = String()
                status.data = data
                self.status_publisher_.publish(status)


def main(args=None):
    rclpy.init(args=args)
    node = SerialNode()
    rclpy.spin(node)
    node.destroy_node()
    rclpy.shutdown()