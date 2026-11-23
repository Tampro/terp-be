package com.terp.erp.crm.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.client.MediaClient;
import com.terp.erp.crm.client.RemoteApiException;
import com.terp.erp.crm.contract.ContractSignature.ESignerType;
import com.terp.erp.crm.contract.ContractSignatureService.Origin;
import com.terp.erp.crm.contract.ContractSignatureService.PortalSignature;
import com.terp.erp.crm.contract.ContractSignatureService.SignForm;
import com.terp.erp.crm.contract.ContractSignatureService.SignatureDto;
import com.terp.erp.crm.portal.ClientPortalController;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.outbox.OutboxPublisher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hand-drawn contract signatures: PNG checks, one per side, locking, client scoping, hash, status, permissions. */
class ContractSignatureServiceTest {
    private static final Origin ORIGIN = new Origin("203.0.113.7", "Test-Agent/1.0");
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final ContractSignatureRepository signatureRepo = mock(ContractSignatureRepository.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final ContractTypeService typeService = new ContractTypeService(mock(ContractTypeRepository.class), contracts);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final ContractService contractService = new ContractService(contracts, typeService,
            new ContractSettingService(mock(ContractSettingRepository.class), contracts),
            mock(ContractRenewalRepository.class), mock(ContractCommentRepository.class),
            mock(ContractNoteRepository.class), mock(ContractAttachmentRepository.class), accounting,
            mock(MediaClient.class), mock(OutboxPublisher.class), signatureRepo, clock);
    private final ContractPortalService portal = new ContractPortalService(contracts,
            mock(ContractRenewalRepository.class), typeService, accounting);
    private final ContractSignatureService service = new ContractSignatureService(contractService, portal,
            signatureRepo, contracts, clock);
    private final List<ContractSignature> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(signatureRepo.saveAndFlush(any(ContractSignature.class))).thenAnswer(inv -> {
            ContractSignature s = inv.getArgument(0);
            stored.add(s);
            return s;
        });
        when(signatureRepo.existsByContractIdAndSignerType(anyLong(), any())).thenAnswer(inv -> stored.stream()
                .anyMatch(s -> s.getContractId().equals(inv.getArgument(0)) && s.getSignerType() == inv.getArgument(1)));
        when(signatureRepo.findByContractIdAndSignerType(anyLong(), any())).thenAnswer(inv -> stored.stream()
                .filter(s -> s.getContractId().equals(inv.getArgument(0)) && s.getSignerType() == inv.getArgument(1))
                .findFirst());
        when(signatureRepo.existsByContractId(anyLong())).thenAnswer(inv -> stored.stream()
                .anyMatch(s -> s.getContractId().equals(inv.getArgument(0))));
        when(signatureRepo.countByContractId(anyLong())).thenAnswer(inv -> stored.stream()
                .filter(s -> s.getContractId().equals(inv.getArgument(0))).count());
        when(signatureRepo.findByContractIdOrderBySignedAtAsc(anyLong())).thenAnswer(inv -> stored.stream()
                .filter(s -> s.getContractId().equals(inv.getArgument(0))).toList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Contract contract(long id, long company, long customer, String createdBy) {
        Contract c = new Contract();
        c.setId(id);
        c.setCompanyId(company);
        c.setCustomerId(customer);
        c.setCustomerName("Test Customer Ltd");
        c.setNumber("CON000" + id);
        c.setSubject("Test support agreement");
        c.setContractTypeId(2L);
        c.setValue(new BigDecimal("12000.0000"));
        c.setStartDate(LocalDate.of(2026, 12, 1));
        c.setEndDate(LocalDate.of(2027, 11, 30));
        c.setDescription("<p>Test agreement text.</p>");
        c.setCreatedBy(createdBy);
        when(contracts.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(c));
        when(contracts.lockById(id)).thenReturn(Optional.of(c));
        return c;
    }

    static String dataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    static byte[] realPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawLine(0, 0, w - 1, h - 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** A PNG header claiming w x h, padded to size bytes (never decoded). */
    static byte[] header(long w, long h, int size) {
        byte[] b = new byte[Math.max(size, 33)];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(head, 0, b, 0, head.length);
        for (int i = 0; i < 4; i++) {
            b[16 + i] = (byte) (w >>> (24 - 8 * i));
            b[20 + i] = (byte) (h >>> (24 - 8 * i));
        }
        return b;
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }

    private SignForm form(String dataUrl) {
        return new SignForm(dataUrl, null);
    }

    // ---- PNG data URL ------------------------------------------------------------------------------------------

    @Test
    void dataUrlValidation() throws Exception {
        assertThat(SignaturePng.decode(dataUrl(realPng(300, 100)))).isNotEmpty();
        assertThat(SignaturePng.decode("DATA:IMAGE/PNG;BASE64," + Base64.getEncoder().encodeToString(header(10, 10, 40))))
                .hasSize(40);
        assertCode(() -> SignaturePng.decode(null), EContractError.CTR_013);
        assertCode(() -> SignaturePng.decode("data:image/jpeg;base64,/9j/4AAQ"), EContractError.CTR_013);
        assertCode(() -> SignaturePng.decode("data:image/png;base64,***not base64***"), EContractError.CTR_013);
        byte[] gif = header(10, 10, 40);
        gif[1] = 'G';
        assertCode(() -> SignaturePng.decode(dataUrl(gif)), EContractError.CTR_013);
        assertCode(() -> SignaturePng.decode(dataUrl(new byte[]{(byte) 0x89, 'P', 'N', 'G'})), EContractError.CTR_013);
        assertCode(() -> SignaturePng.decode(dataUrl(header(10, 10, SignaturePng.MAX_BYTES + 1))),
                EContractError.CTR_014);
        assertCode(() -> SignaturePng.decode(dataUrl(header(2001, 100, 40))), EContractError.CTR_015);
        assertCode(() -> SignaturePng.decode(dataUrl(header(100, 1001, 40))), EContractError.CTR_015);
        assertCode(() -> SignaturePng.decode(dataUrl(header(0, 10, 40))), EContractError.CTR_015);
        assertCode(() -> SignaturePng.decode(dataUrl(header(0xFFFFFFFFL, 10, 40))), EContractError.CTR_015);
    }

    // ---- signing -----------------------------------------------------------------------------------------------

    @Test
    void eachSideSignsOnceAndBothMakeTheContractActive() throws Exception {
        Contract c = contract(70L, 10L, 5L, "kc-5");
        String png = dataUrl(realPng(300, 100));
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        List<SignatureDto> afterCompany = service.signAsCompany(70L, new SignForm(png, " Test Signer "), ORIGIN);
        assertThat(afterCompany).singleElement().satisfies(s -> {
            assertThat(s.signerType()).isEqualTo(ESignerType.COMPANY);
            assertThat(s.signerName()).isEqualTo("Test Signer");
            assertThat(s.contentMatches()).isTrue();
            assertThat(s.ip()).isEqualTo("203.0.113.7");
            assertThat(s.userAgent()).isEqualTo("Test-Agent/1.0");
        });
        assertThat(c.getStatus()).isEqualTo(Contract.EStatus.PENDING);
        assertCode(() -> service.signAsCompany(70L, form(png), ORIGIN), EContractError.CTR_016);

        TestLogin.user(31L, "kc-client", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        List<PortalSignature> afterClient = service.signAsClient(70L, new SignForm(png, "Someone Else"), ORIGIN);
        assertThat(afterClient).hasSize(2);
        // The client's name comes from the token (TestLogin has no name claim -> username), never from the body.
        assertThat(stored.get(1).getSignerName()).isEqualTo("u31");
        assertThat(stored.get(1).getSignerUserId()).isEqualTo(31L);
        assertThat(c.getStatus()).isEqualTo(Contract.EStatus.ACTIVE);
        assertCode(() -> service.signAsClient(70L, form(png), ORIGIN), EContractError.CTR_017);
    }

    @Test
    void onlyPendingContractsAreSignable() throws Exception {
        Contract c = contract(71L, 10L, 5L, "kc-5");
        c.setStatus(Contract.EStatus.DECLINED);
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        assertCode(() -> service.signAsCompany(71L, form(dataUrl(header(10, 10, 40))), ORIGIN), EContractError.CTR_017);
    }

    @Test
    void signedContractIsLocked() throws Exception {
        contract(72L, 10L, 5L, "kc-5");
        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        service.signAsCompany(72L, form(dataUrl(realPng(50, 20))), ORIGIN);
        ContractService.ContractForm edit = new ContractService.ContractForm(null, "Changed", 5L, 2L, BigDecimal.ONE,
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1), null, null);
        assertCode(() -> contractService.update(72L, edit), EContractError.CTR_018);
        assertCode(() -> contractService.delete(72L), EContractError.CTR_018);
    }

    @Test
    void clientSignsOnlyContractsOfTheLinkedCustomer() {
        contract(73L, 10L, 6L, "kc-5");
        contract(74L, 11L, 5L, "kc-5");
        TestLogin.user(31L, "kc-client", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        String png = dataUrl(header(10, 10, 40));
        assertCode(() -> service.signAsClient(73L, form(png), ORIGIN), EContractError.CTR_002);
        assertCode(() -> service.signAsClient(74L, form(png), ORIGIN), EContractError.CTR_002);
        assertCode(() -> service.listForClient(73L), EContractError.CTR_002);
        assertCode(() -> service.imageForClient(73L, ESignerType.COMPANY), EContractError.CTR_002);

        when(accounting.myCustomerId()).thenThrow(new RemoteApiException(HttpStatus.NOT_FOUND, "ACC_098", "not linked"));
        assertThatThrownBy(() -> service.signAsClient(73L, form(png), ORIGIN)).isInstanceOf(RemoteApiException.class)
                .extracting(e -> ((RemoteApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stored).isEmpty();
    }

    @Test
    void companyScopeOtherCompanyAndOwnOnly() {
        contract(75L, 10L, 5L, "kc-other");
        String png = dataUrl(header(10, 10, 40));
        TestLogin.as(11L, "CRM_CONTRACT_MANAGE");
        assertCode(() -> service.signAsCompany(75L, form(png), ORIGIN), EContractError.CTR_002);
        TestLogin.user(8L, "kc-8", 10L, "CRM_CONTRACT_MANAGE_OWN");
        assertCode(() -> service.signAsCompany(75L, form(png), ORIGIN), EContractError.CTR_002);
        assertThat(stored).isEmpty();
    }

    @Test
    void hashRecomputationDetectsAChange() throws Exception {
        Contract c = contract(76L, 10L, 5L, "kc-5");
        String before = ContractContentHash.sha256(c);
        c.setValue(new BigDecimal("12000.00"));
        assertThat(ContractContentHash.sha256(c)).as("scale does not matter").isEqualTo(before);
        assertThat(ContractContentHash.canonical(c)).startsWith("version:2:v1\nnumber:8:CON00076\n")
                .contains("value:5:12000\n");

        TestLogin.as(10L, "CRM_CONTRACT_MANAGE");
        service.signAsCompany(76L, form(dataUrl(realPng(40, 20))), ORIGIN);
        // Simulate a change behind the service's back (e.g. a direct DB edit).
        c.setDescription("<p>Test agreement text, quietly changed.</p>");
        assertThat(service.listForCompany(76L)).singleElement()
                .satisfies(s -> assertThat(s.contentMatches()).isFalse());
        TestLogin.user(31L, "kc-client", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        assertThat(service.listForClient(76L)).singleElement()
                .satisfies(s -> assertThat(s.contentMatches()).isFalse());
    }

    @Test
    void imageAndPdf() throws Exception {
        contract(77L, 10L, 5L, "kc-5");
        byte[] png = realPng(120, 40);
        TestLogin.as(10L, "CRM_CONTRACT_VIEW", "CRM_CONTRACT_MANAGE");
        assertCode(() -> service.imageForCompany(77L, ESignerType.COMPANY), EContractError.CTR_019);
        service.signAsCompany(77L, form(dataUrl(png)), ORIGIN);
        assertThat(service.imageForCompany(77L, ESignerType.COMPANY)).isEqualTo(png);
        TestLogin.user(31L, "kc-client", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        service.signAsClient(77L, form(dataUrl(realPng(200, 60))), ORIGIN);
        ContractSignatureService.Pdf pdf = service.pdfForClient(77L);
        assertThat(pdf.fileName()).isEqualTo("contract-CON00077.pdf");
        assertThat(new String(pdf.content(), 0, 5)).isEqualTo("%PDF-");
        assertThat(ContractPdf.plainText("<p>A &amp; B</p><p>C</p>")).isEqualTo("A & B\nC");
    }

    // ---- contract of the API -----------------------------------------------------------------------------------

    @Test
    void portalSignatureHidesIpUserAgentAndIds() {
        assertThat(Arrays.stream(PortalSignature.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("signerType", "signerName", "signedAt", "contentMatches");
    }

    @Test
    void permissions() throws Exception {
        String manage = "@PermissionChecker.hasAny('CRM_CONTRACT_MANAGE','CRM_CONTRACT_MANAGE_OWN')";
        Class<?> ctl = ContractSignatureController.class;
        assertThat(ctl.getMethod("sign", Long.class, SignForm.class, jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(manage);
        for (String m : List.of("list", "image", "pdf")) {
            Method method = Arrays.stream(ctl.getMethods()).filter(x -> x.getName().equals(m)
                    && x.getParameterTypes()[0] == Long.class).findFirst().orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class).value()).contains("CRM_CONTRACT_VIEW");
        }
        for (String m : List.of("sign", "contractSignatures", "signatureImage", "contractPdf")) {
            Method method = Arrays.stream(ClientPortalController.class.getMethods())
                    .filter(x -> x.getName().equals(m)).findFirst().orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("@PermissionChecker.has('PORTAL_CLIENT')");
        }
    }
}
