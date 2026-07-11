package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Subscription;
import kr.ac.kopo.ttd.domain.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        SubscriptionStatus status,
        LocalDateTime currentPeriodStart,
        LocalDateTime nextBillingAt,
        LocalDateTime canceledAt,
        boolean cancelAtPeriodEnd,
        LocalDateTime cancelRequestedAt) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getNextBillingAt(),
                subscription.getCanceledAt(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelRequestedAt());
    }
}
