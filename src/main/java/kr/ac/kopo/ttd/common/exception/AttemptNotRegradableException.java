package kr.ac.kopo.ttd.common.exception;

public class AttemptNotRegradableException extends BusinessException {

    public AttemptNotRegradableException() {
        super(ErrorCode.ATTEMPT_NOT_REGRADABLE);
    }
}
