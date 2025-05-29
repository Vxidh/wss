package com.example.websocket;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet; // Added for safe iteration over nodes

public class Server extends WebSocketServer {
    private static final long IDLE_TIMEOUT_MS = 30000;
    // Define the admin token expected from the mock master for authentication
    private static final String ADMIN_TOKEN = "super-secret-admin-token"; // MUST match mock_master.py

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
        public final Role role; // Added role field to NodeInfo

        public NodeInfo(WebSocket conn, String nodeId, Role role) {
            this.conn = conn;
            this.lastActivity = System.currentTimeMillis();
            this.connectedAt = this.lastActivity;
            this.status = NodeStatus.ACTIVE;
            this.nodeId = nodeId;
            this.role = role; // Initialize role
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
            this.status = NodeStatus.ACTIVE;
        }

        public boolean isIdle() {
            return System.currentTimeMillis() - lastActivity > IDLE_TIMEOUT_MS;
        }

        public boolean shouldDisconnect() {
            return System.currentTimeMillis() - connectedAt >= 30 * 60 * 1000;
        }
    }

    // Define roles for incoming connections to this server
    public enum Role {
        CLIENT_NODE,          // Standard node clients connecting to this server
        INCOMING_TEST_MASTER // The mock_master.py connecting to this server for testing
    }

    private final ConcurrentHashMap<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, NodeInfo> connToNode = new ConcurrentHashMap<>();
    // requestId -> nodeId mapping: To track which node was the target of a request
    // This is useful when a node sends a response, to know which original request it belongs to.
    private final ConcurrentHashMap<String, String> requestIdMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // --- Variables specifically for the INCOMING_TEST_MASTER (mock_master.py) ---
    private WebSocket incomingTestMasterWebSocket = null;
    // Map to quickly check if a WebSocket is the incoming test master
    private final ConcurrentHashMap<WebSocket, Boolean> isIncomingTestMaster = new ConcurrentHashMap<>();


    // --- Variables for connecting OUT to an UPSTREAM Master Server (Scenario 1 ideal case) ---
    private WebSocketClient upstreamMasterClient;
    private String upstreamMasterServerUrl;
    private volatile boolean upstreamMasterConnected = false;

    public Server(int port) {
        super(new InetSocketAddress(port));
        scheduler.scheduleAtFixedRate(this::checkIdleNodes, 10, 10, TimeUnit.SECONDS);
    }

    // --- Methods for connecting OUT to an UPSTREAM Master Server ---
    // This is your existing 'connectToMaster' logic, renamed for clarity.
    public void connectToUpstreamMaster(String masterUrl) {
        this.upstreamMasterServerUrl = masterUrl;
        try {
            URI serverUri = URI.create(masterUrl);

            this.upstreamMasterClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("✅ Connected to UPSTREAM Master Server: " + upstreamMasterServerUrl);
                    upstreamMasterConnected = true;

                    // Send registration message to the upstream master
                    JsonObject registration = new JsonObject();
                    registration.addProperty("type", "middleman_register");
                    registration.addProperty("serverId", "middleman-" + System.currentTimeMillis()); // Unique ID for this server
                    send(registration.toString());
                }

                @Override
                public void onMessage(String message) {
                    handleMessageFromUpstreamMaster(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("❌ Disconnected from UPSTREAM Master Server: " + reason);
                    upstreamMasterConnected = false;

                    // Auto-reconnect after 5 seconds
                    scheduler.schedule(() -> {
                        System.out.println("🔄 Attempting to reconnect to UPSTREAM Master Server...");
                        connectToUpstreamMaster(upstreamMasterServerUrl);
                    }, 5, TimeUnit.SECONDS);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("❌ UPSTREAM Master Server connection error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            };

            upstreamMasterClient.connect();

        } catch (Exception e) {
            System.err.println("Failed to connect to UPSTREAM Master Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Handles commands received from the UPSTREAM Master Server
    private void handleMessageFromUpstreamMaster(String message) {
        try {
            JsonObject command = JsonParser.parseString(message).getAsJsonObject();
            System.out.println("📨 Received command from UPSTREAM Master: " + message);

            String type = command.get("type").getAsString();

            if ("node_command".equals(type)) {
                String targetNodeId = command.get("nodeId").getAsString();
                String requestId = command.get("requestId").getAsString();
                JsonObject nodeCommand = command.get("command").getAsJsonObject();

                // Store requestId mapping for responses
                requestIdMap.put(requestId, targetNodeId); // Map requestId to targetNodeId

                // Forward to the target node
                boolean sent = sendToNodeWithRequestId(targetNodeId, nodeCommand, requestId);

                if (!sent) {
                    // Send error response back to the UPSTREAM Master
                    sendErrorToUpstreamMaster(requestId, targetNodeId, "Node not connected or idle");
                }
            } else {
                System.out.println("UPSTREAM Master sent unrecognized message type: " + type);
            }

        } catch (Exception e) {
            System.err.println("Error processing UPSTREAM master command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends an error message back to the UPSTREAM Master
    private void sendErrorToUpstreamMaster(String requestId, String nodeId, String errorMessage) {
        if (upstreamMasterClient != null && upstreamMasterConnected) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("type", "node_response");
            errorResponse.addProperty("requestId", requestId);
            errorResponse.addProperty("nodeId", nodeId);

            JsonObject response = new JsonObject();
            response.addProperty("status", "error");
            response.addProperty("message", errorMessage);
            errorResponse.add("response", response);

            upstreamMasterClient.send(errorResponse.toString());
            System.out.println("📤 Sent error to UPSTREAM Master: " + errorMessage);
        }
    }

    // Forwards a node's response back to the UPSTREAM Master
    private void forwardResponseToUpstreamMaster(String nodeId, JsonObject response, String requestId) {
        if (upstreamMasterClient != null && upstreamMasterConnected) {
            JsonObject masterResponse = new JsonObject();
            masterResponse.addProperty("type", "node_response");
            masterResponse.addProperty("requestId", requestId);
            masterResponse.addProperty("nodeId", nodeId);
            masterResponse.add("response", response);

            upstreamMasterClient.send(masterResponse.toString());
            System.out.println("📤 Forwarded response to UPSTREAM Master Server for requestId: " + requestId);

            // Clean up requestId mapping
            requestIdMap.remove(requestId);
        }
    }

    // --- Core WebSocket Server Callbacks (for incoming connections) ---

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection handshake from " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + handshake.getResourceDescriptor());

        String adminToken = getQueryParam(handshake.getResourceDescriptor(), "adminToken");

        // --- Step 1: Check if this is the INCOMING_TEST_MASTER (mock_master.py) ---
        if (ADMIN_TOKEN.equals(adminToken)) {
            if (incomingTestMasterWebSocket == null || !incomingTestMasterWebSocket.isOpen()) {
                // This is the first or a replacement incoming test master connection
                incomingTestMasterWebSocket = conn;
                isIncomingTestMaster.put(conn, true); // Mark this connection as the test master
                // Create a NodeInfo, but it won't be part of the 'nodes' map that holds client nodes
                // It's mainly for connToNode consistency and role tracking.
                connToNode.put(conn, new NodeInfo(conn, "INCOMING_TEST_MASTER", Role.INCOMING_TEST_MASTER));
                System.out.println("✅ INCOMING Mock Master Server connected from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
                return; // Do not process as a regular node
            } else {
                // Another mock master tried to connect, but we only allow one for testing
                System.out.println("Another INCOMING Mock Master tried to connect. Only one test master allowed. Closing connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
                conn.close(1008, "Only one test master is allowed.");
                return;
            }
        }

        // --- Step 2: If not an incoming test master, process as a regular CLIENT_NODE ---
        String nodeId = getQueryParam(handshake.getResourceDescriptor(), "nodeId");

        if (nodeId == null || nodeId.isEmpty()) {
            conn.close(1008, "Missing nodeId query parameter. Connection closed.");
            System.out.println("❌ Connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + " closed due to missing nodeId.");
            return;
        }

        NodeInfo nodeInfo = new NodeInfo(conn, nodeId, Role.CLIENT_NODE); // Assign CLIENT_NODE role
        nodeInfo.authenticated = true; // Assuming nodeId in URL is authentication for now

        // Handle potential existing connection for the same nodeId
        NodeInfo oldInfo = nodes.put(nodeId, nodeInfo);
        connToNode.put(conn, nodeInfo);

        if (oldInfo != null && oldInfo.conn != conn && oldInfo.conn.isOpen()) {
            oldInfo.conn.close(1000, "Replaced by new connection for same nodeId: " + nodeId);
            System.out.println("Replacement: Old connection for node " + nodeId + " closed.");
        }

        System.out.println("🔗 Node connected: " + nodeId + " from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());

        // Notify UPSTREAM Master Server about new node connection (if connected)
        if (upstreamMasterClient != null && upstreamMasterConnected) {
            JsonObject nodeStatus = new JsonObject();
            nodeStatus.addProperty("type", "node_status");
            nodeStatus.addProperty("nodeId", nodeId);
            nodeStatus.addProperty("status", "connected");
            upstreamMasterClient.send(nodeStatus.toString());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // --- Step 1: Check if the disconnecting connection is the INCOMING_TEST_MASTER ---
        if (isIncomingTestMaster.containsKey(conn)) {
            System.out.println("❌ INCOMING Mock Master Server disconnected: " + reason);
            incomingTestMasterWebSocket = null;
            isIncomingTestMaster.remove(conn);
            connToNode.remove(conn); // Also remove from the general connToNode map
            return; // Handled, no further processing as a regular node
        }

        // --- Step 2: Handle as a regular CLIENT_NODE disconnection ---
        NodeInfo info = connToNode.remove(conn);
        if (info != null) {
            nodes.remove(info.nodeId); // Remove from active nodes map
            System.out.println("❌ Node disconnected: " + info.nodeId + ". Reason: " + reason);

            // Notify UPSTREAM Master Server about node disconnection (if connected)
            if (upstreamMasterClient != null && upstreamMasterConnected) {
                JsonObject nodeStatus = new JsonObject();
                nodeStatus.addProperty("type", "node_status");
                nodeStatus.addProperty("nodeId", info.nodeId);
                nodeStatus.addProperty("status", "disconnected");
                upstreamMasterClient.send(nodeStatus.toString());
            }
        } else {
            System.out.println("❌ Unknown connection disconnected: " + reason);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // --- Step 1: Check if the message is from the INCOMING_TEST_MASTER ---
        if (isIncomingTestMaster.containsKey(conn)) {
            handleMessageFromIncomingTestMaster(conn, message);
            return; // Message handled, no further processing for regular nodes
        }

        // --- Step 2: Message is from a regular CLIENT_NODE ---
        NodeInfo sender = connToNode.get(conn);
        if (sender == null) {
            System.out.println("Unknown sender (not a recognized node) message: " + message);
            return;
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            System.out.println("Invalid JSON from node " + sender.nodeId + ": " + message);
            return;
        }

        sender.updateActivity(); // Update activity for the sending node

        // Handle Ping/Pong
        if (json.has("type") && json.get("type").getAsString().equals("ping")) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "pong");
            conn.send(pong.toString());
            System.out.println("🏓 Ping received from " + sender.nodeId + ", sent pong.");
            return;
        }

        // Handle messages targeting another node (e.g., node-to-node communication through relay)
        if (json.has("targetNodeId")) {
            String targetNodeId = json.get("targetNodeId").getAsString();
            NodeInfo target = nodes.get(targetNodeId);
            if (target != null && target.conn != null && target.conn.isOpen()) {
                target.conn.send(json.toString());
                System.out.println("📨 Forwarded message from " + sender.nodeId + " to " + targetNodeId);
            } else {
                System.out.println("❌ Target node not found or not available: " + targetNodeId);
            }
        } else {
            // Handle command responses from nodes
            if (json.has("type") && json.get("type").getAsString().equals("commandResponse")) {
                System.out.println("📨 Command response from node " + sender.nodeId + ": " + message);

                String requestId = json.has("requestId") ? json.get("requestId").getAsString() : null;
                JsonObject responsePayload = json.has("response") ? json.get("response").getAsJsonObject() : new JsonObject();

                if (requestId != null) {
                    // Check if the original command came from the INCOMING_TEST_MASTER or UPSTREAM_MASTER
                    // This is a simplification; a more robust system might store which master issued the request
                    if (incomingTestMasterWebSocket != null && isIncomingTestMaster.containsKey(incomingTestMasterWebSocket)) {
                        // Assuming, for testing, that if mock master is connected, responses go back to it
                        // You might need more complex logic if specific requestId needs to go to a specific master.
                        // For testing, this is usually sufficient: mock master sends, mock master gets response.
                        forwardResponseToIncomingTestMaster(sender.nodeId, responsePayload, requestId);
                    } else if (upstreamMasterClient != null && upstreamMasterConnected) {
                        forwardResponseToUpstreamMaster(sender.nodeId, responsePayload, requestId);
                    } else {
                        System.out.println("⚠️ No active master connection to forward response for requestId: " + requestId);
                    }
                } else {
                    System.out.println("⚠️ No requestId found in commandResponse from node " + sender.nodeId + ", cannot forward to master.");
                }
            } else {
                // General messages from nodes (not specific command responses or pings)
                System.out.println("📨 Received general message from node " + sender.nodeId + ": " + message);
                // You might choose to log these, or forward them to a master for monitoring, etc.
            }
        }
    }

    // --- Methods for handling messages from the INCOMING_TEST_MASTER (mock_master.py) ---

    // Handles commands received from the INCOMING_TEST_MASTER (mock_master.py)
    private void handleMessageFromIncomingTestMaster(WebSocket conn, String message) {
        System.out.println("📨 Received message from INCOMING Mock Master Client: " + message);
        try {
            JsonObject command = JsonParser.parseString(message).getAsJsonObject();

            String type = command.get("type").getAsString();

            if ("node_command".equals(type)) {
                String targetNodeId = command.get("nodeId").getAsString();
                String requestId = command.get("requestId").getAsString();
                JsonObject nodeCommand = command.get("command").getAsJsonObject();

                // Store requestId mapping for responses.
                // For simplicity, we just map requestId to targetNodeId.
                // If you had multiple *incoming* test masters, this would need to map requestId to the specific WebSocket conn of the master.
                requestIdMap.put(requestId, targetNodeId);

                boolean sent = sendToNodeWithRequestId(targetNodeId, nodeCommand, requestId);

                if (!sent) {
                    // Send error response back to the INCOMING_TEST_MASTER
                    sendErrorToIncomingTestMaster(requestId, targetNodeId, "Node not connected or idle.");
                }
            } else {
                System.out.println("INCOMING Mock Master sent unrecognized message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error processing message from INCOMING Mock Master: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends an error message back to the INCOMING_TEST_MASTER
    private void sendErrorToIncomingTestMaster(String requestId, String nodeId, String errorMessage) {
        if (incomingTestMasterWebSocket != null && incomingTestMasterWebSocket.isOpen()) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("type", "node_response");
            errorResponse.addProperty("requestId", requestId);
            errorResponse.addProperty("nodeId", nodeId); // Include nodeId for clarity
            JsonObject response = new JsonObject();
            response.addProperty("status", "error");
            response.addProperty("message", errorMessage);
            errorResponse.add("response", response);
            incomingTestMasterWebSocket.send(errorResponse.toString());
            System.out.println("📤 Sent error to INCOMING Mock Master: " + errorMessage);
        }
    }

    // Forwards a node's response back to the INCOMING_TEST_MASTER
    private void forwardResponseToIncomingTestMaster(String nodeId, JsonObject response, String requestId) {
        if (incomingTestMasterWebSocket != null && incomingTestMasterWebSocket.isOpen()) {
            JsonObject masterResponse = new JsonObject();
            masterResponse.addProperty("type", "node_response");
            masterResponse.addProperty("requestId", requestId);
            masterResponse.addProperty("nodeId", nodeId);
            masterResponse.add("response", response);

            incomingTestMasterWebSocket.send(masterResponse.toString());
            System.out.println("📤 Forwarded response to INCOMING Mock Master Server for requestId: " + requestId);

            // Clean up requestId mapping
            requestIdMap.remove(requestId);
        }
    }

    // --- General Utility and Management Methods ---

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null && isIncomingTestMaster.containsKey(conn)) {
            System.err.println("❌ INCOMING Mock Master connection error: " + ex.getMessage());
        } else if (conn != null && connToNode.containsKey(conn)) {
            NodeInfo info = connToNode.get(conn);
            System.err.println("❌ Node " + info.nodeId + " connection error: " + ex.getMessage());
        } else {
            System.err.println("❌ Unknown connection error: " + ex.getMessage());
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("🚀 Server started on port " + getPort());
    }

    private void checkIdleNodes() {
        // Iterate over a copy of the key set to avoid ConcurrentModificationException
        // if nodes are removed during iteration.
        new HashSet<>(nodes.keySet()).forEach(nodeId -> {
            NodeInfo info = nodes.get(nodeId);
            if (info == null) return; // Node might have been removed by another thread

            if (info.status == NodeStatus.ACTIVE && info.isIdle()) {
                info.status = NodeStatus.IDLE;
                System.out.println("😴 Node " + nodeId + " marked as IDLE");
                // Optional: Notify UPSTREAM Master about idle status
                if (upstreamMasterClient != null && upstreamMasterConnected) {
                    JsonObject nodeStatus = new JsonObject();
                    nodeStatus.addProperty("type", "node_status");
                    nodeStatus.addProperty("nodeId", nodeId);
                    nodeStatus.addProperty("status", "idle");
                    upstreamMasterClient.send(nodeStatus.toString());
                }
            }

            // Disconnect nodes that have been connected for too long (e.g., 30 minutes)
            if (info.shouldDisconnect()) {
                System.out.println("⏰ Node " + nodeId + " has been connected >30 minutes. Disconnecting.");
                disconnectNode(nodeId); // This will trigger the onClose method, which notifies upstream master.
            }
        });
    }

    // Sends a command to a specific node (used by both incoming masters)
    public boolean sendToNode(String nodeId, JsonObject command) {
        // This public method is generally used by external logic (like a main server class)
        // to send arbitrary commands to nodes.
        NodeInfo info = nodes.get(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "command"); // Wrap the command
            wrapper.add("command", command);
            info.conn.send(wrapper.toString());
            System.out.println("📤 Sent direct command to node " + nodeId + ": " + command.toString());
            return true;
        }
        System.out.println("❌ Failed to send command to node " + nodeId + ": Node not found, not open, or idle.");
        return false;
    }

    // Sends a command to a specific node, including a requestId for response tracking
    // This is the version used internally when commands come from a master.
    public boolean sendToNodeWithRequestId(String nodeId, JsonObject command, String requestId) {
        NodeInfo info = nodes.get(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "command");
            wrapper.addProperty("requestId", requestId); // Include requestId
            wrapper.add("command", command);

            info.conn.send(wrapper.toString());
            System.out.println("📤 Forwarded command to node " + nodeId + " with requestId: " + requestId);
            return true;
        }
        return false;
    }

    // Disconnects a specific node by its ID
    public boolean disconnectNode(String nodeId) {
        NodeInfo info = nodes.remove(nodeId); // Remove from 'nodes' map
        if (info != null && info.conn != null && info.conn.isOpen()) {
            info.conn.close(1000, "Disconnected by server request"); // Close the WebSocket connection
            // The onClose callback will handle the removal from connToNode and notification to upstream master
            System.out.println("❌ Node " + nodeId + " disconnected by server request.");
            return true;
        }
        return false;
    }

    // Extracts a query parameter from a WebSocket resource descriptor (URL)
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

    // Returns an unmodifiable map of currently connected nodes
    public Map<String, NodeInfo> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    // Checks if the UPSTREAM master is connected
    public boolean isUpstreamMasterConnected() {
        return upstreamMasterConnected;
    }

    public static void main(String[] args) {
        System.err.println("Please use MainServer.java to start the application (handles config init and port).");
        System.exit(1);
    }
}