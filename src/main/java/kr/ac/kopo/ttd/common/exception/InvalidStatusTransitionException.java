package kr.ac.kopo.ttd.common.exception;

public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException() {
        super(ErrorCode.INVALID_STATUS_TRANSITION);
    }
}
