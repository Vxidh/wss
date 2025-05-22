package com.example.websocket;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

public class MainServer {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        String jwtSecret = dotenv.get("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.length() < 32) {
            System.err.println("JWT_SECRET is missing or too short in .env (must be ≥ 32 characters). Generating a new one...");
            jwtSecret = generateSecureSecret(48);
            updateEnvFileWithSecret(jwtSecret);
            System.out.println("A new JWT_SECRET was generated and written to .env. Please keep it safe!");
        }

        Config.init(jwtSecret);

        int wsPort = 8080;
        int httpPort = 4567;

        Server wsServer = new Server(wsPort);
        wsServer.setNodeSecret("AGENT001", "bd0acd7235c524ba11834ec79dfa5a5738190276c4522f38d96e31f2cd2de522");
        HTTPServer httpServer = new HTTPServer(wsServer);

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
                httpServer.start(httpPort);
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

    private static String generateSecureSecret(int numBytes) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void updateEnvFileWithSecret(String secret) {
        try {
            Path envPath = Paths.get(".env");
            List<String> lines;
            if (Files.exists(envPath)) {
                lines = Files.readAllLines(envPath);
            } else {
                lines = new java.util.ArrayList<>();
            }
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("JWT_SECRET=")) {
                    lines.set(i, "JWT_SECRET=" + secret);
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add("JWT_SECRET=" + secret);
            }
            Files.write(envPath, lines);
        } catch (Exception e) {
            System.err.println("Failed to update .env with new JWT_SECRET: " + e.getMessage());
            System.exit(1);
        }
    }
}
