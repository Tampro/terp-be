package com.terp.erp.meeting.zoom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.config.MeetingProperties;
import com.terp.erp.meeting.zoom.ZoomSettingService.SettingsForm;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZoomSettingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T02:00:00Z");
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final SecretCipher cipher = new SecretCipher(new MeetingProperties("http://iam", "http://emp", KEY,
            new MeetingProperties.Zoom("http://oauth", "http://api")));
    private final ZoomSettingRepository settings = mock(ZoomSettingRepository.class);
    private final ZoomClient zoom = mock(ZoomClient.class);
    private final ZoomSettingService service = new ZoomSettingService(settings, cipher, zoom,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "ZOOM_MANAGE");
        when(settings.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cipherRoundTripIsBoundToTheCompany() {
        String stored = cipher.encrypt("s3cret-value", 10L);
        assertThat(stored).doesNotContain("s3cret");
        assertThat(cipher.decrypt(stored, 10L)).isEqualTo("s3cret-value");
        assertThatThrownBy(() -> cipher.decrypt(stored, 11L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EMeetingError.MTG_026));
        assertThatThrownBy(() -> new SecretCipher(new MeetingProperties("a", "b", "c2hvcnQ=",
                new MeetingProperties.Zoom("x", "y")))).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("c2hvcnQ=");
    }

    @Test
    void saveKeepsSecretWhenBlankAndResetsVerified() {
        when(settings.findByCompanyId(10L)).thenReturn(Optional.empty());
        assertCode(() -> service.save(null, new SettingsForm("acc", "cid", " ")), EMeetingError.MTG_025);

        ZoomSetting s = new ZoomSetting();
        s.setCompanyId(10L);
        s.setClientSecretCipher(cipher.encrypt("old", 10L));
        s.setVerified(true);
        when(settings.findByCompanyId(10L)).thenReturn(Optional.of(s));
        var dto = service.save(null, new SettingsForm(" acc ", "cid", null));
        assertThat(dto.accountId()).isEqualTo("acc");
        assertThat(dto.clientSecretSet()).isTrue();
        assertThat(dto.verified()).isFalse();
        assertThat(cipher.decrypt(s.getClientSecretCipher(), 10L)).isEqualTo("old");
    }

    @Test
    void testRemembersTheResult() {
        ZoomSetting s = new ZoomSetting();
        s.setCompanyId(10L);
        s.setAccountId("acc");
        s.setClientId("cid");
        s.setClientSecretCipher(cipher.encrypt("sec", 10L));
        when(settings.findByCompanyId(10L)).thenReturn(Optional.of(s));
        var ok = service.test(null);
        assertThat(ok.verified()).isTrue();
        assertThat(ok.verifiedAt()).isEqualTo(NOW);
        assertThat(service.status(null).enabled()).isTrue();
        assertThat(service.verified(10L).clientSecret()).isEqualTo("sec");

        doThrow(ApiException.badRequest(EMeetingError.MTG_023)).when(zoom).test(any());
        assertCode(() -> service.test(null), EMeetingError.MTG_023);
        assertThat(s.isVerified()).isFalse();
        assertCode(() -> service.verified(10L), EMeetingError.MTG_022);

        // Zoom down: the last result stays
        s.setVerified(true);
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMeetingError.MTG_024)).when(zoom).test(any());
        assertCode(() -> service.test(null), EMeetingError.MTG_024);
        assertThat(s.isVerified()).isTrue();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EMeetingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
