// src/main/java/com/example/websocket/HTTPServer.java
package com.example.websocket;

import static spark.Spark.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.util.Map;

import com.example.websocket.NodeRegistry.NodeInfo;

public class HTTPServer {
    private final int port;
    private final Server wsServer; 
    private static final Gson gson = new Gson();

    public HTTPServer(int port, Server wsServer) {
        this.port = port;
        this.wsServer = wsServer; // Assign the WebSocket server instance
        System.out.println("HTTPServer: Initialized on port " + port);
    }

    public void start() {
        port(port);
        staticFiles.location("/public");

        // API endpoint to get a list of active nodes
        get("/api/nodes", (req, res) -> {
            res.type("application/json");
            // Assuming Server has a public method getNodes() that returns Map<String, NodeInfo>
            Map<String, NodeInfo> activeNodes = wsServer.getNodes();
            return gson.toJson(activeNodes.values()); // Return values as a list
        });

        // API endpoint to send a command to a specific node by ID
        post("/api/send/:nodeId", (req, res) -> {
            res.type("application/json");
            String nodeId = req.params(":nodeId");

            try {
                JsonObject command = JsonParser.parseString(req.body()).getAsJsonObject();
                if (wsServer.getNodeCommander().sendToNode(nodeId, command)) {
                    return gson.toJson(Map.of("status", "success", "message", "Command sent to node " + nodeId));
                } else {
                    res.status(404);
                    return gson.toJson(Map.of("status", "error", "message", "Node " + nodeId + " not found or not active."));
                }
            } catch (JsonParseException e) {
                res.status(400);
                return gson.toJson(Map.of("status", "error", "message", "Invalid JSON command: " + e.getMessage()));
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", "Server error: " + e.getMessage()));
            }
        });
        post("/api/disconnect/:nodeId", (req, res) -> {
            res.type("application/json");
            String nodeId = req.params(":nodeId");
            if (wsServer.disconnectNode(nodeId)) {
                return gson.toJson(Map.of("status", "success", "message", "Node " + nodeId + " disconnected."));
            } else {
                res.status(404);
                return gson.toJson(Map.of("status", "error", "message", "Node " + nodeId + " not found or could not be disconnected."));
            }
        });

        System.out.println("HTTP Server: Routes configured.");
    }

    public void stop() {
        System.out.println("HTTP Server: Stopping...");
        stop(); // Spark stop method
        System.out.println("HTTP Server: Stopped.");
    }
}