package com.example.websocket;
import static spark.Spark.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class HTTPServer {
    private final Server wsServer;
    private final Gson gson = new Gson();

    public HTTPServer(Server wsServer) {
        this.wsServer = wsServer;
    }

    public void start(int port) {
        port(port);
        staticFiles.location("/public");
        configureRoutes();
        configureErrorHandling();
    }

    private void configureRoutes() {
        get("/api/nodes", (req, res) -> {
            List<Map<String, Object>> nodesList = new ArrayList<>();
            wsServer.getNodes().forEach((nodeId, info) -> {
                Map<String, Object> node = new HashMap<>();
                node.put("id", nodeId);
                node.put("status", info.status.toString());
                node.put("lastActivity", info.lastActivity);
                nodesList.add(node);
            });
            res.type("application/json");
            return gson.toJson(nodesList);
        });

        // MODIFIED: This route now correctly extracts the nested command and requestId
        post("/api/send/:nodeId", (req, res) -> {
            String nodeId = req.params(":nodeId");
            JsonObject incomingFullPayload; // This will hold the entire JSON received from mock_master.py
            try {
                incomingFullPayload = JsonParser.parseString(req.body()).getAsJsonObject();
            } catch (Exception e) {
                res.status(400);
                return "{\"error\":\"Invalid JSON payload\"}";
            }

            // --- CRITICAL FIX START ---
            // Extract the 'command' object which is the *actual* payload for the Python node
            JsonObject commandForNode = incomingFullPayload.getAsJsonObject("command");
            if (commandForNode == null) {
                res.status(400);
                return "{\"error\":\"Missing 'command' object in payload for node\"}";
            }

            // Extract the 'requestId' if it exists in the incoming payload
            String requestId = null;
            if (incomingFullPayload.has("requestId")) {
                requestId = incomingFullPayload.get("requestId").getAsString();
            }
            // --- CRITICAL FIX END ---

            // Now, pass the extracted 'commandForNode' and 'requestId' to the WebSocket server.
            // The Server.java's sendToNodeWithRequestId will add the requestId to the top level
            // of the JSON it sends to the Python node.
            boolean sent = wsServer.sendToNodeWithRequestId(nodeId, commandForNode, requestId);
            res.type("application/json");
            if (sent) {
                // Include requestId in the success response for better tracking
                return "{\"status\":\"Command sent to node " + nodeId + "\", \"requestId\":\"" + (requestId != null ? requestId : "") + "\"}";
            } else {
                res.status(404);
                return "{\"error\":\"Node " + nodeId + " not connected or idle\"}";
            }
        });

        post("/api/disconnect/:nodeId", (req, res) -> {
            String nodeId = req.params(":nodeId");
            boolean disconnected = wsServer.disconnectNode(nodeId);
            res.type("application/json");
            if (disconnected) {
                return "{\"status\":\"Node " + nodeId + " disconnected\"}";
            } else {
                res.status(404);
                return "{\"error\":\"Node " + nodeId + " not found or already disconnected\"}";
            }
        });

        // New endpoint to check Master Server connection (uncomment if needed)
        // get("/api/master/status", (req, res) -> {
        //     res.type("application/json");
        //     Map<String, Object> status = new HashMap<>();
        //     status.put("masterConnected", wsServer.isUpstreamMasterConnected()); // Use the correct method name
        //     return gson.toJson(status);
        // });

        get("/health", (req, res) -> "OK");
    }

    private void configureErrorHandling() {
        exception(Exception.class, (e, req, res) -> {
            System.err.println("[UNHANDLED ERROR] " + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip()
                                + ": " + e.getMessage());
            e.printStackTrace();
            res.status(500);
            res.type("application/json");
            res.header("Access-Control-Allow-Origin", "*");
            res.body("{\"error\":\"Internal server error\"}");
        });
    }

    public static void main(String[] args) {
        System.err.println("Please use MainServer.java to start the application (handles config init).");
        System.exit(1);
    }
}