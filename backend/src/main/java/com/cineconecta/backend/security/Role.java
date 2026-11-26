package com.cineconecta.backend.security;

/** CLIENT = customer account (Oracle ACCOUNTS). ADMIN = back-office user (MongoDB Admin_user). */
public enum Role {
    CLIENT,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
