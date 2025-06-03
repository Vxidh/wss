package com.example.websocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

import com.example.websocket.NodeRegistry.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(CommandOrchestrator.class);

    private final NodeRegistry nodeRegistry;
    // REMOVED: private final UpstreamMasterClient upstreamMasterClient; // THIS LINE IS NOW GONE
    private final NodeCommander nodeCommander;
    private final IncomingTestMasterSender incomingMasterSender;

    // Maps requestId to the source client's ID (Batch Server Client ID or "INCOMING_TEST")
    private final ConcurrentHashMap<String, String> pendingRequestsSource = new ConcurrentHashMap<>();

    // CORRECTED CONSTRUCTOR: Removed UpstreamMasterClient parameter
    public CommandOrchestrator(NodeRegistry nodeRegistry, NodeCommander nodeCommander, IncomingTestMasterSender incomingMasterSender) {
        this.nodeRegistry = nodeRegistry;
        this.nodeCommander = nodeCommander;
        this.incomingMasterSender = incomingMasterSender;
        logger.info("CommandOrchestrator: Initialized.");
    }

    /**
     * Handles commands coming from the Incoming Test Master.
     * Commands from the Batch Server Client are handled directly in Server.onMessage and routed via trackPendingRequest.
     * @param masterType "INCOMING_TEST"
     * @param message The raw JSON message string from the master.
     */
    public void handleMasterCommand(String masterType, String message) {
        // Since UPSTREAM is removed, this method should only be called for "INCOMING_TEST"
        if (!"INCOMING_TEST".equals(masterType)) {
            logger.warn("CommandOrchestrator: Received unexpected masterType '{}'. Ignoring message: {}", masterType, message);
            return;
        }

        logger.info("CommandOrchestrator: Received command from {} Master: {}", masterType, message);
        try {
            JsonObject command = JsonParser.parseString(message).getAsJsonObject();
            String type = command.has("type") ? command.get("type").getAsString() : null;

            if ("node_command".equals(type)) {
                String targetNodeId = command.has("nodeId") ? command.get("nodeId").getAsString() : null;
                String requestId = command.has("requestId") ? command.get("requestId").getAsString() : null;
                JsonObject nodeCommand = command.has("command") ? command.get("command").getAsJsonObject() : null;

                if (targetNodeId == null || requestId == null || nodeCommand == null) {
                    logger.error("CommandOrchestrator: Invalid 'node_command' from {} Master: Missing nodeId, requestId, or command payload. Message: {}", masterType, message);
                    sendErrorToMaster(masterType, requestId, "Invalid 'node_command' format", targetNodeId);
                    return;
                }

                // Track this request, mapping its requestId to its original master type
                pendingRequestsSource.put(requestId, masterType);
                logger.debug("CommandOrchestrator: Tracking request {} from {}.", requestId, masterType);

                boolean sent = nodeCommander.sendToNodeWithRequestId(targetNodeId, nodeCommand, requestId);

                if (!sent) {
                    pendingRequestsSource.remove(requestId); // Remove if not sent
                    sendErrorToMaster(masterType, requestId, "Node " + targetNodeId + " not connected or idle.", targetNodeId);
                }
            } else {
                logger.warn("CommandOrchestrator: {} Master sent unrecognized message type: {}. Message: {}", masterType, type, message);
                sendErrorToMaster(masterType, null, "Unrecognized message type: " + type, null);
            }
        } catch (Exception e) {
            logger.error("CommandOrchestrator: Error processing message from {} Master: {}. Message: {}", masterType, e.getMessage(), message, e);
            sendErrorToMaster(masterType, null, "Error processing command: " + e.getMessage(), null);
        }
    }

    public void trackPendingRequest(String requestId, String sourceClientId, String targetNodeId) {
        // Store the requestId mapping to the sourceClientId to route response back
        pendingRequestsSource.put(requestId, sourceClientId);
        logger.info("CommandOrchestrator: Tracking pending request {} for Batch Server Client {} (target RPA Node: {}).", requestId, sourceClientId, targetNodeId);
    }
    public void handleNodeResponse(NodeInfo sender, JsonObject responseJson) {
        String requestId = responseJson.has("requestId") ? responseJson.get("requestId").getAsString() : null;

        if (requestId != null) {
            String sourceIdentifier = pendingRequestsSource.remove(requestId); // Attempt to remove and get original source

            if (sourceIdentifier != null) {
                // Determine the original source type and route the response
                if ("INCOMING_TEST".equals(sourceIdentifier)) {
                    if (nodeRegistry.getIncomingTestMasterWebSocket() != null && nodeRegistry.getIncomingTestMasterWebSocket().isOpen()) {
                        incomingMasterSender.forwardResponseToIncomingTestMaster(sender.nodeId, responseJson, requestId);
                        logger.info("CommandOrchestrator: Routed response for requestId {} to INCOMING Test Master.", requestId);
                    } else {
                        logger.warn("CommandOrchestrator: INCOMING Test Master not connected. Cannot route response for requestId {}.", requestId);
                    }
                } else {
                    // This must be a Batch Server Client's request (sourceIdentifier is clientId here)
                    WebSocket batchServerClientConn = nodeRegistry.getBatchServerClientWebSocket(sourceIdentifier);
                    if (batchServerClientConn != null && batchServerClientConn.isOpen()) {
                        // Reconstruct the full response message as expected by the Batch Server Client
                        JsonObject fullResponse = new JsonObject();
                        fullResponse.addProperty("type", "node_response");
                        fullResponse.addProperty("requestId", requestId);
                        fullResponse.addProperty("nodeId", sender.nodeId);

                        JsonObject actualResponse = responseJson.getAsJsonObject("response");

                        if (actualResponse != null) {
                            fullResponse.add("response", actualResponse);
                        } else {
                            actualResponse = new JsonObject();
                            actualResponse.addProperty("status", "error");
                            actualResponse.addProperty("message", "Node response missing 'response' object from RPA Node.");
                            fullResponse.add("response", actualResponse);
                            logger.warn("CommandOrchestrator: RPA Node {} response for requestId {} missing 'response' object. Sending error back to Batch Server Client {}.", sender.nodeId, requestId, sourceIdentifier);
                        }
                        batchServerClientConn.send(fullResponse.toString());
                        logger.info("CommandOrchestrator: Routed response for requestId {} to Batch Server Client {}.", requestId, sourceIdentifier);
                    } else {
                        logger.warn("CommandOrchestrator: Batch Server Client {} (for request {}) not found or disconnected. Cannot route response.", sourceIdentifier, requestId);
                    }
                }
            } else {
                logger.warn("CommandOrchestrator: Received node response for unknown or already handled requestId: {}. Response: {}", requestId, responseJson);
            }
        } else {
            logger.warn("CommandOrchestrator: Received node response without requestId: {}", responseJson);
        }
    }

    private void sendErrorToMaster(String masterType, String requestId, String errorMessage, String nodeId) {
        if ("INCOMING_TEST".equals(masterType)) {
            if (nodeRegistry.getIncomingTestMasterWebSocket() != null && nodeRegistry.getIncomingTestMasterWebSocket().isOpen()) {
                incomingMasterSender.sendErrorToIncomingTestMaster(requestId, nodeId, errorMessage);
                logger.info("CommandOrchestrator: Sent error to INCOMING Test Master for request {}: {}", requestId, errorMessage);
            } else {
                logger.warn("CommandOrchestrator: Failed to send error to INCOMING Test Master, not connected.");
            }
        }
    }
}