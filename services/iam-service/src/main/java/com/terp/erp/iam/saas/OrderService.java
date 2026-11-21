package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.saas.SaasDtos.BankTransferDto;
import com.terp.erp.iam.saas.SaasDtos.OrderDto;
import com.terp.erp.iam.saas.SaasDtos.OrderForm;
import com.terp.erp.iam.saas.SaasDtos.QuoteDto;
import com.terp.erp.iam.saas.SaasDtos.SubscriptionDto;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Plan orders paid by bank transfer: the company orders (price frozen, coupon applied), transfers with the order
 * number as content, the super admin approves (plan activated / extended) or rejects.
 */
@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class OrderService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlanOrderRepository orders;
    private final CompanyRepository companies;
    private final UserAccountRepository users;
    private final CatalogService catalog;
    private final PricingService pricing;
    private final SubscriptionService subscriptions;
    private final BankTransferProperties bank;
    private final com.terp.erp.iam.settings.SettingsService settings;
    private final LocalAuthorityResolver authorityCache;
    private final com.terp.erp.iam.referral.ReferralService referral;
    private final com.terp.erp.iam.email.EmailNotifier notifier;
    private final com.terp.erp.iam.me.AvatarMediaClient media;
    private final CompanyPlanTrialRepository trials;
    private final com.terp.erp.iam.payment.StripeClient stripeClient;
    @org.springframework.beans.factory.annotation.Value("${erp.app-url:http://localhost:5173}")
    private String appUrl;
    private final Clock clock;

    // ------------------------------------------------------------------ company side

    /** Start Trial of an offered plan for the caller's company; returns the new subscription. */
    @Transactional
    public SubscriptionDto startTrial(Long planId) {
        Long companyId = TenantGuard.resolveCompany(null, EIamError.IAM_003);
        Company c = company(companyId);
        Plan p = offered(planId);
        subscriptions.startTrial(c, p, trials.existsByCompanyIdAndPlanId(companyId, p.getId()));
        CompanyPlanTrial t = new CompanyPlanTrial();
        t.setCompanyId(companyId);
        t.setPlanId(p.getId());
        t.setEndsAt(c.getPlanExpiresAt());
        trials.save(t);
        authorityCache.evictAll();
        return mySubscription(companyId);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto mySubscription(Long requestedCompanyId) {
        Company c = company(TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003));
        var e = subscriptions.effective(c);
        return new SubscriptionDto(e.status(), SaasDtos.PlanDto.of(e.plan()), e.expiresAt(), e.fallback(),
                users.countByCompanyIdAndDeletedFalse(c.getId()));
    }

    @Transactional(readOnly = true)
    public QuoteDto quote(OrderForm form) {
        Long companyId = TenantGuard.resolveCompany(null, EIamError.IAM_003);
        var q = pricing.quote(offered(form.planId()), form.billingCycle(), form.couponCode(), companyId);
        return new QuoteDto(q.originalPrice(), q.discountAmount(), q.finalPrice(),
                q.coupon() == null ? null : q.coupon().getCode());
    }

    @Transactional
    public OrderDto create(OrderForm form) {
        Long companyId = TenantGuard.resolveCompany(null, EIamError.IAM_003);
        // One open order at a time keeps the approver from activating two periods for one transfer.
        if (orders.existsByCompanyIdAndStatus(companyId, EOrderStatus.PENDING)) {
            throw ApiException.badRequest(ESaasError.SAAS_010);
        }
        Plan plan = offered(form.planId());
        var q = pricing.quote(plan, form.billingCycle(), form.couponCode(), companyId);
        PlanOrder o = new PlanOrder();
        o.setOrderNumber(newOrderNumber());
        o.setCompanyId(companyId);
        o.setPlanId(plan.getId());
        o.setBillingCycle(form.billingCycle());
        o.setCouponId(q.coupon() == null ? null : q.coupon().getId());
        o.setCouponCode(q.coupon() == null ? null : q.coupon().getCode());
        o.setOriginalPrice(q.originalPrice());
        o.setDiscountAmount(q.discountAmount());
        o.setFinalPrice(q.finalPrice());
        EPaymentMethod method = form.paymentMethod() == null ? EPaymentMethod.BANK_TRANSFER : form.paymentMethod();
        if (method == EPaymentMethod.MANUAL) {
            throw ApiException.badRequest(ESaasError.SAAS_039);
        }
        if (method == EPaymentMethod.STRIPE && settings.activeStripe() == null) {
            throw ApiException.badRequest(ESaasError.SAAS_037);
        }
        o.setPaymentMethod(method);
        o.setPaymentReference(form.paymentReference());
        o.setStatus(EOrderStatus.PENDING);
        return dto(orders.save(o));
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> myOrders(Long requestedCompanyId, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return orders.findByCompanyId(companyId, pageable).map(this::dto);
    }

    @Transactional
    public OrderDto cancel(Long id) {
        PlanOrder o = require(id);
        decide(o, EOrderStatus.CANCELLED, null);
        return dto(o);
    }

    /** Receipt of a pending order (proof of the bank transfer); fileId null removes it. */
    @Transactional
    public OrderDto attachReceipt(Long id, Long fileId) {
        PlanOrder o = require(id);
        if (o.getStatus() != EOrderStatus.PENDING) {
            throw ApiException.badRequest(ESaasError.SAAS_009);
        }
        if (fileId != null) {
            media.requireCompanyFile(fileId, o.getCompanyId(), RECEIPT_TYPES, 5L * 1024 * 1024, ESaasError.SAAS_033);
        }
        o.setReceiptFileId(fileId);
        return dto(o);
    }

    static final java.util.Set<String> RECEIPT_TYPES = java.util.Set.of("application/pdf", "image/jpeg", "image/png");

    // ------------------------------------------------------------------ Stripe

    /** Pay a pending STRIPE order of the caller's company: a Checkout Session in the default currency. */
    @Transactional
    public SaasDtos.StripeCheckoutDto stripeCheckout(Long id) {
        PlanOrder o = require(id);
        if (o.getStatus() != EOrderStatus.PENDING || o.getPaymentMethod() != EPaymentMethod.STRIPE
                || o.getFinalPrice() == null || o.getFinalPrice().signum() <= 0) {
            throw ApiException.badRequest(ESaasError.SAAS_039);
        }
        var stripe = settings.activeStripe();
        String currency = settings.currency().defaultCurrencyCode();
        if (stripe == null || currency == null) {
            throw ApiException.badRequest(ESaasError.SAAS_037);
        }
        Plan plan = catalog.requirePlan(o.getPlanId());
        long amount = com.terp.erp.iam.payment.StripeClient.minorUnits(o.getFinalPrice(), currency);
        String back = appUrl + "/billing/orders/" + o.getId() + "?stripe=";
        var session = stripeClient.createCheckout(stripe.secretKey(), o.getId().toString(), o.getOrderNumber(),
                o.getCompanyId().toString(), plan.getName() + " (" + o.getBillingCycle().name().toLowerCase(java.util.Locale.ROOT) + ")",
                amount, currency, back + "success", back + "cancel");
        o.setStripeSessionId(session.id());
        return new SaasDtos.StripeCheckoutDto(session.id(), session.url());
    }

    /**
     * Webhook: the Checkout Session was paid. Approves the order once (a repeated event changes nothing) and only
     * when Stripe charged the order's amount in the default currency; anything else is logged, never approved.
     */
    @Transactional
    public void onStripePaid(String sessionId, String paymentIntent, long amountTotal, String currency) {
        PlanOrder o = orders.findByStripeSessionId(sessionId).orElse(null);
        if (o == null) {
            log.warn("Stripe session {} matches no order", sessionId);
            return;
        }
        if (o.getStatus() != EOrderStatus.PENDING) {
            log.info("Stripe event for order {} already handled ({})", o.getOrderNumber(), o.getStatus());
            return;
        }
        String expectedCurrency = settings.currency().defaultCurrencyCode();
        long expected = expectedCurrency == null ? -2
                : com.terp.erp.iam.payment.StripeClient.minorUnits(o.getFinalPrice(), expectedCurrency);
        if (expected != amountTotal || expectedCurrency == null || !expectedCurrency.equalsIgnoreCase(currency)) {
            log.error("Stripe paid {} {} for order {} but {} {} was expected: left PENDING", amountTotal, currency,
                    o.getOrderNumber(), expected, expectedCurrency);
            return;
        }
        o.setPaymentReference(paymentIntent);
        decide(o, EOrderStatus.APPROVED, "Paid with Stripe", "stripe");
        activate(o, "Paid with Stripe");
        log.info("Order {} paid with Stripe and approved", o.getOrderNumber());
    }

    // ------------------------------------------------------------------ super admin side

    /** Plan Orders page: keyword (order number, company name/code/email), order-date range, status tab. */
    @Transactional(readOnly = true)
    public Page<SaasDtos.AdminOrderDto> all(String keyword, java.time.LocalDate from, java.time.LocalDate to,
                                           EOrderStatus status, Pageable pageable) {
        return orders.searchWithNames(kw(keyword), start(from), end(to), status, pageable)
                .map(r -> new SaasDtos.AdminOrderDto(dto((PlanOrder) r[0]), (String) r[1], (String) r[2], (String) r[3]));
    }

    @Transactional(readOnly = true)
    public SaasDtos.StatusCountsDto counts(String keyword, java.time.LocalDate from, java.time.LocalDate to) {
        return SaasDtos.StatusCountsDto.of(orders.countByStatus(kw(keyword), start(from), end(to)));
    }

    /** View: one order with its company and plan names (super admin). */
    @Transactional(readOnly = true)
    public SaasDtos.AdminOrderDto adminGet(Long id) {
        PlanOrder o = orders.findById(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_008));
        Company c = companies.findById(o.getCompanyId()).orElse(null);
        Plan p = catalog.findAny(o.getPlanId());
        return new SaasDtos.AdminOrderDto(dto(o), c == null ? null : c.getName(), c == null ? null : c.getEmail(),
                p == null ? null : p.getName());
    }

    static String kw(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    static Instant start(java.time.LocalDate from) {
        return from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static Instant end(java.time.LocalDate to) {
        return to == null ? Instant.parse("9999-01-01T00:00:00Z") : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Activates or extends the ordered plan from the price frozen in the order. */
    @Transactional
    public OrderDto approve(Long id, String notes) {
        PlanOrder o = require(id);
        decide(o, EOrderStatus.APPROVED, notes, CurrentUser.get().keycloakId());
        return activate(o, notes);
    }

    /** After approval (by the super admin or by Stripe): the plan runs, commission, email. */
    private OrderDto activate(PlanOrder o, String notes) {
        Company c = company(o.getCompanyId());
        Plan plan = catalog.requirePlan(o.getPlanId());
        subscriptions.activate(c, plan, o.getBillingCycle());
        referral.onOrderApproved(o, c);
        authorityCache.evictAll();
        notifier.orderDecided(true, c, o.getOrderNumber(), plan.getName(), o.getBillingCycle().name(),
                o.getFinalPrice(), c.getPlanExpiresAt(), notes);
        return dto(o);
    }

    @Transactional
    public OrderDto reject(Long id, String notes) {
        PlanOrder o = require(id);
        decide(o, EOrderStatus.REJECTED, notes);
        Plan plan = catalog.findAny(o.getPlanId());
        notifier.orderDecided(false, company(o.getCompanyId()), o.getOrderNumber(), plan == null ? "" : plan.getName(),
                o.getBillingCycle().name(), o.getFinalPrice(), null, notes);
        return dto(o);
    }

    /**
     * "Upgrade Plan" from the companies list: the super admin moves the company to a plan for one billing cycle.
     * Recorded as an APPROVED order with payment method MANUAL at the plan's list price, so the order history shows
     * every plan change. No coupon; a pending order of the company is left as it is.
     */
    @Transactional
    public OrderDto upgradeByAdmin(Long companyId, Long planId, EBillingCycle cycle) {
        return activateManually(companyId, planId, cycle, "Plan set by super admin");
    }

    /** Shared by "Upgrade Plan" and approved plan requests: one cycle, an APPROVED MANUAL order for history. */
    @Transactional
    public OrderDto activateManually(Long companyId, Long planId, EBillingCycle cycle, String note) {
        Company c = company(companyId);
        Plan plan = catalog.requirePlan(planId);
        var q = pricing.quote(plan, cycle, null, companyId);
        PlanOrder o = new PlanOrder();
        o.setOrderNumber(newOrderNumber());
        o.setCompanyId(companyId);
        o.setPlanId(plan.getId());
        o.setBillingCycle(cycle);
        o.setOriginalPrice(q.originalPrice());
        o.setDiscountAmount(q.discountAmount());
        o.setFinalPrice(q.finalPrice());
        o.setPaymentMethod(EPaymentMethod.MANUAL);
        o.setStatus(EOrderStatus.APPROVED);
        o.setProcessedAt(clock.instant());
        o.setProcessedBy(CurrentUser.get().keycloakId());
        o.setNotes(note);
        subscriptions.activate(c, plan, cycle);
        authorityCache.evictAll();
        return dto(orders.save(o));
    }

    /** A deleted company's open orders are cancelled so they cannot be approved later. */
    @Transactional
    public void cancelPendingOf(Long companyId) {
        orders.findByCompanyId(companyId, Pageable.unpaged()).stream()
                .filter(o -> o.getStatus() == EOrderStatus.PENDING)
                .forEach(o -> {
                    o.setStatus(EOrderStatus.CANCELLED);
                    o.setProcessedAt(clock.instant());
                    o.setProcessedBy(CurrentUser.get().keycloakId());
                    o.setNotes("Company deleted");
                });
    }

    @Transactional
    public SubscriptionDto assign(Long companyId, Long planId, Instant expiresAt) {
        Company c = company(companyId);
        subscriptions.assign(c, catalog.requirePlan(planId), expiresAt);
        authorityCache.evictAll();
        var e = subscriptions.effective(c);
        return new SubscriptionDto(e.status(), SaasDtos.PlanDto.of(e.plan()), e.expiresAt(), e.fallback(),
                users.countByCompanyIdAndDeletedFalse(c.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private void decide(PlanOrder o, EOrderStatus to, String notes) {
        decide(o, to, notes, CurrentUser.get().keycloakId());
    }

    private void decide(PlanOrder o, EOrderStatus to, String notes, String processedBy) {
        if (o.getStatus() != EOrderStatus.PENDING) {
            throw ApiException.badRequest(ESaasError.SAAS_009);
        }
        o.setStatus(to);
        o.setProcessedAt(clock.instant());
        o.setProcessedBy(processedBy);
        if (notes != null) {
            o.setNotes(notes);
        }
    }

    private PlanOrder require(Long id) {
        PlanOrder o = orders.findById(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_008));
        TenantGuard.assertSameCompany(o.getCompanyId(), ESaasError.SAAS_008);
        return o;
    }

    private Plan offered(Long planId) {
        Plan p = catalog.requirePlan(planId);
        if (!p.isEnabled()) {
            throw ApiException.badRequest(ESaasError.SAAS_014);
        }
        return p;
    }

    private Company company(Long id) {
        return companies.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EIamError.IAM_001));
    }

    private String newOrderNumber() {
        // ORD + UTC date + 6 random digits; unique key in the table catches the rare collision.
        return "ORD" + DAY.format(clock.instant()) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private OrderDto dto(PlanOrder o) {
        BankTransferDto transfer = null;
        if (o.getStatus() == EOrderStatus.PENDING) {
            var s = settings.bankTransfer();
            boolean fromSettings = s.accountNumber() != null && !s.accountNumber().isBlank();
            transfer = fromSettings
                    ? new BankTransferDto(s.bankName(), s.accountNumber(), s.accountName(), o.getOrderNumber())
                    : new BankTransferDto(bank.bankName(), bank.accountNumber(), bank.accountName(), o.getOrderNumber());
        }
        return OrderDto.of(o, transfer);
    }
}
