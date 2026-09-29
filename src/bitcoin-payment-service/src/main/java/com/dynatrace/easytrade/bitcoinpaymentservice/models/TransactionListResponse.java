package com.dynatrace.easytrade.bitcoinpaymentservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionListResponse {
    private List<BitcoinTransaction> transactions;
    private int total;
    private int offset;
    private int limit;
}
