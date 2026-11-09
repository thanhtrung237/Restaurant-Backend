package com.group.restaurant.configurations.jwt;

import com.group.restaurant.enums.TokenType;
import com.group.restaurant.models.Account;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${spring.app.jwtAccessSecret}")
    private String jwtAccessSecret;

    @Value("${spring.app.jwtRefreshSecret}")
    private String jwtRefreshSecret;

    @Value("${spring.app.jwtAccessExpirationMs}")
    private long jwtAccessExpirationMs;

    @Value("${spring.app.jwtRefreshExpirationDays}")
    private int jwtRefreshExpirationDays;

    public String generateAccessToken(Account account) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", account.getId());
        if (account.getRestaurant() != null) {
            claims.put("restaurantId", account.getRestaurant().getId());
        }
        return buildToken(claims, account.getUsername(), jwtAccessExpirationMs, getSignKey(TokenType.ACCESS_TOKEN));
    }

    public String generateRefreshToken(Account account) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", account.getId());
        if (account.getRestaurant() != null) {
            claims.put("restaurantId", account.getRestaurant().getId());
        }
        return buildToken(claims, account.getUsername(), (long) jwtRefreshExpirationDays * 24 * 60 * 60 * 1000,
                getSignKey(TokenType.REFRESH_TOKEN));
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs, Key key) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token, TokenType type) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey(type))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public Long getAccountIdFromToken(String token, TokenType type) {
        Object id = Jwts.parserBuilder()
                .setSigningKey(getSignKey(type))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("accountId");
        return id != null ? ((Number) id).longValue() : null;
    }

    public boolean validateToken(String token, TokenType type) {
        try {
            Jwts.parserBuilder().setSigningKey(getSignKey(type)).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw e;
        }
    }

    public LocalDate getExpirationDate(String token, TokenType type) {
        Date exp = Jwts.parserBuilder()
                .setSigningKey(getSignKey(type))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        return exp.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Key getSignKey(TokenType type) {
        String secret = type == TokenType.ACCESS_TOKEN ? jwtAccessSecret : jwtRefreshSecret;
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
