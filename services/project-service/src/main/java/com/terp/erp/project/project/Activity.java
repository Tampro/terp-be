package com.terp.erp.project.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;

/** One line of the project activity tab: createdBy is the actor (Keycloak sub), createdDate the time. */
@Getter
@Setter
@Entity
@Table(name = "prj_activity")
public class Activity extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "detail", length = 500)
    private String detail;
}
