package com.cineconecta.backend.model.relational;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "PLANS")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planId")
    private Integer planId;

    // Único en BD (uq_plans_name)
    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "maxProfiles", nullable = false)
    private Integer maxProfiles;

    protected Plan() { }

    public Integer getPlanId() { return planId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getMaxProfiles() { return maxProfiles; }
    public void setMaxProfiles(Integer maxProfiles) { this.maxProfiles = maxProfiles; }
}