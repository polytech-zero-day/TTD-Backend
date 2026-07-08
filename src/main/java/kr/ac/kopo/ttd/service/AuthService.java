package kr.ac.kopo.ttd.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import kr.ac.kopo.ttd.common.crypto.HmacHasher;
import kr.ac.kopo.ttd.common.exception.DuplicateEmailException;
import kr.ac.kopo.ttd.common.exception.InvalidCredentialsException;
import kr.ac.kopo.ttd.common.exception.InvalidRefreshTokenException;
import kr.ac.kopo.ttd.common.exception.UserNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final HmacHasher hmacHasher;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String emailHash = hmacHasher.hash(request.email());
        if (userRepository.existsByEmailHash(emailHash)) {
            throw new DuplicateEmailException();
        }

        User user = User.builder()
                .email(request.email())
                .emailHash(emailHash)
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .role(UserRole.USER)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailHash(hmacHasher.hash(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtProvider.parseRefreshToken(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }

        Long userId = jwtProvider.getUserId(claims);
        String storedRefreshToken = refreshTokenRepository.find(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!storedRefreshToken.equals(request.refreshToken())) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.delete(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken, jwtProvider.getRefreshTokenTtl());
        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtProvider.getAccessTokenTtlSeconds());
    }
}
