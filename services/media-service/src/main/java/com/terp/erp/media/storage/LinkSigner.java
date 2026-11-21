package com.terp.erp.media.storage;

import org.springframework.stereotype.Component;
import com.terp.erp.media.config.MediaProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * Time-limited links to a file (PO, 2026-11-18): {@code /api/v1/media/files/{id}/content?exp=..&dl=0|1&sig=..}
 * with sig = HMAC-SHA256(secret, "id:exp:dl"). Issued only after the caller passed the permission and company
 * checks; anyone holding the link can use it until {@code exp}, then it stops working.
 */
@Component
public class LinkSigner {
    private final byte[] secret;
    private final long ttlSeconds;
    private final String baseUrl;
    private final Clock clock;

    public LinkSigner(MediaProperties props, Clock clock) {
        if (props.linkSecret().length() < 32) {
            throw new IllegalStateException("MEDIA_LINK_SECRET must be at least 32 characters");
        }
        this.secret = props.linkSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = props.linkTtl().toSeconds();
        this.baseUrl = props.publicBaseUrl().replaceAll("/+$", "");
        this.clock = clock;
    }

    public record SignedLink(String url, long expiresAt) {
    }

    public SignedLink link(long fileId, boolean download) {
        long exp = clock.instant().getEpochSecond() + ttlSeconds;
        int dl = download ? 1 : 0;
        String url = baseUrl + "/api/v1/media/files/" + fileId + "/content?exp=" + exp + "&dl=" + dl
                + "&sig=" + sign(fileId, exp, dl);
        return new SignedLink(url, exp);
    }

    public boolean verify(long fileId, long exp, int dl, String sig) {
        if (sig == null || exp < clock.instant().getEpochSecond() || (dl != 0 && dl != 1)) {
            return false;
        }
        // Constant-time comparison: a timing difference must not reveal how much of a guessed signature matched.
        return MessageDigest.isEqual(sign(fileId, exp, dl).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(long fileId, long exp, int dl) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal((fileId + ":" + exp + ":" + dl).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
