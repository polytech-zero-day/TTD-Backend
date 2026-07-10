package kr.ac.kopo.ttd.common.exception;

public class SubscriptionNotFoundException extends BusinessException {

    public SubscriptionNotFoundException() {
        super(ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }
}
