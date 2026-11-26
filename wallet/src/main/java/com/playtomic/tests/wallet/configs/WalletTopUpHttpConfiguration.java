package com.playtomic.tests.wallet.configs;

import org.springframework.boot.webflux.autoconfigure.WebFluxRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/** Keeps unauthenticated local-demo handlers out of the routing table by default. */
@Configuration(proxyBeanMethods = false)
public class WalletTopUpHttpConfiguration {
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalTopUpRoute {
    }

    @Bean
    WebFluxRegistrations walletTopUpHttpRegistrations(WalletTopUpHttpProps props, Environment environment) {
        boolean enabled = props.isEnabled();
        if (enabled) {
            WalletTopUpHttpProps.requireApprovedExposure(environment);
        }
        return new WebFluxRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new RequestMappingHandlerMapping() {
                    @Override
                    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
                        if (!enabled && method.isAnnotationPresent(LocalTopUpRoute.class)) {
                            return null;
                        }
                        return super.getMappingForMethod(method, handlerType);
                    }
                };
            }
        };
    }
}
