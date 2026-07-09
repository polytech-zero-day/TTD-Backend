package kr.ac.kopo.ttd.common.exception;

public class AttemptNotInProgressException extends BusinessException {
    public AttemptNotInProgressException() {
        super(ErrorCode.ATTEMPT_NOT_IN_PROGRESS);
    }
}
