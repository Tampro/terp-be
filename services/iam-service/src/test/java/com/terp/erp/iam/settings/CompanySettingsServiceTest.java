package com.terp.erp.iam.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.language.LanguageRepository;
import com.terp.erp.iam.repository.CurrencyRepository;
import com.terp.erp.iam.settings.CompanySettingsService.CompanyBrand;
import com.terp.erp.iam.settings.CompanySettingsService.CompanyCurrency;
import com.terp.erp.iam.settings.CompanySettingsService.CompanySystem;
import com.terp.erp.iam.settings.SettingsDtos.BrandSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencyFormatSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencySettingsView;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;
import com.terp.erp.iam.user.UserProfileDeleteTest;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanySettingsServiceTest {
    private final Map<String, CompanySetting> rows = new HashMap<>();
    private final CompanySettingRepository repo = mock(CompanySettingRepository.class);
    private final SettingsService platform = mock(SettingsService.class);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final CurrencyRepository currencies = mock(CurrencyRepository.class);
    private final CompanySettingsService service = new CompanySettingsService(repo, platform, languages, currencies,
            new ObjectMapper());

    @BeforeEach
    void setUp() {
        UserProfileDeleteTest.login(1L, 10L, "IAM_SETTINGS_MANAGE");
        when(repo.findByCompanyIdAndSection(anyLong(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(1))));
        when(repo.save(any())).thenAnswer(i -> {
            CompanySetting s = i.getArgument(0);
            rows.put(s.getSection(), s);
            return s;
        });
        when(platform.system()).thenReturn(new SystemSettings("vi", "dd/MM/yyyy", "HH:mm", DayOfWeek.MONDAY,
                "Asia/Ho_Chi_Minh", false, true, false, null, true));
        when(platform.brand()).thenReturn(new BrandSettings(null, null, null, "ERP", null, "blue",
                SettingsDtos.ESidebarVariant.INSET, SettingsDtos.ESidebarStyle.PLAIN, SettingsDtos.ELayoutDirection.LTR,
                SettingsDtos.EThemeMode.LIGHT, null, null));
        when(platform.currency()).thenReturn(new CurrencySettingsView(new CurrencyFormatSettings(0,
                SettingsDtos.ESymbolPosition.AFTER, SettingsDtos.ESeparator.COMMA, SettingsDtos.ESeparator.DOT, false,
                true), "VND", "₫"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownValuesWinAndTheRestIsThePlatforms() {
        var none = service.effective(null);
        assertThat(none.system().defaultLanguage()).isEqualTo("vi");
        assertThat(none.currency().currencyCode()).isEqualTo("VND");

        var ko = new com.terp.erp.iam.language.Language();
        ko.setCode("ko");
        ko.setEnabled(true);
        when(languages.findByCode("ko")).thenReturn(Optional.of(ko));
        service.saveSystem(null, new CompanySystem("ko", null, null, null, "Asia/Seoul"));
        service.saveBrand(null, new CompanyBrand(null, null, null, "Acme HR", "", "#112233", null, null, null, null,
                null, null));
        var usd = new com.terp.erp.iam.model.Currency();
        usd.setCode("USD");
        usd.setSymbol("$");
        when(currencies.existsByCode("USD")).thenReturn(true);
        when(currencies.findByCode("USD")).thenReturn(Optional.of(usd));
        service.saveCurrency(null, new CompanyCurrency("USD", 2, SettingsDtos.ESymbolPosition.BEFORE, null, null, true, false));

        var e = service.effective(null);
        assertThat(e.system().defaultLanguage()).isEqualTo("ko");
        assertThat(e.system().defaultTimezone()).isEqualTo("Asia/Seoul");
        assertThat(e.system().dateFormat()).isEqualTo("dd/MM/yyyy");
        assertThat(e.brand().titleText()).isEqualTo("Acme HR");
        assertThat(e.brand().themeColor()).isEqualTo("#112233");
        assertThat(e.brand().sidebarVariant()).isEqualTo(SettingsDtos.ESidebarVariant.INSET);
        assertThat(e.currency().currencyCode()).isEqualTo("USD");
        assertThat(e.currency().currencySymbol()).isEqualTo("$");
        assertThat(e.currency().format().decimalPlaces()).isEqualTo(2);
        assertThat(e.currency().format().thousandsSeparator()).isEqualTo(SettingsDtos.ESeparator.DOT);
    }

    @Test
    void unknownLanguageZoneOrCurrencyIsRefused() {
        assertError(() -> service.saveSystem(null, new CompanySystem("ja", null, null, null, null)), ESettingsError.SETTINGS_008);
        assertError(() -> service.saveSystem(null, new CompanySystem(null, null, null, null, "Mars/Base")), ESettingsError.SETTINGS_001);
        assertError(() -> service.saveCurrency(null, new CompanyCurrency("XYZ", null, null, null, null, null, null)), ESettingsError.SETTINGS_009);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ESettingsError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
