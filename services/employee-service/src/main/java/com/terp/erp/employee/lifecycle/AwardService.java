package com.terp.erp.employee.lifecycle;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.award.AwardType;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.employee.award.AwardTypeRepository;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Awards page (docs/analysis/06 §7.2.1). The award type must be the company's and active when chosen. */
@Service
@RequiredArgsConstructor
public class AwardService {
    private final EmployeeAwardRepository awards;
    private final AwardTypeRepository types;
    private final LifecycleSupport support;
    private final OutboxPublisher outbox;

    /**
     * certificate / photo (name, links) are only filled on the details — one media lookup per file; the lists carry
     * certificateMediaId / photoMediaId so a row can show that a file exists and open it (like the warning list).
     */
    public record AwardDto(String id, EmployeeRef employee, Ref awardType, LocalDate awardDate, String gift,
                           String description, String certificateMediaId, FileRef certificate, String photoMediaId,
                           FileRef photo, Instant createdDate, Instant updatedDate) {
    }

    public record AwardForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long awardTypeId,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate awardDate,
            @Schema(example = "Gift voucher") @Size(max = 255) String gift,
            @Size(max = 2000) String description,
            @Schema(description = "Media library file id") Long certificateMediaId,
            @Schema(description = "Media library file id") Long photoMediaId) {
    }

    @Transactional(readOnly = true)
    public Page<AwardDto> search(Long requestedCompanyId, String keyword, Long awardTypeId, Long employeeId,
                                 LocalDate from, LocalDate to, Pageable pageable) {
        Page<EmployeeAward> page = awards.search(LifecycleSupport.company(requestedCompanyId), awardTypeId,
                employeeId, from, to, DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeAward::getEmployeeId).toSet());
        Map<Long, String> typeNames = types.findAllById(page.map(EmployeeAward::getAwardTypeId).toSet()).stream()
                .collect(Collectors.toMap(AwardType::getId, AwardType::getName));
        return page.map(a -> dto(a, people.get(a.getEmployeeId()), typeNames.get(a.getAwardTypeId()), false));
    }

    /** The caller's own awards (any authenticated employee; EMP_041 without a profile). */
    @Transactional(readOnly = true)
    public Page<AwardDto> mine(Pageable pageable) {
        Employee me = support.me();
        Page<EmployeeAward> page = awards.search(me.getCompanyId(), null, me.getId(), null, null, "", pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeAward::getEmployeeId).toSet());
        Map<Long, String> typeNames = types.findAllById(page.map(EmployeeAward::getAwardTypeId).toSet()).stream()
                .collect(Collectors.toMap(AwardType::getId, AwardType::getName));
        return page.map(a -> dto(a, people.get(a.getEmployeeId()), typeNames.get(a.getAwardTypeId()), false));
    }

    @Transactional(readOnly = true)
    public AwardDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public AwardDto create(AwardForm form) {
        EmployeeAward a = new EmployeeAward();
        apply(a, form);
        AwardDto dto = detail(awards.save(a));
        // webhook NEW_AWARD; no recipient (gift and description are not sent)
        String employee = dto.employee() == null ? null : dto.employee().fullName();
        String type = dto.awardType() == null ? null : dto.awardType().name();
        outbox.publish(a.getCompanyId(), "employee.award.created", "AWARD", a.getId(), Payload.of(
                "awardId", a.getId(), "title", type == null ? employee : type + " - " + employee,
                "employeeId", a.getEmployeeId(), "employeeName", employee, "awardTypeId", a.getAwardTypeId(),
                "awardTypeName", type, "awardDate", a.getAwardDate()));
        return dto;
    }

    /** Full replacement. */
    @Transactional
    public AwardDto update(Long id, AwardForm form) {
        EmployeeAward a = require(id);
        apply(a, form);
        return detail(a);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(EmployeeAward a, AwardForm f) {
        Employee e = support.employee(f.employeeId());
        AwardType t = types.findByIdAndDeletedFalse(f.awardTypeId())
                .filter(x -> x.getCompanyId().equals(e.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_042));
        if (t.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(t.getId(), a.getAwardTypeId())) {
            throw ApiException.badRequest(EEmployeeError.EMP_042);
        }
        a.setCompanyId(e.getCompanyId());
        a.setEmployeeId(e.getId());
        a.setAwardTypeId(t.getId());
        a.setAwardDate(f.awardDate());
        a.setGift(blankToNull(f.gift()));
        a.setDescription(blankToNull(f.description()));
        a.setCertificateMediaId(support.file(f.certificateMediaId(), e.getCompanyId()));
        a.setPhotoMediaId(support.file(f.photoMediaId(), e.getCompanyId()));
    }

    private AwardDto detail(EmployeeAward a) {
        return dto(a, support.refs(java.util.List.of(a.getEmployeeId())).get(a.getEmployeeId()),
                types.findById(a.getAwardTypeId()).map(AwardType::getName).orElse(null), true);
    }

    private AwardDto dto(EmployeeAward a, EmployeeRef employee, String typeName, boolean withFiles) {
        return new AwardDto(a.getId().toString(), employee, new Ref(a.getAwardTypeId().toString(), typeName),
                a.getAwardDate(), a.getGift(), a.getDescription(), idOf(a.getCertificateMediaId()),
                withFiles ? support.fileRef(a.getCertificateMediaId()) : null, idOf(a.getPhotoMediaId()),
                withFiles ? support.fileRef(a.getPhotoMediaId()) : null, a.getCreatedDate(), a.getUpdatedDate());
    }

    private static String idOf(Long id) {
        return id == null ? null : id.toString();
    }

    private EmployeeAward require(Long id) {
        EmployeeAward a = awards.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_043));
        TenantGuard.assertSameCompany(a.getCompanyId(), EEmployeeError.EMP_043);
        return a;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
