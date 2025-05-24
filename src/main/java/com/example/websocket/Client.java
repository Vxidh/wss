package com.example.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.awt.event.InputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Client extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private Robot robot;
    public Client(URI serverUri) throws AWTException {
        super(serverUri);
        this.robot = new Robot();
    }
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        MDC.put("correlationId", java.util.UUID.randomUUID().toString());
        logger.info("Connected to server");
        sendPing();
    }
    @Override
    public void onMessage(String message) {
        logger.info("Received: {}", message);
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");
            switch (type) {
                case "mouseMove":
                    int x = json.getInt("x");
                    int y = json.getInt("y");
                    robot.mouseMove(x, y);
                    break;
                case "click":
                    int button = json.optInt("button", 1);
                    int awtButton = InputEvent.BUTTON1_DOWN_MASK;
                    if (button == 2) awtButton = InputEvent.BUTTON2_DOWN_MASK;
                    else if (button == 3) awtButton = InputEvent.BUTTON3_DOWN_MASK;
                    robot.mousePress(awtButton);
                    robot.mouseRelease(awtButton);
                    break;
                case "type":
                    String text = json.getString("text");
                    for (char c : text.toCharArray()) {
                        typeChar(c);
                    }
                    break;
                case "ping":
                    send("{\"type\":\"pong\"}");
                    break;
                default:
                    logger.warn("Unknown command type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Failed to process message", e);
        }
    }
    private void typeChar(char c) {
        try {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                logger.warn("Cannot type character: {}", c);
                return;
            }
            boolean shiftNeeded = Character.isUpperCase(c) || isSpecialShiftChar(c);
            if (shiftNeeded) robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            if (shiftNeeded) robot.keyRelease(KeyEvent.VK_SHIFT);
        } catch (Exception e) {
            logger.error("Could not type character: {}", c, e);
        }
    }
    private boolean isSpecialShiftChar(char c) {
        String shiftChars = "~!@#$%^&*()_+{}|:\\\\\\\"<>?";
        return shiftChars.indexOf(c) >= 0;
    }
    public void sendPing() {
        send("{\"type\":\"ping\"}");
    }
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Connection closed: {}", reason);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                logger.info("Attempting to reconnect...");
                reconnect();
            } catch (Exception e) {
                logger.error("Failed to reconnect", e);
            }
        }).start();
    }
    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error", ex);
    }
    public static void main(String[] args) throws Exception {
        String nodeId = args.length > 0 ? args[0] : "node-" + System.currentTimeMillis();
        String host = args.length > 1 ? args[1] : "localhost:8080";
        URI serverUri = new URI("ws://" + host + "/ws?nodeId=" + nodeId);
        MDC.put("correlationId", nodeId);
        Client client = new Client(serverUri);
        client.connectBlocking();
        while (client.isOpen()) {
            Thread.sleep(60000);
            client.sendPing();
        }
    }
}
