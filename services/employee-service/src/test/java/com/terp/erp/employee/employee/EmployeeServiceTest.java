package com.terp.erp.employee.employee;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.client.RemoteApiException;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.common.FieldCipher;
import com.terp.erp.employee.document.DocumentService;
import com.terp.erp.employee.employee.EmployeeDtos.BankingForm;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeCreateForm;
import com.terp.erp.employee.employee.EmployeeDtos.ProfileForm;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {
    private static final Long COMPANY = 10L;

    private final EmployeeRepository repo = mock(EmployeeRepository.class);
    private final OrgService orgs = mock(OrgService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final IamClient iam = mock(IamClient.class);
    private final MediaClient media = mock(MediaClient.class);
    private final FieldCipher cipher = new FieldCipher(new com.terp.erp.employee.config.EmployeeProperties(
            "http://iam", "http://media", Base64.getEncoder().encodeToString(new byte[32])));
    private final EmployeeService service =
            new EmployeeService(repo, orgs, documents, iam, media, cipher, mock(EntityManager.class));

    @BeforeEach
    void setUp() {
        when(orgs.names(anyLong(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new OrgService.Names(Map.of(), Map.of(), Map.of()));
        when(repo.lockSequence(COMPANY)).thenReturn(7L);
        when(iam.company(COMPANY)).thenReturn(new IamClient.CompanyContext(COMPANY, "Demo", 50));
        when(repo.saveAndFlush(any())).thenAnswer(i -> {
            Employee e = i.getArgument(0);
            e.setId(99L);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createIssuesTheNextEmployeeNoAndAnAccount() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE");
        when(iam.createAccount(COMPANY, "a.nguyen@example.com", "Nguyen Van A", null)).thenReturn(42L);

        var d = service.create(null, form("A.Nguyen@Example.com", null));

        assertThat(d.employeeNo()).isEqualTo("EMP000007");
        assertThat(d.email()).isEqualTo("a.nguyen@example.com");
        assertThat(d.userId()).isEqualTo("42");
        assertThat(d.loginEnabled()).isTrue();
        verify(repo).bumpSequence(COMPANY);
    }

    @Test
    void planLimitStopsBeforeAnyAccountIsCreated() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE");
        when(repo.countByCompanyIdAndDeletedFalse(COMPANY)).thenReturn(50L);

        assertError(() -> service.create(null, form("a@example.com", null)), EEmployeeError.EMP_009);
        verify(iam, never()).createAccount(any(), any(), any(), any());
    }

    @Test
    void duplicateEmailInTheCompanyIsRefused() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE");
        when(repo.existsByCompanyIdAndEmailAndDeletedFalseAndIdNot(COMPANY, "a@example.com", -1L)).thenReturn(true);

        assertError(() -> service.create(null, form("a@example.com", null)), EEmployeeError.EMP_007);
        verify(iam, never()).createAccount(any(), any(), any(), any());
    }

    @Test
    void accountIsLockedAgainWhenTheEmployeeCannotBeSaved() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE");
        when(iam.createAccount(any(), any(), any(), any())).thenReturn(42L);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.create(null, form("a@example.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(iam).lockQuietly(42L);
    }

    @Test
    void iamRefusalIsRelayedAndNothingIsSaved() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE");
        when(iam.createAccount(any(), any(), any(), any()))
                .thenThrow(new RemoteApiException(HttpStatus.BAD_REQUEST, "IAM_010", "taken"));

        assertThatThrownBy(() -> service.create(null, form("a@example.com", null)))
                .isInstanceOf(RemoteApiException.class);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void bankingIsEncryptedAndOnlyShownWithThePermission() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE", EmployeeService.BANKING_VIEW);
        when(iam.createAccount(any(), any(), any(), any())).thenReturn(42L);
        BankingForm bank = new BankingForm("VCB", "NGUYEN VAN A", "0123456789", null, null, "8000000000",
                new BigDecimal("15000000"), new BigDecimal("8"), 5, new BigDecimal("85000"));

        var d = service.create(null, form("a@example.com", bank));

        assertThat(d.banking().accountNumber()).isEqualTo("0123456789");
        var saved = org.mockito.ArgumentCaptor.forClass(Employee.class);
        verify(repo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAccountNumberEnc()).isNotBlank().doesNotContain("0123456789");

        when(repo.findByIdAndDeletedFalse(99L)).thenReturn(Optional.of(saved.getValue()));
        TestLogin.as(COMPANY, "EMPLOYEE_VIEW");
        assertThat(service.get(99L).banking()).isNull();
    }

    @Test
    void bankingInTheFormIsIgnoredWithoutThePermission() {
        TestLogin.as(COMPANY, "EMPLOYEE_CREATE");
        when(iam.createAccount(any(), any(), any(), any())).thenReturn(42L);
        BankingForm bank = new BankingForm("VCB", null, "0123456789", null, null, null, new BigDecimal("1"), null, null, null);

        service.create(null, form("a@example.com", bank));

        var saved = org.mockito.ArgumentCaptor.forClass(Employee.class);
        verify(repo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAccountNumberEnc()).isNull();
        assertThat(saved.getValue().getBaseSalary()).isNull();
    }

    @Test
    void employeeOfAnotherCompanyIsNotFound() {
        TestLogin.as(COMPANY, "EMPLOYEE_VIEW");
        Employee other = new Employee();
        other.setId(3L);
        other.setCompanyId(11L);
        when(repo.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(other));

        assertError(() -> service.get(3L), EEmployeeError.EMP_001);
    }

    @Test
    void passwordConfirmationMustMatch() {
        TestLogin.as(COMPANY, "EMPLOYEE_UPDATE");
        assertError(() -> service.changePassword(1L, "Secret#123", "Secret#124"), EEmployeeError.EMP_020);
        verify(iam, never()).changePassword(anyLong(), anyString());
    }

    @Test
    void deleteLocksTheAccountFirst() {
        TestLogin.as(COMPANY, "EMPLOYEE_DELETE");
        Employee e = new Employee();
        e.setId(3L);
        e.setCompanyId(COMPANY);
        e.setUserId(42L);
        e.setLoginEnabled(true);
        when(repo.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(e));

        service.delete(3L);

        verify(iam).setLoginEnabled(42L, false);
        assertThat(e.isDeleted()).isTrue();
        assertThat(e.isLoginEnabled()).isFalse();
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertError(() -> service.create(null, form("a@example.com", null)), EEmployeeError.EMP_019);
        verify(iam, never()).createAccount(any(), any(), any(), eq(null));
    }

    @Test
    void employeeNoIsZeroPadded() {
        assertThat(EmployeeService.formatEmployeeNo(111)).isEqualTo("EMP000111");
    }

    private static EmployeeCreateForm form(String email, BankingForm banking) {
        ProfileForm p = new ProfileForm("Nguyen Van A", null, null, null, null, null, null, null, null,
                LocalDate.of(2026, 12, 1), EEmploymentType.FULL_TIME, EEmployeeStatus.PROBATION, null, null,
                null, null, null, null, null, null, null, null, null, banking, null, null);
        return new EmployeeCreateForm(email, null, p, List.of());
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
