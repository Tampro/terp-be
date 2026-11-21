package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/** A role belongs to one company; each company gets the default set at creation and may add its own. */
@Getter
@Setter
@Entity
@Table(name = "role")
public class Role extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    /** Created from {@code EDefaultRole}; its code cannot change and it cannot be deleted. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @ManyToMany
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();
}
