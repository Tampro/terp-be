package com.terp.erp.order.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and answer shapes of orders (customer and Back Office). */
public final class OrderDtos {
    private OrderDtos() {
    }

    public record PlaceForm(
            @Schema(description = "One of my delivery addresses (member-service)", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long addressId,
            @Schema(description = "May the store substitute a missing item", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ESubstitution substitution,
            @Schema(example = "Ring twice") @Size(max = 200) String note) {
    }

    public enum EPayOutcome {
        APPROVED, DECLINED
    }

    public record PayForm(@Schema(description = "Sandbox only: the outcome the fake PG returns",
            requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EPayOutcome outcome) {
    }

    public record LineDto(String itemId, String name, String sku, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    /** phone: plain for the customer who owns the order, masked (010-****-5678) for Back Office staff. */
    public record OrderDto(String id, String orderNo, EOrderStatus status, String failureReason, String storeId,
                           String storeName, ESubstitution substitution, List<LineDto> lines, BigDecimal subtotal,
                           BigDecimal deliveryFee, BigDecimal bagFee, BigDecimal total, String recipientName, String phone,
                           String postcode, String address1, String address2, BigDecimal lat, BigDecimal lon,
                           String deliveryNote, String note, Instant createdDate, Instant paidAt, Instant cancelledAt,
                           @Schema(description = "Back Office only: journal of a paid order in accounting (PENDING until "
                                   + "accounting answers, POSTED, FAILED); null for customers and unpaid orders")
                           EJournalStatus journalStatus,
                           @Schema(description = "Back Office only: accounting journal entry number once POSTED",
                                   example = "JE-2026-000042") String journalEntryNo,
                           @Schema(description = "Back Office only: accounting error code when FAILED (e.g. ACC_070)")
                           String journalError,
                           @Schema(description = "Latest delivery status (READY, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED); "
                                   + "null before payment", example = "PICKED_UP") String deliveryStatus,
                           @Schema(description = "When the delivery reached that status (UTC)") Instant deliveryStatusAt,
                           @Schema(description = "Email for order and delivery messages, from the sign-in account at "
                                   + "checkout; masked for Back Office (h***@example.com)") String email) {
    }
}
