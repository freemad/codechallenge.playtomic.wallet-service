package com.playtomic.tests.common.observability;

import lombok.RequiredArgsConstructor;
import org.slf4j.event.Level;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/** Record the selected HTTP status, including responses prepared by exception handlers. */
@RequiredArgsConstructor
public final class HttpResponseLoggingFilter implements WebFilter {
    private final StructuredEvents events;

    /** Observes the HTTP exchange while delegating request processing to the remaining filter chain. */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> Mono.fromRunnable(() -> {
            var status = exchange.getResponse().getStatusCode();
            int code = status == null ? 200 : status.value();
            Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            (code >= 500 ? events.fields(Level.ERROR, "http.server_error") : events.fields(Level.DEBUG, "http.response"))
                    .addKeyValue("handler", handler instanceof HandlerMethod method ? method.getMethod().getName() : "other")
                    .addKeyValue("requestId", exchange.getRequest().getId())
                    .addKeyValue("httpStatus", code)
                    .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).log();
        }));
        return chain.filter(exchange);
    }
}