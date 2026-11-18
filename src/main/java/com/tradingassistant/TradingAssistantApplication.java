package com.tradingassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TradingAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingAssistantApplication.class, args);
    }
}
