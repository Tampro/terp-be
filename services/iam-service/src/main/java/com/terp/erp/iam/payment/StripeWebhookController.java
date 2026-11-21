package com.terp.erp.iam.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.saas.ESaasError;
import com.terp.erp.iam.saas.OrderService;
import com.terp.erp.iam.settings.SettingsService;

import java.time.Clock;
import java.util.Map;

/**
 * Stripe calls this after a payment (register it at Stripe with the event checkout.session.completed). Public: the
 * Stripe-Signature header is the authentication. Stripe retries on any non-2xx, so a valid event always answers 200,
 * also when it was already handled (idempotent) or is of no interest.
 */
@Slf4j
@Tag(name = "Payments (public)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/payments/stripe")
public class StripeWebhookController {
    private final SettingsService settings;
    private final OrderService orders;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Operation(summary = "Stripe webhook", description = "Verified with the webhook signing secret (5-minute "
            + "tolerance). checkout.session.completed with payment_status paid approves the order and activates the "
            + "plan; other events are acknowledged and ignored.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Stripe off, bad or old signature (SAAS_040)"))
    @PostMapping("/webhook")
    public ApiMessageDto<Map<String, Boolean>> webhook(@RequestBody String payload,
                                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        var stripe = settings.activeStripe();
        if (stripe == null || !StripeSignature.valid(payload, signature, stripe.webhookSecret(), clock.instant())) {
            log.warn("Stripe webhook refused: {}", stripe == null ? "Stripe is off" : "bad signature");
            throw ApiException.badRequest(ESaasError.SAAS_040);
        }
        JsonNode event;
        try {
            event = mapper.readTree(payload);
        } catch (Exception e) {
            throw ApiException.badRequest(ESaasError.SAAS_040);
        }
        String type = event.path("type").asText();
        JsonNode s = event.path("data").path("object");
        if ("checkout.session.completed".equals(type) && "paid".equals(s.path("payment_status").asText())) {
            orders.onStripePaid(s.path("id").asText(), s.path("payment_intent").asText(null),
                    s.path("amount_total").asLong(-1), s.path("currency").asText(""));
        } else {
            log.info("Stripe event {} ignored", type);
        }
        return ApiMessageDto.ok(Map.of("received", true));
    }
}
