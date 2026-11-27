
from math import sin, cos, radians

class OmniKinematics3():
    def __init__(self, wheel_deg, wheel_radius, robot_radius, max_speed):
        self.wheel_deg = wheel_deg
        self.wheel_radius = wheel_radius
        self.robot_radius = robot_radius
        self.max_speed = max_speed

    def wheel_speeds(self, vx, vy, omega):
        hasil = []
        for beta in self.wheel_deg:
            b = radians(beta)
            v = ((cos(b) * vy + sin(b) * vx + self.robot_radius * omega) / self.wheel_radius) 
            hasil.append(v)
        return hasil
    def normalize_speeds(self, speeds):
        batas = self.max_speed
        max_magnitude = max(abs(speed) for speed in speeds)
        if max_magnitude > batas:
            return [speed * (batas / max_magnitude) for speed in speeds]
        return speeds