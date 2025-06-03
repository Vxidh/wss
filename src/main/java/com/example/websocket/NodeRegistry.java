// src/main/java/com/example/websocket/NodeRegistry.java
package com.example.websocket;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;

public class NodeRegistry {
    private static final Logger logger = LoggerFactory.getLogger(NodeRegistry.class);

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

        public NodeInfo(String nodeId, WebSocket conn, Role role) {
            this.conn = conn;
            this.lastActivity = System.currentTimeMillis();
            this.connectedAt = this.lastActivity;
            this.status = NodeStatus.ACTIVE;
            this.nodeId = nodeId;
            this.role = role;
            if (role == Role.CLIENT_NODE || role == Role.BATCH_SERVER_CLIENT) {
                this.authenticated = true;
            }
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
            if (this.status == NodeStatus.IDLE) {
                logger.debug("NodeRegistry: Node {} ({}) status changed from IDLE to ACTIVE.", nodeId, role);
            }
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
        INCOMING_TEST_MASTER,
        BATCH_SERVER_CLIENT
    }

    private final ConcurrentHashMap<String, NodeInfo> identifiedClientsById;
    private final ConcurrentHashMap<WebSocket, NodeInfo> allConnectionsByWebSocket;
    private volatile WebSocket incomingTestMasterWebSocket = null;
    private final ScheduledExecutorService scheduler;

    public NodeRegistry() {
        this.identifiedClientsById = new ConcurrentHashMap<>();
        this.allConnectionsByWebSocket = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkNodeStates, 10, 10, TimeUnit.SECONDS);
        logger.info("NodeRegistry: Initialized. Node state monitoring started.");
    }

    public NodeInfo registerClientNode(String nodeId, WebSocket conn) {
        if (identifiedClientsById.containsKey(nodeId)) {
            NodeInfo existingInfo = identifiedClientsById.get(nodeId);
            if (existingInfo.conn != conn && existingInfo.conn.isOpen()) {
                logger.warn("NodeRegistry: Node ID '{}' is already in use by an active connection. Closing old connection.", nodeId);
                existingInfo.conn.close(1000, "Replaced by new connection for same ID");
            }
            allConnectionsByWebSocket.remove(existingInfo.conn);
        }

        NodeInfo newNodeInfo = new NodeInfo(nodeId, conn, Role.CLIENT_NODE);
        identifiedClientsById.put(nodeId, newNodeInfo);
        allConnectionsByWebSocket.put(conn, newNodeInfo);

        logger.info("NodeRegistry: RPA CLIENT_NODE '{}' registered. Total Identified Clients: {}", nodeId, identifiedClientsById.size());
        return newNodeInfo;
    }

    public boolean registerIncomingTestMaster(WebSocket conn) {
        if (incomingTestMasterWebSocket == null || !incomingTestMasterWebSocket.isOpen()) {
            incomingTestMasterWebSocket = conn;
            allConnectionsByWebSocket.put(conn, new NodeInfo("INCOMING_TEST_MASTER", conn, Role.INCOMING_TEST_MASTER));
            logger.info("NodeRegistry: INCOMING Test Master Server registered from {}", conn.getRemoteSocketAddress().getAddress().getHostAddress());
            return true;
        } else {
            logger.warn("NodeRegistry: Another INCOMING Test Master tried to connect from {}. Only one test master allowed. Closing connection.", conn.getRemoteSocketAddress().getAddress().getHostAddress());
            conn.close(1008, "Only one test master is allowed.");
            return false;
        }
    }

    public NodeInfo registerBatchServerClient(String clientId, WebSocket conn) {
        if (identifiedClientsById.containsKey(clientId)) {
            NodeInfo existingInfo = identifiedClientsById.get(clientId);
            if (existingInfo.conn != conn && existingInfo.conn.isOpen()) {
                logger.warn("NodeRegistry: Batch Server Client ID '{}' is already in use by an active connection. Closing old connection.", clientId);
                existingInfo.conn.close(1000, "Replaced by new connection for same ID");
            }
            allConnectionsByWebSocket.remove(existingInfo.conn);
        }

        NodeInfo newClientInfo = new NodeInfo(clientId, conn, Role.BATCH_SERVER_CLIENT);
        identifiedClientsById.put(clientId, newClientInfo);
        allConnectionsByWebSocket.put(conn, newClientInfo);

        logger.info("NodeRegistry: Registered Batch Server Client: {}. Total Identified Clients: {}", clientId, identifiedClientsById.size());
        return newClientInfo;
    }

    public WebSocket getBatchServerClientWebSocket(String clientId) {
        NodeInfo info = identifiedClientsById.get(clientId);
        if (info != null && info.role == Role.BATCH_SERVER_CLIENT && info.conn != null && info.conn.isOpen()) {
            return info.conn;
        }
        return null;
    }

    public NodeInfo unregisterConnection(WebSocket conn) {
        NodeInfo info = allConnectionsByWebSocket.remove(conn);
        if (info != null) {
            if (info.role == Role.CLIENT_NODE || info.role == Role.BATCH_SERVER_CLIENT) {
                identifiedClientsById.remove(info.nodeId);
                logger.warn("NodeRegistry: {} '{}' disconnected. Total Identified Clients: {}", info.role, info.nodeId, identifiedClientsById.size());
            } else if (info.role == Role.INCOMING_TEST_MASTER) {
                logger.warn("NodeRegistry: INCOMING Test Master Server disconnected.");
                incomingTestMasterWebSocket = null;
            }
        } else {
            logger.warn("NodeRegistry: Attempted to unregister an unknown connection.");
        }
        return info;
    }

    public NodeInfo getClientNodeInfo(String id) {
        return identifiedClientsById.get(id);
    }

    public NodeInfo getNodeInfoByConnection(WebSocket conn) {
        return allConnectionsByWebSocket.get(conn);
    }

    public boolean isIncomingTestMaster(WebSocket conn) {
        return conn.equals(incomingTestMasterWebSocket);
    }

    public WebSocket getIncomingTestMasterWebSocket() {
        return incomingTestMasterWebSocket;
    }

    public Map<String, NodeInfo> getActiveClientNodes() {
        ConcurrentHashMap<String, NodeInfo> activeNodes = new ConcurrentHashMap<>();
        for (Map.Entry<String, NodeInfo> entry : identifiedClientsById.entrySet()) {
            if (entry.getValue().conn != null && entry.getValue().conn.isOpen() &&
               (entry.getValue().role == Role.CLIENT_NODE || entry.getValue().role == Role.BATCH_SERVER_CLIENT)) {
                activeNodes.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(activeNodes);
    }

    public Map<String, NodeInfo> getAllClientNodeInfo() {
        return Collections.unmodifiableMap(identifiedClientsById);
    }

    public boolean disconnectClientNode(String id) {
        NodeInfo info = identifiedClientsById.get(id); // Get info first, don't remove yet
        if (info != null && info.conn != null && info.conn.isOpen()) {
            info.conn.close(1000, "Disconnected by server request");
            // The unregisterConnection (called by onClose) will handle actual removal from maps
            logger.info("NodeRegistry: {} '{}' explicitly requested to disconnect by server.", info.role, id);
            return true;
        }
        logger.warn("NodeRegistry: Attempted to explicitly disconnect unknown, non-active or non-client node: {}", id);
        return false;
    }

    public void updateNodeStatus(String nodeId, String newStatus) {
        NodeInfo info = identifiedClientsById.get(nodeId);
        if (info != null && info.role == Role.CLIENT_NODE) { // Only update status for actual RPA nodes
            try {
                info.status = NodeStatus.valueOf(newStatus.toUpperCase());
                info.updateActivity();
                logger.info("NodeRegistry: RPA Node '{}' status updated to: {}", nodeId, newStatus);
            } catch (IllegalArgumentException e) {
                logger.warn("NodeRegistry: Invalid status '{}' received for RPA Node '{}'.", newStatus, nodeId);
            }
        } else {
            logger.warn("NodeRegistry: Attempted to update status for unknown or non-RPA node: {}", nodeId);
        }
    }

    private void checkNodeStates() {
        new HashSet<>(allConnectionsByWebSocket.keySet()).forEach(conn -> {
            NodeInfo info = allConnectionsByWebSocket.get(conn);
            if (info == null || !conn.isOpen()) {
                if (info != null) {
                    logger.debug("NodeRegistry: Found closed/null connection for {}. Unregistering.", info.nodeId);
                }
                unregisterConnection(conn);
                return;
            }

            if (info.role == Role.CLIENT_NODE || info.role == Role.BATCH_SERVER_CLIENT) {
                if (info.status == NodeStatus.ACTIVE && info.isIdle()) {
                    info.status = NodeStatus.IDLE;
                    logger.info("NodeRegistry: {} '{}' marked as IDLE.", info.role, info.nodeId);
                }

                if (info.shouldDisconnect()) {
                    logger.warn("NodeRegistry: {} '{}' has exceeded its connection lifespan ({} mins). Disconnecting.",
                                 info.role, info.nodeId, NodeInfo.CONNECTION_LIFESPAN_MS / (60 * 1000));
                    disconnectClientNode(info.nodeId); // Call disconnectClientNode here
                }
            }
        });
    }

}