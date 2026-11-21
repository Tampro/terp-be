package com.terp.erp.iam.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.saas.CatalogService;
import com.terp.erp.iam.settings.SettingsService;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LandingSectionRepository sections = mock(LandingSectionRepository.class);
    private final CustomPageRepository pages = mock(CustomPageRepository.class);
    private final SettingsService settings = mock(SettingsService.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

    private final NewsletterSubscriberRepository subscribers = mock(NewsletterSubscriberRepository.class);

    private SiteService service(boolean customJs) {
        return new SiteService(sections, pages, mock(ContactInquiryRepository.class),
                subscribers, settings, mock(CatalogService.class), mapper,
                Clock.systemUTC(), customJs);
    }

    @Test
    void orderFollowsTheOrderSectionAndAppendsTheRest() throws Exception {
        var order = mapper.readTree("{\"items\":[{\"key\":\"faq\",\"enabled\":true},{\"key\":\"hero\",\"enabled\":false},"
                + "{\"key\":\"general\",\"enabled\":true},{\"key\":\"nope\"}]}");
        List<String> out = SiteService.order(order);
        assertThat(out.get(0)).isEqualTo("faq");
        assertThat(out).doesNotContain("hero", "general", "order", "advanced", "nope").contains("contact", "footer");
        assertThat(SiteService.order(null)).startsWith("header", "hero");
    }

    @Test
    void customJsIsPublishedOnlyWhenTheOperatorAllowsIt() throws Exception {
        when(settings.system().landingPage()).thenReturn(true);
        LandingSection adv = new LandingSection();
        adv.setSectionKey("advanced");
        adv.setContent("{\"metaTitle\":\"ERP\",\"customCss\":\"a{}\",\"customJs\":\"alert(1)\"}");
        when(sections.findBySectionKey(anyString())).thenReturn(Optional.empty());
        when(sections.findBySectionKey("advanced")).thenReturn(Optional.of(adv));
        assertThat(service(false).publicLanding().sections().get("advanced").has("customJs")).isFalse();
        assertThat(service(false).publicLanding().sections().get("advanced").path("customCss").asText()).isEqualTo("a{}");
        assertThat(service(true).publicLanding().sections().get("advanced").path("customJs").asText()).isEqualTo("alert(1)");

        when(settings.system().landingPage()).thenReturn(false);
        assertThatThrownBy(() -> service(false).publicLanding()).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESiteError.SITE_003);
    }

    @Test
    void sectionsAreKnownJsonObjects() throws Exception {
        SiteService s = service(false);
        assertThatThrownBy(() -> s.saveSection("pricing", mapper.readTree("{}")))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESiteError.SITE_001);
        assertThatThrownBy(() -> s.saveSection("hero", mapper.readTree("[1]")))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESiteError.SITE_002);
        String big = "{\"x\":\"" + "a".repeat(70_000) + "\"}";
        assertThatThrownBy(() -> s.saveSection("hero", mapper.readTree(big)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESiteError.SITE_002);
    }

    @Test
    void slugsAndSanitizing() {
        when(pages.existsBySlug("chinh-sach-bao-mat")).thenReturn(true);
        SiteService s = service(false);
        assertThat(s.freeSlug("Chính sách bảo mật", null)).isEqualTo("chinh-sach-bao-mat-2");
        assertThat(s.freeSlug("Chính sách bảo mật", "chinh-sach-bao-mat")).isEqualTo("chinh-sach-bao-mat");
        assertThat(s.freeSlug("!!!", null)).isEqualTo("page");
        String clean = SiteService.sanitize("<h2 onclick=\"x()\">Hi</h2><script>alert(1)</script><a href=\"javascript:x\">a</a>");
        assertThat(clean).contains("<h2>Hi</h2>").doesNotContain("script", "onclick", "javascript");
    }

    @Test
    void subscribeGivesAnUnsubscribeTokenAndTheLinkRemovesTheAddress() {
        SiteService s = service(false);
        var saved = org.mockito.ArgumentCaptor.forClass(NewsletterSubscriber.class);
        s.subscribe(" News@Example.invalid ");
        org.mockito.Mockito.verify(subscribers).save(saved.capture());
        NewsletterSubscriber n = saved.getValue();
        assertThat(n.getEmail()).isEqualTo("news@example.invalid");
        assertThat(n.getUnsubscribeToken()).hasSizeGreaterThan(30);

        when(subscribers.findByUnsubscribeToken(n.getUnsubscribeToken())).thenReturn(Optional.of(n));
        s.unsubscribeByToken(n.getUnsubscribeToken());
        org.mockito.Mockito.verify(subscribers).delete(n);
        // an unknown or used link is not an error
        s.unsubscribeByToken("unknown");
    }

    @Test
    void aFailedReplyReportsItsOwnCodeNotTheTestEmailOne() {
        var unreachable = SettingsService.buildMailer("127.0.0.1", 1, null, null,
                com.terp.erp.iam.settings.SettingsDtos.EMailEncryption.NONE, "noreply@example.invalid", "ERP");
        assertThatThrownBy(() -> SettingsService.send(unreachable, "visitor@example.invalid", "Re: Demo", "Hi",
                ESiteError.SITE_008))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESiteError.SITE_008);
        assertThatThrownBy(() -> SettingsService.sendTest(unreachable, "a@example.invalid", "x"))
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(com.terp.erp.iam.settings.ESettingsError.SETTINGS_004);
    }
}
