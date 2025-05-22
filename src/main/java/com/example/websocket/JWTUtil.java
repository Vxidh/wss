package com.example.websocket;

import io.jsonwebtoken.*;
import java.util.Date;

public class JWTUtil {
    public static String generateToken(String subject) {
        Config.ensureInitialized();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(Config.JWT_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    public static Jws<Claims> validateToken(String token) throws JwtException {
        Config.ensureInitialized();
        return Jwts.parserBuilder()
                .setSigningKey(Config.JWT_KEY)
                .build()
                .parseClaimsJws(token);
    }
}
