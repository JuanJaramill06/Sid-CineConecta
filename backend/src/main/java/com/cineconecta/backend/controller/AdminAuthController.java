package com.cineconecta.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.auth.CurrentUserResponse;
import com.cineconecta.backend.dto.auth.LoginRequest;
import com.cineconecta.backend.dto.auth.TokenResponse;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.service.AuthService;

import jakarta.validation.Valid;

/** Administrators (MongoDB Admin_user). login is public; me needs an ADMIN token. */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.adminLogin(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.email(), user.role().name());
    }
}
