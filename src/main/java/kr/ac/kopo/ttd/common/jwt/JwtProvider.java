package kr.ac.kopo.ttd.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.ac.kopo.ttd.domain.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT access/refresh token 발급 및 검증을 담당한다. access token은 role claim을 포함해
 * 완전히 stateless로 인가 판단이 가능하고, refresh token은 tokenType claim만 가진다
 * (실제 유효성은 Redis 저장값과의 대조로 {@code RefreshTokenRepository}에서 확인).
 */
@Component
public class JwtProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtProvider(
            @Value("${app.jwt.secret-key}") String base64Key,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
            @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT 서명 키는 Base64로 인코딩된 값이어야 합니다.", e);
        }
        if (keyBytes.length < KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("JWT 서명 키는 Base64 디코딩 기준 32바이트 이상이어야 합니다.");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public String generateAccessToken(Long userId, UserRole role) {
        return buildToken(userId, accessTokenTtl, TOKEN_TYPE_ACCESS, role);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, refreshTokenTtl, TOKEN_TYPE_REFRESH, null);
    }

    public Claims parseAccessToken(String token) {
        return parseAndValidateType(token, TOKEN_TYPE_ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parseAndValidateType(token, TOKEN_TYPE_REFRESH);
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public UserRole getRole(Claims claims) {
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    private String buildToken(Long userId, Duration ttl, String tokenType, UserRole role) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey);
        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }
        return builder.compact();
    }

    private Claims parseAndValidateType(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("잘못된 토큰 타입입니다. expected=" + expectedType);
        }
        return claims;
    }
}
