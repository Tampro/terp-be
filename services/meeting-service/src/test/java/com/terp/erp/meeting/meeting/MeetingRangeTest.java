package com.terp.erp.meeting.meeting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.room.MeetingRoomRepository;
import com.terp.erp.meeting.type.MeetingTypeRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingRangeTest {
    private final MeetingRepository meetings = mock(MeetingRepository.class);
    private final MeetingService service = new MeetingService(meetings, mock(MeetingTypeRepository.class),
            mock(MeetingRoomRepository.class), mock(EmployeeClient.class),
            Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void periodOfTheCompanyAtMost62Days() {
        TestLogin.as(10L, "MEETING_VIEW");
        LocalDate from = LocalDate.of(2026, 11, 1);
        when(meetings.inRange(10L, from, from.plusDays(61))).thenReturn(List.of());
        assertThat(service.inRange(null, from, from.plusDays(61))).isEmpty();
        verify(meetings).inRange(10L, from, from.plusDays(61));
        assertThatThrownBy(() -> service.inRange(null, from, from.plusDays(62)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMeetingError.MTG_021);
        assertThatThrownBy(() -> service.inRange(null, from, from.minusDays(1)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMeetingError.MTG_021);
    }
}
