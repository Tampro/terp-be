package com.terp.erp.employee.announcement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** First time an employee opened an announcement; one row per (announcement, employee). */
@Getter
@Setter
@Entity
@Table(name = "announcement_view")
public class AnnouncementView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;
}
