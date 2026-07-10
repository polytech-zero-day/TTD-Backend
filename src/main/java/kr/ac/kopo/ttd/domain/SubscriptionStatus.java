package kr.ac.kopo.ttd.domain;

public enum SubscriptionStatus {
    ACTIVE, PAST_DUE, CANCELED, EXPIRED;

    public boolean canTransitionTo(SubscriptionStatus target) {
        return switch (this) {
            case ACTIVE -> target == PAST_DUE || target == CANCELED;
            case PAST_DUE -> target == ACTIVE || target == EXPIRED || target == CANCELED;
            case CANCELED -> false;
            case EXPIRED -> false;
        };
    }
}
