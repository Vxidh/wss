// src/main/java/com/example/websocket/Server.java
package com.example.websocket;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.example.websocket.NodeRegistry;
import com.example.websocket.NodeRegistry.NodeInfo;
import com.example.websocket.NodeRegistry.NodeStatus;
import com.example.websocket.NodeRegistry.Role;
import com.example.websocket.UpstreamMasterClient;
import com.example.websocket.CommandOrchestrator;
import com.example.websocket.NodeCommander;
import com.example.websocket.IncomingTestMasterSender;

import org.slf4j.Logger; // NEW IMPORT
import org.slf4j.LoggerFactory; // NEW IMPORT


public class Server extends WebSocketServer implements IncomingTestMasterSender {
    private static final Logger logger = LoggerFactory.getLogger(Server.class); // NEW LOGGER INSTANCE

    private static final String ADMIN_TOKEN = "super-secret-admin-token";

    private final NodeRegistry nodeRegistry;
    private final UpstreamMasterClient upstreamMasterClient;
    private final ScheduledExecutorService sharedScheduler = Executors.newSingleThreadScheduledExecutor();

    private final CommandOrchestrator commandOrchestrator;
    private final NodeCommander nodeCommander;


    public Server(int port) {
        super(new InetSocketAddress(port));
        logger.info("Server: Initializing on port {}", port); // Changed to logger.info

        this.nodeRegistry = new NodeRegistry();

        this.upstreamMasterClient = new UpstreamMasterClient(
            sharedScheduler,
            null,
            this::handleMessageFromUpstreamMaster
        );

        this.nodeCommander = new NodeCommander(nodeRegistry);

        this.commandOrchestrator = new CommandOrchestrator(nodeRegistry, upstreamMasterClient, this.nodeCommander, this);
    }

    public void connectToUpstreamMaster(String masterUrl) {
        this.upstreamMasterClient.setMasterServerUrl(masterUrl);
        this.upstreamMasterClient.connect();
    }

    public boolean sendToNode(String nodeId, JsonObject command) {
        return nodeCommander.sendToNode(nodeId, command);
    }

    public boolean sendToNodeWithRequestId(String nodeId, JsonObject command, String requestId) {
        return nodeCommander.sendToNodeWithRequestId(nodeId, command, requestId);
    }

    public NodeCommander getNodeCommander() {
        return this.nodeCommander;
    }

    public boolean disconnectNode(String nodeId) {
        return nodeRegistry.disconnectClientNode(nodeId);
    }

    public Map<String, NodeInfo> getNodes() {
        return nodeRegistry.getActiveClientNodes();
    }

    public boolean isUpstreamMasterConnected() {
        return upstreamMasterClient.isConnected();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("Server: New connection handshake from {}: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress(), handshake.getResourceDescriptor()); // Changed to logger.info

        String adminToken = NodeRegistry.getQueryParam(handshake.getResourceDescriptor(), "adminToken");

        if (ADMIN_TOKEN.equals(adminToken)) {
            boolean registered = nodeRegistry.registerIncomingTestMaster(conn);
            if (!registered) {
                conn.close(1008, "Only one test master is allowed.");
            }
            return;
        }

        String nodeId = NodeRegistry.getQueryParam(handshake.getResourceDescriptor(), "nodeId");

        if (nodeId == null || nodeId.isEmpty()) {
            conn.close(1008, "Missing nodeId query parameter. Connection closed.");
            logger.warn("Server: Connection from {} closed due to missing nodeId.", conn.getRemoteSocketAddress().getAddress().getHostAddress()); // Changed to logger.warn
            return;
        }

        nodeRegistry.registerClientNode(nodeId, conn);

        if (isUpstreamMasterConnected()) {
            upstreamMasterClient.reportNodeStatus(nodeId, "connected");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        NodeInfo info = nodeRegistry.getNodeInfoByConnection(conn);
        nodeRegistry.unregisterConnection(conn);

        if (info != null) {
            logger.warn("Server: {} disconnected: {}. Reason: {}", info.role, info.nodeId, reason); // Changed to logger.warn
        } else {
            logger.warn("Server: Unknown connection disconnected: {}. Reason: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress(), reason); // Changed to logger.warn
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        NodeInfo sender = nodeRegistry.getNodeInfoByConnection(conn);
        if (sender == null) {
            logger.info("Server: Unknown sender (not a recognized connection) message: {}", message); // Changed to logger.info
            return;
        }

        if (nodeRegistry.isIncomingTestMaster(conn)) {
            commandOrchestrator.handleMasterCommand("INCOMING_TEST", message);
            return;
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            logger.warn("Server: Invalid JSON from node {}: {}", sender.nodeId, message); // Changed to logger.warn
            return;
        }

        sender.updateActivity();

        if (json.has("type") && json.get("type").getAsString().equals("ping")) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "pong");
            conn.send(pong.toString());
            logger.info("Server: Ping received from {}, sent pong.", sender.nodeId); // Changed to logger.info
            return;
        }

        if (json.has("targetNodeId")) {
            String targetNodeId = json.get("targetNodeId").getAsString();
            NodeInfo target = nodeRegistry.getClientNodeInfo(targetNodeId);
            if (target != null && target.conn != null && target.conn.isOpen()) {
                target.conn.send(json.toString());
                logger.info("Server: Forwarded message from {} to {}", sender.nodeId, targetNodeId); // Changed to logger.info
            } else {
                logger.warn("Server: Target node not found or not available: {}", targetNodeId); // Changed to logger.warn
            }
        } else {
            commandOrchestrator.handleNodeResponse(sender, json);
        }
    }

    private void handleMessageFromUpstreamMaster(String sourceUrl, String message) {
        commandOrchestrator.handleMasterCommand("UPSTREAM", message);
    }

    @Override
    public void sendErrorToIncomingTestMaster(String requestId, String nodeId, String errorMessage) {
        WebSocket masterConn = nodeRegistry.getIncomingTestMasterWebSocket();
        if (masterConn != null && masterConn.isOpen()) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("type", "node_response");
            errorResponse.addProperty("requestId", requestId);
            errorResponse.addProperty("nodeId", nodeId);
            JsonObject response = new JsonObject();
            response.addProperty("status", "error");
            response.addProperty("message", errorMessage);
            errorResponse.add("response", response);
            masterConn.send(errorResponse.toString());
            logger.info("Server: Sent error to INCOMING Mock Master: {}", errorMessage); // Changed to logger.info
        }
    }

    @Override
    public void forwardResponseToIncomingTestMaster(String nodeId, JsonObject response, String requestId) {
        WebSocket masterConn = nodeRegistry.getIncomingTestMasterWebSocket();
        if (masterConn != null && masterConn.isOpen()) {
            JsonObject masterResponse = new JsonObject();
            masterResponse.addProperty("type", "node_response");
            masterResponse.addProperty("requestId", requestId);
            masterResponse.addProperty("nodeId", nodeId);
            masterResponse.add("response", response);

            masterConn.send(masterResponse.toString());
            logger.info("Server: Forwarded response to INCOMING Mock Master Server for requestId: {}", requestId); // Changed to logger.info
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        NodeInfo info = nodeRegistry.getNodeInfoByConnection(conn);
        if (info != null) {
            logger.error("Server: {} connection error for {}: {}", info.role, info.nodeId, ex.getMessage(), ex); // Changed to logger.error, added ex
        } else {
            logger.error("Server: Unknown connection error: {}: {}", (conn != null ? conn.getRemoteSocketAddress().getAddress().getHostAddress() : "null connection"), ex.getMessage(), ex); // Changed to logger.error, added ex
        }
    }

    @Override
    public void onStart() {
        logger.info("🚀 Server started on port {}", getPort()); // Changed to logger.info
    }

    @Override
    public void stop() throws InterruptedException {
        super.stop();
        logger.info("Server: Shutting down shared scheduler..."); // Changed to logger.info
        sharedScheduler.shutdown();
        try {
            if (!sharedScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.error("Server: Shared scheduler did not terminate in time. Forcing shutdown."); // Changed to logger.error
                sharedScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Server: Interrupted while waiting for scheduler to terminate.", e); // Changed to logger.error, added e
            sharedScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Server: Shared scheduler shut down."); // Changed to logger.info
    }
}