package com.cineconecta.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cineconecta.backend.dto.subscription.SubscriptionResponse;
import com.cineconecta.backend.model.relational.Subscription;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "planId", source = "plan.planId")
    @Mapping(target = "planName", source = "plan.name")
    SubscriptionResponse toResponse(Subscription subscription);

    List<SubscriptionResponse> toResponses(List<Subscription> subscriptions);
}
