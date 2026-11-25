package com.playtomic.tests.simulator.configs;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties("payment-simulator")
public class SimulatorProps {

    private final Scenario scenario;

    public SimulatorProps(@DefaultValue("SUCCESS") Scenario scenario) {
        this.scenario = scenario;
    }

    public enum Scenario {
        SUCCESS,
        REJECT,
        TIMEOUT
    }
}

