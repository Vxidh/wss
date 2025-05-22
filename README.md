# WebSocket Node Status Dashboard

## Overview

This project provides a WebSocket-based backend for managing node connections and a dashboard frontend to monitor node status in real time.

## Features

- Nodes connect to the backend via WebSocket and report their status.
- Dashboard UI displays all connected nodes, their status (ACTIVE/IDLE), and last activity.
- REST API for node management (send commands, disconnect nodes).
- JWT-based authentication for all API and WebSocket endpoints.
- Frontend code is modularized: HTML, CSS, and JS are in separate files.

## Directory Structure

```
src/
  main/
    java/com/example/websocket/
      Server.java
      HTTPServer.java
      JWTUtil.java
      ...
    resources/public/
      index.html
      nodes.html
      nodes.js
      nodes.css
```

## Running the Application

1. **Build and Start the Server**
   - Use `MainServer.java` to start the application (handles config and startup).
   - Ensure all dependencies are available (see `pom.xml` or build.gradle).

2. **Access the Dashboard**
   - Open [http://localhost:PORT/nodes](http://localhost:PORT/nodes) in your browser.

3. **Node Authentication**
   - Nodes must connect via WebSocket with a valid JWT as the `authToken` query parameter.

4. **Dashboard Authentication**
   - The dashboard fetches a JWT via `/api/generate-jwt/dashboard-ui` for demo/development.
   - **In production, implement a secure authentication flow for dashboard users.**
     - Require user login.
     - Issue JWTs only after successful authentication.
     - Do not expose `/api/generate-jwt/:nodeId` for public use.

5. **API Endpoints**
   - `GET /api/nodes` — List all nodes (requires JWT).
   - `POST /api/send/:nodeId` — Send command to node (requires JWT).
   - `POST /api/disconnect/:nodeId` — Disconnect node (requires JWT).
   - `GET /api/generate-jwt/:nodeId` — Generate JWT for a node (for development/demo only).

## Frontend Structure

- `nodes.html` — Dashboard UI.
- `nodes.js` — Fetches node data from the API using JWT authentication.
- `nodes.css` — Styles for the dashboard.

## Security Notes

- All API and WebSocket endpoints require JWT authentication.
- **Do not use the public JWT generation endpoint in production for dashboard users.**
- Implement proper user authentication and authorization for dashboard access in production.

## Recommendations for Production

- Replace polling in the dashboard with WebSocket-based real-time updates.
- Secure dashboard access with user authentication (OAuth, SSO, etc.).
- Use HTTPS in production.
- Add monitoring, logging, and error handling as needed.



