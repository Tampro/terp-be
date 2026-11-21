package com.terp.erp.iam.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for secrets kept in platform settings (SMTP password, reCAPTCHA secret). The key is
 * ERP_SETTINGS_ENC_KEY (base64 of 32 random bytes) from the environment only; without it secrets can neither be
 * saved nor used, everything else keeps working. Output = base64(12-byte IV || ciphertext+tag).
 */
@Component
public class SecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(@Value("${erp.settings.enc-key:}") String base64Key) {
        SecretKeySpec k = null;
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(base64Key.trim());
            } catch (IllegalArgumentException e) {
                // Never print the value. Typical cause: a hand-typed key instead of `openssl rand -base64 32`.
                throw new IllegalStateException("ERP_SETTINGS_ENC_KEY is not valid base64 "
                        + "(generate one with: openssl rand -base64 32)");
            }
            if (raw.length != 32) {
                // Never print the value: only its length.
                throw new IllegalStateException("ERP_SETTINGS_ENC_KEY must be base64 of 32 bytes, got " + raw.length);
            }
            k = new SecretKeySpec(raw, "AES");
        }
        this.key = k;
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + out.length).put(iv).put(out).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Key rotated or value tampered with: the tag check fails.
            throw ApiException.badRequest(ESettingsError.SETTINGS_007);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_002);
        }
    }
}
