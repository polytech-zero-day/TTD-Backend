package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.crypto.HmacHasher;
import kr.ac.kopo.ttd.common.exception.DuplicateEmailException;
import kr.ac.kopo.ttd.common.exception.UserNotFoundException;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.dto.UserCreateRequest;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.dto.UserUpdateRequest;
import kr.ac.kopo.ttd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HmacHasher hmacHasher;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        String emailHash = hmacHasher.hash(request.email());
        if (userRepository.existsByEmailHash(emailHash)) {
            throw new DuplicateEmailException();
        }

        User user = User.builder()
                .email(request.email())
                .emailHash(emailHash)
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .role(request.role())
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getUser(Long id) {
        return UserResponse.from(findUserOrThrow(id));
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findUserOrThrow(id);
        user.changeNickname(request.nickname());
        user.changeRole(request.role());
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException();
        }
        userRepository.deleteById(id);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }
}
