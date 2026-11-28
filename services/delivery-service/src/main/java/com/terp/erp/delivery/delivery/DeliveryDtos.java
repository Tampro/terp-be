package com.terp.erp.delivery.delivery;

import com.terp.erp.delivery.client.OrderClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class DeliveryDtos {
    private DeliveryDtos() {
    }

    public record AssignForm(
            @Schema(description = "IAM user id of the rider (an active user of the company holding DELIVERY_RIDE)",
                    example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long riderUserId) {
    }

    public record CancelForm(
            @Schema(description = "Why the delivery is cancelled", example = "Customer unreachable")
            @Size(max = 200) String reason) {
    }

    public record DeliveryDto(String id, String orderId, String orderNo, String storeId, EDeliveryStatus status,
                              String riderSub, String riderName, Instant createdDate, Instant assignedAt,
                              Instant pickedUpAt, Instant deliveredAt, Instant cancelledAt, String cancelReason) {
        static DeliveryDto of(Delivery d) {
            return new DeliveryDto(String.valueOf(d.getId()), String.valueOf(d.getOrderId()), d.getOrderNo(),
                    String.valueOf(d.getStoreId()), d.getStatus(), d.getRiderSub(), d.getRiderName(), d.getCreatedDate(),
                    d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(), d.getCancelledAt(), d.getCancelReason());
        }
    }

    public record LineDto(String name, int quantity) {
    }

    public record DestinationDto(String storeName, String recipientName,
                                 @Schema(description = "Masked for Back Office (010-****-5678), plain for the rider")
                                 String phone, String postcode, String address1, String address2, BigDecimal lat,
                                 BigDecimal lon, String deliveryNote, String note, BigDecimal total, List<LineDto> lines) {
        static DestinationDto of(OrderClient.OrderView o, boolean masked) {
            return new DestinationDto(o.storeName(), o.recipientName(), masked ? mask(o.phone()) : o.phone(), o.postcode(),
                    o.address1(), o.address2(), o.lat(), o.lon(), o.deliveryNote(), o.note(), o.total(),
                    o.lines().stream().map(l -> new LineDto(l.name(), l.quantity())).toList());
        }
    }

    public record DeliveryDetailDto(DeliveryDto delivery, DestinationDto destination) {
    }

    /** 01012345678 → 010-****-5678. */
    static String mask(String digits) {
        if (digits == null || digits.length() < 7) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
