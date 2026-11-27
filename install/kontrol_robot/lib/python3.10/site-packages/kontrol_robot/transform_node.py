import rclpy
from rclpy.node import Node
from std_msgs.msg import String, Float32
from geometry_msgs.msg import Twist

RENTANG_IN = 100.0  # satuannya persen


def parse_pesan_android(pesan):
    try:
        parts = pesan.split(',')
        if len(parts) != 4:
            return None
        return tuple(float(p.strip()) for p in parts)
    except ValueError:
        return None


def skala(nilai, rentang_in, maks):
    return float((nilai / rentang_in) * maks)


class TransformNode(Node):
    def __init__(self):
        super().__init__('transform_node')
        self.pose_subscriber_ = self.create_subscription(
            String, '/perintah_android', self.cek_pesan_android, 10
        )
        self.cmd_vel_publisher_ = self.create_publisher(Twist, '/cmd_vel', 10)
        self.grip_publisher_ = self.create_publisher(Float32, '/grip/command', 10)
        self.get_logger().info("get data dari HP...")

    def cek_pesan_android(self, msg):
        pesan = msg.data.strip()
        if not pesan:
            self.get_logger().warning("kosong")
            return

        hasil = parse_pesan_android(pesan)
        if hasil is None:
            self.get_logger().warning(f"Format pesan tidak valid: {pesan!r}")
            return
        x, y, z, w = hasil

        cmd = Twist()
        cmd.linear.x = skala(x, RENTANG_IN, 1.0)
        cmd.linear.y = skala(y, RENTANG_IN, 1.0)
        cmd.angular.z = skala(z, RENTANG_IN, 1.0)

        grip = Float32()
        grip.data = w

        self.get_logger().info(
            f"diterima: {pesan} -> x: {cmd.linear.x:.2f}, y: {cmd.linear.y:.2f}, z: {cmd.angular.z:.2f}, grip: {grip.data}"
        )
        self.cmd_vel_publisher_.publish(cmd)
        self.grip_publisher_.publish(grip)


def main(args=None):
    rclpy.init(args=args)
    node = TransformNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()