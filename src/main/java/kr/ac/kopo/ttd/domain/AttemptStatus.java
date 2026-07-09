package kr.ac.kopo.ttd.domain;

public enum AttemptStatus {
    IN_PROGRESS, GRADING, GRADED;

    public boolean canTransitionTo(AttemptStatus target) {
        return switch (this) {
            case IN_PROGRESS -> target == GRADING;
            case GRADING -> target == GRADED;
            case GRADED -> false;
        };
    }
}