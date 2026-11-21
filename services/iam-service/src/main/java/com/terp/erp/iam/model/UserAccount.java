package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.constant.EUserStatus;

import java.util.HashSet;
import java.util.Set;

/**
 * The ERP side of a Keycloak user. Keycloak owns the password and sessions; this row owns the company and roles.
 * No password column on purpose.
 */
@Getter
@Setter
@Entity
@Table(name = "user_account")
public class UserAccount extends BaseAuditableEntity {
    @Column(name = "keycloak_id", nullable = false, length = 64)
    private String keycloakId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Optional, as typed (with country code); kept here only, not in Keycloak. */
    @Column(name = "mobile", length = 30)
    private String mobile;

    /** Profile picture: a file of the company's media library. */
    @Column(name = "avatar_media_id")
    private Long avatarMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EUserStatus status;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @ManyToMany
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
