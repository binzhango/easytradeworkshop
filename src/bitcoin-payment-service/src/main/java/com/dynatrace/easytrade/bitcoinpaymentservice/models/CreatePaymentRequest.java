package com.dynatrace.easytrade.bitcoinpaymentservice.models;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    @NotNull(message = "Account ID is required")
    private Integer accountId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency; // Should be "BTC"

    private String purpose; // e.g., "stock_purchase"

    private Map<String, Object> metadata; // Additional context (tradeId, instrumentId, etc.)
}
