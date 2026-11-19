# STM32 ros2_control Hardware Interface (ROS 2 Humble)

This package is a **real C++ `hardware_interface::SystemInterface`**. It opens a Linux serial device (USB CDC or USB-UART) and exchanges newline-delimited ASCII frames directly with an STM32. It is not a mock/dummy hardware interface.

The package includes:

- `STM32System`: four wheel velocity joints plus one gripper position joint.
- A ROS `Twist` relay from `/cmd_vel` to the Humble mecanum controller input topic.
- A gripper adapter from `/grip/command` (`std_msgs/Int16`, degrees) to the gripper controller (radians).
- Xacro, controller YAML, and a bringup launch file.

## IMPORTANT: firmware protocol must match

Since the STM32 firmware/protocol from Evan was not supplied, this package uses the following explicit **starter protocol**. The STM32 firmware must implement exactly the same framing and units, or the C++ parser/formatter must be adapted.

Host -> STM32 (one line, repeated by the ros2_control write loop, at the configured controller rate):

```text
CMD,<front_left_rad_s>,<front_right_rad_s>,<rear_left_rad_s>,<rear_right_rad_s>,<gripper_degrees>\n
```

STM32 -> Host (send periodically, each record ending with newline):

```text
FB,<front_left_rad_s>,<front_right_rad_s>,<rear_left_rad_s>,<rear_right_rad_s>,<gripper_degrees>\n
```

- Wheel values are **angular velocity in rad/s**.
- Gripper value is **degrees, 0..180** on the serial wire. The ROS joint interface uses radians; conversion is done in the hardware interface.
- Serial framing is 8 data bits, no parity, 1 stop bit (8N1), raw ASCII, comma separated, newline terminated.
- Default serial settings are `/dev/ttyACM0`, `115200` baud.
- The current protocol is a starter format: it has no checksum, sequence number, or explicit ACK. Do not treat it as a production safety protocol.

If Evan's firmware uses different packet names, units, field order, or feedback format, agree the final protocol and update `make_command_frame()` / `parse_feedback_line()` accordingly. Opening the serial device only proves Linux can open the port; it does not prove STM32 and host understand each other.

## Install dependencies on Ubuntu 22.04 / ROS 2 Humble

```bash
sudo apt update
sudo apt install -y \
  ros-humble-ros2-control \
  ros-humble-ros2-controllers \
  ros-humble-mecanum-drive-controller \
  ros-humble-position-controllers \
  ros-humble-joint-state-broadcaster \
  ros-humble-xacro \
  ros-humble-robot-state-publisher
```

If a package name is unavailable in your apt snapshot, `ros-humble-ros2-controllers` normally provides the controller plugins; check with `apt search ros-humble-mecanum-drive-controller`.

## Add to workspace

Copy this entire package folder into `~/ros2_ws/src/`:

```text
~/ros2_ws/src/stm32_ros2_control_hardware/
```

Build and source:

```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
rosdep install --from-paths src --ignore-src -r -y
colcon build --packages-select stm32_ros2_control_hardware --symlink-install
source install/setup.bash
```

Verify plugin package is visible:

```bash
ros2 pkg list | grep stm32_ros2_control_hardware
```

## Serial permission and port

Connect the STM32 board, then inspect the device:

```bash
ls -l /dev/ttyACM* /dev/ttyUSB* 2>/dev/null
python3 -m serial.tools.list_ports -v
```

If the current user is not in `dialout`:

```bash
sudo usermod -aG dialout $USER
```

Log out and log in again (or reboot) so the new group takes effect. Do not run the ROS control node with `sudo` as a workaround.

## Launch

Make sure no other program (including a previous Python serial bridge, serial monitor, or STM32CubeIDE terminal) has the same serial port open. Only this hardware interface should own the device while running.

Start with the wheel drive physically unable to move and verify the firmware protocol before enabling actuators. Then:

```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
source install/setup.bash
ros2 launch stm32_ros2_control_hardware bringup.launch.py device:=/dev/ttyACM0 baud_rate:=115200
```

The launch starts `ros2_control_node`, `robot_state_publisher`, the joint-state broadcaster, mecanum drive controller, gripper position controller, and the two small topic adapters.

## Topic contracts

- `/cmd_vel`: `geometry_msgs/msg/Twist`, relayed to `/mecanum_drive_controller/reference_unstamped` (the mecanum controller is configured with `use_stamped_vel: false`).
- `/grip/command`: `std_msgs/msg/Int16`, degrees 0..180, converted to radians for the gripper joint.
- `/joint_states`: published by `joint_state_broadcaster`.
- `/mecanum_drive_controller/odometry`: mecanum controller odometry (only meaningful once feedback values are valid and wheel geometry is calibrated).

## Check the loaded system

In another terminal:

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
ros2 control list_hardware_components
ros2 control list_hardware_interfaces
ros2 control list_controllers
ros2 topic echo /joint_states
```

Send a small software command only after the hardware is restrained and the STM32 firmware/protocol is confirmed:

```bash
ros2 topic pub --once /cmd_vel geometry_msgs/msg/Twist "{linear: {x: 0.02, y: 0.0}, angular: {z: 0.0}}"
ros2 topic pub --once /grip/command std_msgs/msg/Int16 "{data: 90}"
```

## Parameters in Xacro

The hardware tag contains:

- `device`: Linux serial path.
- `baud_rate`: baud rate supported by this starter driver (9600, 19200, 38400, 57600, 115200, optionally 230400).
- `wheel_joint_names`: four comma-separated wheel joints in the required order.
- `gripper_joint_name`: gripper joint name.
- `require_feedback`: if `true`, `read()` returns `ERROR` when a valid `FB,...` frame is not received within `feedback_timeout_ms`. Default `false` is useful for initial port-open checks; set `true` for integrated testing once STM32 is sending feedback reliably.
- `feedback_timeout_ms`: feedback freshness timeout.

The mecanum geometry in `config/controllers.yaml` (`wheels_radius` and `sum_of_robot_center_projection_on_X_Y_axis`) is an example and must be replaced by measured robot dimensions. Wheel joint signs/order must also be agreed with the firmware/motor wiring.

## Important integration note

Do **not** run `stm32_serial_bridge` and this hardware interface against the same `/dev/ttyACM0` at the same time. Both would compete for the same serial port. This package replaces the Python serial bridge as the single serial owner for the `ros2_control` architecture. If the team decides to keep the Python node as the owner instead, the hardware interface must communicate with that node through a ROS interface rather than opening the serial port itself.
