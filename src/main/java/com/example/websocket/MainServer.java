package com.example.websocket;

public class MainServer {
    public static void main(String[] args) {
        int wsPort = 8080;
        int httpPort = 4567;

        Server wsServer = new Server(wsPort);
        HTTPServer httpServer = new HTTPServer(wsServer);

        Thread wsThread = new Thread(() -> {
            wsServer.start();
            System.out.println("WebSocket server started on port " + wsPort);
        });

        Thread httpThread = new Thread(() -> {
            httpServer.start(httpPort);
            System.out.println("HTTP server started on port " + httpPort);
        });

        wsThread.start();
        httpThread.start();

        try {
            wsThread.join();
            httpThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
