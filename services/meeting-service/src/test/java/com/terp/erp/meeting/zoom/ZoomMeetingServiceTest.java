package com.terp.erp.meeting.zoom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.config.MeetingProperties;
import com.terp.erp.meeting.zoom.ZoomClient.Credentials;
import com.terp.erp.meeting.zoom.ZoomMeetingService.MeetingForm;
import com.terp.erp.security.PermissionChecker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZoomMeetingServiceTest {
    private final ZoomMeetingRepository meetings = mock(ZoomMeetingRepository.class);
    private final ZoomSettingService settings = mock(ZoomSettingService.class);
    private final ZoomClient zoom = mock(ZoomClient.class);
    private final SecretCipher cipher = new SecretCipher(new MeetingProperties("i", "e",
            Base64.getEncoder().encodeToString(new byte[32]), new MeetingProperties.Zoom("o", "a")));
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final PermissionChecker permissions = mock(PermissionChecker.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = org.mockito.Mockito.mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final ZoomMeetingService service = new ZoomMeetingService(meetings, settings, zoom, cipher, employees,
            permissions, outbox);
    private final Credentials creds = new Credentials(10L, "acc", "cid", "sec");

    @BeforeEach
    void setUp() throws Exception {
        TestLogin.as(10L, "ZOOM_MANAGE");
        when(settings.verified(10L)).thenReturn(creds);
        when(employees.byIds(any(), eq(10L))).thenReturn(Map.of(7L, new EmployeeClient.Employee("7", "10", "E7",
                "Test Seven", "t7@example.invalid", null, "ACTIVE", null, null)));
        when(zoom.post(eq(creds), eq("/users/me/meetings"), anyMap())).thenReturn(new ObjectMapper().readTree(
                "{\"id\":81001,\"password\":\"123456\",\"join_url\":\"https://z.invalid/j/81001\","
                        + "\"start_url\":\"https://z.invalid/s/81001?zak=t\"}"));
        when(meetings.saveAndFlush(any())).thenAnswer(i -> {
            ZoomMeeting z = i.getArgument(0);
            z.setId(5L);
            return z;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bodyIsAScheduledMeetingInLocalTime() {
        var b = ZoomMeetingService.body(form(null), "123456", true);
        assertThat(b).containsEntry("type", 2).containsEntry("start_time", "2026-11-25T09:30:00")
                .containsEntry("timezone", "Asia/Ho_Chi_Minh").containsEntry("duration", 45)
                .containsEntry("password", "123456");
        assertThat(ZoomMeetingService.body(form(null), "x", false)).doesNotContainKey("type");
    }

    @Test
    void createStoresTheStartUrlEncryptedAndMasksForViewers() {
        when(permissions.has("ZOOM_MANAGE")).thenReturn(true);
        var dto = service.create(null, form(null));
        {
            var ev = org.mockito.ArgumentCaptor.forClass(Object.class);
            org.mockito.Mockito.verify(outbox, org.mockito.Mockito.atLeastOnce()).publish(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("meeting.zoom.created"), org.mockito.ArgumentMatchers.eq("ZOOM_MEETING"),
                    org.mockito.ArgumentMatchers.any(), ev.capture());
            @SuppressWarnings("unchecked") var p = (java.util.Map<String, Object>) ev.getValue();
            org.assertj.core.api.Assertions.assertThat(p).containsKeys("meetingId", "title", "startDate", "startTime");
            org.assertj.core.api.Assertions.assertThat(p).doesNotContainKeys("passcode", "joinUrl", "startUrl");
        }
        assertThat(dto.zoomMeetingId()).isEqualTo("81001");
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(10, 15));
        assertThat(dto.startUrl()).isEqualTo("https://z.invalid/s/81001?zak=t");
        assertThat(dto.passcode()).isEqualTo("123456");
        assertThat(dto.invitees()).extracting(ZoomMeetingService.Person::fullName).containsExactly("Test Seven");

        ZoomMeeting stored = new ZoomMeeting();
        stored.setId(5L);
        stored.setCompanyId(10L);
        stored.setStartTime(LocalTime.of(9, 0));
        stored.setDurationMinutes(30);
        stored.setPasscode("123456");
        stored.setStartUrlCipher(cipher.encrypt("https://z.invalid/s/1?zak=t", 10L));
        assertThat(stored.getStartUrlCipher()).doesNotContain("zak");
        when(meetings.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(stored));
        when(permissions.has("ZOOM_MANAGE")).thenReturn(false);
        var viewer = service.get(5L);
        assertThat(viewer.startUrl()).isNull();
        assertThat(viewer.passcode()).isNull();
    }

    @Test
    void failedLocalSaveDeletesTheZoomMeeting() {
        doThrow(new IllegalStateException("db down")).when(meetings).saveAndFlush(any());
        assertThatThrownBy(() -> service.create(null, form("abc123"))).isInstanceOf(IllegalStateException.class);
        verify(zoom).delete(creds, "/meetings/81001");
    }

    @Test
    void zoomDownSavesNothingAndRules() {
        when(zoom.post(eq(creds), eq("/users/me/meetings"), anyMap()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMeetingError.MTG_024));
        assertCode(() -> service.create(null, form(null)), EMeetingError.MTG_024);
        verify(meetings, never()).saveAndFlush(any());
        assertCode(() -> service.create(null, new MeetingForm("X", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), 30,
                null, Set.of(99L), null, null, null, null, null)), EMeetingError.MTG_013);
        ZoomMeeting ended = new ZoomMeeting();
        ended.setId(6L);
        ended.setCompanyId(10L);
        ended.setStatus(ZoomMeeting.EStatus.ENDED);
        when(meetings.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(ended));
        assertCode(() -> service.start(6L), EMeetingError.MTG_028);
        assertCode(() -> service.between(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null),
                EMeetingError.MTG_021);
    }

    @Test
    void calendarIsScopedToTheCallersCompanyAndAllows62Days() {
        ZoomMeeting z = new ZoomMeeting();
        z.setId(8L);
        z.setCompanyId(10L);
        z.setTopic("Sync");
        z.setStartDate(LocalDate.of(2026, 11, 25));
        z.setStartTime(LocalTime.of(9, 0));
        z.setDurationMinutes(30);
        z.setStatus(ZoomMeeting.EStatus.SCHEDULED);
        LocalDate from = LocalDate.of(2026, 10, 26);
        LocalDate to = from.plusDays(61);
        when(meetings.between(10L, from, to, null)).thenReturn(java.util.List.of(z));
        // a company user naming another company still gets their own
        assertThat(service.calendar(99L, from, to)).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo("8");
            assertThat(e.endTime()).isEqualTo(LocalTime.of(9, 30));
        });
        assertCode(() -> service.calendar(null, from, to.plusDays(1)), EMeetingError.MTG_021);
        assertCode(() -> service.calendar(null, to, from), EMeetingError.MTG_021);
        TestLogin.superAdmin();
        assertCode(() -> service.calendar(null, from, to), EMeetingError.MTG_001);
    }

    private static MeetingForm form(String passcode) {
        return new MeetingForm(" Team Sync ", LocalDate.of(2026, 11, 25), LocalTime.of(9, 30), 45, passcode,
                Set.of(7L), "Agenda", true, false, true, ZoomMeeting.ERecording.CLOUD);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EMeetingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
