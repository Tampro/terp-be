package com.terp.erp.support.ticket;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A ticket category of one company (reference ticket_categories: name, color). */
@Getter
@Setter
@Entity
@Table(name = "ticket_category")
public class TicketCategory extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
