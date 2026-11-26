package com.cineconecta.backend.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.repository.relational.AccountRepository;

/** Customer login against ACCOUNTS (Oracle). The email is compared without letter case. */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    public AccountUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String email) {
        return accounts.findByEmailCi(email.trim())
                .map(AuthenticatedUser::client)
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no encontrada"));
    }
}
