package com.example.websocket;

public class GenerateJWT {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -cp target/websocket-bot-server-1.0-SNAPSHOT-jar-with-dependencies.jar com.example.websocket.GenerateJWT <JWT_SECRET> <nodeId>");
            System.exit(1);
        }
        String jwtSecret = args[0];
        String nodeId = args[1];

        Config.init(jwtSecret);
        String token = JWTUtil.generateToken(nodeId);
        System.out.println("JWT for nodeId '" + nodeId + "':");
        System.out.println(token);
    }
}