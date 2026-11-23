package com.terp.erp.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EMediaKind;
import com.terp.erp.media.constant.EMediaSort;
import com.terp.erp.media.model.MediaFile;
import com.terp.erp.media.model.MediaFolder;
import com.terp.erp.media.repository.MediaFileRepository;
import com.terp.erp.media.repository.MediaFolderRepository;
import com.terp.erp.media.service.MediaDtos.FolderDto;
import com.terp.erp.media.service.MediaDtos.MediaFileDto;
import com.terp.erp.media.service.MediaDtos.StatsDto;
import com.terp.erp.media.constant.EStorageDriver;
import com.terp.erp.media.storage.FileTypePolicy;
import com.terp.erp.media.storage.LinkSigner;
import com.terp.erp.media.storage.StoragePolicyClient;
import com.terp.erp.media.storage.StorageQuotaClient;
import com.terp.erp.media.storage.StorageRouter;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {
    private final MediaFileRepository files;
    private final MediaFolderRepository folders;
    private final StorageRouter storage;
    private final LinkSigner links;
    private final MediaProperties props;
    private final StoragePolicyClient storagePolicy;
    private final StorageQuotaClient storageQuota;

    // ------------------------------------------------------------------ folders

    @Transactional(readOnly = true)
    public List<FolderDto> folders(Long requestedCompanyId) {
        MediaScope s = MediaScope.forRead(requestedCompanyId);
        Map<Long, Long> counts = files.countByFolder(s.all(), s.companyId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return folders.findVisible(s.all(), s.companyId()).stream()
                .map(f -> FolderDto.of(f, counts.getOrDefault(f.getId(), 0L))).toList();
    }

    @Transactional
    public FolderDto createFolder(Long requestedCompanyId, String name) {
        MediaScope s = MediaScope.forWrite(requestedCompanyId);
        String trimmed = name.trim();
        if (folders.existsByOwnerAndName(s.companyId(), trimmed)) {
            throw ApiException.badRequest(EMediaError.MEDIA_003);
        }
        MediaFolder f = new MediaFolder();
        f.setCompanyId(s.companyId());
        f.setName(trimmed);
        return FolderDto.of(folders.save(f), 0);
    }

    @Transactional
    public FolderDto renameFolder(Long id, String name) {
        MediaFolder f = requireFolder(id);
        String trimmed = name.trim();
        if (!f.getName().equalsIgnoreCase(trimmed) && folders.existsByOwnerAndName(f.getCompanyId(), trimmed)) {
            throw ApiException.badRequest(EMediaError.MEDIA_003);
        }
        f.setName(trimmed);
        return FolderDto.of(f, 0);
    }

    /** The folder goes; its files stay, without a folder. */
    @Transactional
    public void deleteFolder(Long id) {
        MediaFolder f = requireFolder(id);
        files.detachFromFolder(f.getId());
        f.setDeleted(true);
    }

    // ------------------------------------------------------------------ files

    @Transactional(readOnly = true)
    public Page<MediaFileDto> search(Long requestedCompanyId, Long folderId, EMediaKind kind, String keyword,
                                     EMediaSort sort, int page, int size) {
        MediaScope s = MediaScope.forRead(requestedCompanyId);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sortOf(sort));
        return files.search(s.all(), s.companyId(), folderId, kind, keyword == null ? "" : keyword.trim(), pageable)
                .map(this::dto);
    }

    @Transactional(readOnly = true)
    public StatsDto stats(Long requestedCompanyId) {
        MediaScope s = MediaScope.forRead(requestedCompanyId);
        Object[] row = files.stats(s.all(), s.companyId()).get(0);
        Long limit = s.all() || s.companyId() == null ? null : storageQuota.limitBytes(s.companyId());
        return new StatsDto(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(), limit);
    }

    @Transactional(readOnly = true)
    public MediaFileDto get(Long id) {
        return dto(requireFile(id));
    }

    /**
     * Each file is checked (size, extension allow-list, signature bytes) before anything is written; the batch stops
     * at the first refused file and nothing of the batch is kept. A stored file whose row fails is removed again.
     */
    @Transactional
    public List<MediaFileDto> upload(Long requestedCompanyId, Long folderId, List<MultipartFile> uploads) {
        if (uploads == null || uploads.isEmpty() || uploads.stream().allMatch(MultipartFile::isEmpty)) {
            throw ApiException.badRequest(EMediaError.MEDIA_008);
        }
        MediaScope s = MediaScope.forWrite(requestedCompanyId);
        if (folderId != null) {
            assertFolderOf(folderId, s.companyId());
        }
        record Checked(MultipartFile file, String name, FileTypePolicy.FileType type) {
        }
        // Limits of the super admin's Storage Settings, on top of what this service can verify.
        StoragePolicyClient.Policy policy = storagePolicy.current();
        List<Checked> checked = new ArrayList<>();
        for (MultipartFile u : uploads) {
            if (u.isEmpty()) {
                continue;
            }
            if (u.getSize() > policy.maxBytes()) {
                throw ApiException.badRequest(EMediaError.MEDIA_005, (policy.maxBytes() / 1024) + " KB");
            }
            String name = cleanName(u.getOriginalFilename());
            FileTypePolicy.FileType type = FileTypePolicy.check(name, head(u));
            if (policy.allowedTypes() != null && !policy.allowedTypes().contains(type.extension())) {
                throw ApiException.badRequest(EMediaError.MEDIA_004, type.extension());
            }
            checked.add(new Checked(u, name, type));
        }
        assertQuota(s, checked.stream().mapToLong(c -> c.file().getSize()).sum());
        // Refused here (MEDIA_012/013/014) before any byte is written: never a silent fallback to local.
        EStorageDriver driver = storage.writeDriver(policy.driver());
        var target = storage.of(driver);
        List<String> stored = new ArrayList<>();
        try {
            List<MediaFileDto> result = new ArrayList<>();
            for (Checked c : checked) {
                String key = s.storageOwner() + "/" + UUID.randomUUID() + "." + c.type().extension();
                try (InputStream in = c.file().getInputStream()) {
                    target.put(key, in, c.file().getSize(), c.type().mimeType());
                }
                stored.add(key);
                MediaFile m = new MediaFile();
                m.setCompanyId(s.companyId());
                m.setFolderId(folderId);
                m.setOriginalName(c.name());
                m.setDisplayName(c.name());
                m.setMimeType(c.type().mimeType());
                m.setExtension(c.type().extension());
                m.setKind(c.type().kind());
                m.setSizeBytes(c.file().getSize());
                m.setStorageKey(key);
                m.setStorageDriver(driver);
                result.add(dto(files.save(m)));
            }
            return result;
        } catch (IOException | RuntimeException e) {
            stored.forEach(target::delete);
            if (e instanceof ApiException api) {
                throw api;
            }
            log.error("Upload failed: {}", e.getClass().getSimpleName());
            throw ApiException.badRequest(EMediaError.MEDIA_009);
        }
    }

    /**
     * The company's plan storage limit (user manual §4.1): used + incoming must fit. Platform files (super admin, no
     * company) have no limit. TODO(assumption): concurrent uploads are not serialised, so two batches at once can
     * overshoot the limit by at most one batch.
     */
    private void assertQuota(MediaScope s, long incoming) {
        if (s.companyId() == null) {
            return;
        }
        Long limit = storageQuota.limitBytes(s.companyId());
        if (limit == null) {
            return;
        }
        long used = ((Number) files.stats(false, s.companyId()).get(0)[1]).longValue();
        if (used + incoming > limit) {
            throw ApiException.badRequest(EMediaError.MEDIA_010, gb(limit));
        }
    }

    private static String gb(long bytes) {
        return java.math.BigDecimal.valueOf(bytes).divide(java.math.BigDecimal.valueOf(1024L * 1024 * 1024), 2,
                java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Full replacement of display name and folder. */
    @Transactional
    public MediaFileDto update(Long id, String displayName, Long folderId) {
        MediaFile m = requireFile(id);
        if (folderId != null) {
            assertFolderOf(folderId, m.getCompanyId());
        }
        m.setDisplayName(displayName.trim());
        m.setFolderId(folderId);
        return dto(m);
    }

    /** Permanent (docs/analysis/06: "permanently removes"): row and bytes. Links already handed out stop working. */
    @Transactional
    public void delete(Long id) {
        MediaFile m = requireFile(id);
        files.delete(m);
        storage.of(m.getStorageDriver()).delete(m.getStorageKey());
    }

    // ------------------------------------------------------------------ internal (service account)

    /**
     * Signed links of a file for another ERP service that has already authorized its own caller (e.g. an employee
     * reading an announcement). The file must belong to the named company: a service cannot reach across companies
     * by guessing ids.
     */
    @Transactional(readOnly = true)
    public MediaFileDto internalFile(Long id, Long companyId) {
        MediaFile m = files.findById(id)
                .filter(f -> Objects.equals(f.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.notFound(EMediaError.MEDIA_001));
        return dto(m);
    }

    // ------------------------------------------------------------------ signed content

    /** The caller streams and closes {@code body}. */
    public record Content(InputStream body, long size, String mimeType, String downloadName, boolean download) {
    }

    /** No caller identity here: the valid signature is the authorization (it was issued after the checks). */
    @Transactional(readOnly = true)
    public Content content(Long id, long exp, int dl, String sig) {
        if (!links.verify(id, exp, dl, sig)) {
            throw ApiException.forbidden(EMediaError.MEDIA_007);
        }
        MediaFile m = files.findById(id).orElseThrow(() -> ApiException.notFound(EMediaError.MEDIA_001));
        // The row's driver, not the current one: files written before a switch are read from where they are.
        InputStream body = storage.of(m.getStorageDriver()).get(m.getStorageKey());
        return new Content(body, m.getSizeBytes(), m.getMimeType(), m.getOriginalName(), dl == 1);
    }

    // ------------------------------------------------------------------ storage test (super admin)

    /**
     * Probes the S3 storage of this server's environment: bucket reachable, a tiny object written and removed.
     * Allowed before switching the driver, so the super admin can check the setup first.
     */
    public MediaDtos.StorageTestDto testStorage(EStorageDriver driver) {
        EStorageDriver d = driver == null || driver == EStorageDriver.LOCAL ? EStorageDriver.S3 : driver;
        String failed = storage.s3().probe(d);
        if (failed != null) {
            throw ApiException.badRequest(EMediaError.MEDIA_015, failed);
        }
        return new MediaDtos.StorageTestDto(d, storage.s3().endpointHost(), storage.s3().bucket(), true);
    }

    // ------------------------------------------------------------------ helpers

    private MediaFileDto dto(MediaFile m) {
        return MediaFileDto.of(m, links.link(m.getId(), false), links.link(m.getId(), true));
    }

    private MediaFile requireFile(Long id) {
        MediaFile m = files.findById(id).orElseThrow(() -> ApiException.notFound(EMediaError.MEDIA_001));
        MediaScope.assertOwns(m.getCompanyId(), EMediaError.MEDIA_001);
        return m;
    }

    private MediaFolder requireFolder(Long id) {
        MediaFolder f = folders.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EMediaError.MEDIA_002));
        MediaScope.assertOwns(f.getCompanyId(), EMediaError.MEDIA_002);
        return f;
    }

    /** A file may only go into a folder of its own owner. */
    private void assertFolderOf(Long folderId, Long ownerCompanyId) {
        MediaFolder f = requireFolder(folderId);
        if (!Objects.equals(f.getCompanyId(), ownerCompanyId)) {
            throw ApiException.notFound(EMediaError.MEDIA_002);
        }
    }

    private static byte[] head(MultipartFile u) {
        try (InputStream in = u.getInputStream()) {
            return in.readNBytes(16);
        } catch (IOException e) {
            throw ApiException.badRequest(EMediaError.MEDIA_009);
        }
    }

    /** Keeps only the last path segment of a client file name ("C:\\x\\a.png" or "../a.png" → "a.png"). */
    static String cleanName(String original) {
        String n = original == null ? "" : original;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1).strip();
        n = n.replaceAll("[\\p{Cntrl}]", "");
        if (n.length() > 255) {
            n = n.substring(n.length() - 255);
        }
        return n.isEmpty() ? "file" : n;
    }

    private static Sort sortOf(EMediaSort sort) {
        return switch (sort == null ? EMediaSort.DATE_DESC : sort) {
            case NAME_ASC -> Sort.by("displayName").ascending();
            case NAME_DESC -> Sort.by("displayName").descending();
            case DATE_ASC -> Sort.by("id").ascending();
            case DATE_DESC -> Sort.by("id").descending();
            case SIZE_DESC -> Sort.by("sizeBytes").descending();
            case SIZE_ASC -> Sort.by("sizeBytes").ascending();
            case TYPE -> Sort.by("extension").ascending().and(Sort.by("displayName"));
        };
    }
}
