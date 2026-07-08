package kr.ac.kopo.ttd.common.exception;

public class SkeletonCodeMismatchException extends BusinessException {

    public SkeletonCodeMismatchException() {
        super(ErrorCode.INVALID_SKELETON_CODE);
    }
}
