package com.terp.erp.meeting.zoom;

import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.config.MeetingProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for the Zoom client secrets. The key is MEETING_ENC_KEY (base64 of 32 random bytes) from the environment
 * only; the service refuses to start without a valid one. The company id is bound as associated data, so a secret
 * copied to another company's row does not decrypt. Output = base64(12-byte IV || ciphertext+tag).
 */
@Component
public class SecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(MeetingProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.encKey().trim());
        } catch (IllegalArgumentException e) {
            // Never print the value.
            throw new IllegalStateException("MEETING_ENC_KEY is not valid base64 (openssl rand -base64 32)");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("MEETING_ENC_KEY must be base64 of 32 bytes, got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain, Long companyId) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(aad(companyId));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + out.length).put(iv).put(out).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public String decrypt(String stored, Long companyId) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            c.updateAAD(aad(companyId));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Key rotated or value tampered with: the tag check fails.
            throw ApiException.badRequest(EMeetingError.MTG_026);
        }
    }

    private static byte[] aad(Long companyId) {
        return ("zoom:" + companyId).getBytes(StandardCharsets.UTF_8);
    }
}
