package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.subscription.PlanResponse;

/** List plans. */
public interface PlanService {

    /** The fixed plans, cheapest first (RF13, RF14). Public: needed before registering. */
    List<PlanResponse> list();
}
