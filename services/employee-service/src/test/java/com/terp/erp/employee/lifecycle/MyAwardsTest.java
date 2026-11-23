package com.terp.erp.employee.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.award.AwardTypeRepository;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyAwardsTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeAwardRepository awards = mock(EmployeeAwardRepository.class);
    private final AwardTypeRepository types = mock(AwardTypeRepository.class);
    private final AwardService service = new AwardService(awards, types,
            new LifecycleSupport(employees, mock(MediaClient.class)), mock(OutboxPublisher.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesOnlyMyEmployeeInMyCompany() {
        Employee me = new Employee();
        me.setId(1L);
        me.setCompanyId(10L);
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(me));
        EmployeeAward a = new EmployeeAward();
        a.setId(3L);
        a.setCompanyId(10L);
        a.setEmployeeId(1L);
        a.setAwardTypeId(4L);
        var page = PageRequest.of(0, 20);
        when(awards.search(eq(10L), isNull(), eq(1L), isNull(), isNull(), eq(""), eq(page)))
                .thenReturn(new PageImpl<>(List.of(a), page, 1));
        TestLogin.as(10L);

        var out = service.mine(page);

        assertThat(out.getContent()).extracting(AwardService.AwardDto::id).containsExactly("3");
    }

    @Test
    void noProfileIsForbidden() {
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        TestLogin.as(10L);
        assertThatThrownBy(() -> service.mine(PageRequest.of(0, 20))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(awards, never()).search(anyLong(), any(), any(), any(), any(), any(), any());
    }
}
