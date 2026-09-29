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
public class BitcoinTransaction {
    private String id;
    private Integer accountId;
    private String walletAddress;
    private String transactionHash;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private Integer confirmations;
    private String purpose;
    private String metadata;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime expiresAt;
}
