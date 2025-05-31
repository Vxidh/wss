// src/main/java/com/example/websocket/UpstreamMasterClient.java
package com.example.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer; // To pass callbacks for handling messages/errors

public class UpstreamMasterClient {
    private WebSocketClient client;
    private String masterServerUrl; // Will be set via constructor
    private volatile boolean connected = false;
    private final ScheduledExecutorService scheduler; // For reconnection attempts
    // This is the *only* callback needed for messages *from* the upstream master.
    private final BiConsumer<String, String> commandReceivedHandler; // Callback for commands from upstream master

    /**
     * Constructor for UpstreamMasterClient.
     * @param scheduler The shared ScheduledExecutorService for reconnection logic.
     * @param initialMasterUrl The URL of the upstream master server.
     * @param commandReceivedHandler A callback to handle messages received *from* the upstream master.
     * (String: masterUrl, String: messageContent).
     */
    public UpstreamMasterClient(ScheduledExecutorService scheduler,
                                String initialMasterUrl,
                                BiConsumer<String, String> commandReceivedHandler) {
        this.scheduler = scheduler;
        this.masterServerUrl = initialMasterUrl;
        this.commandReceivedHandler = commandReceivedHandler;
        System.out.println("UpstreamMasterClient: Initialized for " + initialMasterUrl);
    }

    public void connect() {
        if (masterServerUrl == null || masterServerUrl.trim().isEmpty()) {
            System.err.println("UpstreamMasterClient: Master server URL is not set. Cannot connect.");
            return;
        }
        if (client != null && client.isOpen()) {
            System.out.println("UpstreamMasterClient: Already connected. Disconnecting old client before new connection attempt.");
            client.close(); // Close existing connection before attempting new one
        }
        try {
            URI serverUri = URI.create(masterServerUrl);

            this.client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("✅ UpstreamMasterClient: Connected to UPSTREAM Master Server: " + masterServerUrl);
                    connected = true;

                    // Send registration message to the upstream master
                    JsonObject registration = new JsonObject();
                    registration.addProperty("type", "middleman_register");
                    registration.addProperty("serverId", "middleman-" + System.currentTimeMillis()); // Unique ID for this server
                    send(registration.toString());
                }

                @Override
                public void onMessage(String message) {
                    // Delegate handling of incoming commands from Upstream Master
                    commandReceivedHandler.accept(masterServerUrl, message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("❌ UpstreamMasterClient: Disconnected from UPSTREAM Master Server: " + reason + " Code: " + code);
                    connected = false;

                    // Auto-reconnect after 5 seconds
                    scheduler.schedule(() -> {
                        System.out.println("🔄 UpstreamMasterClient: Attempting to reconnect to UPSTREAM Master Server...");
                        connect(); // Recursively call connect
                    }, 5, TimeUnit.SECONDS);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("❌ UpstreamMasterClient: UPSTREAM Master Server connection error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            };

            client.connect();

        } catch (Exception e) {
            System.err.println("UpstreamMasterClient: Failed to connect to UPSTREAM Master Server at " + masterServerUrl + ": " + e.getMessage());
            e.printStackTrace();
            // Schedule reconnection even on initial connection failure
            scheduler.schedule(() -> {
                System.out.println("🔄 UpstreamMasterClient: Attempting to reconnect to UPSTREAM Master Server (after initial failure)...");
                connect();
            }, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Sends a raw JSON message to the Upstream Master.
     * This method is called by `sendError`, `forwardResponse`, `reportNodeStatus`.
     * @param message The JSON message string to send to the upstream master.
     */
    public void sendToUpstreamMaster(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
            // System.out.println("📤 UpstreamMasterClient: Sent message to UPSTREAM Master."); // Can uncomment for verbose logging
        } else {
            System.out.println("⚠️ UpstreamMasterClient: Not connected to UPSTREAM Master. Message not sent.");
        }
    }

    /**
     * Checks if the client is currently connected to the upstream master.
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Stops the upstream client connection.
     */
    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
            System.out.println("UpstreamMasterClient: Explicitly disconnected.");
        }
    }
    
    // --- Public methods to be called by Server/CommandOrchestrator ---

    /**
     * Sends an error message back to the UPSTREAM Master.
     * @param requestId The request ID.
     * @param nodeId The target nodeId.
     * @param errorMessage The error message.
     */
    public void sendError(String requestId, String nodeId, String errorMessage) {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("type", "node_response");
        errorResponse.addProperty("requestId", requestId);
        errorResponse.addProperty("nodeId", nodeId);

        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("message", errorMessage);
        errorResponse.add("response", response);

        sendToUpstreamMaster(errorResponse.toString());
        System.out.println("📤 UpstreamMasterClient: Sent error to UPSTREAM Master: " + errorMessage);
    }

    /**
     * Forwards a node's response back to the UPSTREAM Master.
     * @param nodeId The nodeId of the responding bot.
     * @param response The JSON response payload from the bot.
     * @param requestId The request ID.
     */
    public void forwardResponse(String nodeId, JsonObject response, String requestId) {
        JsonObject masterResponse = new JsonObject();
        masterResponse.addProperty("type", "node_response");
        masterResponse.addProperty("requestId", requestId);
        masterResponse.addProperty("nodeId", nodeId);
        masterResponse.add("response", response);

        sendToUpstreamMaster(masterResponse.toString());
        System.out.println("📤 UpstreamMasterClient: Forwarded response to UPSTREAM Master Server for requestId: " + requestId);
    }

    /**
     * Reports a node's status (connected/disconnected/idle) to the Upstream Master.
     * @param nodeId The ID of the node.
     * @param status The status string (e.g., "connected", "disconnected", "idle").
     */
    public void reportNodeStatus(String nodeId, String status) {
        JsonObject nodeStatus = new JsonObject();
        nodeStatus.addProperty("type", "node_status");
        nodeStatus.addProperty("nodeId", nodeId);
        nodeStatus.addProperty("status", status);
        sendToUpstreamMaster(nodeStatus.toString());
        System.out.println("📤 UpstreamMasterClient: Reported node status to UPSTREAM Master: " + nodeId + " is " + status);
    }
    /**
     * Sets the master server URL. Used when initializing or re-configuring the client.
     * @param url The URL of the upstream master server.
     */
    public void setMasterServerUrl(String url) {
        this.masterServerUrl = url;
        System.out.println("UpstreamMasterClient: Master server URL set to: " + url);
    }
}