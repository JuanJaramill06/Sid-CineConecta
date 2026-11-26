package com.cineconecta.backend.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.cineconecta.backend.repository.mongo.AdminUserRepository;

/** Administrator login against Admin_user (MongoDB), independent of customer accounts (RF64). */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository admins;

    public AdminUserDetailsService(AdminUserRepository admins) {
        this.admins = admins;
    }

    // Admin emails are stored lowercase (the validator rejects uppercase).
    @Override
    public AuthenticatedUser loadUserByUsername(String email) {
        return admins.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(AuthenticatedUser::admin)
                .orElseThrow(() -> new UsernameNotFoundException("Administrador no encontrado"));
    }
}
