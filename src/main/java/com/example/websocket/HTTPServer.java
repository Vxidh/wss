package com.example.websocket;

import static spark.Spark.*;

import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.jsonwebtoken.JwtException;

public class HTTPServer {
   private final Server wsServer;
   private final Gson gson = new Gson();

   public HTTPServer(Server wsServer) {
       this.wsServer = wsServer;
   }

   public void start(int port) {
       port(port);
       staticFiles.location("/public");
       configureCORS();
       configureRoutes();
       configureErrorHandling();
   }

   private void configureCORS() {
       options("/*", (request, response) -> {
           String headers = request.headers("Access-Control-Request-Headers");
           if (headers != null) {
               response.header("Access-Control-Allow-Headers", headers);
           }

           String methods = request.headers("Access-Control-Request-Method");
           if (methods != null) {
               response.header("Access-Control-Allow-Methods", methods);
           }

           return "OK";
       });

       before((req, res) -> {
           res.header("Access-Control-Allow-Origin", "*");
           res.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
           res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
       });

       before("/api/*", (req, res) -> {
           // Allow unauthenticated access to /api/generate-jwt and /api/register-agent
           if (req.pathInfo().startsWith("/api/generate-jwt") || req.pathInfo().startsWith("/api/register-agent")) {
               return;
           }
           authenticateRequest(req, res);
       });
   }

   private void authenticateRequest(spark.Request req, spark.Response res) {
       String authHeader = req.headers("Authorization");
       if (authHeader == null || !authHeader.startsWith("Bearer ")) {
           logAuthError(req, "Missing or invalid Authorization header");
           sendErrorResponse(res, 401, "Missing or invalid Authorization header");
       }
       String token = authHeader.substring("Bearer ".length());
       try {
           JWTUtil.validateToken(token);
       } catch (JwtException e) {
           logAuthError(req, "Invalid or expired token");
           sendErrorResponse(res, 401, "Invalid or expired token");
       }
   }

   private void sendErrorResponse(spark.Response res, int status, String message) {
       res.status(status);
       res.type("application/json");
       res.header("Access-Control-Allow-Origin", "*");
       halt(status, "{\"error\":\"" + message + "\"}");
   }

   private void logAuthError(spark.Request req, String message) {
       System.err.println("[AUTH ERROR] " + message + " for " + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip());
   }

   private void configureRoutes() {
       // NOTE: /nodes is NOT a WebSocket endpoint. The WebSocket server runs on a separate port (default: 4567).
       // If the dashboard needs real-time updates, use /api/ws-info to get the WebSocket server info and connect accordingly.

       get("/api/ws-info", (req, res) -> {
           logRequest(req, "GET /api/ws-info");
           // Return WebSocket host/port info for dashboard JS
           String wsHost = "localhost"; // Hardcoded to match MainServer.java
           int wsPort = 4567;            // Hardcoded to match MainServer.java
           res.type("application/json");
           return String.format("{\"host\":\"%s\",\"port\":%d}", wsHost, wsPort);
       });

       get("/api/nodes", (req, res) -> {
           logRequest(req, "GET /api/nodes");
           List<Map<String, Object>> nodesList = new ArrayList<>();

           wsServer.getNodes().forEach((nodeId, info) -> {
               Map<String, Object> node = new HashMap<>();
               node.put("id", nodeId);
               node.put("status", info.status.toString());
               node.put("lastActivity", info.lastActivity);
               nodesList.add(node);
           });

           res.type("application/json");
           logInfo(req, "Returned " + nodesList.size() + " nodes");
           return gson.toJson(nodesList);
       });

       post("/api/send/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           logRequest(req, "POST /api/send/" + nodeId);

           JsonObject command;
           try {
               command = JsonParser.parseString(req.body()).getAsJsonObject();
           } catch (Exception e) {
               logError(req, "Invalid JSON payload for nodeId=" + nodeId, e);
               System.err.println("[PAYLOAD ERROR] Invalid JSON payload for nodeId=" + nodeId + " from " + req.ip() + ": " + e.getMessage());
               e.printStackTrace();
               res.status(400);
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Invalid JSON payload\"}";
           }

           boolean sent = wsServer.sendToNode(nodeId, command);
           res.type("application/json");

           if (sent) {
               logInfo(req, "Command sent to node " + nodeId);
               return "{\"status\":\"Command sent to node " + nodeId + "\"}";
           } else {
               logError(req, "Node " + nodeId + " not connected or idle", null);
               System.err.println("[SEND ERROR] Node " + nodeId + " not connected or idle (request from " + req.ip() + ")");
               res.status(404);
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Node " + nodeId + " not connected or idle\"}";
           }
       });

       post("/api/disconnect/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           logRequest(req, "POST /api/disconnect/" + nodeId);
           boolean disconnected = wsServer.disconnectNode(nodeId);
           res.type("application/json");
           if (disconnected) {
               logInfo(req, "Node " + nodeId + " disconnected via API");
               System.out.println("[DISCONNECT] Node " + nodeId + " disconnected via API (request from " + req.ip() + ")");
               return "{\"status\":\"Node " + nodeId + " disconnected\"}";
           } else {
               logError(req, "Node " + nodeId + " not found or already disconnected", null);
               System.err.println("[DISCONNECT ERROR] Node " + nodeId + " not found or already disconnected (request from " + req.ip() + ")");
               res.status(404);
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Node " + nodeId + " not found or already disconnected\"}";
           }
       });

       post("/api/rotate-secret/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           logRequest(req, "POST /api/rotate-secret/" + nodeId);
           // Actually rotate the secret and return it
           String newSecret = wsServer.rotateNodeSecret(nodeId);
           res.type("application/json");
           logInfo(req, "Secret rotated for node " + nodeId);
           return "{\"status\":\"Secret rotated for node " + nodeId + "\",\"newSecret\":\"" + newSecret + "\"}";
       });

       get("/api/generate-jwt/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           logRequest(req, "GET /api/generate-jwt/" + nodeId);
           if (nodeId == null || nodeId.isEmpty()) {
               logError(req, "Missing nodeId in /api/generate-jwt", null);
               System.err.println("[JWT ERROR] Missing nodeId in /api/generate-jwt request from " + req.ip());
               res.status(400);
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Missing nodeId\"}";
           }
           String token = JWTUtil.generateToken(nodeId);
           logInfo(req, "Generated JWT for nodeId=" + nodeId);
           res.type("application/json");
           res.header("Access-Control-Allow-Origin", "*");
           return "{\"nodeId\":\"" + nodeId + "\",\"token\":\"" + token + "\"}";
       });

       post("/api/register-agent", (req, res) -> {
           logRequest(req, "POST /api/register-agent");
           JsonObject body;
           try {
               body = JsonParser.parseString(req.body()).getAsJsonObject();
           } catch (Exception e) {
               logError(req, "Invalid JSON in /api/register-agent", e);
               res.status(400);
               return "{\"error\":\"Invalid JSON\"}";
           }
           String nodeId = body.has("nodeId") ? body.get("nodeId").getAsString() : null;
           String secret = body.has("secret") ? body.get("secret").getAsString() : null;
           String regToken = body.has("registrationToken") ? body.get("registrationToken").getAsString() : null;

           if (!"super-secret-token-123".equals(regToken)) {
               logError(req, "Invalid registration token in /api/register-agent", null);
               res.status(403);
               return "{\"error\":\"Invalid registration token\"}";
           }

           if (nodeId == null || secret == null) {
               logError(req, "Missing nodeId or secret in /api/register-agent", null);
               res.status(400);
               return "{\"error\":\"Missing nodeId or secret\"}";
           }

           wsServer.setNodeSecret(nodeId, secret);
           logInfo(req, "Registered agent nodeId=" + nodeId);
           res.type("application/json");
           return "{\"status\":\"registered\"}";
       });

       // Serve static HTML for "/" and "/nodes"
       get("/", (req, res) -> {
           logRequest(req, "GET /");
           String upgrade = req.headers("Upgrade");
           if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
               logError(req, "WebSocket upgrade not supported on /", null);
               res.status(400);
               res.type("text/plain");
               return "WebSocket upgrade not supported on /. Use ws://localhost:4567/ for WebSocket connections.";
           }
           // Serve index.html from /public
           res.type("text/html");
           return renderStaticHtml("index.html");
       });

       // Redirect /nodes to /nodes.html to avoid 404 or WebSocket upgrade error
       get("/nodes", (req, res) -> {
           logRequest(req, "GET /nodes");
           String upgrade = req.headers("Upgrade");
           if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
               logError(req, "WebSocket upgrade not supported on /nodes", null);
               res.status(400);
               res.type("text/plain");
               return "WebSocket upgrade not supported on /nodes. Use ws://localhost:4567/ for WebSocket connections.";
           }
           res.redirect("/nodes.html");
           return null;
       });

       get("/health", (req, res) -> {
           logRequest(req, "GET /health");
           res.type("text/plain");
           return "OK";
       });

       // Add this route to handle /ws and prevent 404 WebSocket upgrade failure
       get("/ws", (req, res) -> {
           logRequest(req, "GET /ws");
           System.out.println("[DEBUG] HTTP GET /ws route hit. This is NOT the WebSocket server.");
           String upgrade = req.headers("Upgrade");
           if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
               logError(req, "WebSocket upgrade not supported on /ws (handled by separate server on :4567)", null);
               System.out.println("[DEBUG] WebSocket upgrade attempted on HTTP server at /ws. This will fail.");
               res.status(400);
               res.type("text/plain");
               return "WebSocket upgrade not supported on /ws here. Use ws://localhost:4567/ for WebSocket connections.";
           }
           res.status(400);
           res.type("text/plain");
           return "This endpoint is reserved for WebSocket connections on port 4567.";
       });
   }

   private String renderStaticHtml(String filename) {
       try (java.io.InputStream is = getClass().getResourceAsStream("/public/" + filename)) {
           if (is == null) return "<h1>File not found: " + filename + "</h1>";
           return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
       } catch (Exception e) {
           return "<h1>Error loading " + filename + "</h1>";
       }
   }

   private void configureErrorHandling() {
       exception(Exception.class, (e, req, res) -> {
           System.err.println("[UNHANDLED ERROR] " + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip() + ": " + e.getMessage());
           e.printStackTrace();
           res.status(500);
           res.type("application/json");
           res.header("Access-Control-Allow-Origin", "*");
           res.body("{\"error\":\"Internal server error\"}");
       });
   }

   // --- Enhanced logging helpers ---
   private void logRequest(spark.Request req, String action) {
       System.out.println("[HTTP] " + action + " from " + req.ip() +
           " | Params: " + req.params() + " | Query: " + req.queryParams() +
           (req.requestMethod().equals("POST") ? " | Body: " + req.body() : ""));
   }

   private void logInfo(spark.Request req, String message) {
       System.out.println("[INFO] " + message + " (" + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip() + ")");
   }

   private void logError(spark.Request req, String message, Exception e) {
       System.err.println("[ERROR] " + message + " (" + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip() + ")");
       if (e != null) e.printStackTrace();
   }

   public static void main(String[] args) {
       System.err.println("Please use MainServer.java to start the application (handles config init).");
       System.exit(1);
   }
}