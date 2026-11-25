package com.playtomic.tests.simulator;

import com.playtomic.tests.simulator.configs.SimulatorProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProps.class)
public class PaymentSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentSimulatorApplication.class, args);
    }
}

