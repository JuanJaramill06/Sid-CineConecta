package com.cineconecta.backend.model.relational;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PAYMENTS")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paymentId")
    private Integer paymentId;

    // 1 a 1: cada suscripción confirmada tiene exactamente un pago
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriptionId", nullable = false, unique = true)
    private Subscription subscription;

    @Column(name = "paidAt", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference", nullable = false, unique = true, length = 100)
    private String reference;

    protected Payment() { }

    public Integer getPaymentId() { return paymentId; }
    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) { this.subscription = subscription; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}