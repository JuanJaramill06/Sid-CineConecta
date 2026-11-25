package com.cineconecta.backend.repository.relational;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cineconecta.backend.model.relational.Plan;

public interface PlanRepository extends JpaRepository<Plan, Integer> {

    List<Plan> findAllByOrderByPriceAsc();
}
