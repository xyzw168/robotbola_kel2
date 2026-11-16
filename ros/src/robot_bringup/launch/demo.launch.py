from launch import LaunchDescription
from launch_ros.actions import Node

def generate_launch_description():

    wifi_node = Node(
        package='komunikasi_robot',
        executable='wifi_node',
        name='wifi_node',
        output='screen'
    )

    serial_node = Node(
        package='komunikasi_robot',
        executable='serial_node',
        name='serial_node',
        output='screen'
    )
    
    udp_node = Node(
        package='komunikasi_robot',
        executable='udp_node',
        name='udp_node',
        output='screen'
    )

    return LaunchDescription([
        wifi_node,
        serial_node,
        udp_node
    ])
