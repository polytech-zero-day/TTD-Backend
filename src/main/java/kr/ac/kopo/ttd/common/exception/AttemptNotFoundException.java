package kr.ac.kopo.ttd.common.exception;

public class AttemptNotFoundException extends BusinessException {
    public AttemptNotFoundException() {
        super(ErrorCode.ATTEMPT_NOT_FOUND);
    }
}