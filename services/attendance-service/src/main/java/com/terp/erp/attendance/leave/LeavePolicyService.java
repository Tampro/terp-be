package com.terp.erp.attendance.leave;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.leave.LeaveDtos.LeavePolicyDto;
import com.terp.erp.attendance.leave.LeaveDtos.LeavePolicyForm;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeRef;
import com.terp.erp.attendance.leave.LeaveDtos.StatusCounts;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leave Policies page (docs/analysis/06 §6.5.4). A policy belongs to a leave type of the same company; at most one
 * active policy per leave type (ATT_036); min days per application never above max (ATT_037).
 */
@Service
@RequiredArgsConstructor
public class LeavePolicyService {
    private final LeavePolicyRepository policies;
    private final LeaveTypeRepository types;
    private final LeaveTypeService typeService;

    @Transactional(readOnly = true)
    public Page<LeavePolicyDto> search(Long requestedCompanyId, String keyword, Long leaveTypeId, EStatus status,
                                       Pageable pageable) {
        Long companyId = LeaveTypeService.company(requestedCompanyId);
        Page<LeavePolicy> page = policies.search(companyId, leaveTypeId, status, Texts.kw(keyword), pageable);
        Map<Long, LeaveType> byId = types.findByCompanyIdAndIdIn(companyId,
                        page.getContent().stream().map(LeavePolicy::getLeaveTypeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        return page.map(p -> dto(p, byId.get(p.getLeaveTypeId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long leaveTypeId) {
        long active = 0;
        long inactive = 0;
        for (Object[] r : policies.countByStatus(LeaveTypeService.company(requestedCompanyId), leaveTypeId,
                Texts.kw(keyword))) {
            if (r[0] == EStatus.ACTIVE) {
                active = (Long) r[1];
            } else {
                inactive = (Long) r[1];
            }
        }
        return new StatusCounts(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    public LeavePolicyDto get(Long id) {
        LeavePolicy p = require(id);
        return dto(p, types.findById(p.getLeaveTypeId()).orElse(null));
    }

    @Transactional
    public LeavePolicyDto create(LeavePolicyForm form) {
        LeaveType t = typeService.require(form.leaveTypeId());
        LeavePolicy p = new LeavePolicy();
        p.setCompanyId(t.getCompanyId());
        apply(p, t, form, -1L);
        return dto(policies.save(p), t);
    }

    /** Full replacement: omitted description is cleared, omitted requiresApproval = true, status kept. */
    @Transactional
    public LeavePolicyDto update(Long id, LeavePolicyForm form) {
        LeavePolicy p = require(id);
        LeaveType t = typeService.require(form.leaveTypeId());
        apply(p, t, form, id);
        return dto(p, t);
    }

    @Transactional
    public LeavePolicyDto changeStatus(Long id, EStatus status) {
        LeavePolicy p = require(id);
        if (status == EStatus.ACTIVE && policies.existsByLeaveTypeIdAndStatusAndDeletedFalseAndIdNot(
                p.getLeaveTypeId(), EStatus.ACTIVE, id)) {
            throw ApiException.badRequest(EAttendanceError.ATT_036);
        }
        p.setStatus(status);
        return dto(p, types.findById(p.getLeaveTypeId()).orElse(null));
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(LeavePolicy p, LeaveType t, LeavePolicyForm f, Long selfId) {
        if (f.minDaysPerApplication() > f.maxDaysPerApplication()) {
            throw ApiException.badRequest(EAttendanceError.ATT_037);
        }
        String name = f.name().trim();
        if (policies.nameTaken(t.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EAttendanceError.ATT_003);
        }
        EStatus status = f.status() != null ? f.status() : p.getStatus();
        if (status == EStatus.ACTIVE
                && policies.existsByLeaveTypeIdAndStatusAndDeletedFalseAndIdNot(t.getId(), EStatus.ACTIVE, selfId)) {
            throw ApiException.badRequest(EAttendanceError.ATT_036);
        }
        p.setName(name);
        p.setDescription(Texts.blankToNull(f.description()));
        p.setLeaveTypeId(t.getId());
        p.setCarryForwardLimit(f.carryForwardLimit());
        p.setMinDaysPerApplication(f.minDaysPerApplication());
        p.setMaxDaysPerApplication(f.maxDaysPerApplication());
        p.setRequiresApproval(f.requiresApproval() == null || f.requiresApproval());
        p.setStatus(status);
    }

    private static LeavePolicyDto dto(LeavePolicy p, LeaveType t) {
        LeaveTypeRef ref = t == null ? null : new LeaveTypeRef(t.getId().toString(), t.getName(), t.getColor());
        return new LeavePolicyDto(p.getId().toString(), p.getName(), p.getDescription(), ref, p.getCarryForwardLimit(),
                p.getMinDaysPerApplication(), p.getMaxDaysPerApplication(), p.isRequiresApproval(), p.getStatus(),
                p.getCreatedDate(), p.getUpdatedDate());
    }

    LeavePolicy require(Long id) {
        LeavePolicy p = policies.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_035));
        TenantGuard.assertSameCompany(p.getCompanyId(), EAttendanceError.ATT_035);
        return p;
    }
}
