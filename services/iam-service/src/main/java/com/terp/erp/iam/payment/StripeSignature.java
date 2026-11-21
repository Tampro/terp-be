package com.terp.erp.iam.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies the Stripe-Signature header of a webhook: "t=<unix time>,v1=<hex HMAC-SHA256 of "t.payload">" signed with
 * the endpoint's signing secret. Events older (or newer) than the tolerance are refused, against replays.
 */
public final class StripeSignature {
    static final long TOLERANCE_SECONDS = 300;

    private StripeSignature() {
    }

    public static boolean valid(String payload, String header, String secret, Instant now) {
        if (payload == null || header == null || secret == null) {
            return false;
        }
        Long t = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    t = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (t == null || signatures.isEmpty() || Math.abs(now.getEpochSecond() - t) > TOLERANCE_SECONDS) {
            return false;
        }
        byte[] expected = sign(t + "." + payload, secret).getBytes(StandardCharsets.US_ASCII);
        // Constant time: an attacker learns nothing from how long the comparison takes.
        return signatures.stream().anyMatch(s -> MessageDigest.isEqual(expected, s.getBytes(StandardCharsets.US_ASCII)));
    }

    public static String sign(String signedPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
