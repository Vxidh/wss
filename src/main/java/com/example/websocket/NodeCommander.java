// src/main/java/com/example/websocket/NodeCommander.java
package com.example.websocket;

import com.google.gson.JsonObject;
import com.example.websocket.NodeRegistry.NodeInfo; 
import com.example.websocket.NodeRegistry.NodeStatus; 
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonSyntaxException;
import java.util.function.BiConsumer; 

public class NodeCommander {

    private static final Logger logger = LoggerFactory.getLogger(NodeCommander.class);

    private final NodeRegistry nodeRegistry;

    private final BiConsumer<NodeInfo, JsonObject> nodeResponseHandler;
    public NodeCommander(NodeRegistry nodeRegistry, BiConsumer<NodeInfo, JsonObject> nodeResponseHandler) {
        this.nodeRegistry = nodeRegistry;
        this.nodeResponseHandler = nodeResponseHandler; // Assign the handler
        logger.info("NodeCommander: Initialized.");
    }


    public boolean sendToNode(String nodeId, JsonObject command) {
        NodeInfo info = nodeRegistry.getClientNodeInfo(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "command");
            wrapper.add("command", command);
            info.conn.send(wrapper.toString());
            logger.info("NodeCommander: Sent direct command to node {}: {}", nodeId, command.toString());
            return true;
        }
        logger.warn("NodeCommander: Failed to send command to node {}: Node not found, not open, or idle.", nodeId);
        return false;
    }
    public boolean sendToNodeWithRequestId(String nodeId, JsonObject command, String requestId) {
        NodeInfo info = nodeRegistry.getClientNodeInfo(nodeId);
        if (info != null && info.conn != null && info.conn.isOpen() && info.status == NodeStatus.ACTIVE) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "command");
            wrapper.addProperty("requestId", requestId);
            wrapper.add("command", command);

            info.conn.send(wrapper.toString());
            logger.info("NodeCommander: Forwarded command to node {} with requestId: {}", nodeId, requestId);
            return true;
        }
        logger.warn("NodeCommander: Failed to forward command to node {}: Node not found, not open, or idle.", nodeId);
        return false;
    }

    public void handleIncomingNodeMessage(NodeInfo sender, String message) {
        try {
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            String type = jsonMessage.has("type") ? jsonMessage.get("type").getAsString() : null;

            if ("node_response".equals(type)) {
                // Delegate the handling of the node response to the provided handler
                if (nodeResponseHandler != null) {
                    nodeResponseHandler.accept(sender, jsonMessage);
                } else {
                    logger.warn("NodeCommander: nodeResponseHandler is null. Cannot process node response for node {}: {}", sender.nodeId, message);
                }
            } else {
                logger.warn("NodeCommander: Received unrecognized message type '{}' from node {}: {}", type, sender.nodeId, message);
            }
        } catch (JsonSyntaxException e) {
            logger.warn("NodeCommander: Invalid JSON received from node {}: {}", sender.nodeId, message, e);
        } catch (Exception e) {
            logger.error("NodeCommander: Error processing message from node {}: {}", sender.nodeId, e.getMessage(), e);
        }
    }
}