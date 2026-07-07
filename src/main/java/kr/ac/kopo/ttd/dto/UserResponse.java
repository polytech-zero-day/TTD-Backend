package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.domain.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        UserRole role,
        LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
