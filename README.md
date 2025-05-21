# Bot Server  

Java backend for remote test automation. Coordinate mouse, keyboard, and gesture actions across multiple nodes using WebSocket and HTTP APIs. Includes simple token auth, node status, and a web setup page.

## Features
- WebSocket server for real-time node control
- HTTP API for sending commands and listing nodes
- Simple token-based node authentication (`authToken`)
- Node status and last activity tracking
- Web client setup page for easy onboarding

## Quick Start
1. Build:
   ```powershell
   mvn clean package
   ```
2. Run everything:
   ```powershell
   .\start.bat
   ```
   - WebSocket: `ws://localhost:8080/ws?nodeId=YOUR_NODE_ID&authToken=cr7`
   - HTTP API: `http://localhost:4567`

## Auth
- All nodes need `authToken` (default: `cr7`). JWT planned.

## Status
Work in progress. Auth, node management, and web onboarding are live.

