package com.terp.erp.employee.employee;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.employee.EmployeeDtos.ChartNode;
import com.terp.erp.employee.employee.EmployeeDtos.OrgChart;
import com.terp.erp.employee.org.OrgService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Organization Chart (docs/analysis/06 §6.2): the company on top, everyone under the person they report to.
 * Someone without a manager in the chart hangs under the company.
 * TODO(assumption): TERMINATED employees are left out; PROBATION shows the green (active) dot — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class OrgChartService {
    private final EmployeeRepository employees;
    private final OrgService orgs;
    private final IamClient iam;
    private final MediaClient media;

    @Transactional(readOnly = true)
    public OrgChart chart(Long requestedCompanyId) {
        Long companyId = EmployeeService.company(requestedCompanyId);
        List<Employee> people = employees.findByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(e -> e.getStatus() != EEmployeeStatus.TERMINATED).toList();
        Map<Long, String> designations = orgs.names(companyId, Set.of(), Set.of(),
                people.stream().map(Employee::getDesignationId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .designations();
        String companyName = iam.company(companyId).name();
        return new OrgChart(companyName, people.size(), build(people, designations));
    }

    List<ChartNode> build(List<Employee> people, Map<Long, String> designations) {
        Set<Long> ids = people.stream().map(Employee::getId).collect(Collectors.toSet());
        Map<Long, List<Employee>> byManager = new HashMap<>();
        List<Employee> roots = new ArrayList<>();
        for (Employee e : people) {
            if (e.getReportsToId() != null && ids.contains(e.getReportsToId())) {
                byManager.computeIfAbsent(e.getReportsToId(), k -> new ArrayList<>()).add(e);
            } else {
                roots.add(e);
            }
        }
        Set<Long> placed = new HashSet<>();
        List<ChartNode> top = nodes(roots, byManager, designations, placed);
        // Rows caught in a loop (bad data written before the check existed) would never be reached from a root.
        List<Employee> orphans = people.stream().filter(e -> !placed.contains(e.getId())).toList();
        if (!orphans.isEmpty()) {
            List<Employee> breakLoop = new ArrayList<>(orphans);
            breakLoop.forEach(e -> byManager.values().forEach(l -> l.remove(e)));
            top = new ArrayList<>(top);
            top.addAll(nodes(breakLoop, byManager, designations, placed));
        }
        return top;
    }

    private List<ChartNode> nodes(List<Employee> level, Map<Long, List<Employee>> byManager,
                                  Map<Long, String> designations, Set<Long> placed) {
        List<ChartNode> out = new ArrayList<>();
        level.stream().sorted(Comparator.comparing(Employee::getFullName)).forEach(e -> {
            if (!placed.add(e.getId())) {
                return;
            }
            var photo = media.find(e.getProfileMediaId());
            out.add(new ChartNode(e.getId().toString(), e.getFullName(),
                    e.getDesignationId() == null ? null : designations.get(e.getDesignationId()),
                    photo == null ? null : photo.viewUrl(),
                    e.getStatus() == EEmployeeStatus.ACTIVE || e.getStatus() == EEmployeeStatus.PROBATION,
                    nodes(byManager.getOrDefault(e.getId(), List.of()), byManager, designations, placed)));
        });
        return out;
    }
}
