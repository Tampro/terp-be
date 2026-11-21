package com.terp.erp.attendance.leave;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeDto;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeForm;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeOption;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.List;

/** Leave Types page (docs/analysis/06 §6.5.3). Names are unique per company; a type with policies cannot be deleted. */
@Service
@RequiredArgsConstructor
public class LeaveTypeService {
    private final LeaveTypeRepository types;
    private final LeavePolicyRepository policies;
    private final LeaveApplicationRepository applications;

    @Transactional(readOnly = true)
    public Page<LeaveTypeDto> search(Long requestedCompanyId, String keyword, EStatus status, Pageable pageable) {
        return types.search(company(requestedCompanyId), status, Texts.kw(keyword), pageable).map(LeaveTypeDto::of);
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeOption> options(Long requestedCompanyId) {
        return types.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(company(requestedCompanyId), EStatus.ACTIVE)
                .stream().map(t -> new LeaveTypeOption(t.getId().toString(), t.getName(), t.getColor(), t.isPaid()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveTypeDto get(Long id) {
        return LeaveTypeDto.of(require(id));
    }

    @Transactional
    public LeaveTypeDto create(Long requestedCompanyId, LeaveTypeForm form) {
        LeaveType t = new LeaveType();
        t.setCompanyId(company(requestedCompanyId));
        apply(t, form, -1L);
        return LeaveTypeDto.of(types.save(t));
    }

    /** Full replacement: omitted description is cleared, omitted paid = true, color = #3B82F6, status kept. */
    @Transactional
    public LeaveTypeDto update(Long id, LeaveTypeForm form) {
        LeaveType t = require(id);
        apply(t, form, id);
        return LeaveTypeDto.of(t);
    }

    @Transactional
    public LeaveTypeDto changeStatus(Long id, EStatus status) {
        LeaveType t = require(id);
        t.setStatus(status);
        return LeaveTypeDto.of(t);
    }

    /** Refused while a policy or an application uses it. */
    @Transactional
    public void delete(Long id) {
        LeaveType t = require(id);
        if (policies.existsByLeaveTypeIdAndDeletedFalse(id) || applications.existsByLeaveTypeId(id)) {
            throw ApiException.badRequest(EAttendanceError.ATT_038);
        }
        t.setDeleted(true);
    }

    private void apply(LeaveType t, LeaveTypeForm f, Long selfId) {
        String name = f.name().trim();
        if (types.nameTaken(t.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EAttendanceError.ATT_003);
        }
        t.setName(name);
        t.setDescription(Texts.blankToNull(f.description()));
        t.setMaxDaysPerYear(f.maxDaysPerYear());
        t.setPaid(f.paid() == null || f.paid());
        t.setColor(f.color() == null ? "#3B82F6" : f.color().toUpperCase());
        if (f.status() != null) {
            t.setStatus(f.status());
        }
    }

    LeaveType require(Long id) {
        LeaveType t = types.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_034));
        TenantGuard.assertSameCompany(t.getCompanyId(), EAttendanceError.ATT_034);
        return t;
    }

    static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
