package kr.ac.kopo.ttd.domain;

public enum ProblemType {
    CLASSIFY,
    CONSTRAINT,
    ANALYSIS_BASIC,
    ANALYSIS_ADV,
    AMBIGUOUS,
    REPORT,
    SKELETON_STAT,
    SKELETON_HTTP,
    SKELETON_LOG,
    SKELETON_RESERVE;

    /**
     * 스켈레톤 개선형 문제(7~10)는 {@code skeleton_code}가 필수이며, 그 외 유형은 null이어야 한다.
     */
    public boolean isSkeleton() {
        return name().startsWith("SKELETON_");
    }
}
