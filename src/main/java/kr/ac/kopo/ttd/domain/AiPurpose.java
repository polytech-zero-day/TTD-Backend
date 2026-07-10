package kr.ac.kopo.ttd.domain;

/**
 * AI 모델을 쓰는 용도. 용도별로 다른 모델을 지정할 수 있다
 * (예: 대화는 빠른 mini, 채점은 정확한 상위 모델). 검수 등 용도는 추후 확장.
 */
public enum AiPurpose {
    CHAT("응시 대화"),
    GRADING("루브릭 채점");

    private final String label;

    AiPurpose(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
