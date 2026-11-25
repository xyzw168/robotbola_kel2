import cv2
import socket
import struct
import time

ANDROID_IP = "10.131.224.93"
PORT_KAMERA = 5006 

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
cap = cv2.VideoCapture(0)  # 0 = Kamera Webcam bawaan Laptop

if not cap.isOpened():
    print("❌ Error: Webcam laptop tidak ditemukan atau gagal dibuka!")
    exit()

CHUNK_SIZE = 1400
frame_id = 0

print(f"🚀 Streaming webcam laptop ke {ANDROID_IP}:{PORT_KAMERA}...")
print("Tekan tombol 'q' di jendela video laptop untuk berhenti.")

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    cv2.imshow("Webcam Laptop (Pengirim)", frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

    frame_resized = cv2.resize(frame, (640, 480))
    _, jpeg = cv2.imencode('.jpg', frame_resized, [int(cv2.IMWRITE_JPEG_QUALITY), 50])
    data = jpeg.tobytes()
    total_bytes = len(data)

    if total_bytes <= CHUNK_SIZE:
        sock.sendto(data, (ANDROID_IP, PORT_KAMERA))
    else:
        num_packets = (total_bytes + CHUNK_SIZE - 1) // CHUNK_SIZE
        for index in range(num_packets):
            start = index * CHUNK_SIZE
            end = min(start + CHUNK_SIZE, total_bytes)
            chunk = data[start:end]
            header = struct.pack('>HHHH', frame_id & 0xFFFF, num_packets, index, len(chunk))
            sock.sendto(header + chunk, (ANDROID_IP, PORT_KAMERA))

    frame_id = (frame_id + 1) % 65536
    time.sleep(0.03)
    
cap.release()
cv2.destroyAllWindows()
sock.close()