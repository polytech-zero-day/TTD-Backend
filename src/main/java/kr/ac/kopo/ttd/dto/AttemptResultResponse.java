package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Attempt;

public record AttemptResultResponse(
        Long attemptId, String status,
        Integer rubricScore, Integer efficiencyScore, String feedback) {

    public static AttemptResultResponse from(Attempt a) {
        return new AttemptResultResponse(
                a.getId(), a.getStatus().name(),
                a.getRubricScore(), a.getEfficiencyScore(), a.getFeedback());
    }
}