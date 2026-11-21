package com.terp.erp.iam.site;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.settings.SettingsService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CookieConsentServiceTest {
    private final CookieConsentRepository repo = mock(CookieConsentRepository.class);
    private final SettingsService settings = mock(SettingsService.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private final CookieConsentService service = new CookieConsentService(repo, settings);

    @Test
    void ipIsShortened() {
        assertThat(CookieConsentService.mask("203.0.113.45")).isEqualTo("203.0.113.0");
        assertThat(CookieConsentService.mask("2001:db8:abcd:12::1")).isEqualTo("2001:db8:abcd::");
        assertThat(CookieConsentService.mask("not-an-ip")).isNull();
        assertThat(CookieConsentService.mask(null)).isNull();
    }

    @Test
    void recordedOnlyWhileLoggingIsOn() {
        when(settings.cookie().enableLogging()).thenReturn(false);
        assertThat(service.record(new CookieConsentService.ConsentForm(CookieConsent.EChoice.ACCEPT_ALL, null), "1.2.3.4")).isFalse();
        verify(repo, never()).save(any());

        when(settings.cookie().enableLogging()).thenReturn(true);
        assertThat(service.record(new CookieConsentService.ConsentForm(CookieConsent.EChoice.CUSTOM,
                List.of("marketing", "analytics")), "1.2.3.4")).isTrue();
        ArgumentCaptor<CookieConsent> saved = ArgumentCaptor.forClass(CookieConsent.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getCategories()).isEqualTo("analytics,marketing,necessary");
        assertThat(saved.getValue().getIpMasked()).isEqualTo("1.2.3.0");
    }

    @Test
    void csvOfThePeriod() {
        CookieConsent c = new CookieConsent();
        c.setChoice(CookieConsent.EChoice.NECESSARY_ONLY);
        c.setCategories("necessary");
        c.setIpMasked("1.2.3.0");
        c.setCreatedDate(Instant.parse("2026-11-20T08:00:00Z"));
        when(repo.findByCreatedDateGreaterThanEqualAndCreatedDateLessThanOrderByCreatedDateAsc(any(), any())).thenReturn(List.of(c));
        String csv = new String(service.csv(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), StandardCharsets.UTF_8);
        assertThat(csv).contains("consented_at,choice,categories,ip").contains("2026-11-20T08:00:00Z,NECESSARY_ONLY,\"necessary\",1.2.3.0");
        assertThatThrownBy(() -> service.csv(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESiteError.SITE_007);
    }
}
