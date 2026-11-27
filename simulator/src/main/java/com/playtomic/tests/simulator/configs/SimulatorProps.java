package com.playtomic.tests.simulator.configs;

import com.playtomic.tests.simulator.enums.SimulationScenario;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Selects the payment scenario exercised by the simulator. */
@Getter
@ConfigurationProperties("payment-simulator")
public class SimulatorProps {

    private final SimulationScenario scenario;

    /** Binds the settings for the local payment scenario. */
    public SimulatorProps(@DefaultValue("SUCCESS") SimulationScenario scenario) {
        this.scenario = scenario;
    }
}
