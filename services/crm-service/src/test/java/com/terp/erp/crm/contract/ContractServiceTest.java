package com.terp.erp.crm.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.client.MediaClient;
import com.terp.erp.crm.contract.ContractService.AttachmentForm;
import com.terp.erp.crm.contract.ContractService.ContractForm;
import com.terp.erp.crm.contract.ContractService.RenewalForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final ContractTypeRepository types = mock(ContractTypeRepository.class);
    private final ContractSettingRepository settingRepo = mock(ContractSettingRepository.class);
    private final ContractRenewalRepository renewals = mock(ContractRenewalRepository.class);
    private final ContractCommentRepository comments = mock(ContractCommentRepository.class);
    private final ContractNoteRepository notes = mock(ContractNoteRepository.class);
    private final ContractAttachmentRepository attachments = mock(ContractAttachmentRepository.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final MediaClient media = mock(MediaClient.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final ContractSignatureRepository signatureRepo = mock(ContractSignatureRepository.class);
    private final ContractTypeService typeService = new ContractTypeService(types, contracts);
    private final ContractSettingService settingService = new ContractSettingService(settingRepo, contracts);
    private final ContractService service = new ContractService(contracts, typeService, settingService, renewals,
            comments, notes, attachments, accounting, media, outbox, signatureRepo,
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC));
    private final ContractSetting setting = new ContractSetting();

    @BeforeEach
    void setUp() {
        ContractType type = new ContractType();
        type.setId(2L);
        type.setCompanyId(10L);
        type.setName("Test Maintenance");
        when(types.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(type));
        when(types.findById(2L)).thenReturn(Optional.of(type));
        setting.setCompanyId(10L);
        when(settingRepo.lock(10L)).thenReturn(Optional.of(setting));
        when(accounting.customer(5L)).thenReturn(new AccountingClient.Customer("5", "C-1", "Test Customer Ltd",
                "Test Person", "test@example.com"));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            c.setId(70L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ContractForm form(LocalDate start, LocalDate end) {
        return new ContractForm(null, "Test support agreement", 5L, 2L, new BigDecimal("1000"), start, end, null,
                "<p>Test</p>");
    }

    private static Contract contract(long id, long companyId) {
        Contract c = new Contract();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setNumber("CON0001");
        c.setSubject("Test");
        c.setCustomerId(5L);
        c.setCustomerName("Test Customer Ltd");
        c.setContractTypeId(2L);
        c.setStartDate(TODAY);
        c.setEndDate(TODAY.plusDays(10));
        return c;
    }

    private static Object code(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @Test
    void numbersFollowThePrefixAndAPrefixChangeAppliesToTheNextOnes() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");

        var first = service.create(form(TODAY, TODAY.plusDays(30)));
        var second = service.create(form(TODAY, TODAY.plusDays(30)));
        settingService.update(new ContractSettingService.SettingForm(null, "AGR"));
        var third = service.duplicate(stubbed(first));

        assertThat(first.contract().number()).isEqualTo("CON0001");
        assertThat(first.contract().status()).isEqualTo(Contract.EStatus.PENDING);
        assertThat(first.contract().customerName()).isEqualTo("Test Customer Ltd");
        assertThat(second.contract().number()).isEqualTo("CON0002");
        assertThat(third.contract().number()).isEqualTo("AGR0003");
        assertThat(third.contract().status()).isEqualTo(Contract.EStatus.PENDING);
        assertThat(third.contract().subject()).isEqualTo("Test support agreement");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAndDuplicatePublishContractCreated() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        var first = service.create(form(TODAY, TODAY.plusDays(30)));
        service.duplicate(stubbed(first));
        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox, org.mockito.Mockito.times(2)).publish(eq(10L), eq("crm.contract.created"), eq("CONTRACT"),
                eq(70L), payload.capture());
        assertThat((java.util.Map<String, Object>) payload.getAllValues().get(0))
                .containsKeys("contractId", "title", "number", "subject", "customerId", "customerName", "value",
                        "status", "startDate", "endDate")
                .containsEntry("title", "CON0001");
    }

    /** Lets duplicate() find the contract created first. */
    private long stubbed(ContractService.ContractDetail d) {
        Contract src = contract(70L, 10L);
        src.setSubject(d.contract().subject());
        src.setStatus(Contract.EStatus.ACTIVE);
        when(contracts.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(src));
        return 70L;
    }

    @Test
    void aNumberAlreadyTakenIsSkipped() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        when(contracts.existsByCompanyIdAndNumber(10L, "CON0001")).thenReturn(true);

        assertThat(service.create(form(TODAY, TODAY)).contract().number()).isEqualTo("CON0002");
        assertThat(setting.getNextNumber()).isEqualTo(3);
    }

    @Test
    void endBeforeStartIsRefused() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");

        assertThatThrownBy(() -> service.create(form(TODAY, TODAY.minusDays(1))))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_006);
        verify(settingRepo, never()).lock(anyLong());

        when(contracts.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(contract(70L, 10L)));
        assertThatThrownBy(() -> service.addRenewal(70L, new RenewalForm(TODAY, TODAY.minusDays(1), BigDecimal.TEN,
                Contract.EStatus.PENDING, null)))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_006);
    }

    @Test
    void contractOfAnotherCompanyIsNotFoundAlsoThroughItsRenewals() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE", "CRM_CONTRACT_VIEW");
        when(contracts.findByIdAndDeletedFalse(80L)).thenReturn(Optional.of(contract(80L, 20L)));
        RenewalForm rf = new RenewalForm(TODAY, TODAY.plusDays(5), BigDecimal.TEN, Contract.EStatus.PENDING, null);

        assertThatThrownBy(() -> service.get(80L)).extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_002);
        assertThatThrownBy(() -> service.update(80L, form(TODAY, TODAY)))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_002);
        assertThatThrownBy(() -> service.addRenewal(80L, rf))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_002);
        assertThatThrownBy(() -> service.updateRenewal(80L, 1L, rf))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_002);
        assertThatThrownBy(() -> service.deleteRenewal(80L, 1L))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_002);
        assertThatThrownBy(() -> service.duplicate(80L))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_002);
        assertThat(((ApiException) catchFor(() -> service.get(80L))).getStatus().value()).isEqualTo(404);
        verify(renewals, never()).save(any());
    }

    private static Throwable catchFor(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            return e;
        }
        throw new AssertionError("expected an exception");
    }

    @Test
    void attachmentMustBeAFileOfTheContractCompany() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        when(contracts.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(contract(70L, 10L)));
        when(media.requireCompanyFile(eq(77L), eq(10L), eq(EContractError.CTR_012)))
                .thenThrow(ApiException.badRequest(EContractError.CTR_012));

        assertThatThrownBy(() -> service.addAttachment(70L, new AttachmentForm(77L)))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_012);
        verify(attachments, never()).save(any());
    }

    @Test
    void progressAndDaysLeftUseTheClock() {
        TestLogin.as(10L, "CRM_CONTRACT_VIEW");
        Contract c = contract(70L, 10L);
        c.setStartDate(LocalDate.of(2026, 11, 1));
        c.setEndDate(LocalDate.of(2026, 12, 11)); // 40 days, 20 elapsed on 2026-11-21
        when(contracts.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(c));

        var row = service.get(70L).contract();

        assertThat(row.progressPercent()).isEqualTo(50);
        assertThat(row.daysLeft()).isEqualTo(20);
        assertThat(ContractService.progressPercent(TODAY.plusDays(1), TODAY.plusDays(5), TODAY)).isZero();
        assertThat(ContractService.progressPercent(TODAY.minusDays(30), TODAY.minusDays(1), TODAY)).isEqualTo(100);
        assertThat(ContractService.daysLeft(TODAY.minusDays(1), TODAY)).isZero();
        assertThat(ContractService.progressPercent(TODAY, TODAY, TODAY)).isEqualTo(100);
    }

    @Test
    void typeInUseCannotBeDeleted() {
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        when(contracts.existsByContractTypeIdAndDeletedFalse(2L)).thenReturn(true);

        assertThatThrownBy(() -> typeService.delete(2L))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_005);

        when(contracts.existsByContractTypeIdAndDeletedFalse(2L)).thenReturn(false);
        typeService.delete(2L);
        assertThat(types.findByIdAndDeletedFalse(2L).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void typeOfAnotherCompanyIsNotFoundAndDuplicateNamesAreRefused() {
        TestLogin.as(20L, "CRM_CONTRACT_MANAGE");
        assertThatThrownBy(() -> typeService.delete(2L))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_003);

        when(types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalse(eq(20L), anyString())).thenReturn(true);
        assertThatThrownBy(() -> typeService.create(new ContractTypeService.TypeForm(null, "Test Maintenance", true)))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_004);
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertThatThrownBy(() -> service.statusCounts(null))
                .extracting(ContractServiceTest::code).isEqualTo(EContractError.CTR_001);
    }
}
