package com.example.websocket;

import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.nio.charset.StandardCharsets;

public class Config {
    public static String JWT_SECRET;
    public static Key JWT_KEY;
    private static boolean initialized = false;

    /**
     * Initialize the configuration with the given JWT secret.
     * This method must be called before using JWT_KEY.
     *
     * @param jwtSecret the secret key for JWT
     */
    public static synchronized void init(String jwtSecret) {
        if (initialized) return;
        if (jwtSecret == null) {
            System.err.println("JWT_SECRET missing. Exiting.");
            System.exit(1);
        }
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            System.err.println("JWT_SECRET too short (needs 32+ bytes, got " + secretBytes.length + "). Exiting.");
            System.exit(1);
        }
        JWT_SECRET = jwtSecret;
        JWT_KEY = Keys.hmacShaKeyFor(secretBytes);
        initialized = true;
    }

    /**
     * Check if the configuration has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Ensure the configuration has been initialized.
     * Throws IllegalStateException if not initialized.
     */
    public static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Config.init() must be called before using JWT_KEY");
        }
    }
}
