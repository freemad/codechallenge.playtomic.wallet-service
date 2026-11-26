package com.playtomic.tests.wallet;

import com.playtomic.tests.common.configs.CommonConfig;
import com.playtomic.tests.common.configs.ObservabilityConfiguration;
import com.playtomic.tests.common.exceptions.CommonExceptionHandler;
import com.playtomic.tests.common.configs.ReactiveHttpClientConfiguration;
import com.playtomic.tests.common.observability.StructuredEvents;
import com.playtomic.tests.wallet.observability.WalletLogEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Spring Boot entry point for the wallet service. */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({CommonConfig.class, ReactiveHttpClientConfiguration.class, CommonExceptionHandler.class, ObservabilityConfiguration.class})
public class WalletApplication {
    /** Starts the Spring Boot application with the supplied command-line arguments. */
    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }

    @Bean
    StructuredEvents walletEvents() {
        return WalletLogEvent.EVENTS;
    }
}