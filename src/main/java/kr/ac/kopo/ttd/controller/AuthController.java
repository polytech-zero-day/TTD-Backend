package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.LoginRequest;
import kr.ac.kopo.ttd.common.exception.InvalidRefreshTokenException;
import kr.ac.kopo.ttd.dto.AccessTokenResponse;
import kr.ac.kopo.ttd.dto.RefreshRequest;
import kr.ac.kopo.ttd.dto.SignupRequest;
import kr.ac.kopo.ttd.dto.TokenResponse;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "ttd_refresh";

    private final AuthService authService;

    @Value("${app.auth.refresh-cookie-secure}")
    private boolean refreshCookieSecure;

    // 운영에서 프론트·백엔드 도메인이 다르면 None(+Secure)로 지정해야 fetch에 쿠키가 실린다. 기본 Lax.
    @Value("${app.auth.refresh-cookie-same-site}")
    private String refreshCookieSameSite;

    @Value("${app.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return withRefreshCookie(authService.login(request), response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return withRefreshCookie(authService.refresh(new RefreshRequest(refreshToken)), response);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        authService.logout(userId);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ApiResponse<AccessTokenResponse>> withRefreshCookie(
            TokenResponse tokens, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie(tokens.refreshToken(), Duration.ofDays(refreshTokenTtlDays)).toString());
        return ResponseEntity.ok(ApiResponse.success(AccessTokenResponse.from(tokens)));
    }

    private ResponseCookie refreshCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
