package com.cineconecta.backend.dto.auth;

import java.time.Instant;

/** Returned by both logins. role is CLIENT or ADMIN; send the token as "Authorization: Bearer ...". */
public record TokenResponse(String token, Instant expiresAt, String role, String fullName) {}
