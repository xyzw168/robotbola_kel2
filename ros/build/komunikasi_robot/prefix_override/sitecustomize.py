import sys
if sys.prefix == '/usr':
    sys.real_prefix = sys.prefix
    sys.prefix = sys.exec_prefix = '/home/fairuz/robotbola_kel2/ros/install/komunikasi_robot'
