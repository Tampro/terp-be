package com.terp.erp.iam.site;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.saas.CatalogService;
import com.terp.erp.iam.saas.SaasDtos.PlanDto;
import com.terp.erp.iam.settings.SettingsService;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The platform's public website (docs/analysis/06 §3.2.7-3.2.10): landing page sections, custom pages, contact
 * inquiries and newsletter subscribers. Managed by the super admin; read and posted to by anonymous visitors.
 */
@Slf4j
@Service
public class SiteService {
    /** The sections of Landing Page Settings, in their default display order. */
    public static final List<String> SECTIONS = List.of("general", "order", "advanced", "header", "hero", "footer",
            "features", "screenshots", "whyUs", "about", "team", "reviews", "plans", "faq", "newsletter", "contact");
    /** Settings sections, not displayed blocks of the page. */
    static final List<String> NOT_DISPLAYED = List.of("general", "order", "advanced");
    static final int MAX_SECTION_BYTES = 64 * 1024;
    private static final Safelist SAFE = Safelist.relaxed()
            .addTags("span", "s", "hr")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow")
            .preserveRelativeLinks(false);
    private static final Document.OutputSettings OUT = new Document.OutputSettings().prettyPrint(false);

    private final LandingSectionRepository sections;
    private final CustomPageRepository pages;
    private final ContactInquiryRepository inquiries;
    private final NewsletterSubscriberRepository subscribers;
    private final SettingsService settings;
    private final CatalogService catalog;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean customJs;
    @Value("${erp.app-url:http://localhost:5173}")
    private String appUrl;

    public SiteService(LandingSectionRepository sections, CustomPageRepository pages,
                       ContactInquiryRepository inquiries, NewsletterSubscriberRepository subscribers,
                       SettingsService settings, CatalogService catalog, ObjectMapper mapper, Clock clock,
                       @Value("${erp.landing.custom-js:false}") boolean customJs) {
        this.sections = sections;
        this.pages = pages;
        this.inquiries = inquiries;
        this.subscribers = subscribers;
        this.settings = settings;
        this.catalog = catalog;
        this.mapper = mapper;
        this.clock = clock;
        this.customJs = customJs;
    }

    // ------------------------------------------------------------------ DTOs

    public record PublicLanding(
            @Schema(description = "Displayed sections in display order (Order section; disabled ones left out)")
            List<String> order,
            @Schema(description = "Content of every saved section, by key (advanced.customJs only when the "
                    + "operator allows it: ERP_LANDING_CUSTOM_JS)") Map<String, JsonNode> sections,
            @Schema(description = "Plans offered (Plans section)") List<PlanDto> plans,
            @Schema(description = "Published custom pages for the navigation") List<PageLink> pages) {
    }

    public record PageLink(String title, String slug, int sortOrder) {
    }

    public record PageForm(
            @Schema(example = "Privacy Policy", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Rich text (HTML); scripts and event handlers are removed",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 500_000) String content,
            @Schema(description = "Recommended 50-60 characters") @Size(max = 200) String metaTitle,
            @Schema(description = "Recommended 150-160 characters") @Size(max = 500) String metaDescription,
            @Schema(description = "Navigation order, lower first") @Min(0) @Max(100_000) Integer sortOrder,
            @Schema(description = "false = draft") @NotNull Boolean published) {
    }

    public record PageDto(String id, String title, String slug, String content, String metaTitle,
                          String metaDescription, int sortOrder, boolean published, Instant createdDate,
                          Instant updatedDate) {
        static PageDto of(CustomPage p) {
            return new PageDto(p.getId().toString(), p.getTitle(), p.getSlug(), p.getContent(), p.getMetaTitle(),
                    p.getMetaDescription(), p.getSortOrder(), p.isPublished(), p.getCreatedDate(), p.getUpdatedDate());
        }
    }

    public record PageRow(String id, String title, String slug,
                          @Schema(description = "Plain-text preview of the content") String preview,
                          boolean published, Instant createdDate) {
    }

    public record ContactForm(
            @Schema(example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150) String name,
            @Schema(example = "a@example.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String subject,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 5000) String message,
            @Schema(description = "reCAPTCHA token; required when reCAPTCHA is enabled") @Size(max = 4000) String recaptchaToken) {
    }

    public record NewsletterForm(
            @Schema(example = "a@example.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "reCAPTCHA token; required when reCAPTCHA is enabled") @Size(max = 4000) String recaptchaToken) {
    }

    public record InquiryDto(String id, String name, String email, String phone, String subject, String message,
                             ContactInquiry.EStatus status, Instant createdDate, Instant lastRepliedAt) {
        static InquiryDto of(ContactInquiry c) {
            return new InquiryDto(c.getId().toString(), c.getName(), c.getEmail(), c.getPhone(), c.getSubject(),
                    c.getMessage(), c.getStatus(), c.getCreatedDate(), c.getLastRepliedAt());
        }
    }

    public record StatusForm(@NotNull ContactInquiry.EStatus status) {
    }

    public record ReplyForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String subject,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 10_000) String message) {
    }

    public record SubscriberDto(String id, String email, Instant subscribedDate,
                                @Schema(description = "One-click unsubscribe link to put in every newsletter email")
                                String unsubscribeUrl) {
    }

    public record UnsubscribeForm(
            @Schema(description = "Token of the unsubscribe link", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 64) String token) {
    }

    // ------------------------------------------------------------------ landing page

    @Transactional(readOnly = true)
    public Map<String, JsonNode> sections() {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        SECTIONS.forEach(k -> out.put(k, sections.findBySectionKey(k).map(s -> read(s.getContent())).orElse(null)));
        return out;
    }

    /** Full replacement of one section's document. */
    @Transactional
    public JsonNode saveSection(String key, JsonNode content) {
        if (!SECTIONS.contains(key)) {
            throw ApiException.notFound(ESiteError.SITE_001, key);
        }
        String json;
        try {
            json = mapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest(ESiteError.SITE_002);
        }
        if (content == null || !content.isObject() || json.getBytes(StandardCharsets.UTF_8).length > MAX_SECTION_BYTES) {
            throw ApiException.badRequest(ESiteError.SITE_002);
        }
        LandingSection s = sections.findBySectionKey(key).orElseGet(() -> {
            LandingSection n = new LandingSection();
            n.setSectionKey(key);
            return n;
        });
        s.setContent(json);
        sections.save(s);
        return content;
    }

    /** What a visitor's browser gets; 404 SITE_003 while System Settings → Landing Page is off. */
    @Transactional(readOnly = true)
    public PublicLanding publicLanding() {
        if (!settings.system().landingPage()) {
            throw ApiException.notFound(ESiteError.SITE_003);
        }
        Map<String, JsonNode> saved = new LinkedHashMap<>();
        sections().forEach((k, v) -> {
            if (v != null) {
                saved.put(k, v);
            }
        });
        JsonNode advanced = saved.get("advanced");
        if (advanced instanceof ObjectNode o && !customJs) {
            ObjectNode copy = o.deepCopy();
            copy.remove("customJs");
            saved.put("advanced", copy);
        }
        return new PublicLanding(order(saved.get("order")), saved, catalog.offeredPlans(), pageLinks());
    }

    /**
     * Order section: {"items":[{"key":"hero","enabled":true},...]}. Displayed sections only; a section missing from
     * the list is shown after the listed ones, in the default order.
     */
    static List<String> order(JsonNode order) {
        List<String> out = new ArrayList<>();
        List<String> listed = new ArrayList<>();
        if (order != null) {
            for (JsonNode item : order.path("items")) {
                String k = item.path("key").asText();
                if (SECTIONS.contains(k) && !NOT_DISPLAYED.contains(k) && !listed.contains(k)) {
                    listed.add(k);
                    if (item.path("enabled").asBoolean(true)) {
                        out.add(k);
                    }
                }
            }
        }
        SECTIONS.stream().filter(k -> !NOT_DISPLAYED.contains(k) && !listed.contains(k)).forEach(out::add);
        return out;
    }

    // ------------------------------------------------------------------ custom pages

    @Transactional(readOnly = true)
    public Page<PageRow> searchPages(String keyword, Pageable pageable) {
        return pages.search(kw(keyword), pageable).map(p -> new PageRow(p.getId().toString(), p.getTitle(),
                p.getSlug(), preview(p.getContent()), p.isPublished(), p.getCreatedDate()));
    }

    @Transactional(readOnly = true)
    public PageDto page(Long id) {
        return PageDto.of(requirePage(id));
    }

    @Transactional
    public PageDto createPage(PageForm f) {
        CustomPage p = new CustomPage();
        p.setSlug(freeSlug(f.title(), null));
        apply(p, f);
        return PageDto.of(pages.save(com.terp.erp.common.Nulls.nonNull(p)));
    }

    /** Full replacement; a new title gives a new slug (the URL changes). */
    @Transactional
    public PageDto updatePage(Long id, PageForm f) {
        CustomPage p = requirePage(id);
        if (!p.getTitle().equals(f.title().trim())) {
            p.setSlug(freeSlug(f.title(), p.getSlug()));
        }
        apply(p, f);
        return PageDto.of(p);
    }

    @Transactional
    public void deletePage(Long id) {
        pages.delete(requirePage(id));
    }

    @Transactional(readOnly = true)
    public PageDto publicPage(String slug) {
        return pages.findBySlugAndPublishedTrue(slug).map(PageDto::of)
                .orElseThrow(() -> ApiException.notFound(ESiteError.SITE_004));
    }

    @Transactional(readOnly = true)
    public List<PageLink> pageLinks() {
        return pages.findByPublishedTrueOrderBySortOrderAscIdAsc().stream()
                .map(p -> new PageLink(p.getTitle(), p.getSlug(), p.getSortOrder())).toList();
    }

    private void apply(CustomPage p, PageForm f) {
        p.setTitle(f.title().trim());
        p.setContent(sanitize(f.content()));
        p.setMetaTitle(blank(f.metaTitle()));
        p.setMetaDescription(blank(f.metaDescription()));
        p.setSortOrder(f.sortOrder() == null ? 0 : f.sortOrder());
        p.setPublished(f.published());
    }

    /** "Chính sách bảo mật" → "chinh-sach-bao-mat"; -2, -3 … while taken. */
    String freeSlug(String title, String current) {
        String base = Normalizer.normalize(title.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        base = base.isEmpty() ? "page" : base.substring(0, Math.min(200, base.length()));
        String slug = base;
        for (int i = 2; !slug.equals(current) && pages.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    static String sanitize(String html) {
        return Jsoup.clean(html == null ? "" : html, "", SAFE, OUT).trim();
    }

    private static String preview(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text().trim();
        return text.length() <= 150 ? text : text.substring(0, 150).trim() + "…";
    }

    private CustomPage requirePage(Long id) {
        return pages.findById(com.terp.erp.common.Nulls.nonNull(id))
                .orElseThrow(() -> ApiException.notFound(ESiteError.SITE_004));
    }

    // ------------------------------------------------------------------ contact inquiries

    @Transactional
    public void submitInquiry(ContactForm f) {
        ContactInquiry c = new ContactInquiry();
        c.setName(f.name().trim());
        c.setEmail(f.email().trim());
        c.setPhone(blank(f.phone()));
        c.setSubject(f.subject().trim());
        c.setMessage(f.message().trim());
        c.setStatus(ContactInquiry.EStatus.NEW);
        inquiries.save(com.terp.erp.common.Nulls.nonNull(c));
    }

    @Transactional(readOnly = true)
    public Page<InquiryDto> searchInquiries(String keyword, ContactInquiry.EStatus status, Pageable pageable) {
        return inquiries.search(kw(keyword), status, pageable).map(InquiryDto::of);
    }

    @Transactional(readOnly = true)
    public InquiryDto inquiry(Long id) {
        return InquiryDto.of(requireInquiry(id));
    }

    @Transactional
    public InquiryDto changeStatus(Long id, ContactInquiry.EStatus status) {
        ContactInquiry c = requireInquiry(id);
        c.setStatus(status);
        return InquiryDto.of(c);
    }

    /**
     * Send Reply through the platform SMTP (SETTINGS_003 when none, SITE_008 with the server's reason when it fails).
     * TODO(assumption): a NEW inquiry becomes CONTACTED once replied to.
     */
    @Transactional
    public InquiryDto reply(Long id, ReplyForm f) {
        ContactInquiry c = requireInquiry(id);
        SettingsService.send(settings.mailer(), c.getEmail(), f.subject().trim(), f.message(), ESiteError.SITE_008);
        c.setLastRepliedAt(clock.instant());
        if (c.getStatus() == ContactInquiry.EStatus.NEW) {
            c.setStatus(ContactInquiry.EStatus.CONTACTED);
        }
        log.info("Reply sent to inquiry {}", c.getId());
        return InquiryDto.of(c);
    }

    @Transactional
    public void deleteInquiry(Long id) {
        inquiries.delete(requireInquiry(id));
    }

    private ContactInquiry requireInquiry(Long id) {
        return inquiries.findById(com.terp.erp.common.Nulls.nonNull(id))
                .orElseThrow(() -> ApiException.notFound(ESiteError.SITE_005));
    }

    // ------------------------------------------------------------------ newsletter

    /** Idempotent: subscribing twice keeps one row, and the answer never tells whether it was there. */
    @Transactional
    public void subscribe(String email) {
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (!subscribers.existsByEmail(e)) {
            NewsletterSubscriber n = new NewsletterSubscriber();
            n.setEmail(e);
            n.setUnsubscribeToken(com.terp.erp.iam.auth.AccountTokens.newToken());
            subscribers.save(com.terp.erp.common.Nulls.nonNull(n));
        }
    }

    @Transactional(readOnly = true)
    public Page<SubscriberDto> subscribers(String keyword, Pageable pageable) {
        return subscribers.search(kw(keyword), pageable)
                .map(n -> new SubscriberDto(n.getId().toString(), n.getEmail(), n.getCreatedDate(), unsubscribeUrl(n)));
    }

    /** The link for a newsletter email to this subscriber; empty when the address is not subscribed. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> unsubscribeUrl(String email) {
        return subscribers.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(this::unsubscribeUrl);
    }

    private String unsubscribeUrl(NewsletterSubscriber n) {
        return appUrl + "/newsletter/unsubscribe?token=" + n.getUnsubscribeToken();
    }

    /** One click from the email: the address is removed. Unknown or already used token: nothing, still OK. */
    @Transactional
    public void unsubscribeByToken(String token) {
        subscribers.findByUnsubscribeToken(token.trim()).ifPresent(n -> {
            subscribers.delete(n);
            log.info("Newsletter subscriber {} unsubscribed by link", n.getId());
        });
    }

    @Transactional
    public void unsubscribe(Long id) {
        subscribers.delete(subscribers.findById(com.terp.erp.common.Nulls.nonNull(id))
                .orElseThrow(() -> ApiException.notFound(ESiteError.SITE_006)));
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String kw(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
