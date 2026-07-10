package kr.ac.kopo.ttd.common.exception;

public class SubscriptionAlreadyActiveException extends BusinessException {

    public SubscriptionAlreadyActiveException() {
        super(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
    }
}
