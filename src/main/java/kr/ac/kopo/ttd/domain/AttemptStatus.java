package kr.ac.kopo.ttd.domain;

public enum AttemptStatus {
    IN_PROGRESS, GRADING, GRADING_FAILED, GRADED, ABANDONED;

    public boolean canTransitionTo(AttemptStatus target) {
        return switch (this) {
            case IN_PROGRESS -> target == GRADING || target == ABANDONED;
            case GRADING -> target == GRADED || target == GRADING_FAILED;
            case GRADING_FAILED -> target == GRADING; // 재채점
            case GRADED, ABANDONED -> false;
        };
    }
}
