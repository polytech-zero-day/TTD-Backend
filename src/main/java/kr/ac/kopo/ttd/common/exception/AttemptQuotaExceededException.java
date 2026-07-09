package kr.ac.kopo.ttd.common.exception;

public class AttemptQuotaExceededException extends BusinessException {
    public AttemptQuotaExceededException() {
        super(ErrorCode.ATTEMPT_QUOTA_EXCEEDED);
    }
}
