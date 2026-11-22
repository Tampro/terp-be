package com.terp.erp.employee.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.client.MediaClient.MediaFile;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** What every Employee Lifecycle page (docs/analysis/06 §7.2) shares: the employee column and attached files. */
@Component
@RequiredArgsConstructor
public class LifecycleSupport {
    private final EmployeeRepository employees;
    private final MediaClient media;

    /** The Employee column: photo, name, email. */
    public record EmployeeRef(String id, String employeeNo, String fullName, String email, String profileImageUrl) {
    }

    /** An attached media-library file with short-lived signed links. */
    public record FileRef(String id, String name, String mimeType, String viewUrl, String downloadUrl) {
    }

    public record Ref(String id, String name) {
    }

    /**
     * Refuses a status move the entity's table does not allow (400 EMP_096 "from X to Y").
     * TODO(assumption): the transition tables follow the ERP manual §15 (FR-ERP-ISS/CAR/REC-001); confirm with BA.
     */
    public static <E extends Enum<E>> void assertMove(Map<E, Set<E>> allowed, E from, E to) {
        if (!allowed.getOrDefault(from, Set.of()).contains(to)) {
            throw ApiException.badRequest(EEmployeeError.EMP_096, from.name(), to.name());
        }
    }

    /** The caller's own employee profile in their company (403 EMP_041 without one), like the announcement feed. */
    public Employee me() {
        com.terp.erp.security.ErpPrincipal p = com.terp.erp.security.CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            throw ApiException.forbidden(EEmployeeError.EMP_041);
        }
        return employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .orElseThrow(() -> ApiException.forbidden(EEmployeeError.EMP_041));
    }

    public static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    /** An employee of the caller's company (404 EMP_001 otherwise). */
    public Employee employee(Long id) {
        Employee e = employees.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_001));
        TenantGuard.assertSameCompany(e.getCompanyId(), EEmployeeError.EMP_001);
        return e;
    }

    public Map<Long, EmployeeRef> refs(Collection<Long> ids) {
        return employees.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, this::ref, (a, b) -> a));
    }

    public EmployeeRef ref(Employee e) {
        MediaFile photo = media.find(e.getProfileMediaId());
        return new EmployeeRef(e.getId().toString(), e.getEmployeeNo(), e.getFullName(), e.getEmail(),
                photo == null ? null : photo.viewUrl());
    }

    /** A media file of the company, or null; 400 EMP_012 when it is not in the company's library. */
    public Long file(Long mediaId, Long companyId) {
        if (mediaId != null) {
            media.require(mediaId, companyId);
        }
        return mediaId;
    }

    public FileRef fileRef(Long mediaId) {
        MediaFile f = media.find(mediaId);
        return f == null ? null : new FileRef(f.id(), f.displayName() != null ? f.displayName() : f.originalName(),
                f.mimeType(), f.viewUrl(), f.downloadUrl());
    }
}
