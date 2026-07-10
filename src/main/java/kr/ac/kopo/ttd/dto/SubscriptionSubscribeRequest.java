package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionSubscribeRequest(@NotBlank String billingKey) {}
