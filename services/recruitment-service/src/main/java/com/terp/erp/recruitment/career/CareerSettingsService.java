package com.terp.erp.recruitment.career;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.Texts;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Career page settings (PO, 2026-11-20): each company picks a unique slug and turns its public page on.
 * TODO(assumption): a suspended company's page stays up until an admin disables it — recruitment-service does not
 * ask IAM for the company status on public requests.
 */
@Service
@RequiredArgsConstructor
public class CareerSettingsService {
    public record SettingsDto(String slug, boolean enabled, String displayName, String headline, String tagline,
                              String publicPath) {
    }

    public record SettingsForm(
            @Schema(description = "Lowercase letters, digits, single hyphens; unique across companies",
                    example = "test-company", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50)
            String slug,
            @Schema(description = "Show the page and accept applications") boolean enabled,
            @Schema(example = "Test Company", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String displayName,
            @Schema(example = "Build Your Dream Career") @Size(max = 200) String headline,
            @Size(max = 500) String tagline) {
    }

    static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final CareerSettingsRepository settings;

    @Transactional(readOnly = true)
    public SettingsDto get(Long requestedCompanyId) {
        return settings.findByCompanyId(CatalogService.company(requestedCompanyId))
                .map(CareerSettingsService::dto).orElse(null);
    }

    @Transactional
    public SettingsDto save(Long requestedCompanyId, SettingsForm f) {
        Long companyId = CatalogService.company(requestedCompanyId);
        String slug = f.slug().trim().toLowerCase(Locale.ROOT);
        if (slug.length() < 3 || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest(ERecruitmentError.REC_051);
        }
        settings.findBySlug(slug).filter(o -> !o.getCompanyId().equals(companyId)).ifPresent(o -> {
            throw ApiException.badRequest(ERecruitmentError.REC_004);
        });
        CareerSettings s = settings.findByCompanyId(companyId).orElseGet(CareerSettings::new);
        s.setCompanyId(companyId);
        s.setSlug(slug);
        s.setEnabled(f.enabled());
        s.setDisplayName(f.displayName().trim());
        s.setHeadline(Texts.blankToNull(f.headline()));
        s.setTagline(Texts.blankToNull(f.tagline()));
        return dto(settings.save(s));
    }

    private static SettingsDto dto(CareerSettings s) {
        return new SettingsDto(s.getSlug(), s.isEnabled(), s.getDisplayName(), s.getHeadline(), s.getTagline(),
                "/api/v1/public/career/" + s.getSlug());
    }
}
