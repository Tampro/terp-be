package com.terp.erp.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.model.MediaFile;
import com.terp.erp.media.repository.MediaFileRepository;
import com.terp.erp.media.repository.MediaFolderRepository;
import com.terp.erp.media.storage.FileStorage;
import com.terp.erp.media.storage.LinkSigner;
import com.terp.erp.media.storage.StoragePolicyClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The service-account lookup must stay inside the named company: a service cannot reach across by guessing ids. */
class InternalFileTest {
    private final MediaFileRepository files = mock(MediaFileRepository.class);

    private MediaService service(Path dir) {
        MediaProperties props = new MediaProperties(dir.toString(), "test-secret-test-secret-test-secret!!",
                Duration.ofMinutes(10), DataSize.ofMegabytes(10), "http://localhost:8080", "http://iam",
                "erp-employee-service");
        LinkSigner signer = new LinkSigner(props, Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));
        return new MediaService(files, mock(MediaFolderRepository.class), mock(FileStorage.class), signer, props,
                mock(StoragePolicyClient.class));
    }

    @Test
    void fileOfTheNamedCompanyGetsSignedLinks(@TempDir Path dir) {
        when(files.findById(7L)).thenReturn(Optional.of(file(10L)));
        var dto = service(dir).internalFile(7L, 10L);
        assertThat(dto.downloadUrl()).contains("/api/v1/media/files/7/content", "dl=1", "sig=");
    }

    @Test
    void fileOfAnotherCompanyIsNotFound(@TempDir Path dir) {
        when(files.findById(7L)).thenReturn(Optional.of(file(11L)));
        assertThatThrownBy(() -> service(dir).internalFile(7L, 10L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_001);
    }

    private static MediaFile file(Long companyId) {
        MediaFile m = new MediaFile();
        m.setId(7L);
        m.setCompanyId(companyId);
        m.setOriginalName("policy.pdf");
        m.setMimeType("application/pdf");
        return m;
    }
}
