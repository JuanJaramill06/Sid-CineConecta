package com.cineconecta.backend.service;

import java.time.Instant;
import java.util.Optional;

import com.cineconecta.backend.security.AuthenticatedUser;

/** Token generation and validation. */
public interface JwtService {

    IssuedToken issue(AuthenticatedUser user);

    /** The user in a valid token; empty if the token is malformed, tampered with or expired. */
    Optional<AuthenticatedUser> parse(String token);

    record IssuedToken(String token, Instant expiresAt) {}
}
