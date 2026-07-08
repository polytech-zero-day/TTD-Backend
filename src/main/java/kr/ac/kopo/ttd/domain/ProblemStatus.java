package kr.ac.kopo.ttd.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 문제 상태. 전환 규칙: draft ─▶ pending ─▶ active, pending ─(반려)▶ draft.
 * JSON 직렬화/역직렬화는 소문자(draft/pending/active)로 처리하여 프론트 계약과 일치시킨다.
 */
public enum ProblemStatus {
    DRAFT,
    PENDING,
    ACTIVE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ProblemStatus fromJson(String value) {
        return ProblemStatus.valueOf(value.toUpperCase());
    }

    public boolean canTransitionTo(ProblemStatus target) {
        return switch (this) {
            case DRAFT -> target == PENDING;
            case PENDING -> target == ACTIVE || target == DRAFT;
            case ACTIVE -> false;
        };
    }
}
