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
@Table(name = "form_field")
public class FormField extends BaseAuditableEntity {
    public enum EType { TEXT, EMAIL, NUMBER, PHONE, URL, PASSWORD, TEXTAREA, SELECT, RADIO, CHECKBOX, DATE, TIME;

        public boolean hasOptions() {
            return this == SELECT || this == RADIO || this == CHECKBOX;
        }
    }

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "label", nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private EType type;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "placeholder")
    private String placeholder;

    /** JSON array of strings; null for types without options. */
    @Column(name = "options")
    private String options;

    @Column(name = "position", nullable = false)
    private int position;
}
