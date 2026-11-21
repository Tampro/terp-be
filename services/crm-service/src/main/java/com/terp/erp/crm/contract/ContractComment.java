package com.terp.erp.crm.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

/** A comment on a contract; author_name is the caller's username when it was written. */
@Getter
@Setter
@Entity
@Table(name = "contract_comment")
public class ContractComment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
