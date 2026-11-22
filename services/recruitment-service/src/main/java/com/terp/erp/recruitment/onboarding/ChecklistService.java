package com.terp.erp.recruitment.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Onboarding Checklists (docs/analysis/06 §7.1.7) and their Checklist Items (§7.1.8). Setting a checklist as default
 * clears the company's previous default. A checklist used by an onboarding cannot be deleted (REC_013); deleting one
 * deletes its items.
 * TODO(assumption): the spec mentions a "Type" filter on checklists without defining types; not modelled.
 */
@Service
@RequiredArgsConstructor
public class ChecklistService {
    private final OnboardingChecklistRepository checklists;
    private final ChecklistItemRepository items;
    private final CandidateOnboardingRepository onboardings;

    public record Ref(String id, String name) {
    }

    public record ChecklistDto(String id, String name, String description, boolean defaultChecklist, long items,
                               EStatus status, Instant createdDate, Instant updatedDate) {
    }

    public record ItemDto(String id, Ref checklist, String taskName, String description,
                          ChecklistItem.ECategory category, String assignedToRole, boolean required,
                          Integer dueDay, EStatus status, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record ChecklistForm(
            @Schema(example = "Engineering onboarding", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Schema(description = "Set as Default") boolean defaultChecklist,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status) {
    }

    public record ItemForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long checklistId,
            @Schema(example = "Set up laptop", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String taskName,
            @Size(max = 1000) String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ChecklistItem.ECategory category,
            @Schema(description = "Responsible role (free text)", example = "IT") @Size(max = 100)
            String assignedToRole,
            @Schema(description = "Required Task") boolean required,
            @Schema(description = "Days after the onboarding start date", example = "1") @Min(0) @Max(365)
            Integer dueDay,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    // ------------------------------------------------------------------ checklists

    @Transactional(readOnly = true)
    public Page<ChecklistDto> search(Long requestedCompanyId, String keyword, EStatus status, Pageable pageable) {
        Page<OnboardingChecklist> page = checklists.search(CatalogService.company(requestedCompanyId), status,
                Texts.kw(keyword), pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] r : items.countByChecklist(page.map(OnboardingChecklist::getId).getContent())) {
                counts.put((Long) r[0], (Long) r[1]);
            }
        }
        return page.map(c -> dto(c, counts.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : checklists.countByStatus(CatalogService.company(requestedCompanyId), Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Active checklists for the Start Onboarding dropdown. */
    @Transactional(readOnly = true)
    public List<Ref> options(Long requestedCompanyId) {
        return checklists.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(
                        CatalogService.company(requestedCompanyId), EStatus.ACTIVE).stream()
                .map(c -> new Ref(c.getId().toString(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public ChecklistDto get(Long id) {
        return dto(requireChecklist(id));
    }

    @Transactional
    public ChecklistDto create(Long requestedCompanyId, ChecklistForm form) {
        OnboardingChecklist c = new OnboardingChecklist();
        c.setCompanyId(CatalogService.company(requestedCompanyId));
        apply(c, form, 0L);
        checklists.save(c);
        if (c.isDefaultChecklist()) {
            checklists.clearDefault(c.getCompanyId(), c.getId());
        }
        return dto(c);
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public ChecklistDto update(Long id, ChecklistForm form) {
        OnboardingChecklist c = requireChecklist(id);
        apply(c, form, c.getId());
        if (c.isDefaultChecklist()) {
            checklists.clearDefault(c.getCompanyId(), c.getId());
        }
        return dto(c);
    }

    @Transactional
    public ChecklistDto changeStatus(Long id, EStatus status) {
        OnboardingChecklist c = requireChecklist(id);
        c.setStatus(status);
        return dto(c);
    }

    @Transactional
    public void delete(Long id) {
        OnboardingChecklist c = requireChecklist(id);
        if (onboardings.existsByChecklistIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        c.setDeleted(true);
        items.deleteByChecklist(id);
    }

    private void apply(OnboardingChecklist c, ChecklistForm f, Long selfId) {
        String name = f.name().trim();
        if (checklists.nameTaken(c.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(ERecruitmentError.REC_004);
        }
        c.setName(name);
        c.setDescription(Texts.blankToNull(f.description()));
        c.setDefaultChecklist(f.defaultChecklist());
        if (f.status() != null) {
            c.setStatus(f.status());
        }
    }

    private ChecklistDto dto(OnboardingChecklist c) {
        long n = items.countByChecklist(List.of(c.getId())).stream().mapToLong(r -> (Long) r[1]).sum();
        return dto(c, n);
    }

    private static ChecklistDto dto(OnboardingChecklist c, long itemCount) {
        return new ChecklistDto(c.getId().toString(), c.getName(), c.getDescription(), c.isDefaultChecklist(),
                itemCount, c.getStatus(), c.getCreatedDate(), c.getUpdatedDate());
    }

    public OnboardingChecklist requireChecklist(Long id) {
        OnboardingChecklist c = checklists.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_038));
        TenantGuard.assertSameCompany(c.getCompanyId(), ERecruitmentError.REC_038);
        return c;
    }

    // ------------------------------------------------------------------ items

    @Transactional(readOnly = true)
    public Page<ItemDto> searchItems(Long requestedCompanyId, String keyword, Long checklistId,
                                     ChecklistItem.ECategory category, Boolean required, EStatus status,
                                     Pageable pageable) {
        Page<ChecklistItem> page = items.search(CatalogService.company(requestedCompanyId), checklistId, category,
                required, status, Texts.kw(keyword), pageable);
        Map<Long, String> names = checklists.findAllById(page.map(ChecklistItem::getChecklistId).toSet()).stream()
                .collect(Collectors.toMap(OnboardingChecklist::getId, OnboardingChecklist::getName));
        return page.map(i -> itemDto(i, names.get(i.getChecklistId())));
    }

    @Transactional(readOnly = true)
    public ItemDto getItem(Long id) {
        return itemDto(requireItem(id));
    }

    @Transactional
    public ItemDto createItem(ItemForm form) {
        ChecklistItem i = new ChecklistItem();
        apply(i, form);
        return itemDto(items.save(i));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public ItemDto updateItem(Long id, ItemForm form) {
        ChecklistItem i = requireItem(id);
        apply(i, form);
        return itemDto(i);
    }

    @Transactional
    public ItemDto changeItemStatus(Long id, EStatus status) {
        ChecklistItem i = requireItem(id);
        i.setStatus(status);
        return itemDto(i);
    }

    @Transactional
    public void deleteItem(Long id) {
        requireItem(id).setDeleted(true);
    }

    private void apply(ChecklistItem i, ItemForm f) {
        OnboardingChecklist c = requireChecklist(f.checklistId());
        i.setCompanyId(c.getCompanyId());
        i.setChecklistId(c.getId());
        i.setTaskName(f.taskName().trim());
        i.setDescription(Texts.blankToNull(f.description()));
        i.setCategory(f.category());
        i.setAssignedToRole(Texts.blankToNull(f.assignedToRole()));
        i.setRequired(f.required());
        i.setDueDay(f.dueDay());
        if (f.status() != null) {
            i.setStatus(f.status());
        }
    }

    private ItemDto itemDto(ChecklistItem i) {
        return itemDto(i, checklists.findById(i.getChecklistId()).map(OnboardingChecklist::getName).orElse(null));
    }

    private static ItemDto itemDto(ChecklistItem i, String checklistName) {
        return new ItemDto(i.getId().toString(), new Ref(i.getChecklistId().toString(), checklistName), i.getTaskName(),
                i.getDescription(), i.getCategory(), i.getAssignedToRole(), i.isRequired(), i.getDueDay(), i.getStatus(), i.getCreatedDate(),
                i.getUpdatedDate());
    }

    private ChecklistItem requireItem(Long id) {
        ChecklistItem i = items.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_039));
        TenantGuard.assertSameCompany(i.getCompanyId(), ERecruitmentError.REC_039);
        return i;
    }
}
