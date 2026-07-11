package kr.ac.kopo.ttd.common.constant;

/**
 * 종합 점수 가중치 단일 원천. 채점 저장(GradingConsumer)·리더보드 집계(LeaderboardService)·
 * 마이페이지 통계 쿼리(AttemptRepository JPQL, SpEL로 참조)가 모두 이 값을 쓴다.
 * 회의로 가중치가 바뀌어도 한 곳만 고치면 세 지점이 동시에 반영된다.
 * TODO: 산식 확정 시 값 조정 (현재 품질 60% + 효율 40%).
 */
public final class ScoreWeights {

    public static final double RUBRIC = 0.6;
    public static final double EFFICIENCY = 0.4;

    private ScoreWeights() {
    }
}
