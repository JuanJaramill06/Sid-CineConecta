package com.cineconecta.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sizes follow ACCOUNTS: fullName VARCHAR2(150), email VARCHAR2(254).
// BCrypt only uses the first 72 bytes of a password, hence the upper limit.
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {}
