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
           String authHeader = req.headers("Authorization");
           if (authHeader == null || !authHeader.startsWith("Bearer ")) {
               System.err.println("[AUTH ERROR] Missing or invalid Authorization header for " + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip());
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               halt(401, "{\"error\":\"Missing or invalid Authorization header\"}");
           }

           String token = authHeader.substring("Bearer ".length());
           try {
               JWTUtil.validateToken(token);
           } catch (JwtException e) {
               System.err.println("[AUTH ERROR] Invalid or expired token for " + req.requestMethod() + " " + req.pathInfo() + " from " + req.ip());
               e.printStackTrace();
               res.type("application/json");
               res.header("Access-Control-Allow-Origin", "*");
               halt(401, "{\"error\":\"Invalid or expired token\"}");
           }
       });
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

       get("/", (req, res) -> {
           res.type("text/html");
           return getClientSetupPage();
       });

       get("/health", (req, res) -> {
           res.type("text/plain");
           return "OK";
       });

       get("/nodes", (req, res) -> {
           res.type("text/html");
           return getNodesPage();
       });
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

   private String getClientSetupPage() {
       long timestamp = System.currentTimeMillis();
       return "<!DOCTYPE html>" +
               "<html><head><title>Bot Test Node Setup</title>" +
               "<style>" +
               "body { font-family: Arial, sans-serif; margin: 20px; }" +
               ".container { max-width: 800px; margin: 0 auto; }" +
               ".status { margin: 20px 0; padding: 10px; border-radius: 5px; }" +
               ".connected { background-color: #dff0d8; }" +
               ".disconnected { background-color: #f2dede; }" +
               "button { padding: 10px; margin: 10px 5px 10px 0; }" +
               "</style></head>" +
               "<body><div class='container'>" +
               "<h1>Bot Test Node Setup</h1>" +
               "<p>This page helps you set up a test node that will connect to the test server.</p>" +
               "<div><label for='nodeId'>Node ID: </label>" +
               "<input type='text' id='nodeId' value='node-" + timestamp + "'></div>" +
               "<div><label for='authToken'>Auth Token: </label>" +
               "<input type='text' id='authToken' placeholder='Enter your JWT token'>" +
               "<button id='getJwtBtn' type='button'>Get JWT for Node ID</button></div>" +
               "<div style='margin:10px 0;color:#888;font-size:0.95em'>" +
               "Tip: Click 'Get JWT for Node ID' to generate a token for the entered Node ID." +
               "</div>" +
               "<div>" +
               "<button id='connectBtn'>Connect Node</button>" +
               "<button id='disconnectBtn' disabled>Disconnect Node</button>" +
               "</div>" +
               "<div id='status' class='status disconnected'>Disconnected</div>" +
               "<div id='log'></div>" +
               "<script>" +
               "const connectBtn = document.getElementById('connectBtn');" +
               "const disconnectBtn = document.getElementById('disconnectBtn');" +
               "const getJwtBtn = document.getElementById('getJwtBtn');" +
               "let ws = null;" +
               "connectBtn.addEventListener('click', () => {" +
               "  const nodeId = document.getElementById('nodeId').value;" +
               "  const authToken = document.getElementById('authToken').value;" +
               "  if (!nodeId) { alert('Please enter a Node ID'); return; }" +
               "  if (!authToken) { alert('Please enter an Auth Token'); return; }" +
               "  connect(nodeId, authToken);" +
               "});" +
               "disconnectBtn.addEventListener('click', () => { if (ws) ws.close(); });" +
               "getJwtBtn.addEventListener('click', () => {" +
               "  const nodeId = document.getElementById('nodeId').value;" +
               "  if (!nodeId) { alert('Please enter a Node ID'); return; }" +
               "  fetch('/api/generate-jwt/' + encodeURIComponent(nodeId))" +
               "    .then(resp => resp.json())" +
               "    .then(data => {" +
               "      if (data.token) {" +
               "        document.getElementById('authToken').value = data.token;" +
               "        log('JWT generated for ' + nodeId);" +
               "      } else {" +
               "        alert('Error: ' + (data.error || 'Unknown error'));" +
               "      }" +
               "    })" +
               "    .catch(err => alert('Failed to fetch JWT: ' + err));" +
               "});" +
               "function connect(nodeId, authToken) {" +
               "  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';" +
               "  const wsUrl = protocol + '//' + location.host + '/ws?nodeId=' + encodeURIComponent(nodeId) + '&authToken=' + encodeURIComponent(authToken);" +
               "  log('Connecting to ' + wsUrl);" +
               "  ws = new WebSocket(wsUrl);" +
               "  ws.onopen = () => {" +
               "    log('Connected');" +
               "    document.getElementById('status').textContent = 'Connected as ' + nodeId;" +
               "    document.getElementById('status').className = 'status connected';" +
               "    connectBtn.disabled = true;" +
               "    disconnectBtn.disabled = false;" +
               "  };" +
               "  ws.onmessage = (event) => { log('Received: ' + event.data); };" +
               "  ws.onclose = () => {" +
               "    log('Disconnected');" +
               "    document.getElementById('status').textContent = 'Disconnected';" +
               "    document.getElementById('status').className = 'status disconnected';" +
               "    connectBtn.disabled = false;" +
               "    disconnectBtn.disabled = true;" +
               "  };" +
               "  ws.onerror = (error) => { log('Error: ' + error); }" +
               "}" +
               "function log(message) {" +
               "  const logDiv = document.getElementById('log');" +
               "  const entry = document.createElement('div');" +
               "  entry.textContent = new Date().toLocaleTimeString() + ': ' + message;" +
               "  logDiv.prepend(entry);" +
               "}" +
               "</script></div></body></html>";
   }

   private String getNodesPage() {
       StringBuilder sb = new StringBuilder();
       sb.append("<!DOCTYPE html><html><head><title>Node Status Dashboard</title>");
       sb.append("<style>");
       sb.append("body{font-family:Arial,sans-serif;margin:20px;}");
       sb.append("table{border-collapse:collapse;width:100%;max-width:800px;}");
       sb.append("th,td{border:1px solid #ccc;padding:8px;text-align:left;}");
       sb.append("th{background:#f5f5f5;}");
       sb.append(".ACTIVE{background:#dff0d8;}");
       sb.append(".IDLE{background:#fcf8e3;}");
       sb.append(".disconnected{background:#f2dede;}");
       sb.append("</style></head><body>");
       sb.append("<h1>Node Status Dashboard</h1>");
       sb.append("<table><tr><th>Node ID</th><th>Status</th><th>Last Activity</th></tr>");
       wsServer.getNodes().forEach((nodeId, info) -> {
           String status = info.status.toString();
           String rowClass = status;
           sb.append("<tr class='").append(rowClass).append("'>");
           sb.append("<td>").append(nodeId).append("</td>");
           sb.append("<td>").append(status).append("</td>");
           sb.append("<td>").append(new java.util.Date(info.lastActivity)).append("</td>");
           sb.append("</tr>");
       });
       sb.append("</table>");
       sb.append("<p><a href=\"/\">Back to Node Setup</a></p>");
       sb.append("</body></html>");
       return sb.toString();
   }

   public static void main(String[] args) {
       System.err.println("Please use MainServer.java to start the application (handles config init).");
       System.exit(1);
   }
}