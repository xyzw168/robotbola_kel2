import serial
import rclpy
from rclpy.node import Node
from std_msgs.msg import String


class SerialBridgeNode(Node):
    def __init__(self):
        super().__init__('serial_node')
        self.declare_parameter('serial_port', 'COM8')
        self.declare_parameter('baud_rate', 115200)

        port = self.get_parameter('serial_port').value
        baud_rate = self.get_parameter('baud_rate').value
        try:
            self.serial_connection = serial.Serial(
                port,
                baud_rate,
                timeout=1.0,
                write_timeout=1.0,
            )
        except serial.SerialException as error:
            self.get_logger().error(f'Could not open serial port {port}: {error}')
            raise

        self.subscription = self.create_subscription(
            String,
            '/perintah_android',
            self.forward_command,
            10,
        )
        self.get_logger().info(
            f'Forwarding /perintah_android to {port} at {baud_rate} baud.'
        )

    def forward_command(self, message):
        command = message.data.strip()
        if not command:
            self.get_logger().warning('Ignoring an empty robot command.')
            return

        try:
            self.serial_connection.write((command + '\n').encode('utf-8'))
        except serial.SerialException as error:
            self.get_logger().error(f'Could not write robot command: {error}')

    def destroy_node(self):
        if self.serial_connection.is_open:
            self.serial_connection.close()
        super().destroy_node()


def main(args=None):
    rclpy.init(args=args)
    node = None
    try:
        node = SerialBridgeNode()
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        if node is not None:
            node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()