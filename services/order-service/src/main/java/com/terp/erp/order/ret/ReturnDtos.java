package com.terp.erp.order.ret;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {
    private ReturnDtos() {
    }

    public record LineForm(@Schema(description = "Item of the order", requiredMode = Schema.RequiredMode.REQUIRED)
                           @NotNull Long itemId,
                           @Schema(description = "Units to return", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
                           @Min(1) @Max(1000) int quantity) {
    }

    public record RequestForm(@Schema(description = "CHANGE_OF_MIND pays the pick-up fee; DEFECT / WRONG_ITEM are free",
            requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EReturnReason reason,
                              @Schema(description = "What happened", example = "Box arrived crushed") @Size(max = 500) String note,
                              @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 100)
                              List<@Valid @NotNull LineForm> lines) {
    }

    public record ApproveForm(@Schema(description = "Optional: switch the reason group (the fee is recomputed)")
                              EReturnReason reason) {
    }

    public record RejectForm(@Schema(description = "Why the return is refused (shown to the customer)",
            example = "Opened fresh goods", requiredMode = Schema.RequiredMode.REQUIRED)
                             @NotBlank @Size(max = 200) String note) {
    }

    public record PolicyForm(@Schema(description = "Pick-up fee for a change of mind, whole won incl. VAT", example = "3000",
            requiredMode = Schema.RequiredMode.REQUIRED)
                             @NotNull @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 0)
                             BigDecimal pickupFee) {
    }

    public record PolicyDto(String companyId, BigDecimal pickupFee, int windowDays,
                            @Schema(description = "false: the default (3,000 won), never saved") boolean saved) {
    }

    public record LineDto(String itemId, String name, int quantity, BigDecimal unitPrice,
                          @Schema(description = "What the customer paid for these units (coupon and points spread)") BigDecimal share) {
    }

    public record ReturnDto(String id, String returnNo, String orderId, String orderNo, String storeId, EReturnStatus status,
                            EReturnReason reason, String note, String rejectNote,
                            @Schema(description = "Goods at line prices") BigDecimal goodsAmount,
                            @Schema(description = "Goods after coupon and points, pro rata") BigDecimal refundShare,
                            BigDecimal pickupFee,
                            @Schema(description = "refundShare − pickupFee; paid back at COMPLETED (sandbox)") BigDecimal refundAmount,
                            @Schema(description = "Points spent that come back at COMPLETED") int pointsBack,
                            @Schema(description = "Points earned that are taken back at COMPLETED (at most the balance)") int earnedBack,
                            Instant createdDate, Instant approvedAt, Instant rejectedAt, Instant completedAt,
                            List<LineDto> lines) {
    }
}
