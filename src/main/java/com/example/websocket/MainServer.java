package com.example.websocket;

public class MainServer {
    public static void main(String[] args) {
        int wsPort = 8080;
        int httpPort = 4567;
        String upstreamMasterUrl = "ws://localhost:9999";
        Server wsServer = new Server(wsPort, upstreamMasterUrl);
        HTTPServer httpServer = new HTTPServer(httpPort, wsServer);

        Thread wsThread = new Thread(() -> {
            try {
                wsServer.start();
                System.out.println("WebSocket server started on port " + wsPort);
            } catch (Exception e) {
                System.err.println("Failed to start WebSocket server: " + e.getMessage());
                e.printStackTrace();
            }
        });
        Thread httpThread = new Thread(() -> {
            try {
                httpServer.start();
                System.out.println("HTTP server started on port " + httpPort);
            } catch (Exception e) {
                System.err.println("Failed to start HTTP server: " + e.getMessage());
                e.printStackTrace();
            }
        });

        wsThread.start();
        httpThread.start();

        try {
            wsThread.join();
            httpThread.join();
        } catch (InterruptedException e) {
            System.err.println("Server threads interrupted.");
            e.printStackTrace();
        }
    }
}