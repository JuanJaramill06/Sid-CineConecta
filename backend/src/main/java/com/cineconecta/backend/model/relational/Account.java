package com.cineconecta.backend.model.relational;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNTS")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accountId")
    private Integer accountId;

    @Column(name = "fullName", nullable = false, length = 150)
    private String fullName;

    // La unicidad sin distinguir mayúsculas la garantiza el índice
    // ux_accounts_email_ci (LOWER(email)) en la BD, no JPA.
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    // Solo el hash (bcrypt/Argon2), nunca la contraseña
    @Column(name = "passwordHash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Account() { }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Integer getAccountId() { return accountId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
