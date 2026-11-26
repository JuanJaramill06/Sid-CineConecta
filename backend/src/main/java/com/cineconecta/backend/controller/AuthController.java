package com.cineconecta.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.auth.CurrentUserResponse;
import com.cineconecta.backend.dto.auth.LoginRequest;
import com.cineconecta.backend.dto.auth.RegisterRequest;
import com.cineconecta.backend.dto.auth.TokenResponse;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.service.AuthService;

import jakarta.validation.Valid;

/** Customer accounts (Oracle). register and login are public; me needs a CLIENT token. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.email(), user.role().name());
    }
}
