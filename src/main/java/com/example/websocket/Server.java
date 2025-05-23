package com.example.websocket;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.security.SecureRandom;
import java.util.Base64;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;

import io.jsonwebtoken.JwtException;

public class Server extends WebSocketServer {
    private static final long IDLE_TIMEOUT_MS = 30000;

    public enum NodeStatus { ACTIVE, IDLE }
    public enum NodeRole { CONTROLLER, AGENT }

    public static class NodeInfo {
        public final WebSocket conn;
        public volatile long lastActivity;
        public volatile NodeStatus status;
        public final NodeRole role;
        public final String nodeId;

        public NodeInfo(WebSocket conn, NodeRole role, String nodeId) {
            this.conn = conn;
            this.lastActivity = System.currentTimeMillis();
            this.status = NodeStatus.ACTIVE;
            this.role = role;
            this.nodeId = nodeId;
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
            this.status = NodeStatus.ACTIVE;
        }

        public boolean isIdle() {
            return System.currentTimeMillis() - lastActivity > IDLE_TIMEOUT_MS;
        }
    }

    private final ConcurrentHashMap<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, NodeInfo> connToNode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nodeSecrets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String secretsFile = "agent-secrets.json"; // Store in working directory
    private final Gson gson = new Gson();

    public Server(int port) {
        super(new InetSocketAddress(port));
        System.out.println("[DEBUG] WebSocketServer constructor: Listening on port " + port);
        loadNodeSecrets();
        scheduler.scheduleAtFixedRate(this::checkIdleNodes, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[DEBUG] onOpen: New WebSocket connection attempt");
        System.out.println("Handshake resource: " + handshake.getResourceDescriptor());
        String nodeId = getQueryParam(handshake.getResourceDescriptor(), "nodeId");
        String authToken = getQueryParam(handshake.getResourceDescriptor(), "authToken");
        String roleParam = getQueryParam(handshake.getResourceDescriptor(), "role");
        NodeRole role = NodeRole.AGENT;
        if (roleParam != null && roleParam.equalsIgnoreCase("controller")) {
            role = NodeRole.CONTROLLER;
        }

        if (nodeId == null || nodeId.isEmpty()) {
            conn.close(1008, "Missing nodeId");
            return;
        }

        // Use JWT validation for controllers, agent secret validation for agents
        boolean valid;
        if (role == NodeRole.CONTROLLER) {
            valid = validateAuthToken(authToken);
        } else {
            valid = validateAgentSecret(nodeId, authToken);
        }
        if (!valid) {
            conn.close(1008, "Invalid or missing auth token/secret");
            System.out.println("Rejected connection for nodeId=" + nodeId + ": invalid token/secret");
            return;
        }

        NodeInfo nodeInfo = new NodeInfo(conn, role, nodeId);
        NodeInfo oldInfo = nodes.put(nodeId, nodeInfo);
        connToNode.put(conn, nodeInfo);
        if (oldInfo != null && oldInfo.conn != conn && oldInfo.conn.isOpen()) {
            oldInfo.conn.close(1000, "Replaced by new connection");
        }
        System.out.println("Node connected: " + nodeId + " as " + role);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        NodeInfo info = connToNode.remove(conn);
        if (info != null) {
            nodes.remove(info.nodeId);
            System.out.println("Node disconnected: " + info.nodeId + " (" + info.role + ")");
        } else {
            System.out.println("Node disconnected: " + reason);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        NodeInfo sender = connToNode.get(conn);
        if (sender == null) {
            System.out.println("Unknown sender");
            return;
        }
        JsonObject json;
        try {
            json = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            System.out.println("Invalid JSON from client: " + message);
            return;
        }
        sender.updateActivity();
        if (json.has("type") && json.get("type").getAsString().equals("ping")) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "pong");
            conn.send(pong.toString());
            System.out.println("Ping received from " + sender.nodeId + ", sent pong.");
            return;
        }
        // Only controllers can send actionable commands
        if (sender.role == NodeRole.CONTROLLER && json.has("targetNodeId")) {
            String targetNodeId = json.get("targetNodeId").getAsString();
            NodeInfo target = nodes.get(targetNodeId);
            if (target != null && target.role == NodeRole.AGENT && target.conn.isOpen()) {
                target.conn.send(json.toString());
                System.out.println("Forwarded command from controller " + sender.nodeId + " to agent " + targetNodeId);
            } else {
                System.out.println("Target agent not found or not available: " + targetNodeId);
            }
        } else if (sender.role == NodeRole.AGENT) {
            // Handle agent responses or events here if needed
            System.out.println("Received from agent " + sender.nodeId + ": " + message);
        } else {
            System.out.println("Unauthorized or malformed command from " + sender.nodeId);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[DEBUG] onError: WebSocket error occurred");
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("[DEBUG] WebSocket server started on port " + getPort());
        System.out.println("[DEBUG] Accepting connections at ws://localhost:" + getPort() + "/");
    }

    private void checkIdleNodes() {
        nodes.forEach((nodeId, info) -> {
            if (info.status == NodeStatus.ACTIVE && info.isIdle()) {
                info.status = NodeStatus.IDLE;
                System.out.println("Node " + nodeId + " marked as IDLE");
            }
        });
    }

    public boolean sendToNode(String nodeId, JsonObject command) {
        NodeInfo info = nodes.get(nodeId);
        if (info != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            info.conn.send(command.toString());
            return true;
        }
        return false;
    }

    public boolean disconnectNode(String nodeId) {
        NodeInfo info = nodes.remove(nodeId);
        if (info != null && info.conn.isOpen()) {
            info.conn.close(1000, "Disconnected by server request");
            System.out.println("Node " + nodeId + " disconnected by server request.");
            return true;
        }
        return false;
    }

    private String getQueryParam(String resource, String key) {
        if (resource == null || !resource.contains("?")) return null;
        String query = resource.substring(resource.indexOf('?') + 1);
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private boolean validateAuthToken(String token) {
        if (token == null) return false;
        try {
            JWTUtil.validateToken(token);
            return true;
        } catch (JwtException | IllegalStateException e) {
            System.out.println("JWT validation failed: " + e.getMessage());
            return false;
        }
    }

    private boolean validateAgentSecret(String nodeId, String token) {
        String expected = nodeSecrets.get(nodeId);
        System.out.println("Validating nodeId=" + nodeId + ", provided=" + token + ", expected=" + expected);
        return expected != null && expected.equals(token);
    }

    public String rotateNodeSecret(String nodeId) {
        String newSecret = generateSecret();
        nodeSecrets.put(nodeId, newSecret);
        saveNodeSecrets();
        System.out.println("Secret rotated for nodeId=" + nodeId);
        NodeInfo info = nodes.get(nodeId);
        if (info != null && info.conn.isOpen()) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("type", "rotate_secret");
            info.conn.send(cmd.toString());
        }
        return newSecret;
    }

    public void setNodeSecret(String nodeId, String secret) {
        System.out.println("Registering nodeId=" + nodeId + " with secret=" + secret);
        nodeSecrets.put(nodeId, secret);
        saveNodeSecrets();
    }

    private void saveNodeSecrets() {
        try (FileWriter writer = new FileWriter(secretsFile)) {
            gson.toJson(nodeSecrets, writer);
        } catch (IOException e) {
            System.err.println("Failed to save agent secrets: " + e.getMessage());
        }
    }

    private void loadNodeSecrets() {
        File file = new File(secretsFile);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            ConcurrentHashMap<String, String> loaded = gson.fromJson(reader,
                new TypeToken<ConcurrentHashMap<String, String>>(){}.getType());
            if (loaded != null) {
                nodeSecrets.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("Failed to load agent secrets: " + e.getMessage());
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String getNodeSecret(String nodeId) {
        return nodeSecrets.get(nodeId);
    }

    public ConcurrentHashMap<String, NodeInfo> getNodes() {
        return nodes;
    }

    public static void main(String[] args) {
        System.err.println("Please use MainServer.java to start the application (handles config init).");
        System.exit(1);
    }
}
