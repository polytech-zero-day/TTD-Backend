package kr.ac.kopo.ttd.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import kr.ac.kopo.ttd.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final JwtProvider jwtProvider = new JwtProvider(VALID_KEY, 30, 14);

    @Test
    void access_token을_발급하고_파싱하면_userId와_role이_일치한다() {
        String token = jwtProvider.generateAccessToken(1L, UserRole.ADMIN);

        Claims claims = jwtProvider.parseAccessToken(token);

        assertThat(jwtProvider.getUserId(claims)).isEqualTo(1L);
        assertThat(jwtProvider.getRole(claims)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void refresh_token을_발급하고_파싱하면_userId가_일치한다() {
        String token = jwtProvider.generateRefreshToken(1L);

        Claims claims = jwtProvider.parseRefreshToken(token);

        assertThat(jwtProvider.getUserId(claims)).isEqualTo(1L);
    }

    @Test
    void access_token을_refresh_token으로_파싱하면_예외() {
        String accessToken = jwtProvider.generateAccessToken(1L, UserRole.USER);

        assertThatThrownBy(() -> jwtProvider.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refresh_token을_access_token으로_파싱하면_예외() {
        String refreshToken = jwtProvider.generateRefreshToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰을_파싱하면_예외() {
        JwtProvider shortLivedProvider = new JwtProvider(VALID_KEY, -1, -1);
        String token = shortLivedProvider.generateAccessToken(1L, UserRole.USER);

        assertThatThrownBy(() -> shortLivedProvider.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 같은_사용자에_대해_연속_발급한_refresh_token은_서로_달라야_한다() {
        // iat/exp가 초 단위라 같은 초에 발급되면 jti가 없으면 완전히 동일한 토큰이 생성돼
        // 회전(rotation) 후에도 이전 토큰이 여전히 유효한 것처럼 재사용될 수 있었던 회귀 버그 방지용
        String first = jwtProvider.generateRefreshToken(1L);
        String second = jwtProvider.generateRefreshToken(1L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 키_길이가_32바이트_미만이면_생성_시점에_예외() {
        String invalidKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new JwtProvider(invalidKey, 30, 14))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
