package com.terp.erp.iam.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.language.LanguageRepository;
import com.terp.erp.iam.repository.CurrencyRepository;
import com.terp.erp.iam.settings.SettingsDtos.BrandSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencyFormatSettings;
import com.terp.erp.iam.settings.SettingsDtos.ELayoutDirection;
import com.terp.erp.iam.settings.SettingsDtos.ESeparator;
import com.terp.erp.iam.settings.SettingsDtos.ESidebarStyle;
import com.terp.erp.iam.settings.SettingsDtos.ESidebarVariant;
import com.terp.erp.iam.settings.SettingsDtos.ESymbolPosition;
import com.terp.erp.iam.settings.SettingsDtos.EThemeMode;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;
import com.terp.erp.security.TenantGuard;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;

/**
 * Company System / Brand / Currency Settings (docs/analysis/06 §10.5.1-10.5.3, PO 2026-11-21): a company overrides
 * single fields; a field left null uses the platform's value. `effective` merges both for the FE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanySettingsService {
    static final String SYSTEM = "SYSTEM";
    static final String BRAND = "BRAND";
    static final String CURRENCY = "CURRENCY";

    private final CompanySettingRepository repo;
    private final SettingsService platform;
    private final LanguageRepository languages;
    private final CurrencyRepository currencies;
    private final ObjectMapper mapper;

    // ------------------------------------------------------------------ DTOs (null = the platform's value)

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanySystem(
            @Schema(description = "Code of an enabled language", example = "en") @Pattern(regexp = "[a-z]{2,3}(-[A-Z]{2})?") String defaultLanguage,
            @Pattern(regexp = "dd/MM/yyyy|MM/dd/yyyy|yyyy-MM-dd|dd-MM-yyyy|dd.MM.yyyy") String dateFormat,
            @Pattern(regexp = "HH:mm|hh:mm a|HH:mm:ss") String timeFormat,
            DayOfWeek calendarStartDay,
            @Schema(description = "IANA time zone", example = "Asia/Seoul") @Size(max = 60) String defaultTimezone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyBrand(
            @Size(max = 1000) String logoDarkUrl,
            @Size(max = 1000) String logoLightUrl,
            @Size(max = 1000) String faviconUrl,
            @Size(max = 100) String titleText,
            @Size(max = 255) String footerText,
            @Pattern(regexp = "blue|green|purple|orange|red|teal|#[0-9a-fA-F]{6}") String themeColor,
            ESidebarVariant sidebarVariant,
            ESidebarStyle sidebarStyle,
            ELayoutDirection layoutDirection,
            EThemeMode themeMode,
            @Size(max = 30) @Pattern(regexp = "^$|[0-9+()\\-. ]{3,30}") String companyMobile,
            @Size(max = 500) String companyAddress) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyCurrency(
            @Schema(description = "Code of a currency of the Currency page", example = "USD") @Size(max = 10) String currencyCode,
            @Min(0) @Max(4) Integer decimalPlaces,
            ESymbolPosition symbolPosition,
            ESeparator decimalSeparator,
            ESeparator thousandsSeparator,
            Boolean showDecimals,
            Boolean addSpace) {
    }

    public record EffectiveSystem(String defaultLanguage, String dateFormat, String timeFormat,
                                  DayOfWeek calendarStartDay, String defaultTimezone) {
    }

    public record EffectiveCurrency(String currencyCode, String currencySymbol, CurrencyFormatSettings format) {
    }

    @Schema(description = "What applies to the company: its own value where set, else the platform's")
    public record EffectiveSettings(EffectiveSystem system, BrandSettings brand, EffectiveCurrency currency) {
    }

    // ------------------------------------------------------------------ reads and writes

    @Transactional(readOnly = true)
    public CompanySystem system(Long requestedCompanyId) {
        return read(company(requestedCompanyId), SYSTEM, CompanySystem.class);
    }

    /** Full replacement of the company's overrides (null fields = the platform's value). */
    @Transactional
    public CompanySystem saveSystem(Long requestedCompanyId, CompanySystem s) {
        if (s.defaultLanguage() != null
                && !languages.findByCode(s.defaultLanguage()).map(l -> l.isEnabled()).orElse(false)) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_008, s.defaultLanguage());
        }
        if (s.defaultTimezone() != null) {
            try {
                ZoneId.of(s.defaultTimezone());
            } catch (DateTimeException e) {
                throw ApiException.badRequest(ESettingsError.SETTINGS_001, s.defaultTimezone());
            }
        }
        return write(company(requestedCompanyId), SYSTEM, s);
    }

    @Transactional(readOnly = true)
    public CompanyBrand brand(Long requestedCompanyId) {
        return read(company(requestedCompanyId), BRAND, CompanyBrand.class);
    }

    @Transactional
    public CompanyBrand saveBrand(Long requestedCompanyId, CompanyBrand b) {
        return write(company(requestedCompanyId), BRAND, b);
    }

    @Transactional(readOnly = true)
    public CompanyCurrency currency(Long requestedCompanyId) {
        return read(company(requestedCompanyId), CURRENCY, CompanyCurrency.class);
    }

    @Transactional
    public CompanyCurrency saveCurrency(Long requestedCompanyId, CompanyCurrency c) {
        if (c.currencyCode() != null && !currencies.existsByCode(c.currencyCode())) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_009, c.currencyCode());
        }
        return write(company(requestedCompanyId), CURRENCY, c);
    }

    /** The merged settings the FE applies for this company. */
    @Transactional(readOnly = true)
    public EffectiveSettings effective(Long requestedCompanyId) {
        Long companyId = company(requestedCompanyId);
        SystemSettings ps = platform.system();
        CompanySystem cs = read(companyId, SYSTEM, CompanySystem.class);
        EffectiveSystem system = new EffectiveSystem(or(cs.defaultLanguage(), ps.defaultLanguage()),
                or(cs.dateFormat(), ps.dateFormat()), or(cs.timeFormat(), ps.timeFormat()),
                or(cs.calendarStartDay(), ps.calendarStartDay()), or(cs.defaultTimezone(), ps.defaultTimezone()));

        BrandSettings pb = platform.brand();
        CompanyBrand cb = read(companyId, BRAND, CompanyBrand.class);
        BrandSettings brand = new BrandSettings(or(cb.logoDarkUrl(), pb.logoDarkUrl()),
                or(cb.logoLightUrl(), pb.logoLightUrl()), or(cb.faviconUrl(), pb.faviconUrl()),
                or(cb.titleText(), pb.titleText()), or(cb.footerText(), pb.footerText()),
                or(cb.themeColor(), pb.themeColor()), or(cb.sidebarVariant(), pb.sidebarVariant()),
                or(cb.sidebarStyle(), pb.sidebarStyle()), or(cb.layoutDirection(), pb.layoutDirection()),
                or(cb.themeMode(), pb.themeMode()), or(cb.companyMobile(), pb.companyMobile()),
                or(cb.companyAddress(), pb.companyAddress()));

        var pc = platform.currency();
        CurrencyFormatSettings pf = pc.format();
        CompanyCurrency cc = read(companyId, CURRENCY, CompanyCurrency.class);
        CurrencyFormatSettings format = new CurrencyFormatSettings(or(cc.decimalPlaces(), pf.decimalPlaces()),
                or(cc.symbolPosition(), pf.symbolPosition()), or(cc.decimalSeparator(), pf.decimalSeparator()),
                or(cc.thousandsSeparator(), pf.thousandsSeparator()), or(cc.showDecimals(), pf.showDecimals()),
                or(cc.addSpace(), pf.addSpace()));
        String code = pc.defaultCurrencyCode();
        String symbol = pc.defaultCurrencySymbol();
        if (cc.currencyCode() != null) {
            var own = currencies.findByCode(cc.currencyCode());
            if (own.isPresent()) {
                code = own.get().getCode();
                symbol = own.get().getSymbol();
            }
        }
        return new EffectiveSettings(system, brand, new EffectiveCurrency(code, symbol, format));
    }

    // ------------------------------------------------------------------ helpers

    private <T> T read(Long companyId, String section, Class<T> type) {
        return repo.findByCompanyIdAndSection(companyId, section).map(row -> {
            try {
                return mapper.readValue(row.getValueJson(), type);
            } catch (JsonProcessingException e) {
                log.error("Company {} settings {} unreadable, using the platform's", companyId, section);
                return empty(type);
            }
        }).orElseGet(() -> empty(type));
    }

    private <T> T write(Long companyId, String section, T value) {
        CompanySetting row = repo.findByCompanyIdAndSection(companyId, section).orElseGet(() -> {
            CompanySetting n = new CompanySetting();
            n.setCompanyId(companyId);
            n.setSection(section);
            return n;
        });
        try {
            row.setValueJson(mapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("settings not serializable", e);
        }
        repo.save(row);
        return value;
    }

    /** A record with every field null: "use the platform's value everywhere". */
    private <T> T empty(Class<T> type) {
        return mapper.convertValue(java.util.Map.of(), type);
    }

    private static <T> T or(T own, T platform) {
        return own == null || (own instanceof String s && s.isBlank()) ? platform : own;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EIamError.IAM_003);
    }
}
