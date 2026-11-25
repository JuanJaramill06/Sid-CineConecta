package com.cineconecta.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cineconecta.backend.dto.subscription.PlanResponse;
import com.cineconecta.backend.mapper.PlanMapper;
import com.cineconecta.backend.repository.relational.PlanRepository;
import com.cineconecta.backend.service.PlanService;

@Service
public class PlanServiceImpl implements PlanService {

    private final PlanRepository plans;
    private final PlanMapper mapper;

    public PlanServiceImpl(PlanRepository plans, PlanMapper mapper) {
        this.plans = plans;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return mapper.toResponses(plans.findAllByOrderByPriceAsc());
    }
}
