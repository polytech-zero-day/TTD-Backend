package kr.ac.kopo.ttd.dto;

import java.time.LocalDateTime;

public record MyProfileResponse(
    Long id,
    String email,
    String nickname,
    String role,
    LocalDateTime createdAt
) {}
