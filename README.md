# Bot Server

A Java backend for coordinating automated test actions (mouse, keyboard, gestures) across multiple remote nodes using WebSocket and HTTP APIs.

## Features
- WebSocket server for real-time communication with remote nodes (clients)
- HTTP API to send commands to specific nodes
- Supports mouse movement, clicks, keyboard typing, and custom commands
- Node activity tracking and idle detection

## Components
- **Server.java**: WebSocket server managing node connections and command delivery
- **HTTPServer.java**: HTTP API for sending commands to nodes and health checks
- **Client.java**: Example client that connects to the server and executes received commands using Java's `Robot` class

## Dependencies
- Java 11+
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket)
- [SparkJava](http://sparkjava.com/)
- [Gson](https://github.com/google/gson)
- [org.json](https://github.com/stleary/JSON-java)

