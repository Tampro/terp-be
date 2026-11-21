package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.saas.EBillingCycle;
import com.terp.erp.iam.saas.EPlanRequestStatus;

import java.time.Instant;

/**
 * A company asks for a plan change without paying online (docs/analysis/06 §3.2.3 "Plan Requests"; WorkDo
 * `plan_requests`). Approval by a super admin activates the plan for one cycle, recorded as a MANUAL order.
 */
@Getter
@Setter
@Entity
@Table(name = "plan_request")
public class PlanRequest extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private EBillingCycle billingCycle;

    @Column(name = "message", length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EPlanRequestStatus status;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by", length = 64)
    private String processedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    /** The MANUAL order created when the request was approved. */
    @Column(name = "order_id")
    private Long orderId;
}
