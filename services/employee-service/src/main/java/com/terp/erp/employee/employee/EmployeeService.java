package com.terp.erp.employee.employee;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.client.MediaClient.MediaFile;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.common.FieldCipher;
import com.terp.erp.employee.document.DocumentService;
import com.terp.erp.employee.employee.EmployeeDtos.BankingDto;
import com.terp.erp.employee.employee.EmployeeDtos.BankingForm;
import com.terp.erp.employee.employee.EmployeeDtos.DocumentDto;
import com.terp.erp.employee.employee.EmployeeDtos.DocumentForm;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeCreateForm;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeDetail;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeListItem;
import com.terp.erp.employee.employee.EmployeeDtos.MyEmployeeRef;
import com.terp.erp.employee.employee.EmployeeDtos.Filter;
import com.terp.erp.employee.employee.EmployeeDtos.ProfileForm;
import com.terp.erp.employee.employee.EmployeeDtos.Ref;
import com.terp.erp.employee.employee.EmployeeDtos.StatusCounts;
import com.terp.erp.employee.employee.EmployeeDtos.TypeCounts;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.employee.org.OrgService.Names;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Employees of a company (docs/analysis/06 §6.1). Every employee has a login account in IAM, created with the
 * caller's token so IAM's own rules (IAM_USER_CREATE, plan user limit, unique username) apply.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {
    public static final String BANKING_VIEW = "EMPLOYEE_BANKING_VIEW";
    static final long MAX_PROFILE_IMAGE_BYTES = 2L * 1024 * 1024;

    private final EmployeeRepository employees;
    private final OrgService orgs;
    private final DocumentService documents;
    private final IamClient iam;
    private final MediaClient media;
    private final FieldCipher cipher;
    private final EntityManager em;

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<EmployeeListItem> search(Long requestedCompanyId, Filter filter, Pageable pageable) {
        if (filter.ids() != null && filter.ids().size() > 100) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
        Long companyId = company(requestedCompanyId);
        Page<Employee> page = employees.findAll(EmployeeSpecs.of(companyId, filter, true, required(companyId, filter)),
                pageable);
        Names names = namesOf(companyId, page.getContent());
        return page.map(e -> toListItem(e, names, true));
    }

    /** Rows of the export: same filters, no paging, no photo links. */
    @Transactional(readOnly = true)
    public List<EmployeeListItem> searchAll(Long companyId, Filter filter) {
        List<Employee> rows = employees.findAll(EmployeeSpecs.of(companyId, filter, true, required(companyId, filter)),
                org.springframework.data.domain.Sort.by("employeeNo"));
        Names names = namesOf(companyId, rows);
        return rows.stream().map(e -> toListItem(e, names, false)).toList();
    }

    /** Counts of the status tabs under the other filters (keyword, branch, department, designation). */
    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, Filter filter) {
        Long companyId = company(requestedCompanyId);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Employee> root = q.from(Employee.class);
        q.multiselect(root.get("status"), cb.count(root))
                .where(EmployeeSpecs.of(companyId, filter, false, required(companyId, filter)).toPredicate(root, q, cb))
                .groupBy(root.get("status"));
        Map<EEmployeeStatus, Long> by = new EnumMap<>(EEmployeeStatus.class);
        for (Object[] row : em.createQuery(q).getResultList()) {
            by.put((EEmployeeStatus) row[0], (Long) row[1]);
        }
        long all = by.values().stream().mapToLong(Long::longValue).sum();
        return new StatusCounts(all, by.getOrDefault(EEmployeeStatus.ACTIVE, 0L),
                by.getOrDefault(EEmployeeStatus.INACTIVE, 0L), by.getOrDefault(EEmployeeStatus.PROBATION, 0L),
                by.getOrDefault(EEmployeeStatus.TERMINATED, 0L));
    }

    /** Counts of the employment-type tabs under the other filters (the type itself excluded). */
    @Transactional(readOnly = true)
    public TypeCounts typeCounts(Long requestedCompanyId, Filter filter) {
        Long companyId = company(requestedCompanyId);
        Filter f = filter.withoutType();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Employee> root = q.from(Employee.class);
        q.multiselect(root.get("employmentType"), cb.count(root))
                .where(EmployeeSpecs.of(companyId, f, true, required(companyId, f)).toPredicate(root, q, cb))
                .groupBy(root.get("employmentType"));
        Map<EEmploymentType, Long> by = new EnumMap<>(EEmploymentType.class);
        for (EEmploymentType t : EEmploymentType.values()) {
            by.put(t, 0L);
        }
        long all = 0;
        for (Object[] row : em.createQuery(q).getResultList()) {
            long n = (Long) row[1];
            all += n;
            if (row[0] != null) {
                by.put((EEmploymentType) row[0], n);
            }
        }
        return new TypeCounts(all, by);
    }

    /**
     * The caller's own profile: matched on the token's company and user only, so another company's user id never
     * matches. Super admin (no company) = 403 EMP_041; no linked profile = 404 EMP_001.
     */
    @Transactional(readOnly = true)
    public EmployeeDetail me() {
        com.terp.erp.security.ErpPrincipal p = CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            throw ApiException.forbidden(EEmployeeError.EMP_041);
        }
        return toDetail(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_001)));
    }

    /** The caller's own employee id, for "my …" features of other services that need no profile permission. */
    @Transactional(readOnly = true)
    public MyEmployeeRef myRef() {
        com.terp.erp.security.ErpPrincipal p = CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            throw ApiException.forbidden(EEmployeeError.EMP_041);
        }
        Employee e = employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_001));
        return new MyEmployeeRef(String.valueOf(e.getId()), String.valueOf(e.getCompanyId()), e.getFullName());
    }

    @Transactional(readOnly = true)
    public EmployeeDetail get(Long id) {
        return toDetail(require(id));
    }

    // ------------------------------------------------------------------ writes

    /**
     * Order: every local check first, then the IAM account, then the row. If the insert still fails after IAM
     * succeeded, the account is locked again (compensation) so no usable login is left without an employee.
     */
    @Transactional
    public EmployeeDetail create(Long requestedCompanyId, EmployeeCreateForm form) {
        Long companyId = company(requestedCompanyId);
        String email = form.email().trim().toLowerCase(Locale.ROOT);
        ProfileForm p = form.profile();
        if (employees.existsByCompanyIdAndEmailAndDeletedFalseAndIdNot(companyId, email, -1L)) {
            throw ApiException.badRequest(EEmployeeError.EMP_007);
        }
        assertProfile(companyId, p, -1L, null, null, null);
        List<DocumentForm> docs = form.documents() == null ? List.of() : form.documents();
        // Documents are checked before the account exists: a refused file must not leave an account behind.
        for (DocumentForm d : docs) {
            documents.assertAcceptable(companyId, d);
        }
        IamClient.CompanyContext ctx = iam.company(companyId);
        if (ctx.maxEmployees() != null && employees.countByCompanyIdAndDeletedFalse(companyId) >= ctx.maxEmployees()) {
            throw ApiException.badRequest(EEmployeeError.EMP_009, ctx.maxEmployees());
        }

        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setEmail(email);
        e.setEmployeeNo(nextEmployeeNo(companyId));
        apply(e, p);

        Long userId = iam.createAccount(companyId, email, p.fullName().trim(), form.password());
        try {
            e.setUserId(userId);
            e.setLoginEnabled(true);
            Employee saved = employees.saveAndFlush(e);
            for (DocumentForm d : docs) {
                documents.add(companyId, saved.getId(), d);
            }
            return toDetail(saved);
        } catch (RuntimeException ex) {
            iam.lockQuietly(userId);
            throw ex;
        }
    }

    /** Full replacement of the profile; banking is replaced only for callers with EMPLOYEE_BANKING_VIEW. */
    @Transactional
    public EmployeeDetail update(Long id, ProfileForm form) {
        Employee e = require(id);
        assertProfile(e.getCompanyId(), form, id, e.getBranchId(), e.getDepartmentId(), e.getDesignationId());
        apply(e, form);
        return toDetail(e);
    }

    @Transactional
    public EmployeeDetail changeStatus(Long id, EEmployeeStatus status) {
        Employee e = require(id);
        e.setStatus(status);
        return toDetail(e);
    }

    /** Locks or unlocks the IAM account (Keycloak disabled + sessions ended when locked). */
    @Transactional
    public EmployeeDetail setLogin(Long id, boolean enabled) {
        Employee e = requireWithLogin(id);
        iam.setLoginEnabled(e.getUserId(), enabled);
        e.setLoginEnabled(enabled);
        return toDetail(e);
    }

    @Transactional(readOnly = true)
    public void changePassword(Long id, String newPassword, String confirmPassword) {
        if (!Objects.equals(newPassword, confirmPassword)) {
            throw ApiException.badRequest(EEmployeeError.EMP_020);
        }
        iam.changePassword(requireWithLogin(id).getUserId(), newPassword);
    }

    /**
     * "Permanently removes the employee from the platform": the row is soft-deleted (history of payroll and
     * attendance will point at it) and the account is locked first, so a failure leaves the employee untouched.
     */
    @Transactional
    public void delete(Long id) {
        Employee e = require(id);
        if (e.getUserId() != null && e.isLoginEnabled()) {
            iam.setLoginEnabled(e.getUserId(), false);
        }
        e.setLoginEnabled(false);
        e.setDeleted(true);
        // Direct reports move up to the deleted employee's manager, so the chart keeps its shape.
        for (Employee r : employees.findByReportsToIdAndDeletedFalse(e.getId())) {
            r.setReportsToId(e.getReportsToId());
        }
    }

    // ------------------------------------------------------------------ documents (employee checked here)

    @Transactional
    public DocumentDto addDocument(Long id, DocumentForm form) {
        Employee e = require(id);
        return documents.add(e.getCompanyId(), e.getId(), form);
    }

    @Transactional
    public DocumentDto verifyDocument(Long id, Long documentId, boolean verified) {
        Employee e = require(id);
        return documents.verify(e.getCompanyId(), e.getId(), documentId, verified);
    }

    @Transactional
    public void deleteDocument(Long id, Long documentId) {
        documents.delete(require(id).getId(), documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> documents(Long id) {
        Employee e = require(id);
        return documents.list(e.getCompanyId(), e.getId());
    }

    // ------------------------------------------------------------------ shared with letters / import

    public Employee require(Long id) {
        Employee e = employees.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_001));
        TenantGuard.assertSameCompany(e.getCompanyId(), EEmployeeError.EMP_001);
        return e;
    }

    public static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    // ------------------------------------------------------------------ helpers

    private Employee requireWithLogin(Long id) {
        Employee e = require(id);
        if (e.getUserId() == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_013);
        }
        return e;
    }

    private void assertProfile(Long companyId, ProfileForm p, Long selfId, Long currentBranchId,
                               Long currentDepartmentId, Long currentDesignationId) {
        String code = blankToNull(p.employeeCode());
        if (code != null && employees.existsByCompanyIdAndEmployeeCodeAndDeletedFalseAndIdNot(companyId, code, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_008);
        }
        orgs.assertChain(companyId, p.branchId(), p.departmentId(), p.designationId(), currentBranchId,
                currentDepartmentId, currentDesignationId);
        assertReportsTo(companyId, p.reportsToId(), selfId);
        if (p.profileImageId() != null) {
            MediaFile f = media.require(p.profileImageId(), companyId);
            if (!"IMAGE".equals(f.kind()) || f.sizeBytes() > MAX_PROFILE_IMAGE_BYTES) {
                throw ApiException.badRequest(EEmployeeError.EMP_018, "2 MB");
            }
        }
    }

    /**
     * The manager must be a live employee of the same company, and the line must not loop back: walking up from the
     * manager must never reach the employee being edited (chains are short; bounded to be safe on bad data).
     */
    void assertReportsTo(Long companyId, Long reportsToId, Long selfId) {
        if (reportsToId == null) {
            return;
        }
        if (Objects.equals(reportsToId, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_028);
        }
        Employee m = employees.findByIdAndDeletedFalse(reportsToId)
                .filter(x -> Objects.equals(x.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_027));
        Long up = m.getReportsToId();
        for (int i = 0; up != null && i < 1000; i++) {
            if (Objects.equals(up, selfId)) {
                throw ApiException.badRequest(EEmployeeError.EMP_028);
            }
            up = employees.findByIdAndDeletedFalse(up).map(Employee::getReportsToId).orElse(null);
        }
    }

    private Ref reportsTo(Employee e) {
        if (e.getReportsToId() == null) {
            return null;
        }
        return employees.findByIdAndDeletedFalse(e.getReportsToId())
                .map(m -> new Ref(m.getId().toString(), m.getFullName())).orElse(null);
    }

    private void apply(Employee e, ProfileForm p) {
        e.setFullName(p.fullName().trim());
        e.setEmployeeCode(blankToNull(p.employeeCode()));
        e.setPhone(blankToNull(p.phone()));
        e.setDateOfBirth(p.dateOfBirth());
        e.setGender(p.gender());
        e.setProfileMediaId(p.profileImageId());
        e.setBranchId(p.branchId());
        e.setDepartmentId(p.departmentId());
        e.setDesignationId(p.designationId());
        e.setDateOfJoining(p.dateOfJoining());
        if (p.dateOfLeaving() != null && p.dateOfLeaving().isBefore(p.dateOfJoining())) {
            throw ApiException.badRequest(EEmployeeError.EMP_093);
        }
        e.setDateOfLeaving(p.dateOfLeaving());
        e.setEmploymentType(p.employmentType());
        e.setStatus(p.status());
        e.setShiftId(p.shiftId());
        e.setAttendancePolicyId(p.attendancePolicyId());
        e.setReportsToId(p.reportsToId());
        e.setAddressLine1(blankToNull(p.addressLine1()));
        e.setAddressLine2(blankToNull(p.addressLine2()));
        e.setCity(blankToNull(p.city()));
        e.setState(blankToNull(p.state()));
        e.setCountry(blankToNull(p.country()));
        e.setPostalCode(blankToNull(p.postalCode()));
        e.setEmergencyName(blankToNull(p.emergencyName()));
        e.setEmergencyRelationship(blankToNull(p.emergencyRelationship()));
        e.setEmergencyPhone(blankToNull(p.emergencyPhone()));
        if (CurrentUser.get().has(BANKING_VIEW)) {
            BankingForm b = p.banking();
            e.setBankName(b == null ? null : blankToNull(b.bankName()));
            e.setAccountHolderName(b == null ? null : blankToNull(b.accountHolderName()));
            e.setAccountNumberEnc(b == null ? null : cipher.encrypt(b.accountNumber()));
            e.setBankIdentifierCode(b == null ? null : blankToNull(b.bankIdentifierCode()));
            e.setBankBranch(b == null ? null : blankToNull(b.bankBranch()));
            e.setTaxPayerIdEnc(b == null ? null : cipher.encrypt(b.taxPayerId()));
            e.setBaseSalary(b == null ? null : b.baseSalary());
            e.setHoursPerDay(b == null ? null : b.hoursPerDay());
            e.setDaysPerWeek(b == null ? null : b.daysPerWeek());
            e.setRatePerHour(b == null ? null : b.ratePerHour());
        }
    }

    private String nextEmployeeNo(Long companyId) {
        employees.ensureSequence(companyId);
        long n = employees.lockSequence(companyId);
        employees.bumpSequence(companyId);
        return formatEmployeeNo(n);
    }

    static String formatEmployeeNo(long n) {
        return String.format(Locale.ROOT, "EMP%06d", n);
    }

    private Names namesOf(Long companyId, List<Employee> rows) {
        return orgs.names(companyId, ids(rows, Employee::getBranchId), ids(rows, Employee::getDepartmentId),
                ids(rows, Employee::getDesignationId));
    }

    private static Set<Long> ids(List<Employee> rows, java.util.function.Function<Employee, Long> f) {
        return rows.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private EmployeeListItem toListItem(Employee e, Names n, boolean withPhoto) {
        MediaFile photo = withPhoto ? media.find(e.getProfileMediaId()) : null;
        return new EmployeeListItem(e.getId().toString(), e.getEmployeeNo(), e.getEmployeeCode(), e.getFullName(),
                e.getEmail(), photo == null ? null : photo.viewUrl(),
                ref(e.getBranchId(), n.branches()), ref(e.getDepartmentId(), n.departments()),
                ref(e.getDesignationId(), n.designations()), e.getStatus(), e.getEmploymentType(),
                e.getDateOfJoining(), e.getUserId() != null && e.isLoginEnabled(), str(e.getShiftId()),
                str(e.getAttendancePolicyId()));
    }

    /** Required document types, read only when the missing-documents filter is on. */
    private Set<Long> required(Long companyId, Filter f) {
        return Boolean.TRUE.equals(f.missingRequiredDocuments()) ? documents.requiredTypeIds(companyId) : Set.of();
    }

    private EmployeeDetail toDetail(Employee e) {
        Names n = namesOf(e.getCompanyId(), List.of(e));
        MediaFile photo = media.find(e.getProfileMediaId());
        BankingDto banking = CurrentUser.get().has(BANKING_VIEW)
                ? new BankingDto(e.getBankName(), e.getAccountHolderName(), cipher.decrypt(e.getAccountNumberEnc()),
                e.getBankIdentifierCode(), e.getBankBranch(), cipher.decrypt(e.getTaxPayerIdEnc()), e.getBaseSalary(),
                e.getHoursPerDay(), e.getDaysPerWeek(), e.getRatePerHour())
                : null;
        return new EmployeeDetail(e.getId().toString(), e.getCompanyId().toString(), e.getEmployeeNo(),
                e.getEmployeeCode(), e.getFullName(), e.getEmail(), e.getPhone(), e.getDateOfBirth(), e.getGender(),
                str(e.getProfileMediaId()), photo == null ? null : photo.viewUrl(),
                ref(e.getBranchId(), n.branches()), ref(e.getDepartmentId(), n.departments()),
                ref(e.getDesignationId(), n.designations()), e.getDateOfJoining(), e.getDateOfLeaving(),
                e.getEmploymentType(),
                e.getStatus(), str(e.getShiftId()), str(e.getAttendancePolicyId()), reportsTo(e),
                e.getAddressLine1(), e.getAddressLine2(), e.getCity(), e.getState(), e.getCountry(), e.getPostalCode(),
                e.getEmergencyName(), e.getEmergencyRelationship(), e.getEmergencyPhone(), banking,
                str(e.getUserId()), e.getUserId() != null && e.isLoginEnabled(),
                e.getId() == null ? List.of() : documents.list(e.getCompanyId(), e.getId()),
                e.getId() == null ? List.of() : documents.missingRequired(e.getCompanyId(), e.getId()),
                e.getCreatedDate(), e.getUpdatedDate());
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id.toString(), names.get(id));
    }

    private static String str(Long v) {
        return v == null ? null : v.toString();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
