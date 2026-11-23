package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One successful login (docs/analysis/06 §10.1.1 Login History). Written by IAM itself, so no audit columns. */
@Getter
@Setter
@Entity
@Table(name = "login_history")
public class LoginHistory {
    /** User Type column. */
    public enum EUserType { SUPER_ADMIN, COMPANY_USER }

    /** Parsed from the User-Agent (user manual §3.2 "device type"). */
    public enum EDeviceType { DESKTOP, MOBILE, TABLET, BOT, UNKNOWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null for a super admin (no ERP profile). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private EUserType userType;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "browser", length = 50)
    private String browser;

    @Column(name = "os", length = 50)
    private String os;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 10)
    private EDeviceType deviceType;

    /** Primary tag of Accept-Language (e.g. vi-VN). */
    @Column(name = "language", length = 35)
    private String language;

    /** IANA zone sent by the FE in X-Client-Timezone; null when absent or not a valid ZoneId. */
    @Column(name = "timezone", length = 64)
    private String timezone;

    /** Comma-joined role codes the user held at login time (roles can change later). */
    @Column(name = "role_at_login", length = 255)
    private String roleAtLogin;

    // Filled from offline GeoLite2 files (login/GeoIpLookup); isp = ASN organization (GeoLite2 has no ISP database).
    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "isp", length = 150)
    private String isp;

    @Column(name = "login_at", nullable = false)
    private Instant loginAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
