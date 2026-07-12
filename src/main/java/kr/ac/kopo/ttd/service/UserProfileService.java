package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.exception.UserNotFoundException;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.dto.MyProfileResponse;
import kr.ac.kopo.ttd.dto.NicknameUpdateRequest;
import kr.ac.kopo.ttd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    public MyProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole().toString(),
                user.getCreatedAt()
        );
    }

    @Transactional
    public MyProfileResponse changeNickname(Long userId, NicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.changeNickname(request.nickname());
        return new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole().toString(),
                user.getCreatedAt()
        );
    }
}
