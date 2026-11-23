package com.terp.erp.crm.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.client.MediaClient;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.OwnScope;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Customer contracts (ERP manual 7.5) with their renewals, comments, notes and attachments. Every sub-resource is
 * reached through its contract, so the contract's company check covers it. Electronic signatures are out of scope.
 */
@Service
@RequiredArgsConstructor
public class ContractService {
    static final String VIEW = "CRM_CONTRACT_VIEW";
    static final String MANAGE = "CRM_CONTRACT_MANAGE";
    private final ContractRepository contracts;
    private final ContractTypeService typeService;
    private final ContractSettingService settings;
    private final ContractRenewalRepository renewals;
    private final ContractCommentRepository comments;
    private final ContractNoteRepository notes;
    private final ContractAttachmentRepository attachments;
    private final AccountingClient accounting;
    private final MediaClient media;
    private final Clock clock;

    // ---- forms -------------------------------------------------------------------------------------------------

    /** Create and full replacement of a contract; the number is never part of it. */
    public record ContractForm(
            @Schema(description = "Super admin only: the company to act on (create)", example = "10") Long companyId,
            @Schema(description = "Subject", example = "Test annual support agreement",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "Accounting customer id", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long customerId,
            @Schema(description = "Contract type id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long contractTypeId,
            @Schema(description = "Contract value", example = "12000.00")
            @DecimalMin("0") BigDecimal value,
            @Schema(description = "Start date", example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate startDate,
            @Schema(description = "End date, on or after the start", example = "2027-11-30",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate endDate,
            @Schema(description = "Status; omit = PENDING on create, unchanged on edit", example = "PENDING")
            Contract.EStatus status,
            @Schema(description = "Agreement text", example = "<p>Test agreement text.</p>")
            @Size(max = 1_000_000) String description) {
    }

    public record StatusForm(
            @Schema(description = "New status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Contract.EStatus status) {
    }

    public record RenewalForm(
            @Schema(description = "Start date", example = "2027-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate startDate,
            @Schema(description = "End date, on or after the start", example = "2028-11-30",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate endDate,
            @Schema(description = "Renewal value", example = "12500.00", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") BigDecimal value,
            @Schema(description = "Status", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Contract.EStatus status,
            @Schema(description = "Notes", example = "Test renewal with 5% increase") @Size(max = 5000) String notes) {
    }

    public record CommentForm(
            @Schema(description = "Comment", example = "Test comment", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 5000) String message) {
    }

    public record NoteForm(
            @Schema(description = "Note", example = "Test note", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 5000) String note) {
    }

    public record AttachmentForm(
            @Schema(description = "Media library file id of the contract's company", example = "77",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long mediaFileId) {
    }

    // ---- views -------------------------------------------------------------------------------------------------

    public record ContractRow(String id, String number, String subject, String customerId, String customerName,
                              String contractTypeId, BigDecimal value, LocalDate startDate, LocalDate endDate,
                              Contract.EStatus status, int progressPercent, long daysLeft, Instant createdDate) {
    }

    public record RenewalDto(String id, LocalDate startDate, LocalDate endDate, BigDecimal value,
                             Contract.EStatus status, String notes, String createdBy, Instant createdDate) {
        static RenewalDto of(ContractRenewal r) {
            return new RenewalDto(String.valueOf(r.getId()), r.getStartDate(), r.getEndDate(), r.getValue(),
                    r.getStatus(), r.getNotes(), r.getCreatedBy(), r.getCreatedDate());
        }
    }

    public record CommentDto(String id, String message, String authorName, String createdBy, Instant createdDate) {
        static CommentDto of(ContractComment c) {
            return new CommentDto(String.valueOf(c.getId()), c.getMessage(), c.getAuthorName(), c.getCreatedBy(),
                    c.getCreatedDate());
        }
    }

    public record NoteDto(String id, String note, String createdBy, Instant createdDate, Instant updatedDate) {
        static NoteDto of(ContractNote n) {
            return new NoteDto(String.valueOf(n.getId()), n.getNote(), n.getCreatedBy(), n.getCreatedDate(),
                    n.getUpdatedDate());
        }
    }

    public record AttachmentDto(String id, String mediaFileId, String fileName, String createdBy, Instant createdDate) {
        static AttachmentDto of(ContractAttachment a) {
            return new AttachmentDto(String.valueOf(a.getId()), a.getMediaFileId().toString(), a.getFileName(),
                    a.getCreatedBy(), a.getCreatedDate());
        }
    }

    public record ContractDetail(ContractRow contract, String contractTypeName, String description,
                                 List<RenewalDto> renewals, List<CommentDto> comments, List<NoteDto> notes,
                                 List<AttachmentDto> attachments) {
    }

    /** The five summary cards. */
    public record StatusCounts(long total, long active, long pending, long closed, long declined) {
    }

    // ---- contracts ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ContractRow> search(Long companyId, Long customerId, Long contractTypeId, Contract.EStatus status,
                                    LocalDate startFrom, LocalDate startTo, LocalDate endFrom, LocalDate endTo,
                                    String keyword, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EContractError.CTR_001);
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        LocalDate today = LocalDate.now(clock);
        Page<Contract> page = ownView()
                ? contracts.searchOwn(company, customerId, contractTypeId, status, startFrom, startTo, endFrom, endTo,
                kw, OwnScope.sub(), pageable)
                : contracts.search(company, customerId, contractTypeId, status, startFrom, startTo, endFrom, endTo, kw,
                pageable);
        return page.map(c -> row(c, today));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long companyId) {
        Long company = TenantGuard.resolveCompany(companyId, EContractError.CTR_001);
        Map<Contract.EStatus, Long> m = new EnumMap<>(Contract.EStatus.class);
        for (Object[] r : ownView() ? contracts.countByStatusOwn(company, OwnScope.sub())
                : contracts.countByStatus(company)) {
            m.put((Contract.EStatus) r[0], ((Number) r[1]).longValue());
        }
        long total = m.values().stream().mapToLong(Long::longValue).sum();
        return new StatusCounts(total, m.getOrDefault(Contract.EStatus.ACTIVE, 0L),
                m.getOrDefault(Contract.EStatus.PENDING, 0L), m.getOrDefault(Contract.EStatus.CLOSED, 0L),
                m.getOrDefault(Contract.EStatus.DECLINED, 0L));
    }

    @Transactional(readOnly = true)
    public ContractDetail get(Long id) {
        Contract c = requireInCompany(id);
        if (ownView()) {
            OwnScope.assertMine(mine(c), EContractError.CTR_002);
        }
        return detail(c);
    }

    @Transactional
    public ContractDetail create(ContractForm f) {
        Long company = TenantGuard.resolveCompany(f.companyId(), EContractError.CTR_001);
        Contract c = new Contract();
        c.setCompanyId(company);
        apply(c, f, null);
        c.setNumber(settings.take(company));
        return detail(contracts.save(c));
    }

    /** Full replacement; the number never changes. */
    @Transactional
    public ContractDetail update(Long id, ContractForm f) {
        Contract c = require(id);
        apply(c, f, c.getContractTypeId());
        return detail(c);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    @Transactional
    public ContractDetail changeStatus(Long id, StatusForm f) {
        Contract c = require(id);
        // TODO(assumption): the manual names no transition rules, so any status may follow any other.
        c.setStatus(f.status());
        return detail(c);
    }

    /** A new PENDING contract with a new number and the same fields; sub-resources are not copied. */
    @Transactional
    public ContractDetail duplicate(Long id) {
        Contract src = require(id);
        Contract c = new Contract();
        c.setCompanyId(src.getCompanyId());
        // TODO(assumption): the subject is copied as is (no " (copy)" suffix); renewals, comments, notes and
        //  attachments stay with the original.
        c.setSubject(src.getSubject());
        c.setCustomerId(src.getCustomerId());
        c.setCustomerName(src.getCustomerName());
        c.setContractTypeId(src.getContractTypeId());
        c.setValue(src.getValue());
        c.setStartDate(src.getStartDate());
        c.setEndDate(src.getEndDate());
        c.setDescription(src.getDescription());
        c.setStatus(Contract.EStatus.PENDING);
        c.setNumber(settings.take(src.getCompanyId()));
        return detail(contracts.save(c));
    }

    // ---- renewals ----------------------------------------------------------------------------------------------

    @Transactional
    public RenewalDto addRenewal(Long contractId, RenewalForm f) {
        Contract c = require(contractId);
        ContractRenewal r = new ContractRenewal();
        r.setCompanyId(c.getCompanyId());
        r.setContractId(c.getId());
        applyRenewal(r, f);
        return RenewalDto.of(renewals.save(r));
    }

    /** Full replacement. */
    @Transactional
    public RenewalDto updateRenewal(Long contractId, Long renewalId, RenewalForm f) {
        ContractRenewal r = requireRenewal(contractId, renewalId);
        applyRenewal(r, f);
        return RenewalDto.of(r);
    }

    @Transactional
    public void deleteRenewal(Long contractId, Long renewalId) {
        requireRenewal(contractId, renewalId).setDeleted(true);
    }

    // ---- comments ----------------------------------------------------------------------------------------------

    @Transactional
    public CommentDto addComment(Long contractId, CommentForm f) {
        Contract c = require(contractId);
        ContractComment m = new ContractComment();
        m.setCompanyId(c.getCompanyId());
        m.setContractId(c.getId());
        m.setMessage(f.message().trim());
        m.setAuthorName(CurrentUser.find().map(ErpPrincipal::username).orElse(null));
        return CommentDto.of(comments.save(m));
    }

    @Transactional
    public void deleteComment(Long contractId, Long commentId) {
        Contract c = require(contractId);
        // TODO(assumption): anyone with CRM_CONTRACT_MANAGE may delete any comment, not only its author.
        comments.findByIdAndContractIdAndDeletedFalse(commentId, c.getId())
                .orElseThrow(() -> ApiException.notFound(EContractError.CTR_009)).setDeleted(true);
    }

    // ---- notes -------------------------------------------------------------------------------------------------

    @Transactional
    public NoteDto addNote(Long contractId, NoteForm f) {
        Contract c = require(contractId);
        ContractNote n = new ContractNote();
        n.setCompanyId(c.getCompanyId());
        n.setContractId(c.getId());
        n.setNote(f.note().trim());
        return NoteDto.of(notes.save(n));
    }

    /** Full replacement. */
    @Transactional
    public NoteDto updateNote(Long contractId, Long noteId, NoteForm f) {
        ContractNote n = requireNote(contractId, noteId);
        n.setNote(f.note().trim());
        return NoteDto.of(n);
    }

    @Transactional
    public void deleteNote(Long contractId, Long noteId) {
        requireNote(contractId, noteId).setDeleted(true);
    }

    // ---- attachments -------------------------------------------------------------------------------------------

    @Transactional
    public AttachmentDto addAttachment(Long contractId, AttachmentForm f) {
        Contract c = require(contractId);
        MediaClient.MediaFile file = media.requireCompanyFile(f.mediaFileId(), c.getCompanyId(), EContractError.CTR_012);
        ContractAttachment a = new ContractAttachment();
        a.setCompanyId(c.getCompanyId());
        a.setContractId(c.getId());
        a.setMediaFileId(f.mediaFileId());
        a.setFileName(file.name() == null || file.name().isBlank() ? "file-" + f.mediaFileId() : file.name());
        return AttachmentDto.of(attachments.save(a));
    }

    @Transactional
    public void deleteAttachment(Long contractId, Long attachmentId) {
        Contract c = require(contractId);
        attachments.findByIdAndContractIdAndDeletedFalse(attachmentId, c.getId())
                .orElseThrow(() -> ApiException.notFound(EContractError.CTR_011)).setDeleted(true);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    /**
     * Elapsed share of start to end, clamped 0..100.
     * TODO(assumption): a one-day contract (start = end) counts 100% from its start day.
     */
    static int progressPercent(LocalDate start, LocalDate end, LocalDate today) {
        long total = ChronoUnit.DAYS.between(start, end);
        if (total <= 0) {
            return today.isBefore(start) ? 0 : 100;
        }
        long elapsed = ChronoUnit.DAYS.between(start, today);
        return (int) Math.max(0, Math.min(100, elapsed * 100 / total));
    }

    /** Days until the end date. TODO(assumption): 0 once the end has passed, never negative. */
    static long daysLeft(LocalDate end, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(today, end));
    }

    private void apply(Contract c, ContractForm f, Long currentTypeId) {
        checkDates(f.startDate(), f.endDate());
        ContractType type = typeService.require(f.contractTypeId());
        // A super admin acting on company A must not pick a type of company B.
        if (!type.getCompanyId().equals(c.getCompanyId())) {
            throw ApiException.notFound(EContractError.CTR_003);
        }
        // An inactive type stays valid on contracts that already have it.
        if (!type.isActive() && !type.getId().equals(currentTypeId)) {
            throw ApiException.badRequest(EContractError.CTR_007);
        }
        if (!f.customerId().equals(c.getCustomerId()) || c.getCustomerName() == null) {
            AccountingClient.Customer cu = accounting.customer(f.customerId());
            c.setCustomerName(cu.companyName() == null || cu.companyName().isBlank() ? cu.contactPerson()
                    : cu.companyName());
        }
        c.setCustomerId(f.customerId());
        c.setSubject(f.subject().trim());
        c.setContractTypeId(type.getId());
        c.setValue(f.value());
        c.setStartDate(f.startDate());
        c.setEndDate(f.endDate());
        if (f.status() != null) {
            c.setStatus(f.status());
        }
        c.setDescription(f.description());
    }

    private void applyRenewal(ContractRenewal r, RenewalForm f) {
        checkDates(f.startDate(), f.endDate());
        r.setStartDate(f.startDate());
        r.setEndDate(f.endDate());
        r.setValue(f.value());
        r.setStatus(f.status());
        r.setNotes(f.notes() == null || f.notes().isBlank() ? null : f.notes().trim());
    }

    private static void checkDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest(EContractError.CTR_006);
        }
    }

    private ContractRow row(Contract c, LocalDate today) {
        return new ContractRow(String.valueOf(c.getId()), c.getNumber(), c.getSubject(), c.getCustomerId().toString(),
                c.getCustomerName(), c.getContractTypeId().toString(), c.getValue(), c.getStartDate(), c.getEndDate(),
                c.getStatus(), progressPercent(c.getStartDate(), c.getEndDate(), today),
                daysLeft(c.getEndDate(), today), c.getCreatedDate());
    }

    private ContractDetail detail(Contract c) {
        Long id = c.getId();
        return new ContractDetail(row(c, LocalDate.now(clock)), typeService.nameOf(c.getContractTypeId()),
                c.getDescription(),
                renewals.findByContractIdAndDeletedFalseOrderByIdAsc(id).stream().map(RenewalDto::of).toList(),
                comments.findByContractIdAndDeletedFalseOrderByIdAsc(id).stream().map(CommentDto::of).toList(),
                notes.findByContractIdAndDeletedFalseOrderByIdAsc(id).stream().map(NoteDto::of).toList(),
                attachments.findByContractIdAndDeletedFalseOrderByIdAsc(id).stream().map(AttachmentDto::of).toList());
    }

    /** Every caller but {@link #get} writes: with CRM_CONTRACT_MANAGE_OWN only, another user's contract is 404. */
    private Contract require(Long id) {
        Contract c = requireInCompany(id);
        if (!CurrentUser.get().has(MANAGE)) {
            OwnScope.assertMine(mine(c), EContractError.CTR_002);
        }
        return c;
    }

    private Contract requireInCompany(Long id) {
        Contract c = contracts.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EContractError.CTR_002));
        TenantGuard.assertSameCompany(c.getCompanyId(), EContractError.CTR_002);
        return c;
    }

    /**
     * TODO(assumption): a contract has no assignee field, so "own" = I created it (created_by = my Keycloak sub).
     * A full CRM_CONTRACT_MANAGE holder also sees every contract.
     */
    private static boolean ownView() {
        ErpPrincipal p = CurrentUser.get();
        return !(p.has(VIEW) || p.has(MANAGE));
    }

    private static boolean mine(Contract c) {
        return java.util.Objects.equals(c.getCreatedBy(), OwnScope.sub());
    }

    private ContractRenewal requireRenewal(Long contractId, Long renewalId) {
        Contract c = require(contractId);
        return renewals.findByIdAndContractIdAndDeletedFalse(renewalId, c.getId())
                .orElseThrow(() -> ApiException.notFound(EContractError.CTR_008));
    }

    private ContractNote requireNote(Long contractId, Long noteId) {
        Contract c = require(contractId);
        return notes.findByIdAndContractIdAndDeletedFalse(noteId, c.getId())
                .orElseThrow(() -> ApiException.notFound(EContractError.CTR_010));
    }
}
