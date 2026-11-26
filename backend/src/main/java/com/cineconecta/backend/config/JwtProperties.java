package com.cineconecta.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * cineconecta.jwt.secret: Base64 HMAC key of at least 32 bytes, from the JWT_SECRET
 * environment variable. If it is empty, JwtServiceImpl generates a random key at
 * startup (fine for development: tokens stop working after a restart).
 */
@ConfigurationProperties("cineconecta.jwt")
public record JwtProperties(String secret, @DefaultValue("PT8H") Duration expiration) {}
