package com.example.websocket;

import static spark.Spark.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

public class HTTPServer {
    private final Server wsServer;
    private final Gson gson = new Gson();

    private static final String AUTH_SECRET = "cr7"; // Static token for now. Need to migrate to JWT to make sure that there's a proper auth system in place.

    public HTTPServer(Server wsServer) {
        this.wsServer = wsServer;
    }

    public void start(int port) {
        port(port);

        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        before("/api/*", (req, res) -> {
            String authHeader = req.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                halt(401, "{\"error\":\"Missing or invalid Authorization header\"}");
            } else {
                String token = authHeader.substring("Bearer ".length());
                if (!token.equals(AUTH_SECRET)) {
                    halt(401, "{\"error\":\"Invalid token\"}");
                }
            }
        });

        get("/api/nodes", (req, res) -> {
            List<Map<String, Object>> nodesList = new ArrayList<>();
            wsServer.getNodes().forEach((nodeId, info) -> {
                Map<String, Object> nodeInfo = new HashMap<>();
                nodeInfo.put("id", nodeId);
                nodeInfo.put("status", info.status.toString());
                nodeInfo.put("lastActivity", info.lastActivity);
                nodesList.add(nodeInfo);
            });
            res.type("application/json");
            return gson.toJson(nodesList);
        });

        post("/api/send/:nodeId", (req, res) -> {
            String nodeId = req.params(":nodeId");
            JsonObject command;
            try {
                command = JsonParser.parseString(req.body()).getAsJsonObject();
            } catch (Exception e) {
                res.status(400);
                return "{\"error\":\"Invalid JSON payload\"}";
            }
            boolean sent = wsServer.sendToNode(nodeId, command);
            if (sent) {
                res.type("application/json");
                return "{\"status\":\"Command sent to node " + nodeId + "\"}";
            } else {
                res.status(404);
                return "{\"error\":\"Node " + nodeId + " not connected or idle\"}";
            }
        });

        get("/", (req, res) -> {
            res.type("text/html");
            return getClientSetupPage();
        });

        get("/health", (req, res) -> {
            res.type("text/plain");
            return "OK";
        });

        exception(Exception.class, (e, req, res) -> {
            res.status(500);
            res.body("{\"error\":\"Internal server error\"}");
            e.printStackTrace();
        });
    }

    private String getClientSetupPage() {
        return "<!DOCTYPE html>" +
               "<html><head><title>Bot Test Node Setup</title>" +
               "<style>" +
               "body { font-family: Arial, sans-serif; margin: 20px; }" +
               ".container { max-width: 800px; margin: 0 auto; }" +
               ".status { margin: 20px 0; padding: 10px; border-radius: 5px; }" +
               ".connected { background-color: #dff0d8; }" +
               ".disconnected { background-color: #f2dede; }" +
               "button { padding: 10px; margin: 10px 0; }" +
               "</style></head>" +
               "<body><div class='container'>" +
               "<h1>Bot Test Node Setup</h1>" +
               "<p>This page helps you set up a test node that will connect to the test server.</p>" +
               "<div><label for='nodeId'>Node ID: </label>" +
               "<input type='text' id='nodeId' value='node-" + System.currentTimeMillis() + "'></div>" +
               "<div><button id='connectBtn'>Connect Node</button></div>" +
               "<div id='status' class='status disconnected'>Disconnected</div>" +
               "<div id='log'></div>" +
               "<script>" +
               "let ws = null;" +
               "document.getElementById('connectBtn').addEventListener('click', () => {" +
               "  const nodeId = document.getElementById('nodeId').value;" +
               "  if (!nodeId) {" +
               "    alert('Please enter a Node ID');" +
               "    return;" +
               "  }" +
               "  connect(nodeId);" +
               "});" +
               "function connect(nodeId) {" +
               "  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';" +
               "  const wsUrl = protocol + '//' + location.host + '/ws?nodeId=' + encodeURIComponent(nodeId);" +
               "  log('Connecting to ' + wsUrl);" +
               "  ws = new WebSocket(wsUrl);" +
               "  ws.onopen = () => {" +
               "    log('Connected');" +
               "    document.getElementById('status').textContent = 'Connected as ' + nodeId;" +
               "    document.getElementById('status').className = 'status connected';" +
               "  };" +
               "  ws.onmessage = (event) => {" +
               "    log('Received: ' + event.data);" +
               "  };" +
               "  ws.onclose = () => {" +
               "    log('Disconnected');" +
               "    document.getElementById('status').textContent = 'Disconnected';" +
               "    document.getElementById('status').className = 'status disconnected';" +
               "  };" +
               "  ws.onerror = (error) => {" +
               "    log('Error: ' + error);" +
               "  };" +
               "}" +
               "function log(message) {" +
               "  const logDiv = document.getElementById('log');" +
               "  const entry = document.createElement('div');" +
               "  entry.textContent = new Date().toLocaleTimeString() + ': ' + message;" +
               "  logDiv.prepend(entry);" +
               "}" +
               "</script>" +
               "</div></body></html>";
    }

    public static void main(String[] args) {
        int wsPort = 8080;
        int httpPort = 4567;
        Server wsServer = new Server(wsPort);
        wsServer.start();
        System.out.println("WebSocket server started on port " + wsPort);
        HTTPServer httpServer = new HTTPServer(wsServer);
        httpServer.start(httpPort);
        System.out.println("HTTP server started on port " + httpPort);
    }
}
