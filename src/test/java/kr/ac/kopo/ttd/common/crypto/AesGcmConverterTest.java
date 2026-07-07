package kr.ac.kopo.ttd.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmConverterTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void 암호화한_값을_복호화하면_원문과_같다() {
        AesGcmConverter converter = new AesGcmConverter(VALID_KEY);
        String plainText = "dummy@example.com";

        String encrypted = converter.convertToDatabaseColumn(plainText);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
        assertThat(encrypted).isNotEqualTo(plainText);
    }

    @Test
    void 같은_평문이라도_매번_다른_암호문을_생성한다() {
        AesGcmConverter converter = new AesGcmConverter(VALID_KEY);
        String plainText = "dummy@example.com";

        String encryptedOnce = converter.convertToDatabaseColumn(plainText);
        String encryptedTwice = converter.convertToDatabaseColumn(plainText);

        assertThat(encryptedOnce).isNotEqualTo(encryptedTwice);
    }

    @Test
    void 키_길이가_32바이트가_아니면_생성_시점에_예외() {
        String invalidKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmConverter(invalidKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 키가_Base64_형식이_아니면_예외() {
        assertThatThrownBy(() -> new AesGcmConverter("not-base64-!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
