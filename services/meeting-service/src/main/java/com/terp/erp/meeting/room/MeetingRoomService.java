package com.terp.erp.meeting.room;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.meeting.meeting.MeetingRepository;
import com.terp.erp.meeting.room.MeetingRoom.EType;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Meeting Rooms page (docs/analysis/06 §9.1.4): name unique in the company (MTG_003); a room with meetings cannot be
 * deleted (MTG_004), deactivate it instead.
 */
@Service
@RequiredArgsConstructor
public class MeetingRoomService {
    private final MeetingRoomRepository rooms;
    private final MeetingRepository meetings;

    public record RoomForm(
            @Schema(example = "Room A", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Size(max = 1000) String description,
            @Schema(example = "PHYSICAL", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EType type,
            @Schema(description = "Address of a physical room or join link of a virtual one") @Size(max = 500)
            String location,
            @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(1) @Max(10000)
            Integer capacity,
            @Schema(description = "One item per entry; blanks and duplicates dropped", example = "[\"Projector\"]")
            @Size(max = 50) List<@Size(max = 100) String> equipment,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status) {
    }

    /** equipmentCount = the Equipment column; meetings = the Meetings column (cancelled included). */
    public record RoomDto(String id, String name, String description, EType type, String location, int capacity,
                          List<String> equipment, int equipmentCount, long meetings, EStatus status,
                          Instant createdDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record Cards(long totalRooms, long activeRooms, long physicalRooms, long virtualRooms) {
    }

    public record Option(String id, String name, EType type, int capacity) {
    }

    @Transactional(readOnly = true)
    public Page<RoomDto> search(Long requestedCompanyId, String keyword, EType type, EStatus status,
                                Pageable pageable) {
        Page<MeetingRoom> page = rooms.search(Texts.company(requestedCompanyId), type, status, Texts.kw(keyword),
                pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            meetings.countByRoom(page.map(MeetingRoom::getId).toList())
                    .forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        }
        return page.map(r -> dto(r, counts.getOrDefault(r.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, EType type) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : rooms.countByStatus(Texts.company(requestedCompanyId), type, Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Total, Active, Physical and Virtual Rooms over every room of the company. */
    @Transactional(readOnly = true)
    public Cards cards(Long requestedCompanyId) {
        long total = 0;
        long active = 0;
        long physical = 0;
        long virtual = 0;
        for (Object[] r : rooms.countByStatusAndType(Texts.company(requestedCompanyId))) {
            long n = (Long) r[2];
            total += n;
            active += r[0] == EStatus.ACTIVE ? n : 0;
            physical += r[1] == EType.PHYSICAL ? n : 0;
            virtual += r[1] == EType.VIRTUAL ? n : 0;
        }
        return new Cards(total, active, physical, virtual);
    }

    /** The Meeting Room dropdown of the meeting form: active rooms by name. */
    @Transactional(readOnly = true)
    public List<Option> options(Long requestedCompanyId) {
        return rooms.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId),
                        EStatus.ACTIVE).stream()
                .map(r -> new Option(r.getId().toString(), r.getName(), r.getType(), r.getCapacity())).toList();
    }

    @Transactional(readOnly = true)
    public RoomDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public RoomDto create(Long requestedCompanyId, RoomForm form) {
        MeetingRoom r = new MeetingRoom();
        r.setCompanyId(Texts.company(requestedCompanyId));
        apply(r, form);
        return dto(rooms.save(r), 0);
    }

    /** Full replacement (equipment = full list); status omitted = kept. */
    @Transactional
    public RoomDto update(Long id, RoomForm form) {
        MeetingRoom r = require(id);
        apply(r, form);
        return detail(r);
    }

    @Transactional
    public RoomDto changeStatus(Long id, EStatus status) {
        MeetingRoom r = require(id);
        r.setStatus(status);
        return detail(r);
    }

    @Transactional
    public void delete(Long id) {
        MeetingRoom r = require(id);
        if (meetings.existsByRoomIdAndDeletedFalse(r.getId())) {
            throw ApiException.badRequest(EMeetingError.MTG_004);
        }
        r.setDeleted(true);
    }

    private void apply(MeetingRoom r, RoomForm f) {
        String name = f.name().trim();
        if (rooms.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(r.getCompanyId(), name,
                r.getId() == null ? -1L : r.getId())) {
            throw ApiException.badRequest(EMeetingError.MTG_003);
        }
        r.setName(name);
        r.setDescription(Texts.blankToNull(f.description()));
        r.setType(f.type());
        r.setLocation(Texts.blankToNull(f.location()));
        r.setCapacity(f.capacity());
        r.getEquipment().clear();
        if (f.equipment() != null) {
            r.getEquipment().addAll(new LinkedHashSet<>(f.equipment().stream().map(Texts::blankToNull)
                    .filter(Objects::nonNull).toList()));
        }
        if (f.status() != null) {
            r.setStatus(f.status());
        }
    }

    private RoomDto detail(MeetingRoom r) {
        long n = meetings.countByRoom(List.of(r.getId())).stream().mapToLong(x -> (Long) x[1]).sum();
        return dto(r, n);
    }

    static RoomDto dto(MeetingRoom r, long meetingCount) {
        return new RoomDto(r.getId().toString(), r.getName(), r.getDescription(), r.getType(), r.getLocation(),
                r.getCapacity(), List.copyOf(r.getEquipment()), r.getEquipment().size(), meetingCount, r.getStatus(),
                r.getCreatedDate());
    }

    public MeetingRoom require(Long id) {
        MeetingRoom r = rooms.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EMeetingError.MTG_005));
        TenantGuard.assertSameCompany(r.getCompanyId(), EMeetingError.MTG_005);
        return r;
    }
}
