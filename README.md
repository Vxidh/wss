# WebSocket Node Relay System

## Overview

- Users register on the website and download the Electron agent.
- The Electron agent generates and persists a unique node ID per device.
- The agent connects to the backend via WebSocket (no authentication).
- Users interact with the web dashboard to send commands to their node.
- The backend relays commands to the agent, which executes them and can send results (e.g., screenshots) back.

## Architecture

```mermaid
flowchart TD
    subgraph User Device
        A[Electron Agent<br>(Tray App)]
    end
    subgraph Backend
        B[WebSocket/HTTP Server]
    end
    subgraph Web
        C[Website/Dashboard]
    end

    A -- Connects with nodeId --> B
    C -- User registers, downloads agent --> A
    C -- Sends commands (via API) --> B
    B -- Relays commands --> A
    A -- Sends results (e.g. screenshot) --> B
    B -- (Optional) Forwards results to C
```

## Quick Start

1. Build and run the backend (`MainServer.java`).
2. Access the dashboard at `/nodes`.
3. Download and run the Electron agent on the client device.
4. Use the dashboard to send commands to the node.

---



