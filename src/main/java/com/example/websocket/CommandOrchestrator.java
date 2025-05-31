// src/main/java/com/example/websocket/CommandOrchestrator.java
package com.example.websocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket; // Keep for now as NodeRegistry still returns WebSockets

import java.util.concurrent.ConcurrentHashMap;

import com.example.websocket.NodeRegistry.NodeInfo;
import com.example.websocket.NodeRegistry.Role;
import com.example.websocket.NodeCommander;
import com.example.websocket.IncomingTestMasterSender; // <--- NEW IMPORT HERE

public class CommandOrchestrator {
    private final NodeRegistry nodeRegistry;
    private final UpstreamMasterClient upstreamMasterClient;
    private final NodeCommander nodeCommander;
    private final IncomingTestMasterSender incomingMasterSender; // <--- CHANGED FIELD TYPE AND NAME

    private final ConcurrentHashMap<String, String> requestIdMap;

    // <--- UPDATED CONSTRUCTOR SIGNATURE: Now takes IncomingTestMasterSender
    public CommandOrchestrator(NodeRegistry nodeRegistry, UpstreamMasterClient upstreamMasterClient, NodeCommander nodeCommander, IncomingTestMasterSender incomingMasterSender) {
        this.nodeRegistry = nodeRegistry;
        this.upstreamMasterClient = upstreamMasterClient;
        this.nodeCommander = nodeCommander;
        this.incomingMasterSender = incomingMasterSender; // <--- ASSIGN TO NEW FIELD
        this.requestIdMap = new ConcurrentHashMap<>();
        System.out.println("CommandOrchestrator: Initialized.");
    }

    public void handleMasterCommand(String masterType, String message) {
        System.out.println("📨 CommandOrchestrator: Received command from " + masterType + " Master: " + message);
        try {
            JsonObject command = JsonParser.parseString(message).getAsJsonObject();
            String type = command.has("type") ? command.get("type").getAsString() : null;

            if ("node_command".equals(type)) {
                String targetNodeId = command.has("nodeId") ? command.get("nodeId").getAsString() : null;
                String requestId = command.has("requestId") ? command.get("requestId").getAsString() : null;
                JsonObject nodeCommand = command.has("command") ? command.get("command").getAsJsonObject() : null;

                if (targetNodeId == null || requestId == null || nodeCommand == null) {
                    System.err.println("❌ CommandOrchestrator: Invalid 'node_command' from " + masterType + " Master: Missing nodeId, requestId, or command payload.");
                    sendErrorToMaster(masterType, requestId, "Invalid 'node_command' format", targetNodeId);
                    return;
                }

                boolean sent = nodeCommander.sendToNodeWithRequestId(targetNodeId, nodeCommand, requestId);

                if (!sent) {
                    sendErrorToMaster(masterType, requestId, "Node " + targetNodeId + " not connected or idle.", targetNodeId);
                }
            } else {
                System.out.println("CommandOrchestrator: " + masterType + " Master sent unrecognized message type: " + type);
                sendErrorToMaster(masterType, null, "Unrecognized message type: " + type, null);
            }
        } catch (Exception e) {
            System.err.println("❌ CommandOrchestrator: Error processing message from " + masterType + " Master: " + e.getMessage());
            e.printStackTrace();
            sendErrorToMaster(masterType, null, "Error processing command: " + e.getMessage(), null);
        }
    }

    public void handleNodeResponse(NodeInfo sender, JsonObject json) {
        System.out.println("📨 CommandOrchestrator: Node response from " + sender.nodeId + ": " + json.toString());

        String requestId = json.has("requestId") ? json.get("requestId").getAsString() : null;
        JsonObject responsePayload = json.has("response") ? json.get("response").getAsJsonObject() : new JsonObject();

        if (requestId != null) {
            if (nodeRegistry.getIncomingTestMasterWebSocket() != null && nodeRegistry.getIncomingTestMasterWebSocket().isOpen()) {
                // <--- CHANGED CALL TO USE incomingMasterSender
                incomingMasterSender.forwardResponseToIncomingTestMaster(sender.nodeId, responsePayload, requestId);
            } else if (upstreamMasterClient.isConnected()) {
                upstreamMasterClient.forwardResponse(sender.nodeId, responsePayload, requestId);
            } else {
                System.out.println("⚠️ CommandOrchestrator: No active master connection to forward response for requestId: " + requestId);
            }
            requestIdMap.remove(requestId);
        } else {
            System.out.println("⚠️ CommandOrchestrator: No requestId found in commandResponse from node " + sender.nodeId + ", cannot forward to master.");
        }
    }

    private void sendErrorToMaster(String masterType, String requestId, String errorMessage, String nodeId) {
        if ("UPSTREAM".equals(masterType)) {
            if (upstreamMasterClient.isConnected()) {
                upstreamMasterClient.sendError(requestId, nodeId, errorMessage);
            } else {
                System.out.println("⚠️ CommandOrchestrator: Failed to send error to UPSTREAM Master, not connected.");
            }
        } else if ("INCOMING_TEST".equals(masterType)) {
            if (nodeRegistry.getIncomingTestMasterWebSocket() != null && nodeRegistry.getIncomingTestMasterWebSocket().isOpen()) {
                // <--- CHANGED CALL TO USE incomingMasterSender
                incomingMasterSender.sendErrorToIncomingTestMaster(requestId, nodeId, errorMessage);
            } else {
                System.out.println("⚠️ CommandOrchestrator: Failed to send error to INCOMING Test Master, not connected.");
            }
        } else {
            System.err.println("❌ CommandOrchestrator: Unknown master type for error reporting: " + masterType);
        }
    }
}