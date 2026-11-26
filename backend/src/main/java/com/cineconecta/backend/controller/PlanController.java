package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.subscription.PlanResponse;
import com.cineconecta.backend.service.PlanService;

/** Public: the plans page is shown before logging in. */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<PlanResponse> list() {
        return planService.list();
    }
}
