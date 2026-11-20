package com.cineconecta.backend.model.relational;

import org.hibernate.type.YesNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "PROFILES")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profileId")
    private Integer profileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accountId", nullable = false)
    private Account account;

    // Único por cuenta sin distinguir mayúsculas: índice
    // ux_profiles_name_ci (accountId, LOWER(name)) en la BD.
    @Column(name = "name", nullable = false, length = 60)
    private String name;

    // Clasificación MÁXIMA permitida al perfil (no es su edad)
    @Column(name = "maxAgeRating", nullable = false)
    private Integer maxAgeRating;

    @Convert(converter = YesNoConverter.class)
    @Column(name = "isActive", nullable = false, length = 1)
    private Boolean active = true;

    protected Profile() { }

    public Integer getProfileId() { return profileId; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getMaxAgeRating() { return maxAgeRating; }
    public void setMaxAgeRating(Integer maxAgeRating) { this.maxAgeRating = maxAgeRating; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}