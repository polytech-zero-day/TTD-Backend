package kr.ac.kopo.ttd.grading;

import org.springframework.stereotype.Component;

/**
 * 효율 점수 v4 — 예산 이내 100점, 초과율 × 기울기(50) 감점, 하한 0점.
 * 예산의 2배 사용 = 50점, 3배 = 0점. 예산은 문제별 값(problems.token_budget)을 쓴다.
 * 예산 이내 구간에 경사를 주지 않는 이유: "예산 안에서 품질을 뽑아내라"가 시험의 메시지이며,
 * 이내 구간을 차등하면 AI를 덜 쓸수록 유리해져 활용 역량 진단의 취지와 어긋난다.
 * TODO: 기울기·가중치는 회의 확정 시 상수만 조정.
 */
@Component
public class EfficiencyScorer {

    private static final double OVER_PENALTY_SLOPE = 50;

    public int score(long totalTokens, long tokenBudget) {
        if (totalTokens <= tokenBudget) return 100;
        double overRatio = (double) (totalTokens - tokenBudget) / tokenBudget;
        return (int) Math.max(0, Math.round(100 - overRatio * OVER_PENALTY_SLOPE));
    }
}
