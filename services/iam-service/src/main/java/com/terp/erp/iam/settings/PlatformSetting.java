package com.terp.erp.iam.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One settings section stored as JSON (see SettingsDtos for each section's shape). */
@Getter
@Setter
@Entity
@Table(name = "platform_setting")
public class PlatformSetting {
    @Id
    @Column(name = "section", length = 40)
    private String section;

    @Column(name = "value_json", nullable = false, columnDefinition = "TEXT")
    private String valueJson;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_date")
    private Instant updatedDate;
}
