import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import zmq

class ZmqBridgeNode(Node):
    def __init__(self):
        super().__init__('zmq_node')
        
        self.publisher_ = self.create_publisher(String, '/perintah_android', 10)
        
        context = zmq.Context()
        self.socket = context.socket(zmq.REP)
        self.socket.bind("tcp://*:5555")
        
        self.timer = self.create_timer(0.1, self.cek_pesan_android)
        self.get_logger().info("ZeroMQ siap dengerin HP di port 5555...")

    def cek_pesan_android(self):
        try:
            pesan = self.socket.recv_string(flags=zmq.NOBLOCK)
            self.get_logger().info(f"Android nyuruh: {pesan}")

            msg = String()
            msg.data = pesan
            self.publisher_.publish(msg)
            
            self.socket.send_string("OK_DITERIMA")
            
        except zmq.Again:
            pass

def main(args=None):
    rclpy.init(args=args)
    node = ZmqBridgeNode()
    rclpy.spin(node)
    rclpy.shutdown()

if __name__ == '__main__':
    main()