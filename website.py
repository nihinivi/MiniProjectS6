from flask import Flask, request, render_template, redirect, url_for, send_from_directory
import sqlite3
import os
from datetime import datetime
from collections import defaultdict

app = Flask(__name__)

THRESHOLD = 2
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
USERS_DB = 'users.db'
DRIVER_DB = 'driverinfos.db'


def init_db():
    conn = sqlite3.connect(USERS_DB)
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

    conn2 = sqlite3.connect(DRIVER_DB)
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


def get_current_month_time_range():
    now = datetime.now()
    start_of_month = datetime(now.year, now.month, 1)
    next_month = datetime(now.year + 1, 1, 1) if now.month == 12 else datetime(now.year, now.month + 1, 1)
    return int(start_of_month.timestamp()), int(next_month.timestamp())


def get_user_data_counts(usernames):
    start_epoch, end_epoch = get_current_month_time_range()
    if not os.path.exists(DRIVER_DB):
        return {user: 0 for user in usernames}

    conn = sqlite3.connect(DRIVER_DB)
    cursor = conn.cursor()
    counts = {}

    for username in usernames:
        cursor.execute('''
            SELECT COUNT(*) FROM userdata
            WHERE username = ? AND time BETWEEN ? AND ?
        ''', (username, start_epoch, end_epoch))
        count = cursor.fetchone()[0] if cursor else 0
        counts[username] = count

    conn.close()
    return counts


def getspecifc(username):
    start_epoch, end_epoch = get_current_month_time_range()
    if not os.path.exists(DRIVER_DB):
        return []

    conn = sqlite3.connect(DRIVER_DB)
    cursor = conn.cursor()
    cursor.execute('''
        SELECT data FROM userdata
        WHERE username = ? AND time BETWEEN ? AND ?
    ''', (username, start_epoch, end_epoch))
    result = [x[0] for x in cursor.fetchall()]
    conn.close()
    return result


@app.route('/')
def index():
    conn = sqlite3.connect(USERS_DB)
    cursor = conn.cursor()
    cursor.execute('SELECT username FROM users')
    users = [u[0] for u in cursor.fetchall()]
    conn.close()

    counts = get_user_data_counts(users)
    sorted_users_data = sorted(
        [(user, counts.get(user, 0)) for user in users],
        key=lambda x: x[1],
        reverse=True
    )

    return render_template('index.html', users_data=sorted_users_data, threshold=THRESHOLD)


@app.route('/', methods=['POST'])
def handle_form():
    conn = sqlite3.connect(USERS_DB)
    cursor = conn.cursor()

    username = request.form.get('username', '').strip()
    password = request.form.get('password', '').strip()

    if username:
        if 'add' in request.form and password:
            try:
                cursor.execute('INSERT INTO users (username, password) VALUES (?, ?)', (username, password))
                conn.commit()
            except sqlite3.IntegrityError:
                pass
        elif 'delete' in request.form:
            cursor.execute('DELETE FROM users WHERE username = ?', (username,))
            conn.commit()

            conn2 = sqlite3.connect(DRIVER_DB)
            c2 = conn2.cursor()
            c2.execute('DELETE FROM userdata WHERE username = ?', (username,))
            conn2.commit()
            conn2.close()

    conn.close()
    return redirect(url_for('index'))


@app.route('/user/<username>')
def user_detail(username):
    dct = defaultdict(int)
    for item in getspecifc(username):
        dct[item] += 1

    info = {
        'entries': get_user_data_counts([username])[username],
        'distr': dct
    }

    return render_template('user_detail.html', username=username, info=info)


@app.route('/user/<username>/images')
def view_images(username):
    start_epoch, end_epoch = get_current_month_time_range()

    conn = sqlite3.connect(DRIVER_DB)
    cursor = conn.cursor()
    cursor.execute('''
        SELECT data, time FROM userdata
        WHERE username = ? AND time BETWEEN ? AND ?
        ORDER BY time DESC
    ''', (username, start_epoch, end_epoch))

    results = cursor.fetchall()
    conn.close()

    grouped_data = defaultdict(list)
    for category, ts in results:
        readable = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M')
        grouped_data[category].append((ts, readable))

    return render_template("view_images.html", username=username, grouped_data=grouped_data)


@app.route('/download/<username>/<timestamp>')
def download_image(username, timestamp):
    image_folder = r".\Website\static\detects"
    filename = f"{timestamp}.jpg"
    try:
        return send_from_directory(
            directory=image_folder,
            path=filename,
            as_attachment=True,
            download_name=filename
        )
    except FileNotFoundError:
        return "Image not found", 404


if __name__ == '__main__':
    init_db()
    app.run(debug=True, port=11222)
