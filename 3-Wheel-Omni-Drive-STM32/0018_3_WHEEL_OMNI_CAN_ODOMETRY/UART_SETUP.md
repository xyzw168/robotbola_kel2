# UART control from the Android app

## Connection path

The Android app sends UDP packets to the laptop. Run `udp_uart_bridge.py` on the
laptop to forward those packets over a USB-to-UART adapter to the STM32 USART2.
Use ST-LINK only to flash/debug the firmware.

## UART wiring

Configure the adapter for **3.3 V logic**, 115200 baud, 8 data bits, no parity,
1 stop bit (8N1). Connect:

| USB-to-UART adapter | STM32F407 |
| --- | --- |
| TXD | PA3 (USART2_RX) |
| RXD | PA2 (USART2_TX) |
| GND | GND |

Do not connect the adapter's 5 V pin to the STM32. RXD is only needed if
two-way UART telemetry is added; the current bridge sends control data to STM32.

## Run

Install the bridge dependency, find the serial device name, then start the bridge:

```sh
python -m pip install -r requirements-bridge.txt
python udp_uart_bridge.py /dev/ttyUSB0
```

On Windows, replace `/dev/ttyUSB0` with the adapter's COM port, such as `COM4`.
In the app, select **UDP**, enter the laptop's Wi-Fi IP address, and use port
`5005`. The laptop and phone need to be on the same network. Allow inbound UDP
port 5005 through the laptop firewall if needed.

The app's current packet is four comma-separated values followed by the bridge's
newline: `forward,strafe,turn,controller`. The firmware treats the first three
as X/Y/rotation commands. The fourth carries the app's grip/controller value;
this drive firmware currently has no gripper actuator output.

The firmware stops drive commands after 300 ms without a valid UART command.
