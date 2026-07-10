package kr.ac.kopo.ttd.grading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EfficiencyScorerTest {

    private static final long BUDGET = 3000L;

    private final EfficiencyScorer scorer = new EfficiencyScorer();

    @Test
    void 예산_이내는_만점() {
        assertThat(scorer.score(1500L, BUDGET)).isEqualTo(100);
    }

    @Test
    void 예산과_정확히_같으면_만점() {
        assertThat(scorer.score(3000L, BUDGET)).isEqualTo(100);
    }

    @Test
    void 초과분은_초과율의_절반_기울기로_감점된다() {
        // 1.5배(4500) → 초과율 50% → 100 - 25 = 75점
        assertThat(scorer.score(4500L, BUDGET)).isEqualTo(75);
        // 2배(6000) → 초과율 100% → 50점
        assertThat(scorer.score(6000L, BUDGET)).isEqualTo(50);
    }

    @Test
    void 세배_이상_초과하면_0점_하한() {
        assertThat(scorer.score(9000L, BUDGET)).isZero();
        assertThat(scorer.score(100_000L, BUDGET)).isZero();
    }

    @Test
    void 예산은_문제별_값을_따른다() {
        // 같은 사용량이라도 예산이 크면 감점되지 않는다
        assertThat(scorer.score(4500L, 5000L)).isEqualTo(100);
        // 예산 1000 기준 2배 사용 → 50점
        assertThat(scorer.score(2000L, 1000L)).isEqualTo(50);
    }
}
