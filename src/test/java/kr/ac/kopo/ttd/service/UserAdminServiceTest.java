package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.crypto.HmacHasher;
import kr.ac.kopo.ttd.common.exception.DuplicateEmailException;
import kr.ac.kopo.ttd.common.exception.UserNotFoundException;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.UserCreateRequest;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.dto.UserUpdateRequest;
import kr.ac.kopo.ttd.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HmacHasher hmacHasher;

    @InjectMocks
    private UserAdminService userAdminService;

    @Test
    void 신규_유저를_생성한다() {
        UserCreateRequest request = new UserCreateRequest("dummy@example.com", "password123", "nick", UserRole.USER);
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.existsByEmailHash("hash")).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userAdminService.createUser(request);

        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void 이메일이_중복되면_예외() {
        UserCreateRequest request = new UserCreateRequest("dummy@example.com", "password123", "nick", UserRole.USER);
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.existsByEmailHash("hash")).willReturn(true);

        assertThatThrownBy(() -> userAdminService.createUser(request))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void 존재하지_않는_유저_조회시_예외() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.getUser(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 유저_정보를_수정한다() {
        User user = User.builder()
                .email("dummy@example.com")
                .emailHash("hash")
                .passwordHash("encoded")
                .nickname("old")
                .role(UserRole.USER)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userAdminService.updateUser(1L, new UserUpdateRequest("new", UserRole.ADMIN));

        assertThat(response.nickname()).isEqualTo("new");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 존재하지_않는_유저_삭제시_예외() {
        given(userRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> userAdminService.deleteUser(1L))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void 유저를_삭제한다() {
        given(userRepository.existsById(1L)).willReturn(true);

        userAdminService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }
}
