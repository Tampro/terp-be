package com.terp.erp.iam.login;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.iam.model.LoginHistory;
import com.terp.erp.iam.model.LoginHistory.EDeviceType;
import com.terp.erp.iam.model.LoginHistory.EUserType;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.LoginHistoryRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.user.UserProfileDeleteTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginHistoryServiceTest {
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    private final LoginHistoryRepository history = mock(LoginHistoryRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    // Unstubbed mock: lookup returns Optional.empty(), so geo fields stay null unless a test stubs it.
    private final GeoIpLookup geoIp = mock(GeoIpLookup.class);
    private final LoginHistoryService service = new LoginHistoryService(history, users,
            Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC), geoIp);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static HttpServletRequest request(String ua, String lang, String tz) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getHeader("User-Agent")).thenReturn(ua);
        when(r.getHeader("Accept-Language")).thenReturn(lang);
        when(r.getHeader("X-Client-Timezone")).thenReturn(tz);
        when(r.getHeader("X-Client-IP")).thenReturn("203.0.113.10");
        return r;
    }

    private static Role role(String code) {
        Role r = new Role();
        r.setCode(code);
        return r;
    }

    @Test
    void recordsDeviceLanguageTimezoneAndRoles() {
        UserAccount a = new UserAccount();
        a.setId(5L);
        a.setCompanyId(10L);
        a.getRoles().add(role("HR"));
        a.getRoles().add(role("COMPANY_ADMIN"));
        when(users.findByUsernameAndDeletedFalse("test.user")).thenReturn(Optional.of(a));

        service.record("test.user", request(IPHONE, "vi-VN,vi;q=0.9,en;q=0.8", "Asia/Ho_Chi_Minh"));

        ArgumentCaptor<LoginHistory> saved = ArgumentCaptor.forClass(LoginHistory.class);
        verify(history).save(saved.capture());
        LoginHistory h = saved.getValue();
        assertThat(h.getDeviceType()).isEqualTo(EDeviceType.MOBILE);
        assertThat(h.getOs()).isEqualTo("iOS");
        assertThat(h.getLanguage()).isEqualTo("vi-VN");
        assertThat(h.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(h.getRoleAtLogin()).isEqualTo("COMPANY_ADMIN,HR");
        assertThat(h.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(h.getCity()).isNull();
        assertThat(h.getCountry()).isNull();
        assertThat(h.getIsp()).isNull();
    }

    @Test
    void geoFieldsComeFromTheLookupOfTheClientIp() {
        when(users.findByUsernameAndDeletedFalse("test.user")).thenReturn(Optional.empty());
        when(geoIp.lookup("203.0.113.10"))
                .thenReturn(Optional.of(new GeoIpLookup.Geo("Test City", "Testland", "Example Net")));

        service.record("test.user", request(IPHONE, null, null));

        ArgumentCaptor<LoginHistory> saved = ArgumentCaptor.forClass(LoginHistory.class);
        verify(history).save(saved.capture());
        LoginHistory h = saved.getValue();
        assertThat(h.getCity()).isEqualTo("Test City");
        assertThat(h.getCountry()).isEqualTo("Testland");
        assertThat(h.getIsp()).isEqualTo("Example Net");
    }

    @Test
    void badTimezoneAndLanguageAreIgnoredAndSuperAdminRoleIsNamed() {
        when(users.findByUsernameAndDeletedFalse("superadmin")).thenReturn(Optional.empty());

        service.record("superadmin", request("curl/8.4.0", "*;q=0.5", "Mars/Olympus_Mons"));

        ArgumentCaptor<LoginHistory> saved = ArgumentCaptor.forClass(LoginHistory.class);
        verify(history).save(saved.capture());
        LoginHistory h = saved.getValue();
        assertThat(h.getTimezone()).isNull();
        assertThat(h.getLanguage()).isNull();
        assertThat(h.getDeviceType()).isEqualTo(EDeviceType.BOT);
        assertThat(h.getRoleAtLogin()).isEqualTo("SUPER_ADMIN");
        assertThat(h.getUserType()).isEqualTo(EUserType.SUPER_ADMIN);
    }

    @Test
    void deviceTypes() {
        assertThat(LoginHistoryService.deviceType("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0"))
                .isEqualTo(EDeviceType.DESKTOP);
        assertThat(LoginHistoryService.deviceType("Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) Safari/604.1"))
                .isEqualTo(EDeviceType.TABLET);
        assertThat(LoginHistoryService.deviceType("Mozilla/5.0 (Linux; Android 14; SM-X710) Chrome/130.0 Safari"))
                .isEqualTo(EDeviceType.TABLET);
        assertThat(LoginHistoryService.deviceType("Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/130.0 Mobile"))
                .isEqualTo(EDeviceType.MOBILE);
        assertThat(LoginHistoryService.deviceType("Googlebot/2.1")).isEqualTo(EDeviceType.BOT);
        assertThat(LoginHistoryService.deviceType(null)).isEqualTo(EDeviceType.UNKNOWN);
        assertThat(LoginHistoryService.timezone("x".repeat(65))).isNull();
        assertThat(LoginHistoryService.timezone(" UTC ")).isEqualTo("UTC");
    }

    @Test
    void ipFilterIsPassedTrimmedForTheCallersCompany() {
        UserProfileDeleteTest.login(1L, 10L, "IAM_USER_VIEW");
        Pageable page = PageRequest.of(0, 20);
        when(history.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.search(99L, null, null, " 203.0.113.10 ", null, page);
        service.search(null, null, null, "  ", null, page);

        verify(history).search(10L, null, EUserType.COMPANY_USER, "203.0.113.10", "", page);
        verify(history).search(eq(10L), eq(null), eq(EUserType.COMPANY_USER), eq(null), eq(""), eq(page));
    }
}
