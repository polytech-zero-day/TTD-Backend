package kr.ac.kopo.ttd.domain;

/**
 * 루브릭 항목별 채점 결과. LLM 채점 응답에서 파싱되어
 * attempts.rubric_detail(JSON 배열)로 저장되고, 결과 리포트에 그대로 내려간다.
 */
public record RubricCriterion(String name, int score, int maxScore, String comment) {
}
