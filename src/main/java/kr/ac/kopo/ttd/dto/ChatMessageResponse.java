package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.AttemptMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id, String role, String content, long tokensUsed, LocalDateTime createdAt) {
    public static ChatMessageResponse from(AttemptMessage m) {
        return new ChatMessageResponse(
                m.getId(), m.getRole().name().toLowerCase(), m.getContent(),
                m.getTokensUsed(), m.getCreatedAt());
    }
}
