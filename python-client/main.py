import websocket
import json
import threading
import time
import uuid
import os
import queue
from commands import CommandDispatcher

class NodeClient:
    def __init__(self, server_url="ws://localhost:8080"):
        self.server_url = server_url
        self.node_id = self.get_or_create_node_id()
        self.ws = None
        self.running = False
        self.current_task_state = {}
        self.dispatcher = CommandDispatcher(node_client_ref=self)
        self.command_queue = queue.Queue() # Initialize a thread-safe FIFO queue
        self.worker_thread = threading.Thread(target=self._command_worker, daemon=True)
        
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
    
    def _command_worker(self):
        """
        This method runs in a separate thread. It continuously pulls commands
        from the queue and executes them one by one.
        """
        while self.running: # Loop as long as the client is actively running
            try:
                # Get a command item from the queue. 'timeout=1' prevents blocking indefinitely.
                # If the queue is empty for 1 second, it will raise queue.Empty and loop again.
                command_item = self.command_queue.get(timeout=1)
                
                # --- NEW: Inner try-except for processing the retrieved command ---
                # This ensures task_done() is only called for an item that was actually 'gotten'.
                try: 
                    command_data = command_item['command_data']
                    request_id = command_item['request_id']
                    send_response_callback = command_item['send_response_callback']

                    print(f"[Worker] Processing command: {command_data.get('action', 'N/A')}")
                    
                    # Execute the command using the CommandDispatcher
                    response = self.dispatcher.execute_command(command_data)

                    # Prepare the response message to send back to the WebSocket server
                    response_message = {
                        "type": "node_response",
                        "nodeId": self.node_id,
                        "response": response # The actual result of the command execution
                    }
                    if request_id:
                        response_message["requestId"] = request_id
                    
                    # Send the response back using the provided callback (ws.send)
                    send_response_callback(json.dumps(response_message))
                    print(f"[Worker] Sent response for {command_data.get('action', 'N/A')}: {response.get('status', 'N/A')}")

                except Exception as e:
                    print(f"[Worker] Error executing command from queue: {e}")
                    # Try to send an error response if something went wrong during command execution
                    try:
                        error_response = {
                            "type": "node_response",
                            "nodeId": self.node_id,
                            "response": {
                                "status": "error",
                                "message": f"Worker failed to execute command: {str(e)}"
                            }
                        }
                        # Use the request_id and send_response_callback from the retrieved command_item
                        if request_id: 
                            error_response["requestId"] = request_id
                        if send_response_callback:
                            send_response_callback(json.dumps(error_response))
                    except Exception as send_e:
                        print(f"[Worker] Failed to send error response after internal error: {send_e}")
                finally:
                    # --- CRUCIAL CHANGE: task_done() moved here ---
                    # Mark the task as done because 'command_item' was successfully retrieved by 'get()'.
                    self.command_queue.task_done()
                # --- END NEW INNER TRY-EXCEPT ---

            except queue.Empty:
                # If the queue is empty, just continue the loop and check again
                # No item was retrieved, so no task_done() is needed here.
                continue 
            except Exception as e: # Catch any other unexpected errors during queue.get() or initial fetching
                print(f"[Worker] Unexpected error before command processing: {e}")
                # No command_item was reliably retrieved, so no task_done() can be called for it.
        print("Command worker thread stopped gracefully.")
    
    def on_message(self, ws, message):
        try:
            data = json.loads(message)
            print(f"Received: {message}")
            
            if data.get("type") == "ping":
                pong_response = {"type": "pong"}
                ws.send(json.dumps(pong_response))
                print("Sent pong response")
                return
            
            # --- NEW: Handle incoming pong messages explicitly ---
            if data.get("type") == "pong":
                # print("Received pong from server") # You can uncomment this if you want to log it
                return # Just acknowledge and exit, no response needed for pong
            # --- END NEW ---
            
            if data.get("type") == "command" and "command" in data:
                command_data = data["command"]
                request_id = data.get("requestId")
                
                # --- CHANGE: Add command to queue instead of executing directly ---
                # We put the command data, request_id, and a function to send the response
                self.command_queue.put({
                    'command_data': command_data,
                    'request_id': request_id,
                    'send_response_callback': lambda msg: ws.send(msg) # This callback allows the worker to send
                })
                print(f"Command '{command_data.get('action', 'N/A')}' added to queue for processing by worker.")
                return # CRUCIAL: Do NOT send the response from on_message. The worker thread will handle it.
            
            # If the message is not a recognized type, print a warning
            print(f"Unhandled message type: {data.get('type')}")

        except json.JSONDecodeError:
            print(f"Error: Could not decode JSON message: {message}")
            # If the message isn't even valid JSON, we can't extract request_id from it
            error_response = {
                "type": "node_response",
                "nodeId": self.node_id,
                "response": {
                    "status": "error",
                    "message": "Invalid JSON message received."
                }
            }
            ws.send(json.dumps(error_response)) # Send error for malformed JSON
                
        except Exception as e:
            print(f"Error processing message: {e}")
            error_response = {
                "type": "node_response",
                "nodeId": self.node_id,
                "response": {
                    "status": "error",
                    "message": f"Failed to process command: {str(e)}"
                }
            }
            
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
        self.running = False # Signal the worker thread and ping thread to stop
        # Give a small moment for threads to clean up
        time.sleep(1.5) 
    
    def on_open(self, ws):
        print(f"Connected to server as node: {self.node_id}")
        self.running = True

        
        identification_message = {
            "type": "identify_rpa_node", 
            "nodeId": self.node_id,
            "clientName": "Python_Node_Client",
            "clientVersion": "1.0.0"             
        }
        ws.send(json.dumps(identification_message))
        print(f"Sent identification message: {identification_message['type']}")


        if not self.worker_thread.is_alive():
            self.worker_thread = threading.Thread(target=self._command_worker, daemon=True)
            self.worker_thread.start()
            print("Command worker thread started (after WebSocket connection opened).")
        else:
            print("Command worker thread is already running.")
        
        def ping_thread():
            """Sends periodic pings to keep the WebSocket connection alive."""
            while self.running: # Continue as long as NodeClient is running
                try:
                    ping_message = {"type": "ping"}
                    if ws: # Ensure the WebSocket is still open before sending
                         ws.send(json.dumps(ping_message))
                         # print("Sent ping from ping_thread") # Uncomment for more verbose ping logs
                    else:
                        print("WebSocket not connected, ping_thread stopping.")
                        break # Exit loop if connection is lost
                    time.sleep(25) # Ping every 25 seconds
                except websocket._exceptions.WebSocketConnectionClosedException:
                    print("Ping thread: WebSocket connection closed gracefully.")
                    break
                except Exception as e:
                    print(f"Ping thread error: {e}")
                    break # Exit on any other ping thread error
        
        threading.Thread(target=ping_thread, daemon=True).start() # Start ping thread
    
    def connect(self):
        connection_url = f"{self.server_url}?nodeId={self.node_id}"
        print(f"Connecting to: {connection_url}")
        
        websocket.enableTrace(False) # Disable verbose WebSocket tracing
        self.ws = websocket.WebSocketApp(
            connection_url,
            on_open=self.on_open,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close
        )
        
        self.ws.run_forever() # Blocks until connection is closed or error
    
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