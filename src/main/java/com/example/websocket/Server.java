package com.example.websocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.example.websocket.NodeRegistry.NodeInfo;
import com.example.websocket.NodeRegistry.Role;

public class Server extends WebSocketServer implements IncomingTestMasterSender {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private final NodeRegistry nodeRegistry;
    private final NodeCommander nodeCommander;
    private final CommandOrchestrator commandOrchestrator;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // Reduced thread pool size as Upstream client is gone

    public Server(int port) {
        super(new InetSocketAddress(port));
        logger.info("Server: Initializing WebSocket server on port {}", port);

        this.nodeRegistry = new NodeRegistry();
        this.nodeCommander = new NodeCommander(nodeRegistry, this::handleNodeResponse); // Pass handleNodeResponse callback

        // THIS IS THE CORRECTED LINE FOR CommandOrchestrator INSTANTIATION
        this.commandOrchestrator = new CommandOrchestrator(nodeRegistry, nodeCommander, this);

        logger.info("Server: Initialization complete. Waiting for connections...");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("Server: New connection opened from {} with resource: {}", conn.getRemoteSocketAddress(), handshake.getResourceDescriptor());

        String resourceDescriptor = handshake.getResourceDescriptor();
        if ("/ws/incoming_test_master".equals(resourceDescriptor)) {
            if (!nodeRegistry.registerIncomingTestMaster(conn)) {
                logger.warn("Server: Rejected incoming test master connection from {} as one is already active.", conn.getRemoteSocketAddress());
            }
        } else {
            logger.info("Server: New connection {} awaiting identification message.", conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        NodeInfo info = nodeRegistry.getNodeInfoByConnection(conn);

        if (info == null || !info.authenticated) {
            handleIdentificationMessage(conn, message);
        } else {
            handleAuthenticatedMessage(info, message);
        }
    }

    private void handleIdentificationMessage(WebSocket conn, String message) {
        try {
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            String type = jsonMessage.has("type") ? jsonMessage.get("type").getAsString() : null;

            if ("identify_rpa_node".equals(type)) {
                String nodeId = jsonMessage.has("nodeId") ? jsonMessage.get("nodeId").getAsString() : null;
                if (nodeId != null && !nodeId.trim().isEmpty()) {
                    NodeInfo registeredNode = nodeRegistry.registerClientNode(nodeId, conn);
                    if (registeredNode != null) {
                        registeredNode.authenticated = true;
                        logger.info("Server: Identified and registered RPA Node '{}' from {}.", nodeId, conn.getRemoteSocketAddress());
                        sendAcknowledgement(conn, "RPA Node '" + nodeId + "' successfully identified.");
                    } else {
                        logger.warn("Server: RPA Node ID '{}' from {} is already registered. Connection replaced if new.", nodeId, conn.getRemoteSocketAddress());
                        sendError(conn, "RPA Node ID '" + nodeId + "' already in use. Connection might be replaced.");
                    }
                } else {
                    logger.warn("Server: Identification failed for {} - Missing or empty 'nodeId' in 'identify_rpa_node' message: {}", conn.getRemoteSocketAddress(), message);
                    sendError(conn, "Missing or empty 'nodeId' for RPA Node identification.");
                    conn.close(1008, "Invalid identification message: missing nodeId");
                }
            } else if ("identify_batch_client".equals(type)) {
                String clientId = jsonMessage.has("clientId") ? jsonMessage.get("clientId").getAsString() : null;
                if (clientId != null && !clientId.trim().isEmpty()) {
                    NodeInfo registeredClient = nodeRegistry.registerBatchServerClient(clientId, conn);
                    if (registeredClient != null) {
                        registeredClient.authenticated = true;
                        logger.info("Server: Identified and registered Batch Server Client '{}' from {}.", clientId, conn.getRemoteSocketAddress());
                        sendAcknowledgement(conn, "Batch Server Client '" + clientId + "' successfully identified.");
                    } else {
                        logger.warn("Server: Batch Server Client ID '{}' from {} is already registered. Connection replaced if new.", clientId, conn.getRemoteSocketAddress());
                        sendError(conn, "Batch Server Client ID '" + clientId + "' already in use. Connection might be replaced.");
                    }
                } else {
                    logger.warn("Server: Identification failed for {} - Missing or empty 'clientId' in 'identify_batch_client' message: {}", conn.getRemoteSocketAddress(), message);
                    sendError(conn, "Missing or empty 'clientId' for Batch Server Client identification.");
                    conn.close(1008, "Invalid identification message: missing clientId");
                }
            } else {
                logger.warn("Server: Unidentified connection {} sent unknown identification message type: {}. Message: {}", conn.getRemoteSocketAddress(), type, message);
                sendError(conn, "Invalid identification message type. Expected 'identify_rpa_node' or 'identify_batch_client'.");
                conn.close(1008, "Unrecognized identification message");
            }
        } catch (JsonSyntaxException e) {
            logger.warn("Server: Unidentified connection {} sent non-JSON message for identification: {}", conn.getRemoteSocketAddress(), message);
            sendError(conn, "Invalid message format. Expected JSON for identification.");
            conn.close(1008, "Invalid message format");
        } catch (Exception e) {
            logger.error("Server: Error during identification for connection {}: {}", conn.getRemoteSocketAddress(), e.getMessage(), e);
            sendError(conn, "Internal server error during identification.");
            conn.close(1011, "Internal server error during identification");
        }
    }

    private void handleAuthenticatedMessage(NodeInfo senderInfo, String message) {
        logger.debug("Server: Received authenticated message from {}({}): {}", senderInfo.role, senderInfo.nodeId, message);
        senderInfo.updateActivity();

        try {
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            String type = jsonMessage.has("type") ? jsonMessage.get("type").getAsString() : null;

            if (senderInfo.role == Role.CLIENT_NODE) {
                nodeCommander.handleIncomingNodeMessage(senderInfo, message);

                if ("status_update".equals(type)) {
                    String status = jsonMessage.has("status") ? jsonMessage.get("status").getAsString() : "unknown";
                    nodeRegistry.updateNodeStatus(senderInfo.nodeId, status);
                }
            } else if (senderInfo.role == Role.INCOMING_TEST_MASTER) {
                commandOrchestrator.handleMasterCommand("INCOMING_TEST", message);
            } else if (senderInfo.role == Role.BATCH_SERVER_CLIENT) {
                if ("node_command".equals(type)) {
                    String targetNodeId = jsonMessage.has("nodeId") ? jsonMessage.get("nodeId").getAsString() : null;
                    String requestId = jsonMessage.has("requestId") ? jsonMessage.get("requestId").getAsString() : null;
                    JsonObject nodeCommand = jsonMessage.has("command") ? jsonMessage.get("command").getAsJsonObject() : null;

                    if (targetNodeId == null || requestId == null || nodeCommand == null) {
                        logger.error("Server: Invalid 'node_command' from Batch Server Client '{}': Missing nodeId, requestId, or command payload. Message: {}", senderInfo.nodeId, message);
                        sendError(senderInfo.conn, "Invalid 'node_command' format. Missing nodeId, requestId, or command payload.");
                        return;
                    }

                    commandOrchestrator.trackPendingRequest(requestId, senderInfo.nodeId, targetNodeId);

                    boolean sent = nodeCommander.sendToNodeWithRequestId(targetNodeId, nodeCommand, requestId);

                    if (!sent) {
                        logger.warn("Server: Failed to send command for requestId {} to RPA Node {}. Node not connected or idle.", requestId, targetNodeId);
                        sendError(senderInfo.conn, requestId, "Node " + targetNodeId + " not connected or idle.");
                    } else {
                        logger.info("Server: Command for requestId {} sent to RPA Node {} from Batch Server Client {}.", requestId, targetNodeId, senderInfo.nodeId);
                    }
                } else {
                    logger.warn("Server: Unrecognized message type '{}' from Batch Server Client '{}': {}", type, senderInfo.nodeId, message);
                    sendError(senderInfo.conn, "Unrecognized message type for Batch Server Client.");
                }
            } else {
                logger.warn("Server: Received authenticated message from unknown role {} for node {}: {}", senderInfo.role, senderInfo.nodeId, message);
                sendError(senderInfo.conn, "Server does not support commands from this role.");
            }
        } catch (JsonSyntaxException e) {
            logger.warn("Server: Invalid JSON received from {}({}): {}", senderInfo.role, senderInfo.nodeId, message);
            sendError(senderInfo.conn, "Invalid JSON message format.");
        } catch (Exception e) {
            logger.error("Server: Error processing message from {}({}): {}", senderInfo.role, senderInfo.nodeId, e.getMessage(), e);
            sendError(senderInfo.conn, "Internal server error processing message.");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        NodeInfo info = nodeRegistry.unregisterConnection(conn);
        if (info != null) {
            logger.info("Server: Connection to {}({}) closed. Code: {}, Reason: {}, Remote: {}", info.role, info.nodeId, code, reason, remote);
            if (info.role == Role.CLIENT_NODE) {
                // No UpstreamMasterClient to report status to anymore
            }
        } else {
            logger.info("Server: Unknown connection {} closed. Code: {}, Reason: {}, Remote: {}", conn.getRemoteSocketAddress(), code, reason, remote);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        NodeInfo info = nodeRegistry.getNodeInfoByConnection(conn);
        String identifier = (info != null) ? info.nodeId + " (" + info.role + ")" : conn.getRemoteSocketAddress().toString();
        logger.error("Server: Error on connection {}: {}", identifier, ex.getMessage(), ex);

        if (conn != null && conn.isOpen()) {
            conn.close(1011, "Server error: " + ex.getMessage());
        }
    }

    @Override
    public void onStart() {
        logger.info("Server: WebSocket server started successfully on port {}", getPort());
        setConnectionLostTimeout(0); // Disable initial timeout to avoid immediate disconnects
        setConnectionLostTimeout(100); // Set to 100 seconds
    }

    private void handleNodeResponse(NodeInfo sender, JsonObject responseJson) {
        commandOrchestrator.handleNodeResponse(sender, responseJson);
    }

    @Override
    public void forwardResponseToIncomingTestMaster(String nodeId, JsonObject responseJson, String requestId) {
        WebSocket masterConn = nodeRegistry.getIncomingTestMasterWebSocket();
        if (masterConn != null && masterConn.isOpen()) {
            JsonObject fullResponse = new JsonObject();
            fullResponse.addProperty("type", "node_response");
            fullResponse.addProperty("requestId", requestId);
            fullResponse.addProperty("nodeId", nodeId);
            fullResponse.add("response", responseJson);
            masterConn.send(fullResponse.toString());
            logger.info("Server: Forwarded response for requestId {} to INCOMING Test Master.", requestId);
        } else {
            logger.warn("Server: INCOMING Test Master not connected. Cannot forward response for requestId {}.", requestId);
        }
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
            logger.info("Server: Sent error to INCOMING Test Master for request {}: {}", requestId, errorMessage);
        } else {
            logger.warn("Server: INCOMING Test Master not connected. Cannot send error for request {}.", requestId);
        }
    }

    private void sendAcknowledgement(WebSocket conn, String message) {
        JsonObject ack = new JsonObject();
        ack.addProperty("type", "acknowledgement");
        ack.addProperty("message", message);
        conn.send(ack.toString());
    }

    private void sendError(WebSocket conn, String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", errorMessage);
        conn.send(error.toString());
    }

    private void sendError(WebSocket conn, String requestId, String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        if (requestId != null) {
            error.addProperty("requestId", requestId);
        }
        error.addProperty("message", errorMessage);
        conn.send(error.toString());
    }

    public Map<String, NodeInfo> getNodes() {
        return nodeRegistry.getAllClientNodeInfo();
    }

    public NodeCommander getNodeCommander() {
        return nodeCommander;
    }

    public boolean disconnectNode(String nodeId) {
        NodeInfo nodeInfo = nodeRegistry.getClientNodeInfo(nodeId);
        if (nodeInfo != null && nodeInfo.conn != null && nodeInfo.conn.isOpen()) {
            nodeInfo.conn.close(1000, "Disconnected by API request.");
            logger.info("Server: API requested disconnection for node {}.", nodeId);
            return true;
        }
        logger.warn("Server: API disconnection request for node {} failed. Node not found or already closed.", nodeId);
        return false;
    }

    public void shutdown() {
        try {
            logger.info("Server: Shutting down...");
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            stop(1000);
            logger.info("Server: WebSocket server stopped.");
        } catch (InterruptedException e) {
            logger.error("Server: Shutdown interrupted.", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Server: Error during shutdown.", e);
        }
    }
}