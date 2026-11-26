package com.cineconecta.backend.model.document;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Back-office user, independent of customer accounts (no subscription or profiles). */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "Admin_user")
public class AdminUser {

    @Id
    private String id;

    private String email; // stored lowercase
    private String fullName;
    private String passwordHash; // BCrypt

    @CreatedDate
    private Instant createdAt;
}
