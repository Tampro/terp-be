package com.terp.erp.meeting.room;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.room.MeetingRoom.EType;
import com.terp.erp.meeting.room.MeetingRoomService.RoomForm;
import com.terp.erp.meeting.type.MeetingTypeRepository;
import com.terp.erp.meeting.type.MeetingTypeService;
import com.terp.erp.meeting.type.MeetingTypeService.TypeForm;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypeRoomServiceTest {
    private final MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
    private final MeetingTypeRepository types = mock(MeetingTypeRepository.class);
    private final com.terp.erp.meeting.meeting.MeetingRepository meetings =
            mock(com.terp.erp.meeting.meeting.MeetingRepository.class);
    private final MeetingRoomService roomService = new MeetingRoomService(rooms, meetings);
    private final MeetingTypeService typeService = new MeetingTypeService(types, meetings);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "MEETING_MANAGE");
        when(rooms.save(any())).thenAnswer(i -> {
            MeetingRoom r = i.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(types.save(any())).thenAnswer(i -> {
            var t = (com.terp.erp.meeting.type.MeetingType) i.getArgument(0);
            t.setId(2L);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roomEquipmentIsCleanedAndCounted() {
        var r = roomService.create(null, new RoomForm(" Room A ", null, EType.PHYSICAL, "Floor 2", 8,
                Arrays.asList("Projector", " ", "Whiteboard", "Projector", null), null));
        assertThat(r.name()).isEqualTo("Room A");
        assertThat(r.equipment()).containsExactly("Projector", "Whiteboard");
        assertThat(r.equipmentCount()).isEqualTo(2);
        assertThat(r.status()).isEqualTo(EStatus.ACTIVE);
        when(rooms.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), anyString(), anyLong()))
                .thenReturn(true);
        assertCode(() -> roomService.create(null, new RoomForm("Room A", null, EType.VIRTUAL, null, 5, List.of(),
                null)), EMeetingError.MTG_003);
    }

    @Test
    void roomCards() {
        when(rooms.countByStatusAndType(10L)).thenReturn(List.of(
                new Object[]{EStatus.ACTIVE, EType.PHYSICAL, 3L},
                new Object[]{EStatus.INACTIVE, EType.PHYSICAL, 1L},
                new Object[]{EStatus.ACTIVE, EType.VIRTUAL, 2L}));
        var c = roomService.cards(null);
        assertThat(c.totalRooms()).isEqualTo(6);
        assertThat(c.activeRooms()).isEqualTo(5);
        assertThat(c.physicalRooms()).isEqualTo(4);
        assertThat(c.virtualRooms()).isEqualTo(2);
    }

    @Test
    void typeDefaultsAndColour() {
        var t = typeService.create(null, new TypeForm("Team Meeting", null, "#22c55e", null, null));
        assertThat(t.color()).isEqualTo("#22C55E");
        assertThat(t.defaultDurationMinutes()).isEqualTo(60);
        assertThat(t.status()).isEqualTo(EStatus.ACTIVE);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EMeetingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
