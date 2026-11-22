package com.terp.erp.document.hr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.document.TestLogin;
import com.terp.erp.document.category.DocumentCategoryRepository;
import com.terp.erp.document.client.EmployeeClient;
import com.terp.erp.document.client.MediaClient;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.hr.HrDocumentService.DecisionForm;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.ErpPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FR-ERP-DOC-001 (Pending → Approved / Rejected, download gate) and FR-ERP-ACK-001 (download once acknowledged). */
class HrDocumentApprovalTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T02:00:00Z"), ZoneOffset.UTC);
    private final HrDocumentRepository documents = mock(HrDocumentRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private final AcknowledgmentRepository ackRepo = mock(AcknowledgmentRepository.class);
    private final HrDocumentService service = new HrDocumentService(documents, mock(DocumentCategoryRepository.class),
            media, ackRepo, CLOCK);
    private final AcknowledgmentService acks = new AcknowledgmentService(ackRepo, documents, service,
            mock(EmployeeClient.class), CLOCK);
    private final HrDocument doc = new HrDocument();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "DOCUMENT_MANAGE");
        doc.setId(9L);
        doc.setCompanyId(10L);
        doc.setCategoryId(3L);
        doc.setTitle("Test Policy");
        doc.setFileId(41L);
        doc.setStatus(HrDocument.EStatus.PENDING);
        when(documents.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(doc));
        when(media.require(41L, 10L)).thenReturn(new MediaClient.MediaFile("41", "10", "policy.pdf", null,
                "application/pdf", 10L, null, "https://media.example.invalid/signed"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approveOnlyFromPendingRecordsWhoAndComment() {
        var d = service.approve(9L, new DecisionForm(" Checked "));
        assertThat(d.status()).isEqualTo(HrDocument.EStatus.APPROVED);
        assertThat(d.decidedByName()).isEqualTo("u5");
        assertThat(d.decisionComment()).isEqualTo("Checked");
        assertThat(doc.getDecidedBy()).isEqualTo("kc-5");
        assertThat(doc.getDecidedAt()).isEqualTo(CLOCK.instant());
        assertCode(() -> service.approve(9L, null), EDocumentError.DOC_024);
        assertCode(() -> service.reject(9L, null), EDocumentError.DOC_024);
    }

    @Test
    void rejectOnlyFromPending() {
        doc.setStatus(HrDocument.EStatus.DRAFT);
        assertCode(() -> service.reject(9L, null), EDocumentError.DOC_024);
        doc.setStatus(HrDocument.EStatus.PENDING);
        var d = service.reject(9L, null);
        assertThat(d.status()).isEqualTo(HrDocument.EStatus.REJECTED);
        assertThat(d.decisionComment()).isNull();
    }

    @Test
    void downloadRefusedForNonManagersUntilApproved() {
        TestLogin.as(10L, "DOCUMENT_VIEW");
        assertCode(() -> service.download(9L), EDocumentError.DOC_025);
        doc.setStatus(HrDocument.EStatus.REJECTED);
        assertCode(() -> service.download(9L), EDocumentError.DOC_025);
        doc.setStatus(HrDocument.EStatus.APPROVED);
        assertThat(service.download(9L).url()).isEqualTo("https://media.example.invalid/signed");
        doc.setStatus(HrDocument.EStatus.PUBLISHED);
        assertThat(service.download(9L).url()).isNotBlank();
    }

    @Test
    void managerMayDownloadPending() {
        assertThat(service.download(9L).url()).isNotBlank();
    }

    @Test
    void acknowledgmentGatesTheDownload() {
        doc.setStatus(HrDocument.EStatus.PUBLISHED);
        TestLogin.set(new ErpPrincipal(77L, "kc-77", "twelve", 10L, false, Set.of(), Set.of("DOCUMENT_VIEW")));
        when(ackRepo.existsByDocumentIdAndEmployeeUserIdAndStatusAndDeletedFalse(9L, 77L,
                Acknowledgment.EStatus.PENDING)).thenReturn(true);
        assertCode(() -> service.download(9L), EDocumentError.DOC_026);

        Acknowledgment a = new Acknowledgment();
        a.setId(5L);
        a.setCompanyId(10L);
        a.setDocumentId(9L);
        a.setEmployeeId(12L);
        a.setEmployeeUserId(77L);
        when(ackRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(a));
        assertCode(() -> acks.downloadMine(5L), EDocumentError.DOC_026);
        a.setStatus(Acknowledgment.EStatus.ACKNOWLEDGED);
        assertThat(acks.downloadMine(5L).url()).isNotBlank();
        doc.setStatus(HrDocument.EStatus.PENDING);
        assertCode(() -> acks.downloadMine(5L), EDocumentError.DOC_025);
        // someone else's assignment
        TestLogin.set(new ErpPrincipal(78L, "kc-78", "other", 10L, false, Set.of(), Set.of()));
        assertCode(() -> acks.downloadMine(5L), EDocumentError.DOC_011);
    }

    @Test
    void otherCompanysDocumentIsNotFound() {
        TestLogin.as(11L, "DOCUMENT_MANAGE");
        assertCode(() -> service.approve(9L, null), EDocumentError.DOC_009);
        assertCode(() -> service.reject(9L, null), EDocumentError.DOC_009);
        assertCode(() -> service.download(9L), EDocumentError.DOC_009);
        assertThatThrownBy(() -> service.approve(9L, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
        assertThat(doc.getStatus()).isEqualTo(HrDocument.EStatus.PENDING);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EDocumentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
