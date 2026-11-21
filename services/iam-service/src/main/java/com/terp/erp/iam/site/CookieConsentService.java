package com.terp.erp.iam.site;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.iam.settings.SettingsService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TreeSet;

/**
 * Cookie consent logging (docs/analysis/06 §3.3.2): while Enable Logging is on, each choice a visitor makes on the
 * consent banner is kept; the super admin downloads them as CSV. Nothing is kept while logging is off.
 */
@Service
@RequiredArgsConstructor
public class CookieConsentService {
    static final String NECESSARY = "necessary";
    static final int MAX_EXPORT_DAYS = 366;

    private final CookieConsentRepository repo;
    private final SettingsService settings;

    public record ConsentForm(
            @Schema(description = "ACCEPT_ALL, NECESSARY_ONLY or CUSTOM", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull CookieConsent.EChoice choice,
            @Schema(description = "Categories accepted (necessary is always added)", example = "[\"analytics\"]")
            @Size(max = 10) List<@Pattern(regexp = "[a-z_]{1,30}") String> categories) {
    }

    /** Recorded only while logging is on; returns whether it was recorded. */
    @Transactional
    public boolean record(ConsentForm f, String clientIp) {
        if (!settings.cookie().enableLogging()) {
            return false;
        }
        TreeSet<String> cats = new TreeSet<>();
        cats.add(NECESSARY);
        if (f.choice() == CookieConsent.EChoice.CUSTOM && f.categories() != null) {
            cats.addAll(f.categories());
        }
        CookieConsent c = new CookieConsent();
        c.setChoice(f.choice());
        c.setCategories(f.choice() == CookieConsent.EChoice.ACCEPT_ALL ? "all" : String.join(",", cats));
        c.setIpMasked(mask(clientIp));
        repo.save(com.terp.erp.common.Nulls.nonNull(c));
        return true;
    }

    /** Download Accepted Cookies: CSV of the period (dates in UTC, both included; default the last 30 days). */
    @Transactional(readOnly = true)
    public byte[] csv(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || start.plusDays(MAX_EXPORT_DAYS).isBefore(end)) {
            throw com.terp.erp.exception.ApiException.badRequest(ESiteError.SITE_007, MAX_EXPORT_DAYS);
        }
        Instant a = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant b = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        StringBuilder out = new StringBuilder("consented_at,choice,categories,ip\r\n");
        for (CookieConsent c : repo.findByCreatedDateGreaterThanEqualAndCreatedDateLessThanOrderByCreatedDateAsc(a, b)) {
            out.append(c.getCreatedDate()).append(',').append(c.getChoice()).append(',')
                    .append('"').append(c.getCategories()).append('"').append(',')
                    .append(c.getIpMasked() == null ? "" : c.getIpMasked()).append("\r\n");
        }
        // BOM so Excel reads it as UTF-8.
        byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        return all;
    }

    /** 203.0.113.45 → 203.0.113.0; 2001:db8:abcd:12::1 → 2001:db8:abcd::. */
    static String mask(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String v = ip.trim();
        if (v.contains(":")) {
            String[] g = v.split(":", -1);
            return g.length >= 3 ? g[0] + ":" + g[1] + ":" + g[2] + "::" : null;
        }
        int dot = v.lastIndexOf('.');
        return dot > 0 && v.chars().filter(ch -> ch == '.').count() == 3 ? v.substring(0, dot) + ".0" : null;
    }
}
