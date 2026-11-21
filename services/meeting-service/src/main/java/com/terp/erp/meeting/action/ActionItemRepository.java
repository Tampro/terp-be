package com.terp.erp.meeting.action;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long> {
    /** keyword on title, description and the copied assignee name; the caller escapes with !. */
    String FILTERS = "from ActionItem a where a.companyId = :companyId and a.deleted = false "
            + "and (:priority is null or a.priority = :priority) "
            + "and (:assigneeId is null or a.assigneeId = :assigneeId) "
            + "and (:meetingId is null or a.meetingId = :meetingId) "
            + "and (:kw = '' or lower(a.title) like :kw escape '!' or lower(a.description) like :kw escape '!' "
            + "or lower(a.assigneeName) like :kw escape '!')";

    /** Overdue = not completed with a due date before today. */
    String OVERDUE = "(a.status <> com.terp.erp.meeting.action.ActionItem.EStatus.COMPLETED "
            + "and a.dueDate is not null and a.dueDate < :today)";

    /** stage null = any; overdue null = any, true = only overdue, false = none overdue. */
    String TAB = " and (:stage is null or a.status = :stage) and (:overdue is null "
            + "or (:overdue = true and " + OVERDUE + ") or (:overdue = false and not " + OVERDUE + "))";

    @Query(value = "select a " + FILTERS + TAB, countQuery = "select count(a) " + FILTERS + TAB)
    Page<ActionItem> search(Long companyId, ActionItem.EPriority priority, Long assigneeId, Long meetingId, String kw,
                            ActionItem.EStatus stage, Boolean overdue, LocalDate today, Pageable pageable);

    /** Counts of the tabs: [stage, overdue, count]. */
    @Query("select a.status, case when " + OVERDUE + " then true else false end, count(a) " + FILTERS
            + " group by a.status, case when " + OVERDUE + " then true else false end")
    List<Object[]> countTabs(Long companyId, ActionItem.EPriority priority, Long assigneeId, Long meetingId,
                             String kw, LocalDate today);

    Optional<ActionItem> findByIdAndDeletedFalse(Long id);
}
