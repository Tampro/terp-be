package com.terp.erp.employee.announcement;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An announcement (docs/analysis/06 §6.3.5). companyWide = every employee; otherwise the audience is the employees of
 * ANY target branch OR ANY target department (PO, 2026-11-19: union).
 */
@Getter
@Setter
@Entity
@Table(name = "announcement")
public class Announcement extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    /** Sanitized HTML. */
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    // HIGH_PRIORITY is a MySQL reserved word.
    @Column(name = "is_high_priority", nullable = false)
    private boolean highPriority;

    @Column(name = "company_wide", nullable = false)
    private boolean companyWide;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EAnnouncementStatus status = EAnnouncementStatus.ACTIVE;

    @ElementCollection
    @CollectionTable(name = "announcement_branch", joinColumns = @JoinColumn(name = "announcement_id"))
    @Column(name = "branch_id", nullable = false)
    private Set<Long> branchIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "announcement_department", joinColumns = @JoinColumn(name = "announcement_id"))
    @Column(name = "department_id", nullable = false)
    private Set<Long> departmentIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "announcement_attachment", joinColumns = @JoinColumn(name = "announcement_id"))
    @OrderColumn(name = "position")
    private List<Attachment> attachments = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class Attachment {
        @Column(name = "media_file_id", nullable = false)
        private Long mediaFileId;

        @Column(name = "file_name", nullable = false, length = 255)
        private String fileName;

        @Column(name = "mime_type", length = 127)
        private String mimeType;

        @Column(name = "size_bytes", nullable = false)
        private long sizeBytes;
    }
}
