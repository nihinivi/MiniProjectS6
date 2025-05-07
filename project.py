from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_jwt_extended import JWTManager, create_access_token, decode_token
from flask_socketio import SocketIO, disconnect
import sqlite3
import threading
import base64
import numpy as np
import cv2
import mediapipe as mp
from ultralytics import YOLO
from time import time
from collections import deque
import os
 
app = Flask(__name__)
app.config["JWT_SECRET_KEY"] = "secretkeyyyy"
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = False
CORS(app)

jwt = JWTManager(app)
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="eventlet")
 
model = YOLO(r".\Model.pt")
os.makedirs("static/detects", exist_ok=True)
 
mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    static_image_mode=False,
    max_num_faces=1,
    refine_landmarks=True
)
 
labels = {
    0: "normal driving",
    1: "texting",
    2: "talking on the phone",
    3: "texting",
    4: "talking on the phone",
    5: "operating the radio",
    6: "drinking",
    7: "reaching behind",
    8: "hair and makeup",
    9: "talking to passenger"
}
 
connected_users = {}
processing_lock = threading.Lock()
is_processing = False
 
def add_user_data(username, data, time):
    conn = sqlite3.connect('driverinfos.db', check_same_thread=False)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS userdata (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            data TEXT NOT NULL,
            time TIMESTAMP
        )
    ''')
    cursor.execute("INSERT INTO userdata (username, data, time) VALUES (?, ?, ?)", (username, data, time))
    conn.commit()
    conn.close()

def getuserpass(uname):
    conn = sqlite3.connect('users.db', check_same_thread=False)
    cursor = conn.cursor()
    cursor.execute('SELECT password FROM users WHERE username=?', (uname,))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else None

def get_all_users():
    conn = sqlite3.connect('users.db', check_same_thread=False)
    cursor = conn.cursor()
    cursor.execute('SELECT username FROM users')
    users = [x[0] for x in cursor.fetchall()]
    conn.close()
    return users

def init_db():
    conn = sqlite3.connect("users.db")
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL
        )
    ''')
    conn.commit()
    conn.close()

    conn2 = sqlite3.connect("driverinfos.db")
    cursor2 = conn2.cursor()
    cursor2.execute('''
        CREATE TABLE IF NOT EXISTS userdata (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            data TEXT NOT NULL,
            time TIMESTAMP
        )
    ''')
    conn2.commit()
    conn2.close() 
class DrowsinessDetector:
    LEFT_EYE_LANDMARKS = [33, 160, 158, 133, 153, 144]
    RIGHT_EYE_LANDMARKS = [362, 385, 387, 263, 373, 380]

    def __init__(self, alert_threshold=0.7):
        self.face_mesh = mp.solutions.face_mesh.FaceMesh(
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        self.calibrated_ear = None
        self.alert_threshold = alert_threshold
        self.recent_states = deque(maxlen=2)

    def eye_aspect_ratio(self, landmarks, eye_indices, w, h):
        def point(idx):
            return np.array([landmarks[idx].x * w, landmarks[idx].y * h])

        vertical1 = np.linalg.norm(point(eye_indices[1]) - point(eye_indices[5]))
        vertical2 = np.linalg.norm(point(eye_indices[2]) - point(eye_indices[4]))
        horizontal = np.linalg.norm(point(eye_indices[0]) - point(eye_indices[3]))
        return (vertical1 + vertical2) / (2.0 * horizontal) if horizontal != 0 else 0.0

    def process_frame(self, frame):
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        h, w = frame.shape[:2]
        result = self.face_mesh.process(rgb)

        if not result.multi_face_landmarks:
            return False

        face_landmarks = result.multi_face_landmarks[0].landmark
        left_ear = self.eye_aspect_ratio(face_landmarks, self.LEFT_EYE_LANDMARKS, w, h)
        right_ear = self.eye_aspect_ratio(face_landmarks, self.RIGHT_EYE_LANDMARKS, w, h)

        ears = [ear for ear in [left_ear, right_ear] if ear > 0.01]
        if not ears:
            return False

        avg_ear = np.mean(ears)

        if self.calibrated_ear is None:
            self.calibrated_ear = avg_ear
            print(f"[INFO] Calibrated EAR: {self.calibrated_ear:.4f}")
            return False

        threshold = self.calibrated_ear * self.alert_threshold
        state = 1 if avg_ear < threshold else 0
        self.recent_states.append(state)

        return list(self.recent_states) == [1, 1]

Detector = DrowsinessDetector()
 
@app.route("/login", methods=["POST"])
def login():
    data = request.json
    username = data.get("username")
    password = data.get("password")

    if username in get_all_users() and getuserpass(username) == password:
        token = create_access_token(identity=username)
        return jsonify({"token": token}), 200
    return jsonify({"error": "Invalid credentials"}), 401
 
@socketio.on("connect")
def handle_connect():
    print("Client connected. Awaiting authentication...")

@socketio.on("auth")
def handle_auth(data):
    sid = request.sid
    token = data.get("token", "")

    try:
        decoded = decode_token(token)
        username = decoded["sub"]
        connected_users[sid] = username
        print(f"Authenticated: {username} (Session: {sid})")
        socketio.emit("auth_success", {"message": "Authenticated"}, room=sid)
    except Exception as e:
        print(f"Authentication failed: {e}")
        disconnect()

@socketio.on("data")
def handle_data(data):
    global is_processing
    sid = request.sid
    username = connected_users.get(sid)

    if not username:
        disconnect()
        return

    if is_processing:
        return

    decoded = base64.b64decode(data)
    socketio.start_background_task(process_frame, decoded, username, socketio)

@socketio.on("image")
def handle_image(data):
    global is_processing
    sid = request.sid
    username = connected_users.get(sid)

    if not username:
        disconnect()
        return

    if is_processing:
        return

    decoded = base64.b64decode(data)
    process_image(username, decoded, socketio)

@socketio.on("disconnect")
def handle_disconnect():
    sid = request.sid
    if sid in connected_users:
        print(f"{connected_users[sid]} disconnected")
 
def process_frame(decoded_data, username, socketio):
    global is_processing
    with processing_lock:
        is_processing = True

    try:
        frame = cv2.imdecode(np.frombuffer(decoded_data, np.uint8), cv2.IMREAD_COLOR)
        slp = Detector.process_frame(frame)
        dst = model.predict(frame)[0].probs.top1
        ctime = str(time())

        if slp:
            socketio.emit("server_response", "1")
            socketio.sleep(10)
            cv2.imwrite(f"static/detects/{ctime}.jpg", frame)
            add_user_data(username, "Sleeping", ctime)
            socketio.emit("server_response", "0")

        dst_conf = model.predict(frame)[0].probs.top1conf
        if dst and dst_conf > 0.80:
            socketio.emit("server_response", "1")
            socketio.sleep(10)
            cv2.imwrite(f"static/detects/{ctime}.jpg", frame)
            add_user_data(username, labels[dst], ctime)
            socketio.emit("server_response", "0")

    except Exception as e:
        print(f"Error processing frame: {e}")
    finally:
        with processing_lock:
            is_processing = False

def process_image(username, decoded_data, socketio):
    global is_processing
    try:
        frame = cv2.imdecode(np.frombuffer(decoded_data, np.uint8), cv2.IMREAD_COLOR)
        slp = Detector.process_frame(frame)
        ctime = str(time())
        filename = f"static/detects/{ctime}.jpg"

        if slp:
            socketio.emit("img_response", "Sleeping")
            cv2.imwrite(filename, frame)
            add_user_data(username, "Sleeping", ctime)
            return

        dst = model.predict(frame)[0].probs.top1
        socketio.emit("img_response", labels[dst])
        cv2.imwrite(filename, frame)
        if dst:
            add_user_data(username, labels[dst], ctime)

    except Exception as e:
        print(f"Error in process_image: {e}")
    finally:
        with processing_lock:
            is_processing = False
 
init_db()
socketio.run(app, host="0.0.0.0", port=5000, debug=True)
