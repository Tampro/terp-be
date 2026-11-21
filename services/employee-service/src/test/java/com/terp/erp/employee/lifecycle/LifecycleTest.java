package com.terp.erp.employee.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.award.AwardType;
import com.terp.erp.employee.award.AwardTypeRepository;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.AwardService.AwardForm;
import com.terp.erp.employee.lifecycle.PromotionService.PromotionForm;
import com.terp.erp.employee.lifecycle.TransferService.TransferForm;
import com.terp.erp.employee.org.Designation;
import com.terp.erp.employee.org.DesignationRepository;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LifecycleTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private final DesignationRepository designations = mock(DesignationRepository.class);
    private final OrgService orgs = mock(OrgService.class);
    private final AwardTypeRepository awardTypes = mock(AwardTypeRepository.class);
    private final EmployeeAwardRepository awards = mock(EmployeeAwardRepository.class);
    private final EmployeePromotionRepository promotions = mock(EmployeePromotionRepository.class);
    private final EmployeeTransferRepository transfers = mock(EmployeeTransferRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);
    private final LifecycleSupport support = new LifecycleSupport(employees, media);
    private final AwardService awardService = new AwardService(awards, awardTypes, support);
    private final PromotionService promotionService = new PromotionService(promotions, designations, support, clock);
    private final TransferService transferService = new TransferService(transfers, orgs, support, clock);

    private Employee employee;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "LIFECYCLE_MANAGE");
        employee = new Employee();
        employee.setId(1L);
        employee.setCompanyId(10L);
        employee.setFullName("Test Employee");
        employee.setBranchId(100L);
        employee.setDepartmentId(200L);
        employee.setDesignationId(300L);
        when(employees.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(employee));
        when(employees.findAllById(any())).thenReturn(List.of(employee));
        when(designations.findByIdAndDeletedFalse(300L)).thenReturn(Optional.of(designation(300L, 200L, "Developer")));
        when(designations.findById(300L)).thenReturn(Optional.of(designation(300L, 200L, "Developer")));
        when(designations.findByIdAndDeletedFalse(301L)).thenReturn(Optional.of(designation(301L, 200L, "Lead")));
        when(designations.findById(301L)).thenReturn(Optional.of(designation(301L, 200L, "Lead")));
        when(designations.findByIdAndDeletedFalse(400L)).thenReturn(Optional.of(designation(400L, 999L, "Other")));
        when(promotions.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(transfers.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(awards.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(orgs.names(anyLong(), any(), any(), any())).thenReturn(new OrgService.Names(Map.of(), Map.of(), Map.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void awardTypeMustBeTheCompanysAndActive() {
        AwardType other = new AwardType();
        other.setId(5L);
        other.setCompanyId(11L);
        when(awardTypes.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(other));
        assertCode(() -> awardService.create(award(5L)), EEmployeeError.EMP_042);
        other.setCompanyId(10L);
        other.setStatus(EOrgStatus.INACTIVE);
        assertCode(() -> awardService.create(award(5L)), EEmployeeError.EMP_042);
        other.setStatus(EOrgStatus.ACTIVE);
        when(awardTypes.findById(5L)).thenReturn(Optional.of(other));
        assertThat(awardService.create(award(5L)).gift()).isEqualTo("Voucher");
    }

    @Test
    void promotionRulesAndApproval() {
        assertCode(() -> promotionService.create(promotion(300L, DAY, null)), EEmployeeError.EMP_047);
        assertCode(() -> promotionService.create(promotion(400L, DAY, null)), EEmployeeError.EMP_047);
        assertCode(() -> promotionService.create(promotion(301L, DAY.minusDays(1), null)), EEmployeeError.EMP_049);

        var pending = promotionService.create(promotion(301L, DAY, null));
        assertThat(pending.status()).isEqualTo(EDecision.PENDING);
        assertThat(pending.previousDesignation()).isEqualTo("Developer");
        assertThat(employee.getDesignationId()).isEqualTo(300L);

        var approved = promotionService.create(promotion(301L, DAY, EDecision.APPROVED));
        assertThat(approved.status()).isEqualTo(EDecision.APPROVED);
        assertThat(approved.decidedAt()).isEqualTo(clock.instant());
        assertThat(employee.getDesignationId()).isEqualTo(301L);
    }

    @Test
    void decidedPromotionIsLocked() {
        EmployeePromotion p = new EmployeePromotion();
        p.setId(9L);
        p.setCompanyId(10L);
        p.setEmployeeId(1L);
        p.setNewDesignationId(301L);
        p.setStatus(EDecision.REJECTED);
        when(promotions.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(p));
        assertCode(() -> promotionService.changeStatus(9L, EDecision.APPROVED), EEmployeeError.EMP_046);
        assertCode(() -> promotionService.delete(9L), EEmployeeError.EMP_046);
        p.setStatus(EDecision.PENDING);
        promotionService.changeStatus(9L, EDecision.REJECTED);
        assertThat(employee.getDesignationId()).isEqualTo(300L);
    }

    @Test
    void transferKeepsOmittedLevelsAndMovesOnApproval() {
        assertCode(() -> transferService.create(transfer(null, null, null)), EEmployeeError.EMP_048);
        var t = transferService.create(transfer(null, null, 301L));
        assertThat(t.changes()).containsExactly(TransferService.EChange.DESIGNATION);
        verify(orgs).assertChain(10L, 100L, 200L, 301L, 100L, 200L, 300L);

        EmployeeTransfer saved = new EmployeeTransfer();
        saved.setId(9L);
        saved.setCompanyId(10L);
        saved.setEmployeeId(1L);
        saved.setToBranchId(101L);
        saved.setToDepartmentId(201L);
        saved.setToDesignationId(302L);
        when(transfers.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(saved));
        var approved = transferService.approve(9L);
        assertThat(approved.status()).isEqualTo(EDecision.APPROVED);
        assertThat(approved.changes()).containsExactly(TransferService.EChange.BRANCH,
                TransferService.EChange.DEPARTMENT, TransferService.EChange.DESIGNATION);
        assertThat(employee.getBranchId()).isEqualTo(101L);
        assertThat(employee.getDepartmentId()).isEqualTo(201L);
        assertThat(employee.getDesignationId()).isEqualTo(302L);
        assertCode(() -> transferService.reject(9L), EEmployeeError.EMP_046);
    }

    @Test
    void approvalFailsWhenThePlacementIsNoLongerValid() {
        EmployeeTransfer saved = new EmployeeTransfer();
        saved.setId(9L);
        saved.setCompanyId(10L);
        saved.setEmployeeId(1L);
        saved.setToBranchId(101L);
        saved.setToDepartmentId(201L);
        when(transfers.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(saved));
        doThrow(ApiException.badRequest(EEmployeeError.EMP_003)).when(orgs)
                .assertChain(eq(10L), eq(101L), eq(201L), any(), any(), any(), any());
        assertCode(() -> transferService.approve(9L), EEmployeeError.EMP_003);
        assertThat(employee.getBranchId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(EDecision.PENDING);
    }

    @Test
    void otherCompanyRecordsAreNotFound() {
        employee.setCompanyId(11L);
        assertCode(() -> promotionService.create(promotion(301L, DAY, null)), EEmployeeError.EMP_001);
        verify(promotions, never()).save(any());
        EmployeeAward a = new EmployeeAward();
        a.setId(3L);
        a.setCompanyId(11L);
        when(awards.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(a));
        assertCode(() -> awardService.get(3L), EEmployeeError.EMP_043);
    }

    private static <T extends com.terp.erp.employee.common.BaseAuditableEntity> T withId(T t) {
        if (t.getId() == null) {
            t.setId(9L);
        }
        return t;
    }

    private static Designation designation(Long id, Long departmentId, String name) {
        Designation d = new Designation();
        d.setId(id);
        d.setCompanyId(10L);
        d.setDepartmentId(departmentId);
        d.setName(name);
        return d;
    }

    private static AwardForm award(Long typeId) {
        return new AwardForm(1L, typeId, DAY, " Voucher ", null, null, null);
    }

    private static PromotionForm promotion(Long designationId, LocalDate effective, EDecision status) {
        return new PromotionForm(1L, null, designationId, DAY, effective, "Great work", null, status);
    }

    private static TransferForm transfer(Long branch, Long department, Long designation) {
        return new TransferForm(1L, branch, department, designation, DAY, DAY, null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
