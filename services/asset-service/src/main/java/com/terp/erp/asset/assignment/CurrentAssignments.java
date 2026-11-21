package com.terp.erp.asset.assignment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.terp.erp.asset.assignment.AssetAssignmentService.AssignmentDto;
import com.terp.erp.asset.client.EmployeeClient;
import com.terp.erp.asset.client.EmployeeClient.Employee;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The open assignment of each asset: the Assigned To column and the Currently Assigned panel. */
@Component
@RequiredArgsConstructor
public class CurrentAssignments {
    private final AssetAssignmentRepository assignments;
    private final EmployeeClient employees;

    /** By asset id; photo and current name from employee-service (the caller needs EMPLOYEE_VIEW). */
    public Map<Long, AssignmentDto> of(Collection<Long> assetIds, Long companyId) {
        List<AssetAssignment> open = assetIds.isEmpty() ? List.of()
                : assignments.findByAssetIdInAndStatus(assetIds, AssetAssignment.EStatus.ASSIGNED);
        Map<Long, Employee> people = open.isEmpty() ? Map.of()
                : employees.byIds(open.stream().map(AssetAssignment::getEmployeeId).toList(), companyId);
        Map<Long, AssignmentDto> out = new HashMap<>();
        open.forEach(s -> out.put(s.getAssetId(), AssetAssignmentService.dto(s, people.get(s.getEmployeeId()))));
        return out;
    }
}
