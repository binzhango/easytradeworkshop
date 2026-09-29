package com.dynatrace.easytrade.bitcoinpaymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public OpenAPI bitcoinPaymentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bitcoin Payment Service API")
                        .description("Bitcoin payment processing service for EasyTrade")
                        .version("1.0.0"));
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
