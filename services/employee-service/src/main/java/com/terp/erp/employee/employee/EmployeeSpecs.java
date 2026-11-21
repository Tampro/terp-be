package com.terp.erp.employee.employee;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import com.terp.erp.employee.document.EmployeeDocument;
import com.terp.erp.employee.employee.EmployeeDtos.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class EmployeeSpecs {
    private EmployeeSpecs() {
    }

    /** @param withStatus false for the status tabs, which count every status under the other filters */
    static Specification<Employee> of(Long companyId, Filter f, boolean withStatus) {
        return of(companyId, f, withStatus, Set.of());
    }

    /**
     * @param requiredTypeIds Required document types of the company; with filter missingRequiredDocuments = true an
     *                        employee matches when it has no document of at least one of them
     */
    static Specification<Employee> of(Long companyId, Filter f, boolean withStatus, Set<Long> requiredTypeIds) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("companyId"), companyId));
            p.add(cb.isFalse(root.get("deleted")));
            if (f.keyword() != null && !f.keyword().isBlank()) {
                String like = "%" + escape(f.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("employeeNo")), like, '\\'),
                        cb.like(cb.lower(root.get("employeeCode")), like, '\\')));
            }
            if (f.ids() != null) {
                p.add(f.ids().isEmpty() ? cb.disjunction() : root.get("id").in(f.ids()));
            }
            if (f.branchId() != null) {
                p.add(cb.equal(root.get("branchId"), f.branchId()));
            }
            if (f.departmentId() != null) {
                p.add(cb.equal(root.get("departmentId"), f.departmentId()));
            }
            if (f.designationId() != null) {
                p.add(cb.equal(root.get("designationId"), f.designationId()));
            }
            if (withStatus && f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (Boolean.TRUE.equals(f.missingRequiredDocuments())) {
                List<Predicate> missing = new ArrayList<>();
                for (Long typeId : requiredTypeIds) {
                    Subquery<Long> has = query.subquery(Long.class);
                    Root<EmployeeDocument> d = has.from(EmployeeDocument.class);
                    has.select(d.get("id")).where(cb.equal(d.get("employeeId"), root.get("id")),
                            cb.equal(d.get("documentTypeId"), typeId));
                    missing.add(cb.not(cb.exists(has)));
                }
                // No Required type: nobody is missing anything.
                p.add(missing.isEmpty() ? cb.disjunction() : cb.or(missing.toArray(Predicate[]::new)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** A keyword is text, not a pattern: % and _ match themselves. */
    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
