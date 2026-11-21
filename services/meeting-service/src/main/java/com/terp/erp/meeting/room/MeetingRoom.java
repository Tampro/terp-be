package com.terp.erp.meeting.room;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.meeting.common.BaseAuditableEntity;
import com.terp.erp.meeting.common.EStatus;

import java.util.ArrayList;
import java.util.List;

/** A physical or virtual meeting room (docs/analysis/06 §9.1.4). */
@Getter
@Setter
@Entity
@Table(name = "meeting_room")
public class MeetingRoom extends BaseAuditableEntity {
    public enum EType { PHYSICAL, VIRTUAL }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 10)
    private EType type;

    /** The address of a physical room or the join link of a virtual one. */
    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @ElementCollection
    @CollectionTable(name = "meeting_room_equipment", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 100)
    private List<String> equipment = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
