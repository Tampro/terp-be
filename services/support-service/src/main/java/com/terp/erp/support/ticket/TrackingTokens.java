package com.terp.erp.support.ticket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The tracking token of a ticket: 256 random bits, URL-safe. Only its SHA-256 is stored, so a database read does not
 * hand out working portal links; the raw token is returned once, when the ticket is created.
 */
public final class TrackingTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TrackingTokens() {
    }

    public static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String token) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(d.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
