package com.terp.erp.asset.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.asset.assignment.AssetAssignment.EStatus;
import com.terp.erp.asset.client.EmployeeClient;
import com.terp.erp.asset.client.EmployeeClient.Employee;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.Asset.ECondition;
import com.terp.erp.asset.item.AssetService;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * Assign and Return (docs/analysis/06 §8.2.2, PO 2026-11-20: status follows automatically). Only an AVAILABLE asset is
 * assigned (AST_018) and becomes ASSIGNED; Return closes the open assignment and makes it AVAILABLE. The asset's
 * condition becomes the checkout / check-in condition.
 */
@Service
@RequiredArgsConstructor
public class AssetAssignmentService {
    private final AssetAssignmentRepository assignments;
    private final AssetService assets;
    private final EmployeeClient employees;
    private final Clock clock;

    public record AssignForm(
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Omitted = today") LocalDate checkoutDate,
            LocalDate expectedReturnDate,
            @Schema(description = "Omitted = the asset's current condition") ECondition checkoutCondition,
            @Size(max = 1000) String notes) {
    }

    public record ReturnForm(
            @Schema(description = "Omitted = today") LocalDate checkinDate,
            @Schema(description = "Omitted = the asset's current condition") ECondition checkinCondition,
            @Size(max = 1000) String notes) {
    }

    public record EmployeeInfo(String id, String employeeNo, String fullName, String email, String profileImageUrl) {
    }

    public record AssignmentDto(String id, String assetId, EmployeeInfo employee, LocalDate checkoutDate,
                                LocalDate expectedReturnDate, ECondition checkoutCondition, String checkoutNotes,
                                LocalDate returnDate, ECondition returnCondition, String returnNotes, EStatus status) {
    }

    @Transactional
    public AssignmentDto assign(Long assetId, AssignForm f) {
        Asset a = assets.require(assetId);
        if (a.getStatus() != Asset.EStatus.AVAILABLE) {
            throw ApiException.badRequest(EAssetError.AST_018);
        }
        LocalDate checkout = f.checkoutDate() != null ? f.checkoutDate() : Texts.today(clock);
        if (f.expectedReturnDate() != null && f.expectedReturnDate().isBefore(checkout)) {
            throw ApiException.badRequest(EAssetError.AST_019);
        }
        // employee-service answers EMP_001 for another company's employee (relayed)
        Employee e = employees.employee(f.employeeId());
        if (!String.valueOf(a.getCompanyId()).equals(e.companyId())) {
            throw ApiException.notFound(EAssetError.AST_005);
        }
        AssetAssignment s = new AssetAssignment();
        s.setCompanyId(a.getCompanyId());
        s.setAssetId(a.getId());
        s.setEmployeeId(Long.valueOf(e.id()));
        s.setEmployeeName(e.fullName());
        s.setEmployeeNo(e.employeeNo());
        s.setEmployeeEmail(e.email());
        s.setCheckoutDate(checkout);
        s.setExpectedReturnDate(f.expectedReturnDate());
        s.setCheckoutCondition(f.checkoutCondition() != null ? f.checkoutCondition() : a.getCondition());
        s.setCheckoutNotes(Texts.blankToNull(f.notes()));
        a.setStatus(Asset.EStatus.ASSIGNED);
        a.setCondition(s.getCheckoutCondition());
        return dto(assignments.save(s), e);
    }

    @Transactional
    public AssignmentDto giveBack(Long assetId, ReturnForm f) {
        Asset a = assets.require(assetId);
        AssetAssignment s = assignments.findFirstByAssetIdAndStatus(a.getId(), EStatus.ASSIGNED)
                .filter(x -> a.getStatus() == Asset.EStatus.ASSIGNED)
                .orElseThrow(() -> ApiException.badRequest(EAssetError.AST_020));
        LocalDate checkin = f.checkinDate() != null ? f.checkinDate() : Texts.today(clock);
        if (checkin.isBefore(s.getCheckoutDate())) {
            throw ApiException.badRequest(EAssetError.AST_021);
        }
        s.setReturnDate(checkin);
        s.setReturnCondition(f.checkinCondition() != null ? f.checkinCondition() : a.getCondition());
        s.setReturnNotes(Texts.blankToNull(f.notes()));
        s.setStatus(EStatus.RETURNED);
        a.setStatus(Asset.EStatus.AVAILABLE);
        a.setCondition(s.getReturnCondition());
        return dto(s, null);
    }

    /** Assignment History, newest checkout first; photos from employee-service. */
    @Transactional(readOnly = true)
    public Page<AssignmentDto> history(Long assetId, Pageable pageable) {
        Asset a = assets.require(assetId);
        Page<AssetAssignment> page = assignments.findByAssetIdOrderByCheckoutDateDescIdDesc(a.getId(), pageable);
        Map<Long, Employee> people = page.isEmpty() ? Map.of()
                : employees.byIds(page.map(AssetAssignment::getEmployeeId).toList(), a.getCompanyId());
        return page.map(s -> dto(s, people.get(s.getEmployeeId())));
    }

    public static AssignmentDto dto(AssetAssignment s, Employee e) {
        EmployeeInfo info = new EmployeeInfo(s.getEmployeeId().toString(),
                e != null ? e.employeeNo() : s.getEmployeeNo(), e != null ? e.fullName() : s.getEmployeeName(),
                e != null ? e.email() : s.getEmployeeEmail(), e == null ? null : e.profileImageUrl());
        return new AssignmentDto(s.getId().toString(), s.getAssetId().toString(), info, s.getCheckoutDate(),
                s.getExpectedReturnDate(), s.getCheckoutCondition(), s.getCheckoutNotes(), s.getReturnDate(),
                s.getReturnCondition(), s.getReturnNotes(), s.getStatus());
    }
}
