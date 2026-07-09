package kr.ac.kopo.ttd.grading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EfficiencyScorerTest {

    private final EfficiencyScorer scorer = new EfficiencyScorer(3000L);

    @Test
    void 적정선_이내는_만점() {
        assertThat(scorer.score(1500L)).isEqualTo(100);
    }

    @Test
    void 적정선과_정확히_같으면_만점() {
        assertThat(scorer.score(3000L)).isEqualTo(100);
    }

    @Test
    void 초과분은_비례_감점된다() {
        // 1.5배(4500) → 초과율 50% → 50점
        assertThat(scorer.score(4500L)).isEqualTo(50);
    }

    @Test
    void 두배_이상_초과하면_0점_하한() {
        assertThat(scorer.score(6000L)).isZero();
        assertThat(scorer.score(100_000L)).isZero();
    }
}
