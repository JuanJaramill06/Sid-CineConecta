package com.cineconecta.backend.dto.auth;

/** Who the token belongs to. id is the accountId (CLIENT) or the Admin_user _id (ADMIN). */
public record CurrentUserResponse(String id, String email, String role) {}
