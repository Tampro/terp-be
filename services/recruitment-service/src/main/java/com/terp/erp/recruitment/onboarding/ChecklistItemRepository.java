package com.terp.erp.recruitment.onboarding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.recruitment.common.EStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    @Query("select i from ChecklistItem i where i.companyId = :companyId and i.deleted = false "
            + "and (:checklistId is null or i.checklistId = :checklistId) "
            + "and (:category is null or i.category = :category) "
            + "and (:required is null or i.required = :required) "
            + "and (:status is null or i.status = :status) "
            + "and (:kw = '' or lower(i.taskName) like :kw escape '!')")
    Page<ChecklistItem> search(Long companyId, Long checklistId, ChecklistItem.ECategory category, Boolean required,
                               EStatus status, String kw, Pageable pageable);

    /** Active tasks of a checklist in due order (no due day last). */
    @Query("select i from ChecklistItem i where i.checklistId = :checklistId and i.deleted = false "
            + "and i.status = com.terp.erp.recruitment.common.EStatus.ACTIVE "
            + "order by case when i.dueDay is null then 1 else 0 end, i.dueDay, i.id")
    List<ChecklistItem> activeOf(Long checklistId);

    /** [checklist id, count]: the Items column. */
    @Query("select i.checklistId, count(i) from ChecklistItem i where i.deleted = false and i.checklistId in :ids "
            + "group by i.checklistId")
    List<Object[]> countByChecklist(Collection<Long> ids);

    Optional<ChecklistItem> findByIdAndDeletedFalse(Long id);

    @Modifying
    @Query("update ChecklistItem i set i.deleted = true where i.checklistId = :checklistId and i.deleted = false")
    int deleteByChecklist(Long checklistId);
}
