import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import serial

class SerialNode(Node):
    def __init__(self):
        super().__init__('serial_node')
        
        self.subscription = self.create_subscription(
            String,
            '/perintah_android',
            self.kirim_ke_stm32_callback,
            10)
            
        self.publisher_ = self.create_publisher(String, '/status_stm32', 10)
        
        self.ser = serial.Serial('/dev/ttyUSB0', 115200, timeout=0.1)
        
        self.timer = self.create_timer(0.1, self.baca_dari_stm32)
        self.get_logger().info("Serial konek ke STM32...")

    def kirim_ke_stm32_callback(self, msg):
        perintah = msg.data
        self.get_logger().info(f"Kirim ke kabel: {perintah}")
        self.ser.write((perintah + '\n').encode('utf-8'))

    def baca_dari_stm32(self):
        if self.ser.in_waiting > 0:
            data = self.ser.readline().decode('utf-8').strip()
            if data:
                msg = String()
                msg.data = data
                self.publisher_.publish(msg)

def main(args=None):
    rclpy.init(args=args)
    node = SerialNode()
    rclpy.spin(node)
    rclpy.shutdown()