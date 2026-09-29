package com.dynatrace.easytrade.bitcoinpaymentservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/version")
@CrossOrigin
public class VersionController {

    @Value("${spring.application.name:bitcoin-payment-service}")
    private String applicationName;

    @Value("${MANIFEST_VERSION:dev}")
    private String version;

    @GetMapping(produces = { "text/plain", "application/json" })
    public String getVersion() {
        return String.format("%s version: %s", applicationName, version);
    }
}
