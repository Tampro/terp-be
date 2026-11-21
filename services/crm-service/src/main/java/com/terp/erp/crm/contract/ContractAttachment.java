package com.terp.erp.crm.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

/** A media-service file attached to a contract; the file name is copied when attached. */
@Getter
@Setter
@Entity
@Table(name = "contract_attachment")
public class ContractAttachment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
