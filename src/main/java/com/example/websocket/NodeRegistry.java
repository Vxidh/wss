// src/main/java/com/example/websocket/NodeRegistry.java
package com.example.websocket;

import org.java_websocket.WebSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;

public class NodeRegistry {

    // --- Public static nested Enums and Class ---
    public enum NodeStatus {
        ACTIVE, IDLE
    }

    public static class NodeInfo {
        public final WebSocket conn;
        public volatile long lastActivity;
        public volatile NodeStatus status;
        public final long connectedAt;
        public final String nodeId;
        public volatile boolean authenticated = false;
        public final Role role;

        private static final long IDLE_TIMEOUT_MS = 30000; // 30 seconds
        private static final long CONNECTION_LIFESPAN_MS = 30 * 60 * 1000; // 30 minutes

        public NodeInfo(WebSocket conn, String nodeId, Role role) {
            this.conn = conn;
            this.lastActivity = System.currentTimeMillis();
            this.connectedAt = this.lastActivity;
            this.status = NodeStatus.ACTIVE;
            this.nodeId = nodeId;
            this.role = role;
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
            this.status = NodeStatus.ACTIVE;
        }

        public boolean isIdle() {
            return System.currentTimeMillis() - lastActivity > IDLE_TIMEOUT_MS;
        }

        public boolean shouldDisconnect() {
            return System.currentTimeMillis() - connectedAt >= CONNECTION_LIFESPAN_MS;
        }
    }

    public enum Role {
        CLIENT_NODE,
        INCOMING_TEST_MASTER
    }
    // --- END Public static nested Enums and Class ---


    private final ConcurrentHashMap<String, NodeInfo> clientNodes; // nodeId -> NodeInfo (for CLIENT_NODE)
    private final ConcurrentHashMap<WebSocket, NodeInfo> allConnections; // WebSocket -> NodeInfo (all connections: CLIENT_NODE & INCOMING_TEST_MASTER)

    private volatile WebSocket incomingTestMasterWebSocket = null; // Single incoming test master
    private final ScheduledExecutorService scheduler;

    public NodeRegistry() {
        this.clientNodes = new ConcurrentHashMap<>();
        this.allConnections = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkNodeStates, 10, 10, TimeUnit.SECONDS);
        System.out.println("NodeRegistry: Initialized. Node state monitoring started.");
    }

    public NodeInfo registerClientNode(String nodeId, WebSocket conn) {
        NodeInfo newNodeInfo = new NodeInfo(conn, nodeId, Role.CLIENT_NODE);
        newNodeInfo.authenticated = true; // Assuming nodeId in URL is authentication for now
        
        NodeInfo oldInfo = clientNodes.put(nodeId, newNodeInfo); // Replaces if nodeId already exists
        allConnections.put(conn, newNodeInfo);

        if (oldInfo != null && oldInfo.conn != conn && oldInfo.conn.isOpen()) {
            oldInfo.conn.close(1000, "Replaced by new connection for same nodeId: " + nodeId);
            System.out.println("NodeRegistry: Replacement: Old connection for node " + nodeId + " closed.");
        }
        System.out.println("NodeRegistry: Node '" + nodeId + "' registered. Active CLIENT_NODES: " + clientNodes.size());
        return newNodeInfo;
    }

    public boolean registerIncomingTestMaster(WebSocket conn) {
        if (incomingTestMasterWebSocket == null || !incomingTestMasterWebSocket.isOpen()) {
            incomingTestMasterWebSocket = conn;
            allConnections.put(conn, new NodeInfo(conn, "INCOMING_TEST_MASTER", Role.INCOMING_TEST_MASTER));
            System.out.println("NodeRegistry: INCOMING Mock Master Server registered from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
            return true;
        } else {
            System.out.println("NodeRegistry: Another INCOMING Mock Master tried to connect. Only one test master allowed. Closing connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
            conn.close(1008, "Only one test master is allowed.");
            return false;
        }
    }

    public NodeInfo unregisterConnection(WebSocket conn) {
        NodeInfo info = allConnections.remove(conn);
        if (info != null) {
            if (info.role == Role.CLIENT_NODE) {
                clientNodes.remove(info.nodeId);
                System.out.println("NodeRegistry: CLIENT_NODE '" + info.nodeId + "' disconnected. Active CLIENT_NODES: " + clientNodes.size());
            } else if (info.role == Role.INCOMING_TEST_MASTER) {
                System.out.println("NodeRegistry: INCOMING Mock Master Server disconnected.");
                incomingTestMasterWebSocket = null;
            }
        } else {
            System.out.println("NodeRegistry: Attempted to unregister an unknown connection.");
        }
        return info;
    }

    public NodeInfo getClientNodeInfo(String nodeId) {
        return clientNodes.get(nodeId);
    }

    public NodeInfo getNodeInfoByConnection(WebSocket conn) {
        return allConnections.get(conn);
    }

    public boolean isIncomingTestMaster(WebSocket conn) {
        return conn.equals(incomingTestMasterWebSocket);
    }

    public WebSocket getIncomingTestMasterWebSocket() {
        return incomingTestMasterWebSocket;
    }

    public Map<String, NodeInfo> getActiveClientNodes() {
        return Collections.unmodifiableMap(clientNodes);
    }

    public boolean disconnectClientNode(String nodeId) {
        NodeInfo info = clientNodes.remove(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen()) {
            info.conn.close(1000, "Disconnected by server request");
            allConnections.remove(info.conn);
            System.out.println("NodeRegistry: CLIENT_NODE '" + nodeId + "' disconnected by server request.");
            return true;
        }
        return false;
    }

    private void checkNodeStates() {
        new HashSet<>(clientNodes.keySet()).forEach(nodeId -> {
            NodeInfo info = clientNodes.get(nodeId);
            if (info == null) return;

            if (info.status == NodeStatus.ACTIVE && info.isIdle()) {
                info.status = NodeStatus.IDLE;
                System.out.println("NodeRegistry: CLIENT_NODE '" + nodeId + "' marked as IDLE");
            }

            if (info.shouldDisconnect()) {
                System.out.println("NodeRegistry: CLIENT_NODE '" + nodeId + "' has been connected >30 minutes. Disconnecting.");
                disconnectClientNode(nodeId);
            }
        });
    }

    public static String getQueryParam(String resource, String key) {
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
}