package com.terp.erp.iam.ip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.CompanyAllowedIp;
import com.terp.erp.iam.model.CompanyIpRestriction;
import com.terp.erp.iam.repository.CompanyAllowedIpRepository;
import com.terp.erp.iam.repository.CompanyIpRestrictionRepository;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;
import com.terp.erp.iam.settings.SettingsService;
import com.terp.erp.iam.user.UserProfileDeleteTest;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpRestrictionTest {
    private final CompanyIpRestrictionRepository switches = mock(CompanyIpRestrictionRepository.class);
    private final CompanyAllowedIpRepository ips = mock(CompanyAllowedIpRepository.class);
    private final SettingsService settings = mock(SettingsService.class);
    private final IpRestrictionService service = new IpRestrictionService(switches, ips, settings,
            Clock.systemUTC());
    private final List<CompanyAllowedIp> list = new ArrayList<>();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void parsesAndMatches() {
        IpRange net = IpRange.parse(" 203.0.113.77/24 ");
        assertThat(net.text()).isEqualTo("203.0.113.0/24");
        assertThat(net.contains(IpRange.literal("203.0.113.5"))).isTrue();
        assertThat(net.contains(IpRange.literal("203.0.114.5"))).isFalse();
        assertThat(IpRange.parse("10.0.0.1").text()).isEqualTo("10.0.0.1");
        IpRange v6 = IpRange.parse("2001:db8::/32");
        assertThat(v6.contains(IpRange.literal("2001:db8:1::5"))).isTrue();
        assertThat(v6.contains(IpRange.literal("203.0.113.5"))).isFalse();
        for (String bad : new String[]{"example.com", "10.0.0.1/33", "10.0.0/8x", "", "localhost"}) {
            assertThatThrownBy(() -> IpRange.parse(bad)).isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_020));
        }
        assertThat(IpRange.literal("evil.example.com")).isNull();
    }

    @Test
    void loginIsCheckedOnlyWhenBothSwitchesAreOnAndTheListIsNotEmpty() {
        setUp(true, true);
        assertThatCode(() -> service.assertLoginAllowed(10L, "1.2.3.4")).doesNotThrowAnyException();
        list.add(entry("203.0.113.0/24"));
        assertThatCode(() -> service.assertLoginAllowed(10L, "203.0.113.9")).doesNotThrowAnyException();
        assertCode(() -> service.assertLoginAllowed(10L, "1.2.3.4"), EIamError.AUTH_012);
        assertCode(() -> service.assertLoginAllowed(10L, null), EIamError.AUTH_012);
        setUp(true, false);
        assertThatCode(() -> service.assertLoginAllowed(10L, "1.2.3.4")).doesNotThrowAnyException();
        setUp(false, true);
        assertThatCode(() -> service.assertLoginAllowed(10L, "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void aCompanyCannotLockItselfOut() {
        setUp(true, false);
        UserProfileDeleteTest.login(1L, 10L, "IAM_SETTINGS_MANAGE");
        list.add(entry("203.0.113.0/24"));
        assertCode(() -> service.setEnabled(null, true, "1.2.3.4"), EIamError.IAM_021);
        when(switches.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.setEnabled(null, true, "203.0.113.9").enabled()).isTrue();
    }

    private void setUp(boolean platform, boolean company) {
        when(settings.system()).thenReturn(new SystemSettings("vi", "dd/MM/yyyy", "HH:mm", DayOfWeek.MONDAY,
                "Asia/Ho_Chi_Minh", false, true, false, null, platform));
        CompanyIpRestriction s = new CompanyIpRestriction();
        s.setCompanyId(10L);
        s.setEnabled(company);
        when(switches.findById(10L)).thenReturn(Optional.of(s));
        when(ips.findByCompanyId(10L)).thenReturn(list);
    }

    private static CompanyAllowedIp entry(String ip) {
        CompanyAllowedIp a = new CompanyAllowedIp();
        a.setId((long) ip.hashCode());
        a.setCompanyId(10L);
        a.setIpAddress(ip);
        return a;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EIamError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
