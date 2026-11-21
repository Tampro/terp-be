package com.terp.erp.iam.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** A visitor's cookie consent choice, kept while Cookie Settings -> Enable Logging is on. */
@Getter
@Setter
@Entity
@Table(name = "cookie_consent")
public class CookieConsent extends BaseAuditableEntity {
    public enum EChoice { ACCEPT_ALL, NECESSARY_ONLY, CUSTOM }

    @Enumerated(EnumType.STRING)
    @Column(name = "choice", nullable = false, length = 20)
    private EChoice choice;

    /** Comma-separated categories accepted, "necessary" always included. */
    @Column(name = "categories", nullable = false, length = 400)
    private String categories;

    @Column(name = "ip_masked", length = 64)
    private String ipMasked;
}
