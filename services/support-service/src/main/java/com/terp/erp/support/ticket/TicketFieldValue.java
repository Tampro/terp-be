package com.terp.erp.support.ticket;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One custom value of a ticket (reference ticket_field_values: record_id, field_id, value, unique per pair). A row
 * per value rather than a JSON column: values can be filtered / reported on per field, and a renamed field shows
 * its new label on old tickets.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_field_value")
public class TicketFieldValue extends BaseAuditableEntity {
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "field_value", nullable = false, length = 2000)
    private String value;
}
