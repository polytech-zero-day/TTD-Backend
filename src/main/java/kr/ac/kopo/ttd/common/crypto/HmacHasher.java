package kr.ac.kopo.ttd.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 검색이 필요한 암호화 필드(이메일 등)의 인덱싱용 HMAC-SHA256 해시를 계산한다.
 * 동일 입력은 항상 동일 해시를 반환하므로 WHERE 조건/유니크 제약에 사용할 수 있다.
 */
@Component
public class HmacHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public HmacHasher(@Value("${app.security.hmac-key}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("HMAC 키는 Base64로 인코딩된 값이어야 합니다.", e);
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String hash(String plainText) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            byte[] result = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 해시 계산에 실패했습니다.", e);
        }
    }
}
