package kr.ac.kopo.ttd.common.exception;

public class ProblemNotFoundException extends BusinessException {

    public ProblemNotFoundException() {
        super(ErrorCode.PROBLEM_NOT_FOUND);
    }
}
