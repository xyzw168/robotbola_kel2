import socket
import cv2
import rclpy
from cv_bridge import CvBridge
from rclpy.node import Node
from rclpy.qos import qos_profile_sensor_data
from sensor_msgs.msg import Image
from ultralytics import YOLO

class VolleyNode(Node):
    def __init__(self):
        super().__init__("volley_yolo_node")
        p = lambda name, default: self.declare_parameter(name, default).value

        self.model = YOLO(p("model_path", "best2.pt"))
        self.bridge = CvBridge()
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.address = (p("android_ip", "10.131.167.212"), p("android_port", 5005))
        self.conf = p("conf", 0.5)

        self.create_subscription(Image, p("image_topic", "/camera/image_raw"),
                                 self.callback, qos_profile_sensor_data)

    def callback(self, msg):
        frame = self.bridge.imgmsg_to_cv2(msg, "bgr8")
        result = self.model(frame, conf=self.conf, verbose=False)[0]

        for box in result.boxes:
            self.get_logger().info(
                f"{result.names[int(box.cls[0])]} ({float(box.conf[0]):.2f})"
            )

        frame = cv2.resize(result.plot(), (320, 240))
        ok, data = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 35])
        if ok and len(data) <= 8500:
            self.sock.sendto(data.tobytes(), self.address)

    def destroy_node(self):
        self.sock.close()
        super().destroy_node()

def main(args=None):
    rclpy.init(args=args)
    node = VolleyNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()

if __name__ == "__main__":
    main()
