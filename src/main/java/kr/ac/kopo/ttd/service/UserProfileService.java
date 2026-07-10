package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.exception.UserNotFoundException;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.dto.MyProfileResponse;
import kr.ac.kopo.ttd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
