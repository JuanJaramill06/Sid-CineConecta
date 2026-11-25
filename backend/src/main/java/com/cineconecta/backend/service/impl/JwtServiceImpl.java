package com.cineconecta.backend.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cineconecta.backend.config.JwtProperties;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** HS256 tokens. Claims: sub = user id, role, email, name. No password data. */
@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);
    private static final String ISSUER = "cineconecta";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtServiceImpl(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtServiceImpl(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(keyBytes(properties.secret()));
    }

    private static byte[] keyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set: using a random key. Tokens will not survive a restart.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = Base64.getDecoder().decode(secret.trim());
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be Base64 of at least 32 bytes");
        }
        return bytes;
    }

    @Override
    public IssuedToken issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.expiration());
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.id())
                .claim("role", user.role().name())
                .claim("email", user.email())
                .claim("name", user.fullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            if (subject == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(subject, claims.get("email", String.class),
                    claims.get("name", String.class), Role.valueOf(role), null));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
