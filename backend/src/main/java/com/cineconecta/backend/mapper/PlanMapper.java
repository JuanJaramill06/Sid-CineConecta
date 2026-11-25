package com.cineconecta.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.cineconecta.backend.dto.subscription.PlanResponse;
import com.cineconecta.backend.model.relational.Plan;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    PlanResponse toResponse(Plan plan);

    List<PlanResponse> toResponses(List<Plan> plans);
}
