package com.terp.erp.asset.assignment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.asset.TestLogin;
import com.terp.erp.asset.assignment.AssetAssignmentService.AssignForm;
import com.terp.erp.asset.assignment.AssetAssignmentService.ReturnForm;
import com.terp.erp.asset.client.EmployeeClient;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.Asset.ECondition;
import com.terp.erp.asset.item.Asset.EStatus;
import com.terp.erp.asset.item.AssetService;
import com.terp.erp.asset.maintenance.AssetMaintenance;
import com.terp.erp.asset.maintenance.AssetMaintenanceRepository;
import com.terp.erp.asset.maintenance.AssetMaintenanceService;
import com.terp.erp.asset.maintenance.AssetMaintenanceService.MaintenanceForm;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetActivityTest {
    // 2026-11-20 20:00 UTC is already 2026-11-21 in Vietnam
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T20:00:00Z"), ZoneOffset.UTC);
    private final AssetAssignmentRepository assignments = mock(AssetAssignmentRepository.class);
    private final AssetMaintenanceRepository records = mock(AssetMaintenanceRepository.class);
    private final AssetService assets = mock(AssetService.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final AssetAssignmentService assignService = new AssetAssignmentService(assignments, assets, employees,
            CLOCK);
    private final AssetMaintenanceService maintenance = new AssetMaintenanceService(records, assets, CLOCK);
    private final Asset asset = new Asset();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "ASSET_MANAGE");
        asset.setId(9L);
        asset.setCompanyId(10L);
        asset.setCondition(ECondition.GOOD);
        when(assets.require(9L)).thenReturn(asset);
        when(employees.employee(7L)).thenReturn(new EmployeeClient.Employee("7", "10", "EMP000007", "Test Seven",
                "seven@example.invalid", null, "ACTIVE", null, null));
        when(assignments.save(any())).thenAnswer(i -> {
            AssetAssignment s = i.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(records.save(any())).thenAnswer(i -> {
            AssetMaintenance m = i.getArgument(0);
            m.setId(2L);
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignAndReturnMoveTheStatus() {
        var s = assignService.assign(9L, new AssignForm(7L, null, null, ECondition.NEW, " Laptop for work "));
        assertThat(s.checkoutDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(s.checkoutNotes()).isEqualTo("Laptop for work");
        assertThat(asset.getStatus()).isEqualTo(EStatus.ASSIGNED);
        assertThat(asset.getCondition()).isEqualTo(ECondition.NEW);
        assertCode(() -> assignService.assign(9L, new AssignForm(7L, null, null, null, null)), EAssetError.AST_018);

        AssetAssignment open = new AssetAssignment();
        open.setId(1L);
        open.setAssetId(9L);
        open.setEmployeeId(7L);
        open.setCheckoutDate(LocalDate.of(2026, 11, 21));
        when(assignments.findFirstByAssetIdAndStatus(9L, AssetAssignment.EStatus.ASSIGNED))
                .thenReturn(Optional.of(open));
        assertCode(() -> assignService.giveBack(9L, new ReturnForm(LocalDate.of(2026, 11, 1), null, null)),
                EAssetError.AST_021);
        var r = assignService.giveBack(9L, new ReturnForm(null, ECondition.FAIR, null));
        assertThat(r.status()).isEqualTo(AssetAssignment.EStatus.RETURNED);
        assertThat(asset.getStatus()).isEqualTo(EStatus.AVAILABLE);
        assertThat(asset.getCondition()).isEqualTo(ECondition.FAIR);
        assertCode(() -> assignService.giveBack(9L, new ReturnForm(null, null, null)), EAssetError.AST_020);
        assertCode(() -> assignService.assign(9L, new AssignForm(7L, LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 20), null, null)), EAssetError.AST_019);
    }

    @Test
    void maintenanceMovesTheStatus() {
        var later = maintenance.create(9L, form(LocalDate.of(2026, 12, 1), null));
        assertThat(later.status()).isEqualTo(AssetMaintenance.EStatus.SCHEDULED);
        assertThat(asset.getStatus()).isEqualTo(EStatus.AVAILABLE);

        var now = maintenance.create(9L, form(null, null));
        assertThat(now.status()).isEqualTo(AssetMaintenance.EStatus.IN_PROGRESS);
        assertThat(now.startDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(asset.getStatus()).isEqualTo(EStatus.MAINTENANCE);

        AssetMaintenance m = new AssetMaintenance();
        m.setId(2L);
        m.setAssetId(9L);
        m.setStartDate(LocalDate.of(2026, 11, 21));
        m.setStatus(AssetMaintenance.EStatus.IN_PROGRESS);
        when(records.findByIdAndAssetId(2L, 9L)).thenReturn(Optional.of(m));
        maintenance.update(9L, 2L, form(null, AssetMaintenance.EStatus.COMPLETED));
        assertThat(asset.getStatus()).isEqualTo(EStatus.AVAILABLE);
        assertCode(() -> maintenance.update(9L, 2L, form(null, null)), EAssetError.AST_026);

        asset.setStatus(EStatus.ASSIGNED);
        assertCode(() -> maintenance.create(9L, form(null, null)), EAssetError.AST_023);
        assertCode(() -> maintenance.create(9L, new MaintenanceForm(AssetMaintenance.EType.REPAIR,
                LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1), null, null, null, null)), EAssetError.AST_024);
        asset.setStatus(EStatus.DISPOSED);
        assertCode(() -> maintenance.create(9L, form(null, null)), EAssetError.AST_022);
    }

    private static MaintenanceForm form(LocalDate start, AssetMaintenance.EStatus status) {
        return new MaintenanceForm(AssetMaintenance.EType.PREVENTIVE, start, null, null, "Clean fans", null, status);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAssetError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
