package com.playtomic.tests.wallet;

import com.playtomic.tests.common.configs.CommonConfig;
import com.playtomic.tests.common.exceptions.CommonExceptionHandler;
import com.playtomic.tests.common.http.ReactiveHttpClientConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import({CommonConfig.class, ReactiveHttpClientConfiguration.class, CommonExceptionHandler.class})
public class WalletApplication {

	public static void main(String[] args) {
		SpringApplication.run(WalletApplication.class, args);
	}
}
