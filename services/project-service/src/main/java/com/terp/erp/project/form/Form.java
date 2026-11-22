package com.terp.erp.project.form;

import com.terp.erp.project.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "form")
public class Form extends BaseAuditableEntity {
    public enum ELayout { SINGLE_COLUMN, TWO_COLUMN, CARD }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout", nullable = false)
    private ELayout layout = ELayout.SINGLE_COLUMN;

    @Column(name = "public_token", nullable = false)
    private String publicToken;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
