package com.example.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstreamMasterClient {

    private static final Logger logger = LoggerFactory.getLogger(UpstreamMasterClient.class);

    private WebSocketClient client;
    private String masterServerUrl;
    private volatile boolean connected = false;
    private final ScheduledExecutorService scheduler;
    private final BiConsumer<String, String> commandReceivedHandler;

    public UpstreamMasterClient(ScheduledExecutorService scheduler, String initialMasterUrl, BiConsumer<String, String> commandReceivedHandler) {
        this.scheduler = scheduler;
        this.masterServerUrl = initialMasterUrl;
        this.commandReceivedHandler = commandReceivedHandler;
        logger.info("UpstreamMasterClient: Initialized for {}", initialMasterUrl);
    }

    public void connect() {
        logger.info("UpstreamMasterClient: Attempting to establish connection. Current client state: isOpen={}", (client != null ? client.isOpen() : "N/A"));

        if (masterServerUrl == null || masterServerUrl.trim().isEmpty()) {
            logger.error("UpstreamMasterClient: Master server URL is not set. Cannot connect.");
            return;
        }

        // Handle existing client state: always close previous connection cleanly
        if (client != null) {
            if (client.isOpen()) {
                logger.info("UpstreamMasterClient: Existing client is open. Closing it before new connection attempt.");
                try {
                    client.closeBlocking(); // Use closeBlocking for immediate cleanup
                    logger.info("UpstreamMasterClient: Old client closed successfully.");
                } catch (InterruptedException e) {
                    logger.warn("UpstreamMasterClient: Interrupted while closing old client.", e);
                    Thread.currentThread().interrupt(); // Restore interrupt status
                }
            } else {
                // If client is not open (e.g., failed, closed, never connected), ensure it's fully disposed if necessary.
                // The library should handle internal state, but explicit closeBlocking can't hurt if not open.
                logger.info("UpstreamMasterClient: Existing client is not open. Ensuring it's fully disposed for a clean start.");
                try {
                    client.closeBlocking();
                } catch (InterruptedException e) {
                    logger.warn("UpstreamMasterClient: Interrupted while ensuring old client is disposed.", e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        try {
            URI serverUri = new URI(masterServerUrl);

            this.client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    logger.info("✅ UpstreamMasterClient: Connected to UPSTREAM Master Server: {}", masterServerUrl);
                    connected = true;
                    JsonObject registration = new JsonObject();
                    registration.addProperty("type", "middleman_register");
                    registration.addProperty("serverId", "middleman-" + System.currentTimeMillis());
                    send(registration.toString());
                }

                @Override
                public void onMessage(String message) {
                    logger.info("UpstreamMasterClient: Received message from upstream master: {}", message);
                    commandReceivedHandler.accept(masterServerUrl, message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.warn("❌ UpstreamMasterClient: Disconnected from UPSTREAM Master Server: {} (Code: {})", reason, code);
                    connected = false;
                    scheduleReconnect("connection closed", 10);
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("❌ UpstreamMasterClient: UPSTREAM Master Server connection error: {}", ex.getMessage(), ex);
                    connected = false;
                    scheduleReconnect("connection error", 5);
                }
            };

            logger.info("UpstreamMasterClient: Calling client.connect() for a new attempt to {}.", masterServerUrl);
            client.connect();
            logger.info("UpstreamMasterClient: client.connect() method returned. Waiting for onOpen/onError/onClose callback.");

        } catch (URISyntaxException e) {
            logger.error("UpstreamMasterClient: Invalid master server URL: {} - {}", masterServerUrl, e.getMessage(), e);
            connected = false;
            scheduleReconnect("invalid URL", 5);
        } catch (Exception e) {
            logger.error("UpstreamMasterClient: Unexpected error during connection setup to {}: {}", masterServerUrl, e.getMessage(), e);
            connected = false;
            scheduleReconnect("initial connection setup error", 5);
        }
    }

    private void scheduleReconnect(String reason, int delaySeconds) {
        if (scheduler.isShutdown()) {
            logger.warn("UpstreamMasterClient: Scheduler is shut down, cannot schedule reconnect (Reason: {}).", reason);
            return;
        }
        logger.info("🔄 UpstreamMasterClient: Scheduling reconnect in {} seconds (Reason: {})...", delaySeconds, reason);
        scheduler.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        if (client != null) {
            try {
                client.closeBlocking();
                logger.info("UpstreamMasterClient: Explicitly disconnected.");
            } catch (InterruptedException e) {
                logger.warn("UpstreamMasterClient: Interrupted while explicitly disconnecting.", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void sendToUpstreamMaster(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
        } else {
            logger.warn("⚠️ UpstreamMasterClient: Not connected to UPSTREAM Master. Message not sent.");
        }
    }

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
        logger.info("📤 UpstreamMasterClient: Sent error to UPSTREAM Master: {}", errorMessage);
    }

    public void forwardResponse(String nodeId, JsonObject response, String requestId) {
        JsonObject masterResponse = new JsonObject();
        masterResponse.addProperty("type", "node_response");
        masterResponse.addProperty("requestId", requestId);
        masterResponse.addProperty("nodeId", nodeId);
        masterResponse.add("response", response);
        sendToUpstreamMaster(masterResponse.toString());
        logger.info("📤 UpstreamMasterClient: Forwarded response to UPSTREAM Master Server for requestId: {}", requestId);
    }

    public void reportNodeStatus(String nodeId, String status) {
        JsonObject nodeStatus = new JsonObject();
        nodeStatus.addProperty("type", "node_status");
        nodeStatus.addProperty("nodeId", nodeId);
        nodeStatus.addProperty("status", status);
        sendToUpstreamMaster(nodeStatus.toString());
        logger.info("📤 UpstreamMasterClient: Reported node status to UPSTREAM Master: {} is {}", nodeId, status);
    }

    public void setMasterServerUrl(String url) {
        this.masterServerUrl = url;
        logger.info("UpstreamMasterClient: Master server URL set to: {}", url);
    }
}