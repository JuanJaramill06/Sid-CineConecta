package com.cineconecta.backend.service.impl;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.dto.auth.LoginRequest;
import com.cineconecta.backend.dto.auth.RegisterRequest;
import com.cineconecta.backend.dto.auth.TokenResponse;
import com.cineconecta.backend.exception.ConflictException;
import com.cineconecta.backend.model.relational.Account;
import com.cineconecta.backend.repository.relational.AccountRepository;
import com.cineconecta.backend.security.AccountUserDetailsService;
import com.cineconecta.backend.security.AdminUserDetailsService;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.service.AuthService;
import com.cineconecta.backend.service.JwtService;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String DUPLICATE_EMAIL = "Ya existe una cuenta con ese correo";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // One authentication manager per user store: customers and admins never mix (RNF4).
    private final AuthenticationManager clientAuthentication;
    private final AuthenticationManager adminAuthentication;

    public AuthServiceImpl(AccountRepository accounts, PasswordEncoder passwordEncoder,
                           JwtService jwtService, AccountUserDetailsService accountUsers,
                           AdminUserDetailsService adminUsers) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clientAuthentication = manager(accountUsers, passwordEncoder);
        this.adminAuthentication = manager(adminUsers, passwordEncoder);
    }

    private static AuthenticationManager manager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByEmailCi(email)) {
            throw new ConflictException(DUPLICATE_EMAIL);
        }
        Account account = new Account(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()));
        try {
            // Flush now: a concurrent registration with the same email fails here,
            // on ux_accounts_email_ci, and not later at commit.
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_EMAIL);
        }
        AuthenticatedUser user = AuthenticatedUser.client(account);
        user.eraseCredentials();
        return token(user);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        return authenticate(clientAuthentication, request);
    }

    @Override
    public TokenResponse adminLogin(LoginRequest request) {
        return authenticate(adminAuthentication, request);
    }

    // Wrong credentials throw BadCredentialsException (401). The message never says
    // whether the email exists.
    private TokenResponse authenticate(AuthenticationManager manager, LoginRequest request) {
        var authentication = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                request.email().trim(), request.password()));
        return token((AuthenticatedUser) authentication.getPrincipal());
    }

    private TokenResponse token(AuthenticatedUser user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new TokenResponse(issued.token(), issued.expiresAt(), user.role().name(), user.fullName());
    }
}
