package com.dynatrace.easytrade.bitcoinpaymentservice.models;

public enum TransactionType {
    PAYMENT("PAYMENT"),
    REFUND("REFUND"),
    WITHDRAWAL("WITHDRAWAL"),
    DEPOSIT("DEPOSIT");

    private final String value;

    TransactionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
