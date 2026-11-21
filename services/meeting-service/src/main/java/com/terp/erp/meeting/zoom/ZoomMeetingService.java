package com.terp.erp.meeting.zoom;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.client.EmployeeClient.Employee;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.meeting.zoom.ZoomClient.Credentials;
import com.terp.erp.meeting.zoom.ZoomMeeting.EStatus;
import com.terp.erp.security.PermissionChecker;
import com.terp.erp.security.TenantGuard;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zoom Meetings page (docs/analysis/06 §9.3; PO 2026-11-21). Zoom is called first: when it fails nothing is saved
 * (MTG_024); a meeting created on Zoom whose local save then fails is deleted from Zoom again. Delete removes it from
 * Zoom too (a meeting Zoom no longer has is still deleted here). Status is local (Change Status); Start Meeting marks
 * it STARTED. Passcode and start URL are shown to ZOOM_MANAGE only.
 * TODO(assumption): start times are Vietnam local time until companies carry a time zone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoomMeetingService {
    public static final int MAX_RANGE_DAYS = 42;
    private static final String ZONE = "Asia/Ho_Chi_Minh";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ZoomMeetingRepository meetings;
    private final ZoomSettingService settings;
    private final ZoomClient zoom;
    private final SecretCipher cipher;
    private final EmployeeClient employees;
    private final PermissionChecker permissions;

    public record MeetingForm(
            @Schema(example = "Team Sync", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String topic,
            @Schema(example = "2026-11-25", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "09:30", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime startTime,
            @Schema(example = "45", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(1) @Max(1440)
            Integer durationMinutes,
            @Schema(description = "Omitted = a random 6-digit passcode (create) / kept (edit)", example = "a1B2c3")
            @Pattern(regexp = "^[A-Za-z0-9@_*-]{1,10}$") String passcode,
            @Schema(description = "Employee ids") @Size(max = 300) Set<@NotNull Long> inviteeIds,
            @Size(max = 2000) String agenda) {
    }

    public record Person(String id, String fullName, String email, String profileImageUrl) {
    }

    /** passcode and startUrl only for ZOOM_MANAGE (null otherwise). */
    public record MeetingDto(String id, String zoomMeetingId, String topic, String agenda, LocalDate startDate,
                             LocalTime startTime, LocalTime endTime, int durationMinutes, EStatus status,
                             String joinUrl, String passcode, String startUrl, List<Person> invitees,
                             String createdBy) {
    }

    /** Start Meeting: the host URL to open. */
    public record StartDto(String id, EStatus status, String startUrl) {
    }

    /** Calendar and the selected-date panel: meetings starting in [from, to] (at most 42 days). */
    @Transactional(readOnly = true)
    public List<MeetingDto> between(Long requestedCompanyId, LocalDate from, LocalDate to, EStatus status) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest(EMeetingError.MTG_021, MAX_RANGE_DAYS);
        }
        Long companyId = Texts.company(requestedCompanyId);
        return dtos(meetings.between(companyId, from, to, status), companyId);
    }

    @Transactional(readOnly = true)
    public MeetingDto get(Long id) {
        ZoomMeeting z = require(id);
        return dtos(List.of(z), z.getCompanyId()).get(0);
    }

    @Transactional
    public MeetingDto create(Long requestedCompanyId, MeetingForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        Credentials c = settings.verified(companyId);
        Set<Long> invitees = invitees(f, companyId);
        String passcode = f.passcode() != null ? f.passcode() : String.format("%06d", RANDOM.nextInt(1_000_000));
        JsonNode created = zoom.post(c, "/users/me/meetings", body(f, passcode, true));
        long zoomId = created.path("id").asLong();
        ZoomMeeting z = new ZoomMeeting();
        try {
            z.setCompanyId(companyId);
            z.setZoomMeetingId(zoomId);
            z.setPasscode(created.path("password").asText(passcode));
            z.setJoinUrl(created.path("join_url").asText());
            z.setStartUrlCipher(cipher.encrypt(created.path("start_url").asText(), companyId));
            apply(z, f, invitees);
            meetings.saveAndFlush(z);
        } catch (RuntimeException e) {
            // Compensate: never leave a Zoom meeting nobody can see here.
            try {
                zoom.delete(c, "/meetings/" + zoomId);
            } catch (RuntimeException ignored) {
                log.error("Could not remove Zoom meeting {} after a failed save", zoomId);
            }
            throw e;
        }
        return dtos(List.of(z), companyId).get(0);
    }

    /** Full replacement on Zoom and here; passcode omitted = kept. Not for ended / cancelled meetings (MTG_028). */
    @Transactional
    public MeetingDto update(Long id, MeetingForm f) {
        ZoomMeeting z = requireOpen(id);
        Set<Long> invitees = invitees(f, z.getCompanyId());
        String passcode = f.passcode() != null ? f.passcode() : z.getPasscode();
        zoom.patch(settings.verified(z.getCompanyId()), "/meetings/" + z.getZoomMeetingId(),
                body(f, passcode, false));
        z.setPasscode(passcode);
        apply(z, f, invitees);
        return dtos(List.of(z), z.getCompanyId()).get(0);
    }

    /** Change Status (local only; Delete is what removes it from Zoom). */
    @Transactional
    public MeetingDto changeStatus(Long id, EStatus status) {
        ZoomMeeting z = require(id);
        z.setStatus(status);
        return dtos(List.of(z), z.getCompanyId()).get(0);
    }

    /** Start Meeting: STARTED, with the host URL. */
    @Transactional
    public StartDto start(Long id) {
        ZoomMeeting z = requireOpen(id);
        z.setStatus(EStatus.STARTED);
        return new StartDto(z.getId().toString(), z.getStatus(), cipher.decrypt(z.getStartUrlCipher(),
                z.getCompanyId()));
    }

    /** Deletes it from Zoom too; when Zoom no longer has it, only here. */
    @Transactional
    public void delete(Long id) {
        ZoomMeeting z = require(id);
        Credentials c = settings.verified(z.getCompanyId());
        if (!zoom.delete(c, "/meetings/" + z.getZoomMeetingId())) {
            log.info("Zoom meeting {} was already gone from Zoom", z.getZoomMeetingId());
        }
        z.setDeleted(true);
    }

    private Set<Long> invitees(MeetingForm f, Long companyId) {
        Set<Long> ids = f.inviteeIds() == null ? Set.of() : new LinkedHashSet<>(f.inviteeIds());
        if (!ids.isEmpty() && !employees.byIds(ids, companyId).keySet().containsAll(ids)) {
            throw ApiException.badRequest(EMeetingError.MTG_013);
        }
        return ids;
    }

    private static void apply(ZoomMeeting z, MeetingForm f, Set<Long> invitees) {
        z.setTopic(f.topic().trim());
        z.setAgenda(Texts.blankToNull(f.agenda()));
        z.setStartDate(f.startDate());
        z.setStartTime(f.startTime());
        z.setDurationMinutes(f.durationMinutes());
        z.getInviteeIds().clear();
        z.getInviteeIds().addAll(invitees);
    }

    /** Zoom's meeting object: a scheduled meeting (type 2) in the company's local time. */
    static Map<String, Object> body(MeetingForm f, String passcode, boolean create) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("topic", f.topic().trim());
        if (create) {
            b.put("type", 2);
        }
        b.put("start_time", f.startDate() + "T" + f.startTime().withSecond(0).withNano(0) + ":00");
        b.put("timezone", ZONE);
        b.put("duration", f.durationMinutes());
        b.put("password", passcode);
        b.put("agenda", f.agenda() == null ? "" : f.agenda());
        return b;
    }

    private List<MeetingDto> dtos(List<ZoomMeeting> list, Long companyId) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> people = new HashSet<>();
        list.forEach(z -> people.addAll(z.getInviteeIds()));
        Map<Long, Employee> found = people.isEmpty() ? Map.of() : employees.byIds(people, companyId);
        boolean manage = permissions.has("ZOOM_MANAGE");
        return list.stream().map(z -> new MeetingDto(z.getId().toString(), String.valueOf(z.getZoomMeetingId()),
                z.getTopic(), z.getAgenda(), z.getStartDate(), z.getStartTime(),
                z.getStartTime().plusMinutes(z.getDurationMinutes()), z.getDurationMinutes(), z.getStatus(),
                z.getJoinUrl(), manage ? z.getPasscode() : null,
                manage ? cipher.decrypt(z.getStartUrlCipher(), z.getCompanyId()) : null,
                z.getInviteeIds().stream().map(i -> {
                    Employee e = found.get(i);
                    return e == null ? new Person(i.toString(), null, null, null)
                            : new Person(e.id(), e.fullName(), e.email(), e.profileImageUrl());
                }).toList(), z.getCreatedBy())).toList();
    }

    private ZoomMeeting requireOpen(Long id) {
        ZoomMeeting z = require(id);
        if (z.getStatus() == EStatus.ENDED || z.getStatus() == EStatus.CANCELLED) {
            throw ApiException.badRequest(EMeetingError.MTG_028);
        }
        return z;
    }

    public ZoomMeeting require(Long id) {
        ZoomMeeting z = meetings.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EMeetingError.MTG_027));
        TenantGuard.assertSameCompany(z.getCompanyId(), EMeetingError.MTG_027);
        return z;
    }
}
