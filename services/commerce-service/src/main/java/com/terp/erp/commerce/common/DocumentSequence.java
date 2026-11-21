package com.terp.erp.commerce.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Next number of a prefix in a month for one company; the row is locked while a number is taken. */
@Getter
@Setter
@Entity
@Table(name = "document_sequence")
public class DocumentSequence {
    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        @Column(name = "company_id")
        private Long companyId;
        @Column(name = "prefix", length = 4)
        private String prefix;
        @Column(name = "period", length = 7)
        private String period;
    }

    @EmbeddedId
    private Key key;

    @Column(name = "next_value", nullable = false)
    private int nextValue;
}
