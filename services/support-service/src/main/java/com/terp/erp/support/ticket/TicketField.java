package com.terp.erp.support.ticket;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A custom field of the company's ticket form (reference SupportTicket ticket_fields: name, type, placeholder,
 * width, order, status, is_required). The reference's built-in rows (custom_id: name, email, category, subject,
 * description) are fixed form fields here, not rows.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_field")
public class TicketField extends BaseAuditableEntity {
    /**
     * Reference Frontend/CreateTicket.tsx renders text, textarea, email, number, date.
     * TODO(assumption): SELECT with a list of options is not in the reference; added for the ERP manual's form builder.
     */
    public enum EFieldType { TEXT, TEXTAREA, EMAIL, NUMBER, DATE, SELECT }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "placeholder")
    private String placeholder;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private EFieldType type;

    /** SELECT only: JSON array of strings. */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "required", nullable = false)
    private boolean required;

    /** Columns of a 12-column grid: 3, 4, 6 or 12 (reference default 6). */
    @Column(name = "width", nullable = false)
    private int width = 6;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
