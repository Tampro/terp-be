package com.terp.erp.media.service;

import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EMediaKind;
import com.terp.erp.media.model.MediaFile;
import com.terp.erp.media.repository.MediaFileRepository;
import com.terp.erp.media.repository.MediaFolderRepository;
import com.terp.erp.media.storage.LinkSigner;
import com.terp.erp.media.storage.StoragePolicyClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Batch lookup for the storefront: only images of the named company, in the order asked, at most 100. */
class InternalFilesBatchTest {
    private final MediaFileRepository files = mock(MediaFileRepository.class);

    private MediaService service(Path dir) {
        MediaProperties props = new MediaProperties(dir.toString(), "test-secret-test-secret-test-secret!!",
                Duration.ofMinutes(10), DataSize.ofMegabytes(10), "http://localhost:8080", "http://iam",
                List.of("erp-store-service"));
        LinkSigner signer = new LinkSigner(props, Clock.fixed(Instant.parse("2026-11-28T00:00:00Z"), ZoneOffset.UTC));
        return new MediaService(files, mock(MediaFolderRepository.class), mock(com.terp.erp.media.storage.StorageRouter.class),
                signer, props, mock(StoragePolicyClient.class), mock(com.terp.erp.media.storage.StorageQuotaClient.class));
    }

    @Test
    void keepsTheOrderAskedAndDropsOtherCompaniesDocumentsAndUnknownIds(@TempDir Path dir) {
        when(files.findAllById(any())).thenReturn(List.of(
                file(1L, 10L, EMediaKind.IMAGE), file(2L, 11L, EMediaKind.IMAGE),
                file(3L, 10L, EMediaKind.DOCUMENT), file(4L, 10L, EMediaKind.IMAGE)));
        var out = service(dir).internalFiles(List.of(4L, 2L, 3L, 9L, 1L, 4L), 10L);
        assertThat(out).extracting(MediaDtos.MediaFileDto::id).containsExactly("4", "1");
        assertThat(out.get(0).viewUrl()).contains("/api/v1/media/files/4/content", "sig=");
    }

    @Test
    void noIdsAnswersEmptyWithoutAQuery(@TempDir Path dir) {
        assertThat(service(dir).internalFiles(null, 10L)).isEmpty();
        assertThat(service(dir).internalFiles(Collections.emptyList(), 10L)).isEmpty();
        verify(files, never()).findAllById(any());
    }

    @Test
    void moreThanAHundredIdsIsRefused(@TempDir Path dir) {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();
        assertThatThrownBy(() -> service(dir).internalFiles(ids, 10L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_016);
    }

    private static MediaFile file(Long id, Long companyId, EMediaKind kind) {
        MediaFile m = new MediaFile();
        m.setId(id);
        m.setCompanyId(companyId);
        m.setKind(kind);
        m.setOriginalName("f" + id + ".png");
        m.setMimeType("image/png");
        return m;
    }
}
