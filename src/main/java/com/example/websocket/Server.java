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
import com.example.websocket.IncomingTestMasterSender; // <--- THIS IS THE IMPORT YOU NEEDED TO ADD

// UPDATED CLASS DECLARATION: NOW IMPLEMENTS IncomingTestMasterSender
public class Server extends WebSocketServer implements IncomingTestMasterSender {
    private static final String ADMIN_TOKEN = "super-secret-admin-token";

    private final NodeRegistry nodeRegistry;
    private final UpstreamMasterClient upstreamMasterClient;
    private final ScheduledExecutorService sharedScheduler = Executors.newSingleThreadScheduledExecutor();

    private final CommandOrchestrator commandOrchestrator;
    private final NodeCommander nodeCommander;


    public Server(int port) {
        super(new InetSocketAddress(port));
        System.out.println("Server: Initializing on port " + port);

        this.nodeRegistry = new NodeRegistry();

        this.upstreamMasterClient = new UpstreamMasterClient(
            sharedScheduler,
            null,
            this::handleMessageFromUpstreamMaster
        );

        this.nodeCommander = new NodeCommander(nodeRegistry);

        // This line remains the same as our last successful build,
        // but it's important that 'this' (the Server instance) is passed
        // because Server now implements IncomingTestMasterSender.
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
        System.out.println("Server: New connection handshake from " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + handshake.getResourceDescriptor());

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
            System.out.println("❌ Server: Connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + " closed due to missing nodeId.");
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
            System.out.println("❌ Server: " + info.role + " disconnected: " + info.nodeId + ". Reason: " + reason);

            if (info.role == Role.CLIENT_NODE && isUpstreamMasterConnected()) {
                upstreamMasterClient.reportNodeStatus(info.nodeId, "disconnected");
            }
        } else {
            System.out.println("❌ Server: Unknown connection disconnected: " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + ". Reason: " + reason);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        NodeInfo sender = nodeRegistry.getNodeInfoByConnection(conn);
        if (sender == null) {
            System.out.println("Server: Unknown sender (not a recognized connection) message: " + message);
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
            System.out.println("Server: Invalid JSON from node " + sender.nodeId + ": " + message);
            return;
        }

        sender.updateActivity();

        if (json.has("type") && json.get("type").getAsString().equals("ping")) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "pong");
            conn.send(pong.toString());
            System.out.println("🏓 Server: Ping received from " + sender.nodeId + ", sent pong.");
            return;
        }

        if (json.has("targetNodeId")) {
            String targetNodeId = json.get("targetNodeId").getAsString();
            NodeInfo target = nodeRegistry.getClientNodeInfo(targetNodeId);
            if (target != null && target.conn != null && target.conn.isOpen()) {
                target.conn.send(json.toString());
                System.out.println("📨 Server: Forwarded message from " + sender.nodeId + " to " + targetNodeId);
            } else {
                System.out.println("❌ Server: Target node not found or not available: " + targetNodeId);
            }
        } else {
            commandOrchestrator.handleNodeResponse(sender, json);
        }
    }

    private void handleMessageFromUpstreamMaster(String sourceUrl, String message) {
        commandOrchestrator.handleMasterCommand("UPSTREAM", message);
    }

    // These methods now implicitly implement IncomingTestMasterSender's interface methods
    @Override // <--- You can optionally add @Override annotations here for clarity
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
            System.out.println("📤 Server: Sent error to INCOMING Mock Master: " + errorMessage);
        }
    }

    @Override // <--- You can optionally add @Override annotations here for clarity
    public void forwardResponseToIncomingTestMaster(String nodeId, JsonObject response, String requestId) {
        WebSocket masterConn = nodeRegistry.getIncomingTestMasterWebSocket();
        if (masterConn != null && masterConn.isOpen()) {
            JsonObject masterResponse = new JsonObject();
            masterResponse.addProperty("type", "node_response");
            masterResponse.addProperty("requestId", requestId);
            masterResponse.addProperty("nodeId", nodeId);
            masterResponse.add("response", response);

            masterConn.send(masterResponse.toString());
            System.out.println("📤 Server: Forwarded response to INCOMING Mock Master Server for requestId: " + requestId);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        NodeInfo info = nodeRegistry.getNodeInfoByConnection(conn);
        if (info != null) {
            System.err.println("❌ Server: " + info.role + " connection error for " + info.nodeId + ": " + ex.getMessage());
        } else {
            System.err.println("❌ Server: Unknown connection error: " + (conn != null ? conn.getRemoteSocketAddress().getAddress().getHostAddress() : "null connection") + ": " + ex.getMessage());
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("🚀 Server started on port " + getPort());
    }

    @Override
    public void stop() throws InterruptedException {
        super.stop();
        System.out.println("Server: Shutting down shared scheduler...");
        sharedScheduler.shutdown();
        try {
            if (!sharedScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Server: Shared scheduler did not terminate in time. Forcing shutdown.");
                sharedScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Server: Interrupted while waiting for scheduler to terminate.");
            sharedScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Server: Shared scheduler shut down.");
    }
}