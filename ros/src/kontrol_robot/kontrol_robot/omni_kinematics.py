
from math import sin, cos, radians

class OmniKinematics3():
    def __init__(self, wheel_angles_deg, wheel_radius, robot_radius, max_speed):
        self.wheel_angles_deg = wheel_angles_deg
        self.wheel_radius = wheel_radius
        self.robot_radius = robot_radius
        self.max_speed = max_speed

    def wheel_speeds(self, vx, vy, omega):
        # speeds = []
        # for angle_deg in self.wheel_angles_deg:
        #     angle_rad = radians(angle_deg)
        #     speed = (vx * cos(angle_rad) + vy * sin(angle_rad) + omega * self.robot_radius) / self.wheel_radius
        #     speeds.append(speed)
        # return speeds
        
        hasil = []
        for beta in self.wheel_drive_angles_deg:
            b = radians(beta)
            v = (cos(b) * vx + sin(b) * vy - self.robot_radius * omega) / self.wheel_radius
            hasil.append(v)
        return hasil
    def normalize_speeds(self, speeds):
        batas = self.max_speed
        max_magnitude = max(abs(speed) for speed in speeds)
        if max_magnitude > batas:
            return [speed * (batas / max_magnitude) for speed in speeds]
        return speeds