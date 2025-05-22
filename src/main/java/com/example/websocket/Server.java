package com.example.websocket;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// TODO: Replace static token with JWT authentication for production security.
public class Server extends WebSocketServer {
    private static final long IDLE_TIMEOUT_MS = 30000;
    private static final String AUTH_SECRET = "cr7";
    public enum NodeStatus { ACTIVE, IDLE }
    public static class NodeInfo {
        public final WebSocket conn;
        public volatile long lastActivity;
        public volatile NodeStatus status;
        public NodeInfo(WebSocket conn) {
            this.conn = conn;
            this.lastActivity = System.currentTimeMillis();
            this.status = NodeStatus.ACTIVE;
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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public Server(int port) {
        super(new InetSocketAddress(port));
        scheduler.scheduleAtFixedRate(this::checkIdleNodes, 10, 10, TimeUnit.SECONDS);
    }
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Handshake resource: " + handshake.getResourceDescriptor());
        String nodeId = getQueryParam(handshake.getResourceDescriptor(), "nodeId");
        String authToken = getQueryParam(handshake.getResourceDescriptor(), "authToken");
        if (nodeId == null || nodeId.isEmpty()) {
            conn.close(1008, "Missing nodeId");
            return;
        }
        if (!validateAuthToken(authToken)) {
            conn.close(1008, "Invalid auth token");
            return;
        }
        nodes.put(nodeId, new NodeInfo(conn));
        System.out.println("Node connected: " + nodeId);
    }
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        nodes.entrySet().removeIf(entry -> entry.getValue().conn.equals(conn));
        System.out.println("Node disconnected: " + reason);
    }
    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject json;
        try {
            json = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            System.out.println("Invalid JSON from client: " + message);
            return;
        }
        nodes.forEach((nodeId, info) -> {
            if (info.conn.equals(conn)) {
                if (json.has("type") && json.get("type").getAsString().equals("ping")) {
                    info.updateActivity();
                    JsonObject pong = new JsonObject();
                    pong.addProperty("type", "pong");
                    conn.send(pong.toString());
                    System.out.println("Ping received from " + nodeId + ", sent pong.");
                } else {
                    info.updateActivity();
                    System.out.println("Received from " + nodeId + ": " + message);
                }
            }
        });
    }
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }
    @Override
    public void onStart() {
        System.out.println("Server started on port " + getPort());
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
    private String getQueryParam(String resource, String key) {
        if (resource == null || !resource.contains("?")) return null;
        String query = resource.substring(resource.indexOf('?') + 1);
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
    private boolean validateAuthToken(String token) {
        if (token == null) return false;
        return token.equals(AUTH_SECRET);
    }
    public ConcurrentHashMap<String, NodeInfo> getNodes() {
        return nodes;
    }
    public static void main(String[] args) {
        int port = 8080;
        Server server = new Server(port);
        server.start();
    }
}
