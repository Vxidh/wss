import threading
import time
import json
import queue
import websocket
from flask import Flask, request, jsonify
import uuid

app = Flask(__name__)

# Thread-safe queue to hold commands to send via WebSocket
command_queue = queue.Queue()

# Global websocket client reference
ws_client = None

# Replace with your real admin token for relay auth
ADMIN_TOKEN = "super-secret-admin-token"

# WebSocket URL including adminToken for authentication
RELAY_WS_URL = f"ws://localhost:8080?adminToken={ADMIN_TOKEN}"
print(f"DEBUG: WebSocket URL being used: {RELAY_WS_URL}")
# --- Flask HTTP API ---

@app.route('/send', methods=['POST'])
def send_command():
    data = request.get_json()
    if not data:
        return jsonify({"error": "Invalid JSON"}), 400
    
    # Add unique requestId if missing
    if "requestId" not in data:
        data["requestId"] = str(uuid.uuid4())
    
    # Validate nodeId and command exist
    if "nodeId" not in data or "command" not in data:
        return jsonify({"error": "Missing 'nodeId' or 'command' in payload"}), 400

    # Wrap into relay format with type "node_command"
    message = {
        "type": "node_command",
        "nodeId": data["nodeId"],
        "requestId": data["requestId"],
        "command": data["command"]
    }

    # Put command in queue to be sent via WebSocket
    command_queue.put(message)
    print(f"[HTTP API] Queued command: {message}")

    return jsonify({"status": "queued", "requestId": data["requestId"]}), 200

# --- WebSocket event handlers ---

def ws_on_message(ws, message):
    print(f"[Relay WS] Received message: {message}")

def ws_on_error(ws, error):
    print(f"[Relay WS] Error: {error}")

def ws_on_close(ws, close_status_code, close_msg):
    print(f"[Relay WS] Connection closed: {close_status_code} - {close_msg}")

def ws_on_open(ws):
    print("[Relay WS] Connected to relay server")

# --- Thread managing the WebSocket connection ---

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

# --- Thread sending commands from queue to relay ---

def sender_thread():
    global ws_client
    while True:
        command = command_queue.get()  # Blocking wait
        if ws_client and ws_client.sock and ws_client.sock.connected:
            try:
                ws_client.send(json.dumps(command))
                print(f"[Relay WS] Sent command: {command}")
            except Exception as e:
                print(f"[Relay WS] Failed to send command: {e}")
                # Optionally re-queue command or handle failure here
        else:
            print("[Relay WS] Not connected, command not sent")

# --- Main entry point ---

if __name__ == "__main__":
    # Start WebSocket client thread
    threading.Thread(target=websocket_thread, daemon=True).start()
    
    # Start sender thread to forward commands from queue to WS
    threading.Thread(target=sender_thread, daemon=True).start()
    
    # Start Flask server on port 9999
    print("Starting mock master HTTP server on port 9999...")
    app.run(host='0.0.0.0', port=9999)
