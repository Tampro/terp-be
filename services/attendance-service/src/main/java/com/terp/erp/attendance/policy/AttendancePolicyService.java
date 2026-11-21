package com.terp.erp.attendance.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyDto;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyForm;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyOption;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.StatusCounts;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.Summary;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Attendance Policies page (docs/analysis/06 §6.4.6). Defaults from doc 03: 15 min grace both ways, half day under 4 h.
 * TODO(assumption): doc 03 defaults the overtime rate to 150.00 (a USD figure of the reference product); here the rate
 * is required, since the currency is the company's — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class AttendancePolicyService {
    private final AttendancePolicyRepository policies;

    @Transactional(readOnly = true)
    public Page<PolicyDto> search(Long requestedCompanyId, String keyword, EStatus status, Pageable pageable) {
        return policies.search(company(requestedCompanyId), status, Texts.kw(keyword), pageable).map(PolicyDto::of);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword) {
        long active = 0;
        long inactive = 0;
        for (Object[] row : policies.countByStatus(company(requestedCompanyId), Texts.kw(keyword))) {
            if (row[0] == EStatus.ACTIVE) {
                active = (Long) row[1];
            } else {
                inactive = (Long) row[1];
            }
        }
        return new StatusCounts(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    public Summary summary(Long requestedCompanyId) {
        Object[] r = policies.summary(company(requestedCompanyId)).get(0);
        return new Summary(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), avg(r[2]), avg(r[3]));
    }

    @Transactional(readOnly = true)
    public List<PolicyOption> options(Long requestedCompanyId) {
        return policies.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(company(requestedCompanyId),
                EStatus.ACTIVE).stream().map(p -> new PolicyOption(p.getId().toString(), p.getName())).toList();
    }

    @Transactional(readOnly = true)
    public PolicyDto get(Long id) {
        return PolicyDto.of(require(id));
    }

    @Transactional
    public PolicyDto create(Long requestedCompanyId, PolicyForm form) {
        AttendancePolicy p = new AttendancePolicy();
        p.setCompanyId(company(requestedCompanyId));
        apply(p, form, -1L);
        return PolicyDto.of(policies.save(p));
    }

    /** Full replacement: omitted description is cleared, omitted numbers take the defaults, omitted status is kept. */
    @Transactional
    public PolicyDto update(Long id, PolicyForm form) {
        AttendancePolicy p = require(id);
        apply(p, form, id);
        return PolicyDto.of(p);
    }

    @Transactional
    public PolicyDto changeStatus(Long id, EStatus status) {
        AttendancePolicy p = require(id);
        p.setStatus(status);
        return PolicyDto.of(p);
    }

    /** TODO(assumption): like shifts, a policy still set on employee profiles is not checked before delete. */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(AttendancePolicy p, PolicyForm f, Long selfId) {
        String name = f.name().trim();
        if (policies.nameTaken(p.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EAttendanceError.ATT_003);
        }
        p.setName(name);
        p.setDescription(Texts.blankToNull(f.description()));
        p.setLateArrivalGraceMinutes(f.lateArrivalGraceMinutes() == null ? 15 : f.lateArrivalGraceMinutes());
        p.setEarlyDepartureGraceMinutes(f.earlyDepartureGraceMinutes() == null ? 15 : f.earlyDepartureGraceMinutes());
        p.setHalfDayThresholdHours(f.halfDayThresholdHours() == null ? new BigDecimal("4.00") : f.halfDayThresholdHours());
        p.setOvertimeRatePerHour(f.overtimeRatePerHour());
        if (f.status() != null) {
            p.setStatus(f.status());
        }
    }

    private AttendancePolicy require(Long id) {
        AttendancePolicy p = policies.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_002));
        TenantGuard.assertSameCompany(p.getCompanyId(), EAttendanceError.ATT_002);
        return p;
    }

    private static BigDecimal avg(Object v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
