package kr.ac.kopo.ttd.service;

import io.jsonwebtoken.Claims;
import kr.ac.kopo.ttd.common.crypto.HmacHasher;
import kr.ac.kopo.ttd.common.exception.DuplicateEmailException;
import kr.ac.kopo.ttd.common.exception.InvalidCredentialsException;
import kr.ac.kopo.ttd.common.exception.InvalidRefreshTokenException;
import kr.ac.kopo.ttd.common.jwt.JwtProvider;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.LoginRequest;
import kr.ac.kopo.ttd.dto.RefreshRequest;
import kr.ac.kopo.ttd.dto.SignupRequest;
import kr.ac.kopo.ttd.dto.TokenResponse;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.repository.RefreshTokenRepository;
import kr.ac.kopo.ttd.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HmacHasher hmacHasher;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void 회원가입하면_role은_항상_USER로_저장된다() {
        SignupRequest request = new SignupRequest("dummy@example.com", "password123", "nick");
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.existsByEmailHash("hash")).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.signup(request);

        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void 회원가입시_이메일이_중복되면_예외() {
        SignupRequest request = new SignupRequest("dummy@example.com", "password123", "nick");
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.existsByEmailHash("hash")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void 로그인_성공시_토큰을_발급하고_refresh_token을_저장한다() {
        LoginRequest request = new LoginRequest("dummy@example.com", "password123");
        User user = User.builder()
                .id(1L)
                .email("dummy@example.com")
                .emailHash("hash")
                .passwordHash("encoded")
                .nickname("nick")
                .role(UserRole.USER)
                .build();
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.findByEmailHash("hash")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), "encoded")).willReturn(true);
        given(jwtProvider.generateAccessToken(1L, UserRole.USER)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenTtl()).willReturn(Duration.ofDays(14));
        given(jwtProvider.getAccessTokenTtlSeconds()).willReturn(1800L);

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(1L, "refresh-token", Duration.ofDays(14));
    }

    @Test
    void 이메일이_존재하지_않으면_로그인_실패() {
        LoginRequest request = new LoginRequest("dummy@example.com", "password123");
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.findByEmailHash("hash")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 비밀번호가_틀리면_로그인_실패() {
        LoginRequest request = new LoginRequest("dummy@example.com", "wrong-password");
        User user = User.builder()
                .id(1L)
                .email("dummy@example.com")
                .emailHash("hash")
                .passwordHash("encoded")
                .nickname("nick")
                .role(UserRole.USER)
                .build();
        given(hmacHasher.hash(request.email())).willReturn("hash");
        given(userRepository.findByEmailHash("hash")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refresh_토큰이_유효하면_새_토큰쌍을_발급하고_회전한다() {
        RefreshRequest request = new RefreshRequest("old-refresh-token");
        Claims claims = mock(Claims.class);
        given(jwtProvider.parseRefreshToken("old-refresh-token")).willReturn(claims);
        given(jwtProvider.getUserId(claims)).willReturn(1L);
        given(refreshTokenRepository.find(1L)).willReturn(Optional.of("old-refresh-token"));
        User user = User.builder()
                .id(1L)
                .email("dummy@example.com")
                .emailHash("hash")
                .passwordHash("encoded")
                .nickname("nick")
                .role(UserRole.USER)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(1L, UserRole.USER)).willReturn("new-access-token");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("new-refresh-token");
        given(jwtProvider.getRefreshTokenTtl()).willReturn(Duration.ofDays(14));
        given(jwtProvider.getAccessTokenTtlSeconds()).willReturn(1800L);

        TokenResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository).save(1L, "new-refresh-token", Duration.ofDays(14));
    }

    @Test
    void refresh_토큰이_저장된_값과_다르면_예외() {
        RefreshRequest request = new RefreshRequest("presented-token");
        Claims claims = mock(Claims.class);
        given(jwtProvider.parseRefreshToken("presented-token")).willReturn(claims);
        given(jwtProvider.getUserId(claims)).willReturn(1L);
        given(refreshTokenRepository.find(1L)).willReturn(Optional.of("different-stored-token"));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_저장된_토큰이_없으면_예외() {
        RefreshRequest request = new RefreshRequest("presented-token");
        Claims claims = mock(Claims.class);
        given(jwtProvider.parseRefreshToken("presented-token")).willReturn(claims);
        given(jwtProvider.getUserId(claims)).willReturn(1L);
        given(refreshTokenRepository.find(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 로그아웃하면_저장된_refresh_token을_삭제한다() {
        authService.logout(1L);

        verify(refreshTokenRepository).delete(1L);
    }
}
