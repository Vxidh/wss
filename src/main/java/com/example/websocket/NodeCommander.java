// src/main/java/com/example/websocket/NodeCommander.java
package com.example.websocket;

import com.google.gson.JsonObject;
import com.example.websocket.NodeRegistry.NodeInfo; // Import NodeInfo from NodeRegistry
import com.example.websocket.NodeRegistry.NodeStatus; // Import NodeStatus from NodeRegistry

public class NodeCommander {

    private final NodeRegistry nodeRegistry;

    public NodeCommander(NodeRegistry nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
        System.out.println("NodeCommander: Initialized.");
    }

    /**
     * Sends a direct command to a specific client node without an associated requestId.
     * This is useful for one-way commands or API-triggered actions.
     *
     * @param nodeId The ID of the target node.
     * @param command The JSON command payload to send.
     * @return true if the command was successfully sent, false otherwise (node not found, not active, etc.).
     */
    public boolean sendToNode(String nodeId, JsonObject command) {
        NodeInfo info = nodeRegistry.getClientNodeInfo(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "command");
            wrapper.add("command", command);
            info.conn.send(wrapper.toString());
            System.out.println("📤 NodeCommander: Sent direct command to node " + nodeId + ": " + command.toString());
            return true;
        }
        System.out.println("❌ NodeCommander: Failed to send command to node " + nodeId + ": Node not found, not open, or idle.");
        return false;
    }

    /**
     * Sends a command to a specific client node, including a requestId for tracking responses.
     * This is used by the CommandOrchestrator to forward master commands.
     *
     * @param nodeId The ID of the target node.
     * @param command The JSON command payload to send.
     * @param requestId The requestId associated with the original command from the master.
     * @return true if the command was successfully sent, false otherwise.
     */
    public boolean sendToNodeWithRequestId(String nodeId, JsonObject command, String requestId) {
        NodeInfo info = nodeRegistry.getClientNodeInfo(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "command");
            wrapper.addProperty("requestId", requestId);
            wrapper.add("command", command);

            info.conn.send(wrapper.toString());
            System.out.println("📤 NodeCommander: Forwarded command to node " + nodeId + " with requestId: " + requestId);
            return true;
        }
        return false;
    }
}