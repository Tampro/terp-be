package com.terp.erp.employee.employee;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.common.FieldCipher;
import com.terp.erp.employee.document.DocumentService;
import com.terp.erp.employee.employee.EmployeeDtos.Filter;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Employment-type tabs, type / gender filters and GET /employees/me. */
class EmployeeTabsAndSelfTest {
    private final EmployeeRepository repo = mock(EmployeeRepository.class);
    private final OrgService orgs = mock(OrgService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final EmployeeService service = new EmployeeService(repo, orgs, mock(DocumentService.class),
            mock(IamClient.class), mock(MediaClient.class),
            new FieldCipher(new com.terp.erp.employee.config.EmployeeProperties("http://iam", "http://media",
                    Base64.getEncoder().encodeToString(new byte[32]))), em);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void typeCountsFillEveryTypeAndIgnoreTheTypeFilterButKeepGender() {
        TestLogin.as(10L, "EMPLOYEE_VIEW");
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Object[]> q = mock(CriteriaQuery.class, RETURNS_SELF);
        Root<Employee> root = mock(Root.class);
        Path<Object> typePath = mock(Path.class);
        Path<Object> genderPath = mock(Path.class);
        TypedQuery<Object[]> tq = mock(TypedQuery.class);
        when(em.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Object[].class)).thenReturn(q);
        when(q.from(Employee.class)).thenReturn(root);
        when(root.get("employmentType")).thenReturn(typePath);
        when(root.get("gender")).thenReturn(genderPath);
        when(em.createQuery(q)).thenReturn(tq);
        when(tq.getResultList()).thenReturn(List.of(new Object[]{EEmploymentType.FULL_TIME, 4L},
                new Object[]{EEmploymentType.CONTRACT, 1L}, new Object[]{null, 2L}));

        var counts = service.typeCounts(null, new Filter(null, null, null, null, null, null, null,
                EEmploymentType.PART_TIME, EGender.FEMALE));

        assertThat(counts.all()).isEqualTo(7);
        assertThat(counts.byType()).containsEntry(EEmploymentType.FULL_TIME, 4L)
                .containsEntry(EEmploymentType.CONTRACT, 1L).containsEntry(EEmploymentType.PART_TIME, 0L)
                .hasSize(EEmploymentType.values().length);
        verify(cb).equal(genderPath, EGender.FEMALE);
        verify(cb, never()).equal(typePath, EEmploymentType.PART_TIME);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersOnEmploymentTypeAndGender() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<Employee> root = mock(Root.class);
        Path<Object> typePath = mock(Path.class);
        Path<Object> genderPath = mock(Path.class);
        when(root.get("employmentType")).thenReturn(typePath);
        when(root.get("gender")).thenReturn(genderPath);
        EmployeeSpecs.of(10L, new Filter(null, null, null, null, null, null, null, EEmploymentType.TEMPORARY,
                EGender.MALE), true).toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(typePath, EEmploymentType.TEMPORARY);
        verify(cb).equal(genderPath, EGender.MALE);
    }

    @Test
    void meReturnsTheCallersOwnProfile() {
        TestLogin.as(10L, "EMPLOYEE_SELF");
        Employee e = new Employee();
        e.setId(3L);
        e.setCompanyId(10L);
        e.setFullName("Self Test");
        e.setShiftId(4L);
        when(repo.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(e));
        when(orgs.names(anyLong(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new OrgService.Names(Map.of(), Map.of(), Map.of()));
        var d = service.me();
        assertThat(d.id()).isEqualTo("3");
        assertThat(d.companyId()).isEqualTo("10");
        assertThat(d.shiftId()).isEqualTo("4");
        assertThat(d.banking()).isNull();
    }

    @Test
    void meWithoutProfileIsNotFoundAndOnlyTheTokensCompanyIsSearched() {
        TestLogin.as(10L, "EMPLOYEE_SELF");
        // the same user id in another company must never match
        Employee other = new Employee();
        other.setId(8L);
        other.setCompanyId(11L);
        when(repo.findFirstByCompanyIdAndUserIdAndDeletedFalse(11L, 5L)).thenReturn(Optional.of(other));
        when(repo.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        assertError(service::me, EEmployeeError.EMP_001);
        verify(repo, never()).findFirstByCompanyIdAndUserIdAndDeletedFalse(org.mockito.ArgumentMatchers.eq(11L),
                any());

        TestLogin.superAdmin();
        assertError(service::me, EEmployeeError.EMP_041);
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
