import threading
import time
import json
import queue
import websocket
from flask import Flask, request, jsonify
import uuid

app = Flask(__name__)

command_queue = queue.Queue()

ws_client = None

ADMIN_TOKEN = "super-secret-admin-token"

RELAY_WS_URL = f"ws://localhost:8080?adminToken={ADMIN_TOKEN}"
print(f"DEBUG: WebSocket URL being used: {RELAY_WS_URL}")

@app.route('/send', methods=['POST'])
def send_command():
    data = request.get_json()
    if not data:
        return jsonify({"error": "Invalid JSON"}), 400
    
    if "requestId" not in data:
        data["requestId"] = str(uuid.uuid4())
    
    if "nodeId" not in data or "command" not in data:
        return jsonify({"error": "Missing 'nodeId' or 'command' in payload"}), 400

    message = {
        "type": "node_command",
        "nodeId": data["nodeId"],
        "requestId": data["requestId"],
        "command": data["command"]
    }

    command_queue.put(message)
    print(f"[HTTP API] Queued command: {message}")

    return jsonify({"status": "queued", "requestId": data["requestId"]}), 200


def ws_on_message(ws, message):
    print(f"[Relay WS] Received message: {message}")

def ws_on_error(ws, error):
    print(f"[Relay WS] Error: {error}")

def ws_on_close(ws, close_status_code, close_msg):
    print(f"[Relay WS] Connection closed: {close_status_code} - {close_msg}")

def ws_on_open(ws):
    print("[Relay WS] Connected to relay server")


def websocket_thread():
    global ws_client
    while True:
        print(f"[Relay WS] Connecting to {RELAY_WS_URL}")
        ws_client = websocket.WebSocketApp(RELAY_WS_URL,
                                           on_open=ws_on_open,
                                           on_message=ws_on_message,
                                           on_error=ws_on_error,
                                           on_close=ws_on_close)
        ws_client.run_forever()
        print("[Relay WS] Disconnected. Reconnecting in 5 seconds...")
        time.sleep(5)


def sender_thread():
    global ws_client
    while True:
        command = command_queue.get()  
        if ws_client and ws_client.sock and ws_client.sock.connected:
            try:
                ws_client.send(json.dumps(command))
                print(f"[Relay WS] Sent command: {command}")
            except Exception as e:
                print(f"[Relay WS] Failed to send command: {e}")
        else:
            print("[Relay WS] Not connected, command not sent")


if __name__ == "__main__":
    threading.Thread(target=websocket_thread, daemon=True).start()
    
    threading.Thread(target=sender_thread, daemon=True).start()
    print("Starting mock master HTTP server on port 9999...")
    app.run(host='0.0.0.0', port=9999)
