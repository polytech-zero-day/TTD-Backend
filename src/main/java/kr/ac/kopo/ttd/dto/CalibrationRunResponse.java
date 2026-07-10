package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.CalibrationTier;

import java.util.List;

/**
 * 캘리브레이션 일치율 측정 결과. 유효 일치율은 placeholder(임시 답안) 샘플을 제외하고
 * 계산하며, 실데이터 보완 전에는 참고용 전체 일치율과 함께 본다.
 */
public record CalibrationRunResponse(
        Config config,
        Summary summary,
        List<Row> rows
) {

    public record Config(int highMin, int midMin, int tolerance) {}

    public record Summary(
            int total,
            int placeholderCount,
            int errorCount,
            int tierMatches,
            Double tierAgreementRate,        // 전체(에러 제외) 기준
            Double validTierAgreementRate,   // placeholder·에러 제외 — 유효 샘플 없으면 null
            Double avgAbsScoreDiff,          // |채점 점수 - 기준 점수| 평균
            Double withinToleranceRate       // 점수 오차가 tolerance 이내인 비율
    ) {}

    public record Row(
            Long sampleId,
            String problemTitle,
            CalibrationTier expectedTier,
            int referenceScore,
            Integer gradedScore,      // 채점 실패 시 null
            CalibrationTier gradedTier,
            Boolean tierMatch,
            Integer scoreDiff,
            boolean placeholder,      // "[임시]" 답안 — 일치율 신뢰 불가 표시
            String error
    ) {}
}
