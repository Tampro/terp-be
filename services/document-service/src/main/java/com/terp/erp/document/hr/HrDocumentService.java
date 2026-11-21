package com.terp.erp.document.hr;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.document.category.DocumentCategory;
import com.terp.erp.document.category.DocumentCategoryRepository;
import com.terp.erp.document.client.MediaClient;
import com.terp.erp.document.client.MediaClient.MediaFile;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.common.Texts;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HR Documents page (docs/analysis/06 §9.4.1). The file is a media library file of the company (DOC_008). A document
 * past its expiry date and not archived reads as EXPIRED (tab, badge); Expiring Soon = published, expiring within
 * {@value #EXPIRING_DAYS} days. Requires Acknowledgement defaults to the category's Mandatory flag.
 * TODO(assumption): 30 days for Expiring Soon, like the contracts' "near expire" — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class HrDocumentService {
    static final int EXPIRING_DAYS = 30;

    private final HrDocumentRepository documents;
    private final DocumentCategoryRepository categories;
    private final MediaClient media;
    private final Clock clock;

    public record DocumentForm(
            @Schema(example = "Code of Conduct 2026", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String title,
            @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Size(max = 2000) String description,
            @Schema(description = "Media library file id (upload it first)", requiredMode =
                    Schema.RequiredMode.REQUIRED) @NotNull Long fileId,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            @Schema(description = "Omitted = the category's Mandatory flag on create, kept on edit")
            Boolean requiresAcknowledgment) {
    }

    public record CategoryRef(String id, String name, String color, String icon) {
    }

    public record FileRef(String id, String name, String mimeType, long sizeBytes) {
    }

    /** status = effective (EXPIRED once past the expiry date unless archived); storedStatus = as set. */
    public record DocumentDto(String id, String title, CategoryRef category, String description, FileRef file,
                              LocalDate effectiveDate, LocalDate expiryDate, boolean requiresAcknowledgment,
                              HrDocument.EStatus status, HrDocument.EStatus storedStatus, Instant createdDate,
                              Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<HrDocument.EStatus, Long> byStatus) {
    }

    public record Cards(long totalDocuments, long published, BigDecimal publishedPercent, long expiringSoon,
                        long needsAcknowledgment) {
    }

    public record Option(String id, String title, boolean requiresAcknowledgment) {
    }

    public record Download(String url) {
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> search(Long requestedCompanyId, String keyword, Long categoryId, HrDocument.EStatus status,
                                    Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<HrDocument> page = documents.search(companyId, categoryId, status, Texts.kw(keyword), today(), pageable);
        Map<Long, DocumentCategory> byCategory = categoriesOf(page.getContent());
        return page.map(d -> dto(d, byCategory.get(d.getCategoryId()), null));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long categoryId) {
        Map<HrDocument.EStatus, Long> by = new EnumMap<>(HrDocument.EStatus.class);
        for (HrDocument.EStatus s : HrDocument.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : documents.countByStatus(Texts.company(requestedCompanyId), categoryId, Texts.kw(keyword),
                today())) {
            by.merge((HrDocument.EStatus) r[0], (Long) r[1], Long::sum);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** The four summary cards over every document of the company. */
    @Transactional(readOnly = true)
    public Cards cards(Long requestedCompanyId) {
        Long companyId = Texts.company(requestedCompanyId);
        StatusCounts c = statusCounts(requestedCompanyId, null, null);
        long published = c.byStatus().get(HrDocument.EStatus.PUBLISHED);
        BigDecimal percent = c.all() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(published * 100).divide(BigDecimal.valueOf(c.all()), 2, RoundingMode.HALF_UP);
        LocalDate today = today();
        return new Cards(c.all(), published, percent,
                documents.countExpiringSoon(companyId, today, today.plusDays(EXPIRING_DAYS)),
                documents.countByCompanyIdAndDeletedFalseAndRequiresAcknowledgmentTrue(companyId));
    }

    /** The Document dropdown of Assign Document. */
    @Transactional(readOnly = true)
    public List<Option> options(Long requestedCompanyId) {
        return documents.findByCompanyIdAndDeletedFalseOrderByTitleAsc(Texts.company(requestedCompanyId)).stream()
                .map(d -> new Option(d.getId().toString(), d.getTitle(), d.isRequiresAcknowledgment())).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDto get(Long id) {
        return detail(require(id));
    }

    /** Download: a signed, time-limited link of the media library. */
    @Transactional(readOnly = true)
    public Download download(Long id) {
        HrDocument d = require(id);
        MediaFile f = media.require(d.getFileId(), d.getCompanyId());
        return new Download(f.downloadUrl());
    }

    @Transactional
    public DocumentDto create(Long requestedCompanyId, DocumentForm form) {
        HrDocument d = new HrDocument();
        d.setCompanyId(Texts.company(requestedCompanyId));
        apply(d, form);
        return detail(documents.save(d));
    }

    /** Full replacement of the details; status changes through Update Status. */
    @Transactional
    public DocumentDto update(Long id, DocumentForm form) {
        HrDocument d = require(id);
        apply(d, form);
        return detail(d);
    }

    /** Update Status (any stage; EXPIRED can also be set by hand). */
    @Transactional
    public DocumentDto changeStatus(Long id, HrDocument.EStatus status) {
        HrDocument d = require(id);
        d.setStatus(status);
        return detail(d);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(HrDocument d, DocumentForm f) {
        if (f.effectiveDate() != null && f.expiryDate() != null && f.expiryDate().isBefore(f.effectiveDate())) {
            throw ApiException.badRequest(EDocumentError.DOC_010);
        }
        DocumentCategory c = categories.findByIdAndDeletedFalse(f.categoryId())
                .filter(x -> x.getCompanyId().equals(d.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EDocumentError.DOC_002));
        if (c.getStatus() == EStatus.INACTIVE && !c.getId().equals(d.getCategoryId())) {
            throw ApiException.badRequest(EDocumentError.DOC_015, c.getName());
        }
        if (!Objects.equals(f.fileId(), d.getFileId())) {
            media.require(f.fileId(), d.getCompanyId());
        }
        if (f.requiresAcknowledgment() != null) {
            d.setRequiresAcknowledgment(f.requiresAcknowledgment());
        } else if (d.getId() == null) {
            d.setRequiresAcknowledgment(c.isMandatory());
        }
        d.setTitle(f.title().trim());
        d.setCategoryId(c.getId());
        d.setDescription(Texts.blankToNull(f.description()));
        d.setFileId(f.fileId());
        d.setEffectiveDate(f.effectiveDate());
        d.setExpiryDate(f.expiryDate());
    }

    HrDocument.EStatus effective(HrDocument d) {
        return d.getExpiryDate() != null && d.getExpiryDate().isBefore(today())
                && d.getStatus() != HrDocument.EStatus.ARCHIVED ? HrDocument.EStatus.EXPIRED : d.getStatus();
    }

    private DocumentDto detail(HrDocument d) {
        MediaFile f = media.find(d.getFileId());
        return dto(d, categories.findById(d.getCategoryId()).orElse(null),
                f == null ? null : new FileRef(f.id(), f.name(), f.mimeType(), f.sizeBytes()));
    }

    private Map<Long, DocumentCategory> categoriesOf(List<HrDocument> list) {
        return list.isEmpty() ? Map.of() : categories.findAllById(list.stream().map(HrDocument::getCategoryId)
                .distinct().toList()).stream().collect(Collectors.toMap(DocumentCategory::getId, Function.identity()));
    }

    private DocumentDto dto(HrDocument d, DocumentCategory c, FileRef file) {
        return new DocumentDto(d.getId().toString(), d.getTitle(),
                c == null ? null : new CategoryRef(c.getId().toString(), c.getName(), c.getColor(), c.getIcon()),
                d.getDescription(), file, d.getEffectiveDate(), d.getExpiryDate(), d.isRequiresAcknowledgment(),
                effective(d), d.getStatus(), d.getCreatedDate(), d.getUpdatedDate());
    }

    private LocalDate today() {
        return Texts.today(clock);
    }

    public HrDocument require(Long id) {
        HrDocument d = documents.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_009));
        TenantGuard.assertSameCompany(d.getCompanyId(), EDocumentError.DOC_009);
        return d;
    }
}
