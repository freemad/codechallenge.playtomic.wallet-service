package com.playtomic.tests.simulator.configs;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Selects the payment scenario exercised by the simulator. */
@Getter
@ConfigurationProperties("payment-simulator")
public class SimulatorProps {

    private final Scenario scenario;

    /** Binds the settings for the local payment scenario. */
    public SimulatorProps(@DefaultValue("SUCCESS") Scenario scenario) {
        this.scenario = scenario;
    }

    /** Selects which outcome a fixture returns for a valid request. */
    public enum Scenario {
        SUCCESS,
        REJECT,
        TIMEOUT
    }
}

