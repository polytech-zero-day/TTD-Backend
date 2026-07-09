package kr.ac.kopo.ttd.common.exception;

public class AttemptMessageLimitExceededException extends BusinessException {
    public AttemptMessageLimitExceededException() {
        super(ErrorCode.ATTEMPT_MESSAGE_LIMIT_EXCEEDED);
    }
}
