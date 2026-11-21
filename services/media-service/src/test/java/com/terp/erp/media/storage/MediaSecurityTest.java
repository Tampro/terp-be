package com.terp.erp.media.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EMediaKind;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaSecurityTest {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = "%PDF-1.7".getBytes();

    private static MediaProperties props(Path dir) {
        return new MediaProperties(dir.toString(), "test-secret-test-secret-test-secret!!", Duration.ofMinutes(10),
                DataSize.ofMegabytes(10), "http://localhost:8080", "http://iam", "erp-employee-service");
    }

    @Test
    void allowedTypeWithMatchingBytesPasses() {
        assertThat(FileTypePolicy.check("Logo.PNG", PNG).kind()).isEqualTo(EMediaKind.IMAGE);
        assertThat(FileTypePolicy.check("cv.pdf", PDF).mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void scriptCapableAndUnknownTypesAreRefused() {
        for (String name : new String[]{"x.svg", "x.html", "x.exe", "noext", "x.js"}) {
            assertThatThrownBy(() -> FileTypePolicy.check(name, PNG))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_004);
        }
    }

    @Test
    void renamedFileIsRefusedByItsBytes() {
        // An HTML page renamed to .png must not get in as an image.
        assertThatThrownBy(() -> FileTypePolicy.check("evil.png", "<html><script>".getBytes()))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_006);
    }

    @Test
    void signedLinkWorksUntilExpiryAndOnlyForItsFileAndMode(@TempDir Path dir) {
        Instant t0 = Instant.parse("2026-11-19T00:00:00Z");
        LinkSigner signer = new LinkSigner(props(dir), Clock.fixed(t0, ZoneOffset.UTC));
        var link = signer.link(42, false);
        String sig = link.url().substring(link.url().indexOf("sig=") + 4);

        assertThat(signer.verify(42, link.expiresAt(), 0, sig)).isTrue();
        assertThat(signer.verify(43, link.expiresAt(), 0, sig)).isFalse();      // other file
        assertThat(signer.verify(42, link.expiresAt(), 1, sig)).isFalse();      // view link used as download
        assertThat(signer.verify(42, link.expiresAt() + 600, 0, sig)).isFalse(); // extended expiry
        assertThat(signer.verify(42, link.expiresAt(), 0, sig + "x")).isFalse();

        LinkSigner later = new LinkSigner(props(dir), Clock.fixed(t0.plusSeconds(601), ZoneOffset.UTC));
        assertThat(later.verify(42, link.expiresAt(), 0, sig)).isFalse();       // expired
    }

    @Test
    void storageKeysStayUnderTheRoot(@TempDir Path dir) throws Exception {
        FileStorage storage = new FileStorage(props(dir));
        String key = storage.save("c10", "png", new ByteArrayInputStream(PNG));
        assertThat(key).startsWith("c10/").endsWith(".png");
        assertThat(Files.readAllBytes(storage.path(key))).isEqualTo(PNG);
        assertThatThrownBy(() -> storage.path("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientFileNameKeepsOnlyItsLastSegment() {
        assertThat(com.terp.erp.media.service.MediaServiceNames.clean("..\\..\\a.png")).isEqualTo("a.png");
        assertThat(com.terp.erp.media.service.MediaServiceNames.clean("/etc/x.pdf")).isEqualTo("x.pdf");
        assertThat(com.terp.erp.media.service.MediaServiceNames.clean(null)).isEqualTo("file");
    }
}
