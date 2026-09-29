package com.dynatrace.easytrade.bitcoinpaymentservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private Integer accountId;
    private String walletAddress;
    private BigDecimal balance;
    private OffsetDateTime createdAt;
}
