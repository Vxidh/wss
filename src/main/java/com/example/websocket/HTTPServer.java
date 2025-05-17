package com.example.websocket;

import static spark.Spark.*;

import java.net.URI;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class HTTPServer {

    private final Server wsServer;

    public HTTPServer(Server wsServer) {
        this.wsServer = wsServer;
    }

    public void start(int port) {
        port(port);

        post("/send/:nodeId", (req, res) -> {
            String nodeId = req.params(":nodeId");

            JsonObject command;
            try {
                command = JsonParser.parseString(req.body()).getAsJsonObject();
            } catch (Exception e) {
                res.status(400);
                return "{\"error\":\"Invalid JSON payload\"}";
            }

            boolean sent = wsServer.sendToNode(nodeId, command);

            if (sent) {
                res.type("application/json");
                return "{\"status\":\"Command sent to node " + nodeId + "\"}";
            } else {
                res.status(404);
                return "{\"error\":\"Node " + nodeId + " not connected or idle\"}";
            }
        });

        get("/health", (req, res) -> "OK");

        exception(Exception.class, (e, req, res) -> {
            res.status(500);
            res.body("{\"error\":\"Internal server error\"}");
            e.printStackTrace();
        });
    }

    public static void main(String[] args) {
        int wsPort = 8080;
        int httpPort = 4567;
        boolean startTestClient = args.length > 0 && args[0].equals("--with-client");

        Server wsServer = new Server(wsPort);
        wsServer.start();
        System.out.println("WebSocket server started on port " + wsPort);

        HTTPServer httpServer = new HTTPServer(wsServer);
        httpServer.start(httpPort);
        System.out.println("HTTP server started on port " + httpPort);

        if (startTestClient) {
            try {
                String nodeId = "test-node-" + System.currentTimeMillis();
                URI serverUri = new URI("ws://localhost:" + wsPort + "/ws?nodeId=" + nodeId);
                Client client = new Client(serverUri);
                client.connectBlocking();
                System.out.println("Test client connected with ID: " + nodeId);
            } catch (Exception e) {
                System.err.println("Failed to start test client: " + e.getMessage());
            }
        }
    }
}
