package kr.ac.kopo.ttd.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HmacHasherTest {

    private final HmacHasher hmacHasher = new HmacHasher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void 같은_입력은_항상_같은_해시를_생성한다() {
        String first = hmacHasher.hash("dummy@example.com");
        String second = hmacHasher.hash("dummy@example.com");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void 다른_입력은_다른_해시를_생성한다() {
        String first = hmacHasher.hash("dummy1@example.com");
        String second = hmacHasher.hash("dummy2@example.com");

        assertThat(first).isNotEqualTo(second);
    }
}
