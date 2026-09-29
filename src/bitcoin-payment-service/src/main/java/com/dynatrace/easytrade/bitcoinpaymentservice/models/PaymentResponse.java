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
public class PaymentResponse {
    private String paymentId;
    private String walletAddress;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private OffsetDateTime expiresAt;
    private String qrCode; // Base64 encoded QR code image
    private Integer confirmations;
    private String transactionHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime confirmedAt;
}
