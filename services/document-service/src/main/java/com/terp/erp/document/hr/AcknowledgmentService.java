package com.terp.erp.document.hr;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.document.client.EmployeeClient;
import com.terp.erp.document.client.EmployeeClient.Employee;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.Texts;
import com.terp.erp.document.hr.Acknowledgment.EStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Acknowledgments page (docs/analysis/06 §9.4.3; PO 2026-11-21). Assign Document gives an employee of the company
 * (DOC_013) one open acknowledgment per document (DOC_012); archived documents are not assigned (DOC_016). Overdue =
 * pending past its due date (own tab). A DOCUMENT_MANAGE user acknowledges on the employee's behalf, or the employee
 * does it themself (My Acknowledgments, matched by the IAM user id copied at assignment).
 */
@Service
@RequiredArgsConstructor
public class AcknowledgmentService {
    private final AcknowledgmentRepository acks;
    private final HrDocumentRepository documents;
    private final HrDocumentService documentService;
    private final EmployeeClient employees;
    private final Clock clock;

    public enum ETab { PENDING, ACKNOWLEDGED, OVERDUE, EXEMPTED }

    public record AssignForm(
            @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long documentId,
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            LocalDate dueDate,
            @Size(max = 1000) String note) {
    }

    /** Edit: due date, note, and Pending ↔ Exempted. */
    public record EditForm(LocalDate dueDate, @Size(max = 1000) String note,
                           @Schema(description = "PENDING or EXEMPTED; omitted = kept") EStatus status) {
    }

    public record AcknowledgeForm(@Size(max = 1000) String note) {
    }

    public record DocumentRef(String id, String title, String description) {
    }

    public record Person(String id, String fullName, String email) {
    }

    /** tab = the status tag: OVERDUE when pending past the due date. */
    public record AckDto(String id, DocumentRef document, Person employee, LocalDate dueDate, String note,
                         EStatus status, ETab tab, boolean overdue, String assignedByName, Instant acknowledgedAt,
                         String acknowledgeNote, Instant createdDate) {
    }

    public record TabCounts(long all, long pending, long acknowledged, long overdue, long exempted) {
    }

    @Transactional(readOnly = true)
    public Page<AckDto> search(Long requestedCompanyId, String keyword, Long documentId, Long employeeId, ETab tab,
                               Pageable pageable) {
        // Overdue = pending past due: its own tab, left out of Pending.
        EStatus stage = tab == null || tab == ETab.OVERDUE ? null : EStatus.valueOf(tab.name());
        Boolean overdue = tab == ETab.OVERDUE ? Boolean.TRUE : tab == ETab.PENDING ? Boolean.FALSE : null;
        Page<Acknowledgment> page = acks.search(Texts.company(requestedCompanyId), documentId, employeeId,
                Texts.kw(keyword), stage, overdue, today(), pageable);
        return dtos(page);
    }

    @Transactional(readOnly = true)
    public TabCounts tabCounts(Long requestedCompanyId, String keyword, Long documentId, Long employeeId) {
        long pending = 0;
        long acknowledged = 0;
        long overdue = 0;
        long exempted = 0;
        for (Object[] r : acks.countTabs(Texts.company(requestedCompanyId), documentId, employeeId, Texts.kw(keyword),
                today())) {
            long n = (Long) r[2];
            if (Boolean.TRUE.equals(r[1])) {
                overdue += n;
            } else if (r[0] == EStatus.PENDING) {
                pending += n;
            } else if (r[0] == EStatus.ACKNOWLEDGED) {
                acknowledged += n;
            } else {
                exempted += n;
            }
        }
        return new TabCounts(pending + acknowledged + overdue + exempted, pending, acknowledged, overdue, exempted);
    }

    @Transactional(readOnly = true)
    public AckDto get(Long id) {
        return dtos(List.of(require(id))).get(0);
    }

    /** Assign Document. */
    @Transactional
    public AckDto assign(Long requestedCompanyId, AssignForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        HrDocument d = documents.findByIdAndDeletedFalse(f.documentId())
                .filter(x -> x.getCompanyId().equals(companyId))
                .orElseThrow(() -> ApiException.badRequest(EDocumentError.DOC_009));
        if (d.getStatus() == HrDocument.EStatus.ARCHIVED) {
            throw ApiException.badRequest(EDocumentError.DOC_016);
        }
        if (acks.existsByDocumentIdAndEmployeeIdAndStatusAndDeletedFalseAndIdNot(d.getId(), f.employeeId(),
                EStatus.PENDING, -1L)) {
            throw ApiException.badRequest(EDocumentError.DOC_012);
        }
        // The detail call carries the login account; employee-service answers EMP_001 for another company's employee.
        Employee e = employees.employee(f.employeeId());
        if (e == null || !String.valueOf(companyId).equals(e.companyId())) {
            throw ApiException.badRequest(EDocumentError.DOC_013);
        }
        Acknowledgment a = new Acknowledgment();
        a.setCompanyId(companyId);
        a.setDocumentId(d.getId());
        a.setEmployeeId(f.employeeId());
        a.setEmployeeUserId(e.userId() == null ? null : Long.valueOf(e.userId()));
        a.setEmployeeName(e.fullName());
        a.setEmployeeEmail(e.email());
        a.setDueDate(f.dueDate());
        a.setNote(Texts.blankToNull(f.note()));
        ErpPrincipal me = CurrentUser.find().orElse(null);
        a.setAssignedBy(me == null ? null : me.keycloakId());
        a.setAssignedByName(me == null ? null : me.username());
        return dtos(List.of(acks.save(a))).get(0);
    }

    /** Edit: due date, note, Pending ↔ Exempted (an acknowledged one stays acknowledged). */
    @Transactional
    public AckDto edit(Long id, EditForm f) {
        Acknowledgment a = require(id);
        a.setDueDate(f.dueDate());
        a.setNote(Texts.blankToNull(f.note()));
        if (f.status() != null && a.getStatus() != EStatus.ACKNOWLEDGED && f.status() != EStatus.ACKNOWLEDGED) {
            a.setStatus(f.status());
        }
        return dtos(List.of(a)).get(0);
    }

    /** Acknowledge on the employee's behalf (DOCUMENT_MANAGE). */
    @Transactional
    public AckDto acknowledge(Long id, AcknowledgeForm f) {
        return dtos(List.of(acknowledge(require(id), f))).get(0);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    // ------------------------------------------------------------------ the employee themself

    @Transactional(readOnly = true)
    public Page<AckDto> mine(Pageable pageable) {
        ErpPrincipal me = self();
        return dtos(acks.mine(me.companyId(), me.userId(), pageable));
    }

    @Transactional
    public AckDto acknowledgeMine(Long id, AcknowledgeForm f) {
        return dtos(List.of(acknowledge(own(id), f))).get(0);
    }

    /**
     * Download my document (FR-ERP-ACK-001: downloadable once acknowledged): only after I acknowledged it (DOC_026) and
     * only an approved or published document (DOC_025).
     * TODO(assumption): an EXEMPTED assignment is not downloadable from here either — confirm with BA.
     */
    @Transactional(readOnly = true)
    public HrDocumentService.Download downloadMine(Long id) {
        Acknowledgment a = own(id);
        HrDocument d = documentService.require(a.getDocumentId());
        if (a.getStatus() != EStatus.ACKNOWLEDGED) {
            throw ApiException.badRequest(EDocumentError.DOC_026);
        }
        documentService.assertDownloadable(d);
        return documentService.link(d);
    }

    private Acknowledgment own(Long id) {
        ErpPrincipal me = self();
        return acks.findByIdAndDeletedFalse(id)
                .filter(x -> x.getCompanyId().equals(me.companyId()) && me.userId().equals(x.getEmployeeUserId()))
                .orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_011));
    }

    private Acknowledgment acknowledge(Acknowledgment a, AcknowledgeForm f) {
        if (a.getStatus() != EStatus.PENDING) {
            throw ApiException.badRequest(EDocumentError.DOC_014);
        }
        a.setStatus(EStatus.ACKNOWLEDGED);
        a.setAcknowledgedAt(clock.instant());
        a.setAcknowledgedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        a.setAcknowledgeNote(f == null ? null : Texts.blankToNull(f.note()));
        return a;
    }

    /** A company user (a super admin has no assignments of their own). */
    private static ErpPrincipal self() {
        ErpPrincipal me = CurrentUser.find().orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_011));
        if (me.companyId() == null) {
            throw ApiException.badRequest(EDocumentError.DOC_001);
        }
        return me;
    }

    private Page<AckDto> dtos(Page<Acknowledgment> page) {
        Map<Long, AckDto> byId = dtos(page.getContent()).stream()
                .collect(Collectors.toMap(x -> Long.valueOf(x.id()), Function.identity()));
        return page.map(a -> byId.get(a.getId()));
    }

    private List<AckDto> dtos(List<Acknowledgment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, HrDocument> docs = documents.findAllById(list.stream().map(Acknowledgment::getDocumentId).distinct()
                .toList()).stream().collect(Collectors.toMap(HrDocument::getId, Function.identity()));
        LocalDate today = today();
        return list.stream().map(a -> {
            HrDocument d = docs.get(a.getDocumentId());
            boolean overdue = a.getStatus() == EStatus.PENDING && a.getDueDate() != null
                    && a.getDueDate().isBefore(today);
            return new AckDto(a.getId().toString(),
                    d == null ? null : new DocumentRef(d.getId().toString(), d.getTitle(), d.getDescription()),
                    new Person(a.getEmployeeId().toString(), a.getEmployeeName(), a.getEmployeeEmail()),
                    a.getDueDate(), a.getNote(), a.getStatus(), overdue ? ETab.OVERDUE : ETab.valueOf(a.getStatus()
                    .name()), overdue, a.getAssignedByName(), a.getAcknowledgedAt(), a.getAcknowledgeNote(),
                    a.getCreatedDate());
        }).toList();
    }

    private LocalDate today() {
        return Texts.today(clock);
    }

    public Acknowledgment require(Long id) {
        Acknowledgment a = acks.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_011));
        TenantGuard.assertSameCompany(a.getCompanyId(), EDocumentError.DOC_011);
        // the document must still exist
        documentService.require(a.getDocumentId());
        return a;
    }
}
