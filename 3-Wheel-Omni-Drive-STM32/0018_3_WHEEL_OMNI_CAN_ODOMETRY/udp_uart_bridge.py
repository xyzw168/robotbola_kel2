"""Forward Android UDP control packets to the STM32 UART adapter."""

import argparse
import socket
import sys
from typing import Optional

import serial


def valid_packet(packet: bytes) -> Optional[bytes]:
    try:
        values = [float(value.strip()) for value in packet.decode("ascii").split(",")]
    except (UnicodeDecodeError, ValueError):
        return None
    if len(values) != 4:
        return None
    if not all(-100.0 <= value <= 100.0 for value in values):
        return None
    return (",".join(f"{value:g}" for value in values) + "\n").encode("ascii")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Forward app UDP controls (x,y,z,controller) to STM32 USART2."
    )
    parser.add_argument("serial_port", help="USB-to-UART device, e.g. COM4 or /dev/ttyUSB0")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--listen", default="0.0.0.0")
    parser.add_argument("--udp-port", type=int, default=5005)
    args = parser.parse_args()

    try:
        with serial.Serial(args.serial_port, args.baud, timeout=0) as uart:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
                udp.bind((args.listen, args.udp_port))
                print(
                    f"UDP {args.listen}:{args.udp_port} -> {args.serial_port} "
                    f"at {args.baud} baud (Ctrl+C to stop)",
                    flush=True,
                )
                while True:
                    packet, sender = udp.recvfrom(256)
                    line = valid_packet(packet)
                    if line is None:
                        print(f"Ignoring invalid packet from {sender}", file=sys.stderr)
                        continue
                    uart.write(line)
    except KeyboardInterrupt:
        return 0
    except (OSError, serial.SerialException) as error:
        print(f"Bridge error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
