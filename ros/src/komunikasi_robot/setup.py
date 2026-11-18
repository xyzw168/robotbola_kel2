from setuptools import find_packages, setup

package_name = 'komunikasi_robot'

setup(
    name=package_name,
    version='0.0.0',
    packages=find_packages(exclude=['test']),
    data_files=[
        ('share/ament_index/resource_index/packages',
            ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml']),
    ],
    install_requires=['setuptools'],
    zip_safe=True,
    maintainer='ulil',
    maintainer_email='ulil@todo.todo',
    description='TODO: Package description',
    license='TODO: License declaration',
    extras_require={
        'test': [
            'pytest',
        ],
    },
    entry_points={
        'console_scripts': [
            'serial_node = komunikasi_robot.serial_node:main',
            'wifi_node = komunikasi_robot.wifi_node:main',
            'udp_node = komunikasi_robot.udp_node:main'
        ],
    },
)
