package com.terp.erp.support.portal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.HtmlSanitizer;
import com.terp.erp.support.common.Texts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Support portal settings and custom pages (reference SupportTicketSettingController, TitleSectionController,
 * CtaSectionController, SupportInformationController, ContactInformationController, QuickLinkController,
 * CustomPageController). The slug follows the career page rules (recruitment-service CareerSettingsService).
 * TODO(assumption): a suspended company's portal stays up until an admin disables it — support-service does not ask
 * IAM for the company status on public requests.
 */
@Service
@RequiredArgsConstructor
public class PortalSettingsService {
    static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final String URL = "^https?://\\S{1,490}$";

    public record Section(@Size(max = 255) String title, @Size(max = 2000) String description) {
    }

    /** Reference StoreTitleSectionRequest. */
    public record TitleSections(@Valid Section createTicket, @Valid Section searchTicket, @Valid Section knowledgeBase,
                                @Valid Section faq, @Valid Section contact) {
    }

    /** Reference StoreCtaSectionRequest. */
    public record CtaSections(@Valid Section knowledgeBase, @Valid Section faq) {
    }

    public record BusinessDay(@Schema(example = "Monday") @NotBlank @Size(max = 20) String day, boolean open) {
    }

    /** Reference StoreSupportInformationRequest. */
    public record SupportInformation(@Schema(example = "Within 24 hours") @Size(max = 255) String responseTime,
                                     @Schema(example = "08:00") @Size(max = 20) String openingHours,
                                     @Schema(example = "17:30") @Size(max = 20) String closingHours,
                                     @Schema(example = "+84 28 0000 0000") @Size(max = 255) String phoneSupport,
                                     @Valid @Size(max = 7) List<BusinessDay> businessHours) {
    }

    /** Reference StoreContactInformationRequest. */
    public record ContactInformation(@Size(max = 500) String address,
                                     @Schema(example = "+84 28 0000 0000") @Size(max = 255) String phone,
                                     @Schema(example = "support@example.com") @Email @Size(max = 255) String email,
                                     @Schema(description = "https URL of an embedded map")
                                     @Pattern(regexp = URL) String mapEmbedUrl) {
    }

    /** Reference StoreQuickLinkRequest (title, icon, link, order). */
    public record QuickLink(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String icon,
                            @NotBlank @Pattern(regexp = URL) String link, int order) {
    }

    public record PortalContent(@Valid TitleSections titleSections, @Valid CtaSections ctaSections,
                                @Valid SupportInformation supportInformation,
                                @Valid ContactInformation contactInformation,
                                @Valid @Size(max = 20) List<QuickLink> quickLinks) {
    }

    public record SettingsDto(String slug, boolean enabled, String brandName, String logoUrl, String faviconUrl,
                              PortalContent content, String publicPath) {
    }

    public record SettingsForm(
            @Schema(description = "Lowercase letters, digits, single hyphens; unique across companies",
                    example = "test-company", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50)
            String slug,
            @Schema(description = "Open the portal to the public") boolean enabled,
            @Schema(example = "Test Company Support", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String brandName,
            @Schema(description = "http(s) URL of the logo") @Pattern(regexp = URL) String logoUrl,
            @Schema(description = "http(s) URL of the favicon") @Pattern(regexp = URL) String faviconUrl,
            @Valid PortalContent content) {
    }

    public record PageRow(String id, String title, String slug, String description, boolean showInFooter) {
        static PageRow of(PortalPage p) {
            return new PageRow(String.valueOf(p.getId()), p.getTitle(), p.getSlug(), p.getDescription(),
                    p.isShowInFooter());
        }
    }

    public record PageDto(PageRow page, String contents) {
        static PageDto of(PortalPage p) {
            return new PageDto(PageRow.of(p), p.getContents());
        }
    }

    public record PageForm(
            @Schema(example = "Privacy policy", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String title,
            @Schema(description = "Lowercase letters, digits, single hyphens; null = made from the title",
                    example = "privacy-policy") @Size(max = 100) String slug,
            @Schema(description = "HTML; sanitized", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200_000) String contents,
            @Size(max = 500) String description,
            @Schema(description = "Link it in the portal footer") boolean showInFooter) {
    }

    private final PortalSettingsRepository settings;
    private final PortalPageRepository pages;
    private final ObjectMapper mapper;

    /** null when the company has not configured its portal yet. */
    @Transactional(readOnly = true)
    public SettingsDto get(Long requestedCompanyId) {
        return settings.findByCompanyId(Texts.company(requestedCompanyId)).map(this::dto).orElse(null);
    }

    /** Full replacement of the settings. */
    @Transactional
    public SettingsDto save(Long requestedCompanyId, SettingsForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        String slug = f.slug().trim().toLowerCase(Locale.ROOT);
        if (slug.length() < 3 || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest(ESupportError.SUP_011);
        }
        settings.findBySlug(slug).filter(o -> !o.getCompanyId().equals(companyId)).ifPresent(o -> {
            throw ApiException.badRequest(ESupportError.SUP_012);
        });
        PortalSettings s = settings.findByCompanyId(companyId).orElseGet(PortalSettings::new);
        s.setCompanyId(companyId);
        s.setSlug(slug);
        s.setEnabled(f.enabled());
        s.setBrandName(f.brandName().trim());
        s.setLogoUrl(Texts.blankToNull(f.logoUrl()));
        s.setFaviconUrl(Texts.blankToNull(f.faviconUrl()));
        s.setContent(write(f.content()));
        return dto(settings.save(s));
    }

    @Transactional(readOnly = true)
    public List<PageRow> pages(Long requestedCompanyId) {
        return pages.findByCompanyIdAndDeletedFalseOrderByTitleAsc(Texts.company(requestedCompanyId)).stream()
                .map(PageRow::of).toList();
    }

    @Transactional(readOnly = true)
    public PageDto page(Long id) {
        return PageDto.of(requirePage(id));
    }

    @Transactional
    public PageDto createPage(Long requestedCompanyId, PageForm f) {
        PortalPage p = new PortalPage();
        p.setCompanyId(Texts.company(requestedCompanyId));
        apply(p, f);
        return PageDto.of(pages.save(p));
    }

    @Transactional
    public PageDto updatePage(Long id, PageForm f) {
        PortalPage p = requirePage(id);
        apply(p, f);
        return PageDto.of(p);
    }

    @Transactional
    public void deletePage(Long id) {
        requirePage(id).setDeleted(true);
    }

    // ---- for the public portal ----

    PortalContent content(PortalSettings s) {
        if (s.getContent() == null) {
            return null;
        }
        try {
            return mapper.readValue(s.getContent(), PortalContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("portal content", e);
        }
    }

    // ----

    private void apply(PortalPage p, PageForm f) {
        String slug = Texts.blankToNull(f.slug());
        slug = slug == null ? slugOf(f.title()) : slug.toLowerCase(Locale.ROOT);
        if (slug.isEmpty() || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest(ESupportError.SUP_011);
        }
        String finalSlug = slug;
        pages.findByCompanyIdAndSlugAndDeletedFalse(p.getCompanyId(), slug)
                .filter(o -> !Objects.equals(o.getId(), p.getId())).ifPresent(o -> {
                    throw ApiException.badRequest(ESupportError.SUP_027, finalSlug);
                });
        if (HtmlSanitizer.isBlank(f.contents())) {
            throw ApiException.badRequest(ESupportError.SUP_034);
        }
        p.setTitle(f.title().trim());
        p.setSlug(slug);
        p.setContents(HtmlSanitizer.clean(f.contents()));
        p.setDescription(Texts.blankToNull(f.description()));
        p.setShowInFooter(f.showInFooter());
    }

    static String slugOf(String title) {
        String s = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.length() > 100 ? s.substring(0, 100).replaceAll("-$", "") : s;
    }

    private PortalPage requirePage(Long id) {
        PortalPage p = pages.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESupportError.SUP_026));
        TenantGuard.assertSameCompany(p.getCompanyId(), ESupportError.SUP_026);
        return p;
    }

    private String write(PortalContent c) {
        if (c == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("portal content", e);
        }
    }

    private SettingsDto dto(PortalSettings s) {
        return new SettingsDto(s.getSlug(), s.isEnabled(), s.getBrandName(), s.getLogoUrl(), s.getFaviconUrl(),
                content(s), "/api/v1/public/support/" + s.getSlug());
    }
}
