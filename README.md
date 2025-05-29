# WebSocket Node Relay System

## Overview

- Users register on the website and download the Electron agent.
- The Electron agent generates and persists a unique node ID per device.
- The agent connects to the backend via WebSocket (no authentication).
- Users interact with the web dashboard to send commands to their node.
- The backend relays commands to the agent, which executes them and can send results (e.g., screenshots) back.

## Architecture

+----------------+       HTTP POST       +---------------------+     WebSocket      +------------------+
|                |---------------------->|                     |------------------->|                  |
| Master/Orchestrator                    | Java Server         |                    | Python RPA Agent |
| (e.g., PowerShell/curl)                | (HTTPServer & Server)|<-------------------| (main.py)        |
|                |                       |                     |    Command/Status  |                  |
+----------------+                       +---------------------+                    +------------------+
                                                    |
                                                    | WebSocket
                                                    |
                                                    v
                                          +------------------+
                                          | Python RPA Agent |
                                          | (main.py)        |
                                          +------------------+
                                          (Supports multiple agents concurrently)

## Quick Start

1. Build and run the backend (`MainServer.java`).
2. Access the dashboard at `/nodes`.
3. Download and run the Electron agent on the client device.
4. Use the dashboard to send commands to the node.

---

## Testing the Setup

1. **Start the backend server**  
   Ensure `MainServer.java` is running and listening for WebSocket connections.

2. **Open the dashboard**  
   Visit `http://localhost:<port>/nodes` in your browser. You should see a list of connected nodes (initially empty).

3. **Run the Electron agent**  
   Start the Electron agent on a client device. It should connect to the backend and appear in the dashboard node list.

4. **Send a command**  
   Use the dashboard to send a test command (e.g., "ping" or "screenshot") to the connected node.

5. **Verify agent response**  
   The agent should execute the command and send the result (e.g., a screenshot or a "pong" message) back to the backend. The dashboard should display the result if implemented.

6. **Check logs**  
   Review backend and agent logs for connection, command relay, and response messages to confirm end-to-end communication.

7. **Troubleshooting**  
   - Ensure firewall/antivirus is not blocking WebSocket connections.
   - Confirm backend and agent are using the same WebSocket URL and port.
   - Check browser console and backend logs for errors.



