import websocket
import json
import threading
import time
import uuid
import os
from commands import CommandDispatcher

class NodeClient:
    def __init__(self, server_url="ws://localhost:8080"):
        self.server_url = server_url
        self.node_id = self.get_or_create_node_id()
        self.ws = None
        self.running = False
        self.dispatcher = CommandDispatcher()
        
    def get_or_create_node_id(self):
        config_file = "node_config.json"
        
        if os.path.exists(config_file):
            try:
                with open(config_file, 'r') as f:
                    config = json.load(f)
                    return config.get('node_id')
            except:
                pass
        
        # Generate new node ID
        node_id = str(uuid.uuid4())
        config = {
            'node_id': node_id,
            'created_at': time.time()
        }
        
        try:
            with open(config_file, 'w') as f:
                json.dump(config, f)
        except Exception as e:
            print(f"Warning: Could not save node config: {e}")
        
        return node_id
    
    def on_message(self, ws, message):
        try:
            data = json.loads(message)
            print(f"Received: {message}")
            
            # Handle ping
            if data.get("type") == "ping":
                pong_response = {"type": "pong"}
                ws.send(json.dumps(pong_response))
                print("Sent pong response")
                return
            
            # Handle commands
            if data.get("type") == "command" and "command" in data:
                command_data = data["command"]
                request_id = data.get("requestId")  # Extract requestId
                
                response = self.dispatcher.execute_command(command_data)
                
                # Send response back to server with requestId
                response_message = {
                    "type": "commandResponse",
                    "nodeId": self.node_id,
                    "response": response
                }
                
                # Include requestId if provided
                if request_id:
                    response_message["requestId"] = request_id
                
                ws.send(json.dumps(response_message))
                print(f"Sent response: {response}")
                
        except Exception as e:
            print(f"Error processing message: {e}")
            error_response = {
                "type": "commandResponse",
                "nodeId": self.node_id,
                "response": {
                    "status": "error",
                    "message": f"Failed to process command: {str(e)}"
                }
            }
            
            # Include requestId if available
            try:
                data = json.loads(message)
                request_id = data.get("requestId")
                if request_id:
                    error_response["requestId"] = request_id
            except:
                pass
                
            ws.send(json.dumps(error_response))
    
    def on_error(self, ws, error):
        print(f"WebSocket error: {error}")
    
    def on_close(self, ws, close_status_code, close_msg):
        print(f"Connection closed: {close_status_code} - {close_msg}")
        self.running = False
    
    def on_open(self, ws):
        print(f"Connected to server as node: {self.node_id}")
        self.running = True
        
        # Start ping thread
        def ping_thread():
            while self.running:
                try:
                    ping_message = {"type": "ping"}
                    ws.send(json.dumps(ping_message))
                    time.sleep(25)  # Ping every 25 seconds
                except:
                    break
        
        threading.Thread(target=ping_thread, daemon=True).start()
    
    def connect(self):
        connection_url = f"{self.server_url}?nodeId={self.node_id}"
        print(f"Connecting to: {connection_url}")
        
        websocket.enableTrace(False)
        self.ws = websocket.WebSocketApp(
            connection_url,
            on_open=self.on_open,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close
        )
        
        self.ws.run_forever()
    
    def start(self):
        while True:
            try:
                self.connect()
            except Exception as e:
                print(f"Connection failed: {e}")
                print("Retrying in 5 seconds...")
                time.sleep(5)

if __name__ == "__main__":
    client = NodeClient()
    client.start()