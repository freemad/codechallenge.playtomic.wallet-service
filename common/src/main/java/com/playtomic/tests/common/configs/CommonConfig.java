package com.playtomic.tests.common.configs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers shared application configuration. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommonProps.class)
public class CommonConfig {
}
