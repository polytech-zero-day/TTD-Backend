package kr.ac.kopo.ttd.grading;

import kr.ac.kopo.ttd.domain.CalibrationSample;
import kr.ac.kopo.ttd.domain.CalibrationTier;
import kr.ac.kopo.ttd.dto.CalibrationRunResponse;
import kr.ac.kopo.ttd.repository.CalibrationSampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 캘리브레이션 샘플 30건을 루브릭 채점기에 돌려 기대 티어·기준 점수와의 일치율을 측정한다(WBS 3.5.4).
 * 점수→티어 밴드와 허용 오차는 파라미터라 회의에서 밴드를 바꿔가며 재실행할 수 있고,
 * AiClient 캐싱 덕에 동일 프롬프트 재실행은 추가 비용이 없다.
 * "[임시]" placeholder 답안은 표시만 하고 유효 일치율 집계에서 제외한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalibrationRunner {

    private static final String PLACEHOLDER_PREFIX = "[임시]";

    private final CalibrationSampleRepository calibrationSampleRepository;
    private final RubricGrader rubricGrader;

    @Transactional(readOnly = true)
    public CalibrationRunResponse run(int highMin, int midMin, int tolerance) {
        List<CalibrationSample> samples = calibrationSampleRepository.findAll();
        List<CalibrationRunResponse.Row> rows = new ArrayList<>();
        for (CalibrationSample sample : samples) {
            rows.add(evaluate(sample, highMin, midMin, tolerance));
        }
        return new CalibrationRunResponse(
                new CalibrationRunResponse.Config(highMin, midMin, tolerance),
                summarize(rows, tolerance),
                rows);
    }

    private CalibrationRunResponse.Row evaluate(CalibrationSample sample, int highMin, int midMin, int tolerance) {
        boolean placeholder = sample.getSampleAnswer().startsWith(PLACEHOLDER_PREFIX);
        try {
            RubricGrader.RubricResult result =
                    rubricGrader.grade(sample.getProblem(), sample.getSampleAnswer(), List.of());
            CalibrationTier gradedTier = toTier(result.score(), highMin, midMin);
            int scoreDiff = Math.abs(result.score() - sample.getReferenceScore());
            return new CalibrationRunResponse.Row(
                    sample.getId(), sample.getProblem().getTitle(),
                    sample.getTier(), sample.getReferenceScore(),
                    result.score(), gradedTier,
                    gradedTier == sample.getTier(), scoreDiff,
                    placeholder, null);
        } catch (Exception e) {
            // 한 샘플의 실패가 전체 측정을 중단시키지 않는다
            log.warn("캘리브레이션 채점 실패: sampleId={}", sample.getId(), e);
            return new CalibrationRunResponse.Row(
                    sample.getId(), sample.getProblem().getTitle(),
                    sample.getTier(), sample.getReferenceScore(),
                    null, null, null, null, placeholder, e.getMessage());
        }
    }

    /** 점수→티어 밴드 매핑. 기본값(70/40)은 임시 기준으로, 산식 회의에서 확정한다. */
    static CalibrationTier toTier(int score, int highMin, int midMin) {
        if (score >= highMin) return CalibrationTier.HIGH;
        if (score >= midMin) return CalibrationTier.MID;
        return CalibrationTier.LOW;
    }

    private CalibrationRunResponse.Summary summarize(List<CalibrationRunResponse.Row> rows, int tolerance) {
        List<CalibrationRunResponse.Row> graded = rows.stream().filter(r -> r.error() == null).toList();
        List<CalibrationRunResponse.Row> valid = graded.stream().filter(r -> !r.placeholder()).toList();

        int placeholderCount = (int) rows.stream().filter(CalibrationRunResponse.Row::placeholder).count();
        int errorCount = rows.size() - graded.size();
        int tierMatches = (int) graded.stream().filter(r -> Boolean.TRUE.equals(r.tierMatch())).count();
        long validMatches = valid.stream().filter(r -> Boolean.TRUE.equals(r.tierMatch())).count();
        long withinTolerance = graded.stream().filter(r -> r.scoreDiff() != null && r.scoreDiff() <= tolerance).count();

        return new CalibrationRunResponse.Summary(
                rows.size(), placeholderCount, errorCount, tierMatches,
                rate(tierMatches, graded.size()),
                rate(validMatches, valid.size()),
                graded.isEmpty() ? null
                        : graded.stream().mapToInt(CalibrationRunResponse.Row::scoreDiff).average().orElse(0),
                rate(withinTolerance, graded.size()));
    }

    private Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
