package com.terp.erp.employee.common;

import org.springframework.stereotype.Component;
import com.terp.erp.employee.config.EmployeeProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for the bank account number and tax payer id (PO, 2026-11-19). The key is EMPLOYEE_ENC_KEY from the
 * environment; the service refuses to start without a valid one, so these fields are never stored in clear.
 * Output = base64(12-byte IV || ciphertext+tag). Rotating the key needs a re-encryption job (not built).
 */
@Component
public class FieldCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public FieldCipher(EmployeeProperties props) {
        String base64Key = props.encKey();
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            // Never print the value.
            throw new IllegalStateException("EMPLOYEE_ENC_KEY is not valid base64 (generate one with: openssl rand -base64 32)");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("EMPLOYEE_ENC_KEY must be base64 of 32 bytes, got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + out.length).put(iv).put(out).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** A value that no longer decrypts (key changed) is shown as missing rather than failing the whole profile. */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** "0123456789" → "******6789". */
    public static String mask(String plain) {
        if (plain == null) {
            return null;
        }
        int keep = Math.min(4, plain.length());
        return "*".repeat(plain.length() - keep) + plain.substring(plain.length() - keep);
    }
}
