import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import serial
import time

PORT = 'COM8'  # Update to your port
BAUD_RATE = 115200

def main():
    try:
        ser = serial.Serial(PORT, BAUD_RATE, timeout=1.0)
        print(f"Connected to {PORT}. Sending 4 values...")
        time.sleep(2)

        while True:
            # Example values to send (e.g., sensor data, coordinates, or motor speeds)
            val1 = 100
            val2 = 100
            val3 = 10
            val4 = 1
            
            # Format as: "nilai x , nilai y,nilai z, grip\n"
            packet = f"{val1},{val2},{val3},{val4}\n"
            ser.write(packet.encode('utf-8'))
            
            print(f"Sent: {packet.strip()}")
            time.sleep(1.0) # Send once per second

    except serial.SerialException as e:
        print(f"Error: {e}")
    except KeyboardInterrupt:
        print("\nClosing serial port.")
        if 'ser' in locals() and ser.is_open:
            ser.close()

if __name__ == '__main__':
    main()