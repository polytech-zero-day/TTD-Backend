package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.AttemptMessage;

public record ChatMessageResponse(Long id, String role, String content) {
    public static ChatMessageResponse from(AttemptMessage m) {
        return new ChatMessageResponse(m.getId(), m.getRole().name().toLowerCase(), m.getContent());
    }
}