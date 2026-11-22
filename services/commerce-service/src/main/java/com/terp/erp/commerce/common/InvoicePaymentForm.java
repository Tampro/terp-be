package com.terp.erp.commerce.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** A cleared accounting payment applied to a sales or purchase invoice. */
public record InvoicePaymentForm(
        @Schema(description = "Payment id of accounting-service; a repeat with the same id applies nothing twice",
                example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long paymentId,
        @Schema(description = "Amount taken off the invoice balance; at most the balance", example = "500000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount) {
}
