package kr.ac.kopo.ttd.grading;

import kr.ac.kopo.ttd.domain.CalibrationSample;
import kr.ac.kopo.ttd.domain.CalibrationTier;
import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.SourceType;
import kr.ac.kopo.ttd.dto.CalibrationRunResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import kr.ac.kopo.ttd.repository.CalibrationSampleRepository;

@ExtendWith(MockitoExtension.class)
class CalibrationRunnerTest {

    @Mock
    private CalibrationSampleRepository calibrationSampleRepository;

    @Mock
    private RubricGrader rubricGrader;

    @InjectMocks
    private CalibrationRunner calibrationRunner;

    private Problem problem() {
        return Problem.builder()
                .title("고객 문의 라우팅 판정")
                .difficulty(Difficulty.L1)
                .type(ProblemType.CLASSIFY)
                .sourceType(SourceType.RUBRIC_ONLY)
                .description("설명")
                .requirements(List.of("요구사항"))
                .constraints(List.of("제약"))
                .build();
    }

    private CalibrationSample sample(CalibrationTier tier, int referenceScore, String answer) {
        return CalibrationSample.builder()
                .problem(problem())
                .tier(tier)
                .referenceScore(referenceScore)
                .sampleAnswer(answer)
                .build();
    }

    @Test
    void 점수를_밴드_기준으로_티어에_매핑한다() {
        assertThat(CalibrationRunner.toTier(70, 70, 40)).isEqualTo(CalibrationTier.HIGH);
        assertThat(CalibrationRunner.toTier(69, 70, 40)).isEqualTo(CalibrationTier.MID);
        assertThat(CalibrationRunner.toTier(40, 70, 40)).isEqualTo(CalibrationTier.MID);
        assertThat(CalibrationRunner.toTier(39, 70, 40)).isEqualTo(CalibrationTier.LOW);
    }

    @Test
    void 티어_일치율과_점수_오차를_집계한다() {
        given(calibrationSampleRepository.findAll()).willReturn(List.of(
                sample(CalibrationTier.HIGH, 95, "상급 답안"),
                sample(CalibrationTier.MID, 68, "중급 답안"),
                sample(CalibrationTier.LOW, 30, "하급 답안")));
        given(rubricGrader.grade(any(), contains("상급"), any()))
                .willReturn(new RubricGrader.RubricResult(90, "좋음", java.util.List.of()));   // HIGH 일치, 오차 5
        given(rubricGrader.grade(any(), contains("중급"), any()))
                .willReturn(new RubricGrader.RubricResult(75, "무난", java.util.List.of()));   // HIGH 판정 — MID 불일치, 오차 7
        given(rubricGrader.grade(any(), contains("하급"), any()))
                .willReturn(new RubricGrader.RubricResult(25, "부족", java.util.List.of()));   // LOW 일치, 오차 5

        CalibrationRunResponse response = calibrationRunner.run(70, 40, 15);

        assertThat(response.summary().total()).isEqualTo(3);
        assertThat(response.summary().tierMatches()).isEqualTo(2);
        assertThat(response.summary().tierAgreementRate()).isEqualTo(2.0 / 3);
        assertThat(response.summary().validTierAgreementRate()).isEqualTo(2.0 / 3); // placeholder 없음
        assertThat(response.summary().avgAbsScoreDiff()).isCloseTo((5 + 7 + 5) / 3.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.summary().withinToleranceRate()).isEqualTo(1.0); // 전부 오차 15 이내
    }

    @Test
    void 임시_답안은_placeholder로_표시하고_유효_일치율에서_제외한다() {
        given(calibrationSampleRepository.findAll()).willReturn(List.of(
                sample(CalibrationTier.HIGH, 95, "[임시] 정답 100% 일치 요약"),
                sample(CalibrationTier.LOW, 30, "실제 하급 답안")));
        given(rubricGrader.grade(any(), anyString(), any()))
                .willReturn(new RubricGrader.RubricResult(20, "부족", java.util.List.of()));

        CalibrationRunResponse response = calibrationRunner.run(70, 40, 15);

        assertThat(response.summary().placeholderCount()).isEqualTo(1);
        assertThat(response.rows().get(0).placeholder()).isTrue();
        // 유효 일치율은 실제 답안 1건 기준: LOW 판정(20점) == LOW 기대 → 1.0
        assertThat(response.summary().validTierAgreementRate()).isEqualTo(1.0);
        // 전체 일치율은 placeholder 포함 2건 중 1건 → 0.5
        assertThat(response.summary().tierAgreementRate()).isEqualTo(0.5);
    }

    @Test
    void 한_샘플의_채점_실패가_전체_측정을_중단시키지_않는다() {
        given(calibrationSampleRepository.findAll()).willReturn(List.of(
                sample(CalibrationTier.HIGH, 95, "실패할 답안"),
                sample(CalibrationTier.LOW, 30, "정상 답안")));
        given(rubricGrader.grade(any(), contains("실패할"), any()))
                .willThrow(new IllegalStateException("파싱 실패"));
        given(rubricGrader.grade(any(), contains("정상"), any()))
                .willReturn(new RubricGrader.RubricResult(25, "부족", java.util.List.of()));

        CalibrationRunResponse response = calibrationRunner.run(70, 40, 15);

        assertThat(response.summary().errorCount()).isEqualTo(1);
        assertThat(response.rows().get(0).error()).isNotNull();
        assertThat(response.rows().get(1).tierMatch()).isTrue();
        assertThat(response.summary().tierAgreementRate()).isEqualTo(1.0); // 에러 제외 1건 중 1건
    }
}
