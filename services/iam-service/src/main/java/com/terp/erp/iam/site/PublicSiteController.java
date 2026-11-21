package com.terp.erp.iam.site;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.auth.AuthRateLimiter;
import com.terp.erp.iam.login.LoginHistoryService;
import com.terp.erp.iam.settings.RecaptchaVerifier;
import com.terp.erp.iam.site.SiteService.ContactForm;
import com.terp.erp.iam.site.SiteService.NewsletterForm;
import com.terp.erp.iam.site.SiteService.PageDto;
import com.terp.erp.iam.site.SiteService.PageLink;
import com.terp.erp.iam.site.SiteService.PublicLanding;

import java.util.List;

/** The public website's API: no token. The two forms are rate limited per IP and checked with reCAPTCHA. */
@Tag(name = "Website (public)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/site")
public class PublicSiteController {
    private final SiteService service;
    private final AuthRateLimiter limiter;
    private final RecaptchaVerifier recaptcha;
    private final CookieConsentService consents;

    @Operation(summary = "The landing page", description = "Displayed sections in order, their content, the plans "
            + "offered and the published custom pages.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Landing page switched off in System Settings (SITE_003)"))
    @GetMapping("/landing")
    public ApiMessageDto<PublicLanding> landing() {
        return ApiMessageDto.ok(service.publicLanding());
    }

    @Operation(summary = "Published custom pages (navigation)")
    @GetMapping("/pages")
    public ApiMessageDto<List<PageLink>> pages() {
        return ApiMessageDto.ok(service.pageLinks());
    }

    @Operation(summary = "A published custom page")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such published page (SITE_004)"))
    @GetMapping("/pages/{slug}")
    public ApiMessageDto<PageDto> page(@PathVariable String slug) {
        return ApiMessageDto.ok(service.publicPage(slug));
    }

    @Operation(summary = "Contact form")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation, reCAPTCHA (AUTH_010)"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/contact")
    public ApiMessageDto<Void> contact(@Valid @RequestBody ContactForm form, HttpServletRequest request) {
        limiter.checkIp("contact", ip(request));
        recaptcha.check(form.recaptchaToken());
        service.submitInquiry(form);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Subscribe to the newsletter", description = "Always 200, also when already subscribed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation, reCAPTCHA (AUTH_010)"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/newsletter")
    public ApiMessageDto<Void> newsletter(@Valid @RequestBody NewsletterForm form, HttpServletRequest request) {
        limiter.checkIp("newsletter", ip(request));
        recaptcha.check(form.recaptchaToken());
        service.subscribe(form.email());
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Unsubscribe from the newsletter (link in every newsletter email)",
            description = "Always 200: an unknown or already used link changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/newsletter/unsubscribe")
    public ApiMessageDto<Void> unsubscribe(@Valid @RequestBody SiteService.UnsubscribeForm form,
                                           HttpServletRequest request) {
        limiter.checkIp("unsubscribe", ip(request));
        service.unsubscribeByToken(form.token());
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Record a cookie consent choice", description = "Kept only while Cookie Settings -> Enable "
            + "Logging is on (recorded = false otherwise). The IP is stored shortened. No token needed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/cookie-consent")
    public ApiMessageDto<java.util.Map<String, Boolean>> cookieConsent(
            @Valid @RequestBody CookieConsentService.ConsentForm form, HttpServletRequest request) {
        limiter.checkIp("cookie", ip(request));
        return ApiMessageDto.ok(java.util.Map.of("recorded", consents.record(form, ip(request))));
    }

    private static String ip(HttpServletRequest request) {
        return LoginHistoryService.clientIp(request.getHeader("X-Client-IP"), request.getRemoteAddr());
    }
}
