package com.cineconecta.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cineconecta.backend.dto.subscription.PaymentResponse;
import com.cineconecta.backend.model.relational.Payment;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "subscriptionId", source = "subscription.subscriptionId")
    @Mapping(target = "planName", source = "subscription.plan.name")
    PaymentResponse toResponse(Payment payment);

    List<PaymentResponse> toResponses(List<Payment> payments);
}
