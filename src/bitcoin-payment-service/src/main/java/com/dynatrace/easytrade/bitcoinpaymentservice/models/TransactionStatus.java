package com.dynatrace.easytrade.bitcoinpaymentservice.models;

public enum TransactionStatus {
    PENDING("PENDING"),
    CONFIRMING("CONFIRMING"),
    CONFIRMED("CONFIRMED"),
    FAILED("FAILED"),
    EXPIRED("EXPIRED");

    private final String value;

    TransactionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
