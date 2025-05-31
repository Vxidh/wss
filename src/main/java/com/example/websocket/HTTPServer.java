// src/main/java/com/example/websocket/HTTPServer.java
package com.example.websocket;

import static spark.Spark.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.net.ConnectException; // Keep if still used, remove if not
import java.util.Map;

import com.example.websocket.NodeRegistry.NodeInfo;
// REMOVE: import com.example.websocket.Server; // Not directly needed as a field if only accessing via methods now.
// If you still have `Server wsServer;` as a field, keep the import. We'll adjust based on your current setup.


public class HTTPServer {
    private final int port;
    private final Server wsServer; // Keep this field for now, it's still useful for other Server methods

    private static final Gson gson = new Gson();

    public HTTPServer(int port, Server wsServer) {
        this.port = port;
        this.wsServer = wsServer; // Assign the WebSocket server instance
        System.out.println("HTTPServer: Initialized on port " + port);
    }

    public void start() {
        port(port);

        // Serve static files from src/main/resources/public
        staticFiles.location("/public");

        // API endpoint to get a list of active nodes
        get("/api/nodes", (req, res) -> {
            res.type("application/json");
            Map<String, NodeInfo> activeNodes = wsServer.getNodes(); // Access via wsServer
            return gson.toJson(activeNodes.values()); // Return values as a list
        });

        // API endpoint to send a command to a specific node by ID
        post("/api/send/:nodeId", (req, res) -> {
            res.type("application/json");
            String nodeId = req.params(":nodeId");

            try {
                JsonObject command = JsonParser.parseString(req.body()).getAsJsonObject();

                // UPDATED LINE: Call sendToNode via NodeCommander retrieved from wsServer
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

        // API endpoint to disconnect a specific node by ID
        post("/api/disconnect/:nodeId", (req, res) -> {
            res.type("application/json");
            String nodeId = req.params(":nodeId");

            if (wsServer.disconnectNode(nodeId)) { // Call disconnectNode on wsServer
                return gson.toJson(Map.of("status", "success", "message", "Node " + nodeId + " disconnected."));
            } else {
                res.status(404);
                return gson.toJson(Map.of("status", "error", "message", "Node " + nodeId + " not found or could not be disconnected."));
            }
        });

        // API endpoint to check Upstream Master connection status
        get("/api/upstream-status", (req, res) -> {
            res.type("application/json");
            boolean isConnected = wsServer.isUpstreamMasterConnected();
            return gson.toJson(Map.of("status", isConnected ? "connected" : "disconnected"));
        });

        System.out.println("HTTP Server: Routes configured.");
    }

    public void stop() {
        System.out.println("HTTP Server: Stopping...");
        // Ensure this calls Spark's stop method correctly.
        // If your Spark import is 'static spark.Spark.*;', then it's just 'stop();'
        // If it's a Spark instance, it would be 'instance.stop();'
        // Assuming static import:
        stop(); // Spark stop method
        System.out.println("HTTP Server: Stopped.");
    }
}