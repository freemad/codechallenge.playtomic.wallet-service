package com.playtomic.tests.simulator;

import com.playtomic.tests.common.configs.ObservabilityConfiguration;
import com.playtomic.tests.common.observability.StructuredEvents;
import com.playtomic.tests.simulator.observability.SimulationOutcome;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Starts the local payment-provider simulator. */
@SpringBootApplication
@EnableConfigurationProperties(SimulatorProps.class)
@Import(ObservabilityConfiguration.class)
public class PaymentSimulatorApplication {

    /** Starts the Spring Boot application with the supplied command-line arguments. */
    public static void main(String[] args) {
        SpringApplication.run(PaymentSimulatorApplication.class, args);
    }

    @Bean
    StructuredEvents simulatorEvents() {
        return SimulationOutcome.EVENTS;
    }
}