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
       // Serve static files from /public directory
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
           if (req.pathInfo().startsWith("/api/generate-jwt")) {
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

       post("/api/send/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");

           JsonObject command;
           try {
               command = JsonParser.parseString(req.body()).getAsJsonObject();
           } catch (Exception e) {
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
               return "{\"status\":\"Command sent to node " + nodeId + "\"}";
           } else {
               System.err.println("[SEND ERROR] Node " + nodeId + " not connected or idle (request from " + req.ip() + ")");
               res.status(404);
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Node " + nodeId + " not connected or idle\"}";
           }
       });

       post("/api/disconnect/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           boolean disconnected = wsServer.disconnectNode(nodeId);
           res.type("application/json");
           if (disconnected) {
               System.out.println("[DISCONNECT] Node " + nodeId + " disconnected via API (request from " + req.ip() + ")");
               return "{\"status\":\"Node " + nodeId + " disconnected\"}";
           } else {
               System.err.println("[DISCONNECT ERROR] Node " + nodeId + " not found or already disconnected (request from " + req.ip() + ")");
               res.status(404);
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Node " + nodeId + " not found or already disconnected\"}";
           }
       });

       post("/api/rotate-secret/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           // Actually rotate the secret and return it
           String newSecret = wsServer.rotateNodeSecret(nodeId);
           res.type("application/json");
           return "{\"status\":\"Secret rotated for node " + nodeId + "\",\"newSecret\":\"" + newSecret + "\"}";
       });

       get("/api/generate-jwt/:nodeId", (req, res) -> {
           String nodeId = req.params(":nodeId");
           if (nodeId == null || nodeId.isEmpty()) {
               System.err.println("[JWT ERROR] Missing nodeId in /api/generate-jwt request from " + req.ip());
               res.status(400);
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               return "{\"error\":\"Missing nodeId\"}";
           }
           String token = JWTUtil.generateToken(nodeId);
           res.type("application/json");
           res.header("Access-Control-Allow-Origin", "*");
           return "{\"nodeId\":\"" + nodeId + "\",\"token\":\"" + token + "\"}";
       });

       // Serve static HTML for "/" and "/nodes"
       get("/", (req, res) -> {
           res.type("text/html");
           // Serve index.html from /public
           return renderStaticHtml("index.html");
       });

       get("/nodes", (req, res) -> {
           res.type("text/html");
           return renderStaticHtml("nodes.html");
       });

       get("/health", (req, res) -> {
           res.type("text/plain");
           return "OK";
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

   public static void main(String[] args) {
       System.err.println("Please use MainServer.java to start the application (handles config init).");
       System.exit(1);
   }
}