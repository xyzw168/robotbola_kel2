import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, TimerAction
from launch.substitutions import LaunchConfiguration, Command
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description():
    package_share = get_package_share_directory('stm32_ros2_control_hardware')
    default_xacro = os.path.join(package_share, 'description', 'stm32_robot.urdf.xacro')
    default_controllers = os.path.join(package_share, 'config', 'controllers.yaml')

    xacro_file = LaunchConfiguration('xacro_file')
    controllers_file = LaunchConfiguration('controllers_file')
    device = LaunchConfiguration('device')
    baud_rate = LaunchConfiguration('baud_rate')

    robot_description = ParameterValue(
        Command(['xacro ', xacro_file,
                 ' device:=', device,
                 ' baud_rate:=', baud_rate]),
        value_type=str,
    )

    control_node = Node(
        package='controller_manager',
        executable='ros2_control_node',
        parameters=[{'robot_description': robot_description}, controllers_file],
        output='both',
    )

    robot_state_publisher = Node(
        package='robot_state_publisher',
        executable='robot_state_publisher',
        parameters=[{'robot_description': robot_description}],
        output='both',
    )

    joint_state_spawner = Node(
        package='controller_manager', executable='spawner',
        arguments=['joint_state_broadcaster', '--controller-manager', '/controller_manager'],
        output='both',
    )
    mecanum_spawner = Node(
        package='controller_manager', executable='spawner',
        arguments=['mecanum_drive_controller', '--controller-manager', '/controller_manager'],
        output='both',
    )
    gripper_spawner = Node(
        package='controller_manager', executable='spawner',
        arguments=['gripper_controller', '--controller-manager', '/controller_manager'],
        output='both',
    )

    cmd_vel_relay = Node(
        package='stm32_ros2_control_hardware', executable='cmd_vel_relay', output='screen')
    grip_adapter = Node(
        package='stm32_ros2_control_hardware', executable='grip_command_adapter', output='screen')

    return LaunchDescription([
        DeclareLaunchArgument('xacro_file', default_value=default_xacro),
        DeclareLaunchArgument('controllers_file', default_value=default_controllers),
        DeclareLaunchArgument('device', default_value='/dev/ttyACM0'),
        DeclareLaunchArgument('baud_rate', default_value='115200'),
        robot_state_publisher,
        control_node,
        TimerAction(period=2.0, actions=[joint_state_spawner]),
        TimerAction(period=3.0, actions=[mecanum_spawner]),
        TimerAction(period=4.0, actions=[gripper_spawner]),
        cmd_vel_relay,
        grip_adapter,
    ])
