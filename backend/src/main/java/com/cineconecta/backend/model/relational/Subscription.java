package com.cineconecta.backend.model.relational;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SUBSCRIPTIONS")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscriptionId")
    private Integer subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accountId", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planId", nullable = false)
    private Plan plan;

    // Vigente: startsAt <= ahora < endsAt (incluye inicio, excluye fin)
    @Column(name = "startsAt", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "endsAt", nullable = false)
    private LocalDateTime endsAt;

    protected Subscription() { }

    public boolean isActiveAt(LocalDateTime instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }

    public Integer getSubscriptionId() { return subscriptionId; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
}