package kr.ac.kopo.ttd.dto;

/** 브라우저에 반환하는 단기 access token 응답. refresh token은 HttpOnly 쿠키로만 전달한다. */
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static AccessTokenResponse from(TokenResponse tokens) {
        return new AccessTokenResponse(tokens.accessToken(), tokens.tokenType(), tokens.expiresInSeconds());
    }
}
