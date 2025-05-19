package com.example.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.awt.event.InputEvent;

public class Client extends WebSocketClient {
    private Robot robot;
    
    public Client(URI serverUri) throws AWTException {
        super(serverUri);
        this.robot = new Robot(); // for controlling mouse & keyboard
    }
    
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Connected to server");
        // Send initial presence message
        sendPing();
    }
    
    @Override
    public void onMessage(String message) {
        System.out.println("Received: " + message);
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
                    int button = json.optInt("button", 1); // 1 = left, 2 = middle, 3 = right
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
                    System.out.println("Unknown command type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
        }
    }
    
    private void typeChar(char c) {
        try {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                System.err.println("Cannot type character: " + c);
                return;
            }
            boolean shiftNeeded = Character.isUpperCase(c) || isSpecialShiftChar(c);
            if (shiftNeeded) robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            if (shiftNeeded) robot.keyRelease(KeyEvent.VK_SHIFT);
        } catch (Exception e) {
            System.err.println("Could not type character: " + c);
        }
    }

    private boolean isSpecialShiftChar(char c) {
        String shiftChars = "~!@#$%^&*()_+{}|:\\\\\\\\\\\\\\\"<>?";
        return shiftChars.indexOf(c) >= 0;
    }
    
    public void sendPing() {
        send("{\"type\":\"ping\"}");
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + reason);
        // Try to reconnect after a delay
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                System.out.println("Attempting to reconnect...");
                reconnect();
            } catch (Exception e) {
                System.err.println("Failed to reconnect: " + e.getMessage());
            }
        }).start();
    }
    
    @Override
    public void onError(Exception ex) {
        System.err.println("WebSocket error:");
        ex.printStackTrace();
    }
    
    public static void main(String[] args) throws Exception {
        String nodeId = args.length > 0 ? args[0] : "node-" + System.currentTimeMillis();
        String host = args.length > 1 ? args[1] : "localhost:8080";
        URI serverUri = new URI("ws://" + host + "/ws?nodeId=" + nodeId);
        Client client = new Client(serverUri);
        client.connectBlocking();
        
        // Keep the client alive
        while (client.isOpen()) {
            Thread.sleep(60000);
            client.sendPing();
        }
    }
}