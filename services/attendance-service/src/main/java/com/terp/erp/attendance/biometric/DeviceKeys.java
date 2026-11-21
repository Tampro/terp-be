package com.terp.erp.attendance.biometric;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Device keys: 240 random bits, shown once; only the SHA-256 is stored (a key is not a password: no slow hash). */
final class DeviceKeys {
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceKeys() {
    }

    static String generate() {
        byte[] bytes = new byte[30];
        RANDOM.nextBytes(bytes);
        return "bk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String prefix(String key) {
        return key.substring(0, 8);
    }

    static String hash(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of two hashes. */
    static boolean same(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
