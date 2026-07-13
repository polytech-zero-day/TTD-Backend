package kr.ac.kopo.ttd.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 캘리브레이션 샘플 등급. DB에는 enum명(HIGH/MID/LOW)으로 저장하고,
 * JSON 직렬화/역직렬화는 한글(상/중/하)로 처리하여 프론트 계약과 일치시킨다.
 */
public enum CalibrationTier {
    HIGH,
    MID,
    LOW;

    @JsonValue
    public String toJson() {
        return switch (this) {
            case HIGH -> "상";
            case MID -> "중";
            case LOW -> "하";
        };
    }

    @JsonCreator
    public static CalibrationTier fromJson(String value) {
        return switch (value) {
            case "상" -> HIGH;
            case "중" -> MID;
            case "하" -> LOW;
            default -> CalibrationTier.valueOf(value.toUpperCase(Locale.ROOT));
        };
    }
}
