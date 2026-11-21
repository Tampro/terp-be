package com.terp.erp.document.hr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.document.TestLogin;
import com.terp.erp.document.category.DocumentCategory;
import com.terp.erp.document.category.DocumentCategoryRepository;
import com.terp.erp.document.client.EmployeeClient;
import com.terp.erp.document.client.MediaClient;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.hr.AcknowledgmentService.AcknowledgeForm;
import com.terp.erp.document.hr.AcknowledgmentService.AssignForm;
import com.terp.erp.document.hr.HrDocumentService.DocumentForm;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.ErpPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HrDocumentServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:00:00Z"), ZoneOffset.UTC);
    private final HrDocumentRepository documents = mock(HrDocumentRepository.class);
    private final DocumentCategoryRepository categories = mock(DocumentCategoryRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private final AcknowledgmentRepository ackRepo = mock(AcknowledgmentRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final HrDocumentService service = new HrDocumentService(documents, categories, media, CLOCK);
    private final AcknowledgmentService acks = new AcknowledgmentService(ackRepo, documents, service, employees,
            CLOCK);
    private final DocumentCategory policies = new DocumentCategory();
    private final HrDocument doc = new HrDocument();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "DOCUMENT_MANAGE");
        policies.setId(3L);
        policies.setCompanyId(10L);
        policies.setName("Policies");
        policies.setMandatory(true);
        when(categories.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(policies));
        when(categories.findById(3L)).thenReturn(Optional.of(policies));
        when(documents.save(any())).thenAnswer(i -> {
            HrDocument d = i.getArgument(0);
            d.setId(9L);
            return d;
        });
        doc.setId(9L);
        doc.setCompanyId(10L);
        doc.setCategoryId(3L);
        doc.setTitle("Code of Conduct");
        doc.setFileId(41L);
        doc.setStatus(HrDocument.EStatus.PUBLISHED);
        when(documents.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(doc));
        when(ackRepo.save(any())).thenAnswer(i -> {
            Acknowledgment a = i.getArgument(0);
            a.setId(5L);
            return a;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTakesTheMandatoryFlagAndChecksTheFile() {
        var d = service.create(null, form(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertThat(d.requiresAcknowledgment()).isTrue();
        assertThat(d.status()).isEqualTo(HrDocument.EStatus.DRAFT);
        verify(media).require(41L, 10L);
        assertCode(() -> service.create(null, form(null, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1))),
                EDocumentError.DOC_010);
        policies.setStatus(EStatus.INACTIVE);
        assertCode(() -> service.create(null, form(null, null, null)), EDocumentError.DOC_015);
    }

    @Test
    void pastExpiryReadsExpiredUnlessArchived() {
        doc.setExpiryDate(LocalDate.of(2026, 11, 20));
        assertThat(service.get(9L).status()).isEqualTo(HrDocument.EStatus.EXPIRED);
        assertThat(service.get(9L).storedStatus()).isEqualTo(HrDocument.EStatus.PUBLISHED);
        doc.setStatus(HrDocument.EStatus.ARCHIVED);
        assertThat(service.get(9L).status()).isEqualTo(HrDocument.EStatus.ARCHIVED);
    }

    @Test
    void assignAndAcknowledgeThemself() {
        when(employees.employee(12L)).thenReturn(new EmployeeClient.Employee("12", "10", "E12", "Test Twelve",
                "t12@example.invalid", null, "ACTIVE", null, null, "77"));
        var a = acks.assign(null, new AssignForm(9L, 12L, LocalDate.of(2026, 11, 20), " Read it "));
        assertThat(a.overdue()).isTrue();
        assertThat(a.tab()).isEqualTo(AcknowledgmentService.ETab.OVERDUE);
        assertThat(a.assignedByName()).isEqualTo("u5");
        when(ackRepo.existsByDocumentIdAndEmployeeIdAndStatusAndDeletedFalseAndIdNot(eq(9L), eq(12L), any(),
                anyLong())).thenReturn(true);
        assertCode(() -> acks.assign(null, new AssignForm(9L, 12L, null, null)), EDocumentError.DOC_012);

        Acknowledgment stored = new Acknowledgment();
        stored.setId(5L);
        stored.setCompanyId(10L);
        stored.setDocumentId(9L);
        stored.setEmployeeId(12L);
        stored.setEmployeeUserId(77L);
        when(ackRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(stored));
        // someone else cannot acknowledge it as theirs
        assertCode(() -> acks.acknowledgeMine(5L, new AcknowledgeForm("x")), EDocumentError.DOC_011);
        TestLogin.set(new ErpPrincipal(77L, "kc-77", "twelve", 10L, false, Set.of(), Set.of()));
        var done = acks.acknowledgeMine(5L, new AcknowledgeForm(" Read and agreed "));
        assertThat(done.status()).isEqualTo(Acknowledgment.EStatus.ACKNOWLEDGED);
        assertThat(done.acknowledgeNote()).isEqualTo("Read and agreed");
        assertThat(done.overdue()).isFalse();
        assertCode(() -> acks.acknowledgeMine(5L, new AcknowledgeForm(null)), EDocumentError.DOC_014);

        doc.setStatus(HrDocument.EStatus.ARCHIVED);
        TestLogin.as(10L, "DOCUMENT_MANAGE");
        assertCode(() -> acks.assign(null, new AssignForm(9L, 13L, null, null)), EDocumentError.DOC_016);
    }

    private static DocumentForm form(Boolean ack, LocalDate effective, LocalDate expiry) {
        return new DocumentForm(" Code of Conduct ", 3L, null, 41L, effective, expiry, ack);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EDocumentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
