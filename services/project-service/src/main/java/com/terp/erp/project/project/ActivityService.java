package com.terp.erp.project.project;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;

/** The project activity tab: written by this service on every change, in the caller's transaction. */
@Service
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository activities;

    public record ActivityDto(String id, String projectId, String actorUserId,
                              String actorKeycloakId, String action, String detail, Instant createdDate) {
        static ActivityDto of(Activity a) {
            return new ActivityDto(a.getId().toString(), a.getProjectId().toString(),
                    a.getActorUserId() == null ? null : a.getActorUserId().toString(), a.getCreatedBy(),
                    a.getAction(), a.getDetail(), a.getCreatedDate());
        }
    }

    /** action is a stable code the FE translates (PROJECT_CREATED, TASK_MOVED…); detail is a short free text. */
    @Transactional
    public void log(Long companyId, Long projectId, String action, String detail) {
        Activity a = new Activity();
        a.setCompanyId(companyId);
        a.setProjectId(projectId);
        a.setActorUserId(CurrentUser.find().map(ErpPrincipal::userId).orElse(null));
        a.setAction(action);
        a.setDetail(detail == null ? null : detail.length() > 500 ? detail.substring(0, 500) : detail);
        activities.save(a);
    }

    /** Caller checked the project first (ProjectService.require). */
    @Transactional(readOnly = true)
    public Page<ActivityDto> list(Long projectId, Pageable pageable) {
        return activities.findByProjectIdOrderByIdDesc(projectId, pageable).map(ActivityDto::of);
    }
}
