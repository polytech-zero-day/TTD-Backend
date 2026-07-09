package kr.ac.kopo.ttd.grading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 효율 점수 v3. TODO(2.2 문서): 확정 산식으로 교체 — 아래는 "적정선 이내 100점,
 * 초과분에 비례 감점(최저 0점)"의 임시 산식이다.
 */
@Component
public class EfficiencyScorer {

    private final long tokenBaseline;

    public EfficiencyScorer(@Value("${app.attempt.token-baseline}") long tokenBaseline) {
        this.tokenBaseline = tokenBaseline;
    }

    public int score(long totalTokens) {
        if (totalTokens <= tokenBaseline) return 100;
        double overRatio = (double) (totalTokens - tokenBaseline) / tokenBaseline;
        return (int) Math.max(0, Math.round(100 - overRatio * 100));
    }
}