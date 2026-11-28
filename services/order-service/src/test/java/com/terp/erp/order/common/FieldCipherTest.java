package com.terp.erp.order.common;

import com.terp.erp.order.config.OrderProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The address phone is never stored in clear; a wrong key stops the service instead of storing plain text. */
public class FieldCipherTest {
    /** Test-only key (32 zero-free bytes), never a real one. */
    public static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    public static FieldCipher cipher() {
        return new FieldCipher(props(KEY));
    }

    @Test
    void roundTripWithAFreshIvEachTime() {
        FieldCipher c = cipher();
        String a = c.encrypt("01012345678"), b = c.encrypt("01012345678");
        assertThat(a).isNotEqualTo(b).doesNotContain("01012345678");
        assertThat(c.decrypt(a)).isEqualTo("01012345678");
        assertThat(c.decrypt(b)).isEqualTo("01012345678");
    }

    @Test
    void badKeysAreRefused() {
        assertThatThrownBy(() -> new FieldCipher(props("not base64 !"))).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not base64 !");
        assertThatThrownBy(() -> new FieldCipher(props(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static OrderProperties props(String key) {
        return new OrderProperties("http://unused", "http://unused", "http://unused", "http://unused", "http://unused", "http://unused", key, true);
    }
}
