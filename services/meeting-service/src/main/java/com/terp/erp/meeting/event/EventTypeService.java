package com.terp.erp.meeting.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event Type page (docs/analysis/06 §9.2.2): name unique in the company (MTG_003); only active types can be chosen
 * for an event; a type with events cannot be deleted (MTG_004).
 */
@Service
@RequiredArgsConstructor
public class EventTypeService {
    private final EventTypeRepository types;
    private final CompanyEventRepository events;

    public record TypeForm(
            @Schema(example = "Team Building", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Size(max = 1000) String description,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status) {
    }

    /** totalEvents = the Total Events column (deleted events left out). */
    public record TypeDto(String id, String name, String description, EStatus status, long totalEvents,
                          Instant createdDate) {
    }

    public record Option(String id, String name) {
    }

    @Transactional(readOnly = true)
    public Page<TypeDto> search(Long requestedCompanyId, String keyword, EStatus status, Pageable pageable) {
        Page<EventType> page = types.search(Texts.company(requestedCompanyId), status, Texts.kw(keyword), pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            events.countByType(page.map(EventType::getId).toList()).forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        }
        return page.map(t -> dto(t, counts.getOrDefault(t.getId(), 0L)));
    }

    /** The Event Type dropdown of the event form: active types only. */
    @Transactional(readOnly = true)
    public List<Option> options(Long requestedCompanyId) {
        return types.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId),
                EStatus.ACTIVE).stream().map(t -> new Option(t.getId().toString(), t.getName())).toList();
    }

    @Transactional(readOnly = true)
    public TypeDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public TypeDto create(Long requestedCompanyId, TypeForm form) {
        EventType t = new EventType();
        t.setCompanyId(Texts.company(requestedCompanyId));
        apply(t, form);
        return dto(types.save(t), 0);
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public TypeDto update(Long id, TypeForm form) {
        EventType t = require(id);
        apply(t, form);
        return detail(t);
    }

    /** Toggle Status. */
    @Transactional
    public TypeDto changeStatus(Long id, EStatus status) {
        EventType t = require(id);
        t.setStatus(status);
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        EventType t = require(id);
        if (events.existsByEventTypeIdAndDeletedFalse(t.getId())) {
            throw ApiException.badRequest(EMeetingError.MTG_004);
        }
        t.setDeleted(true);
    }

    private void apply(EventType t, TypeForm f) {
        String name = f.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(t.getCompanyId(), name,
                t.getId() == null ? -1L : t.getId())) {
            throw ApiException.badRequest(EMeetingError.MTG_003);
        }
        t.setName(name);
        t.setDescription(Texts.blankToNull(f.description()));
        if (f.status() != null) {
            t.setStatus(f.status());
        }
    }

    private TypeDto detail(EventType t) {
        return dto(t, events.countByType(List.of(t.getId())).stream().mapToLong(r -> (Long) r[1]).sum());
    }

    private static TypeDto dto(EventType t, long n) {
        return new TypeDto(t.getId().toString(), t.getName(), t.getDescription(), t.getStatus(), n,
                t.getCreatedDate());
    }

    public EventType require(Long id) {
        EventType t = types.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EMeetingError.MTG_016));
        TenantGuard.assertSameCompany(t.getCompanyId(), EMeetingError.MTG_016);
        return t;
    }
}
