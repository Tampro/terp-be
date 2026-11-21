package com.terp.erp.employee.employee;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.common.FieldCipher;
import com.terp.erp.employee.config.EmployeeProperties;
import com.terp.erp.employee.document.DocumentService;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrgChartTest {
    private final EmployeeRepository repo = mock(EmployeeRepository.class);
    private final OrgChartService chart = new OrgChartService(repo, mock(OrgService.class), mock(IamClient.class),
            mock(MediaClient.class));
    private final EmployeeService service = new EmployeeService(repo, mock(OrgService.class), mock(DocumentService.class),
            mock(IamClient.class), mock(MediaClient.class),
            new FieldCipher(new EmployeeProperties("i", "m", Base64.getEncoder().encodeToString(new byte[32]))),
            mock(EntityManager.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void treeFollowsReportingLines() {
        Employee ceo = e(1L, "Ceo", null, EEmployeeStatus.ACTIVE);
        Employee hr = e(2L, "Hr", 1L, EEmployeeStatus.ACTIVE);
        Employee it = e(3L, "It", 1L, EEmployeeStatus.INACTIVE);
        Employee dev = e(4L, "Dev", 3L, EEmployeeStatus.PROBATION);
        Employee lone = e(5L, "Lone", 99L, EEmployeeStatus.ACTIVE); // manager not in the chart

        var top = chart.build(List.of(dev, it, hr, ceo, lone), Map.of());

        assertThat(top).extracting(EmployeeDtos.ChartNode::fullName).containsExactly("Ceo", "Lone");
        var ceoNode = top.get(0);
        assertThat(ceoNode.children()).extracting(EmployeeDtos.ChartNode::fullName).containsExactly("Hr", "It");
        assertThat(ceoNode.children().get(1).active()).isFalse();
        assertThat(ceoNode.children().get(1).children().get(0).active()).isTrue();
    }

    @Test
    void aLoopInOldDataStillShowsEveryone() {
        Employee a = e(1L, "A", 2L, EEmployeeStatus.ACTIVE);
        Employee b = e(2L, "B", 1L, EEmployeeStatus.ACTIVE);
        var top = chart.build(List.of(a, b), Map.of());
        assertThat(count(top)).isEqualTo(2);
    }

    @Test
    void cannotReportToSelfOrToOwnReport() {
        TestLogin.as(10L, "EMPLOYEE_UPDATE");
        Employee boss = e(1L, "Boss", null, EEmployeeStatus.ACTIVE);
        Employee mid = e(2L, "Mid", 1L, EEmployeeStatus.ACTIVE);
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(boss));
        when(repo.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(mid));

        assertError(() -> service.assertReportsTo(10L, 1L, 1L), EEmployeeError.EMP_028);
        // Boss would report to Mid, who reports to Boss.
        assertError(() -> service.assertReportsTo(10L, 2L, 1L), EEmployeeError.EMP_028);
        assertError(() -> service.assertReportsTo(11L, 1L, 5L), EEmployeeError.EMP_027);
        service.assertReportsTo(10L, 2L, 7L);
    }

    private static int count(List<EmployeeDtos.ChartNode> nodes) {
        return nodes.stream().mapToInt(n -> 1 + count(n.children())).sum();
    }

    private static Employee e(Long id, String name, Long reportsTo, EEmployeeStatus status) {
        Employee e = new Employee();
        e.setId(id);
        e.setCompanyId(10L);
        e.setFullName(name);
        e.setReportsToId(reportsTo);
        e.setStatus(status);
        return e;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(x -> ((ApiException) x).getErrorCode()).isEqualTo(code);
    }
}
