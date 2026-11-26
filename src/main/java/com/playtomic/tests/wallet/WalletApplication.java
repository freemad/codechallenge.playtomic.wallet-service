package com.playtomic.tests.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the wallet service. */
@SpringBootApplication
public class WalletApplication {

	/** Starts the Spring Boot application with the supplied command-line arguments. */
	public static void main(String[] args) {
		SpringApplication.run(WalletApplication.class, args);
	}
}
