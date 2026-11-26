package com.cineconecta.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.document.AdminUser;
import com.cineconecta.backend.model.relational.Account;

/**
 * The authenticated principal, for both kinds of users. Built from the database at login
 * and from the JWT claims on every other request (then passwordHash is null).
 * In a controller: {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final String id;        // accountId (CLIENT) or Admin_user _id (ADMIN)
    private final String email;
    private final String fullName;
    private final Role role;
    private String passwordHash;

    public AuthenticatedUser(String id, String email, String fullName, Role role, String passwordHash) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public static AuthenticatedUser client(Account account) {
        return new AuthenticatedUser(String.valueOf(account.getAccountId()), account.getEmail(),
                account.getFullName(), Role.CLIENT, account.getPasswordHash());
    }

    public static AuthenticatedUser admin(AdminUser admin) {
        return new AuthenticatedUser(admin.getId(), admin.getEmail(), admin.getFullName(),
                Role.ADMIN, admin.getPasswordHash());
    }

    /** accountId taken from the token. Use it for every ownership check (RNF2). */
    public Integer accountId() {
        if (role != Role.CLIENT) {
            throw new ForbiddenOperationException("Esta operación es solo para cuentas de clientes");
        }
        return Integer.valueOf(id);
    }

    public String id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    // Spring Security clears it after a successful login.
    @Override
    public void eraseCredentials() { passwordHash = null; }
}
