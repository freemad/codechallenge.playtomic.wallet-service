package com.playtomic.tests.common.configs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Data
@Validated
@ConfigurationProperties(prefix = "application.common", ignoreUnknownFields = false)
public class CommonProps {

    private final Authorization authorization = new Authorization();
    private final DevMode devMode = new DevMode();
    private final AuthInfo authInfo = new AuthInfo();
    private final BasicAuthInfo basicAuthInfo = new BasicAuthInfo();
    private final UpstreamInfo upstreamInfo = new UpstreamInfo();
    private final Infinispan infinispan = new Infinispan();
    private final Database database = new Database();
    @Valid
    private final Http http = new Http();
    @Valid
    private final Pagination pagination = new Pagination();
    @Valid
    private final Observability observability = new Observability();
    @Valid
    private final Resilience resilience = new Resilience();

    @Data
    public static class Http {
        @Min(1)
        private int connectTimeoutMs = 3000;
        @Min(1)
        private int responseTimeoutMs = 5000;
        @Min(1)
        private int maxInMemorySizeBytes = 256 * 1024;
    }

    @Data
    public static class Pagination {
        @Min(1)
        private int defaultPageSize = 50;
        @Min(1)
        @Max(1000)
        private int maxPageSize = 200;

        @AssertTrue(message = "default-page-size must not exceed max-page-size")
        public boolean isDefaultPageSizeWithinMaximum() {
            return defaultPageSize <= maxPageSize;
        }
    }

    /**
     * Issuer trust, resource-role access and endorsed delegation settings for reactive HTTP security.
     */
    @Data
    @EqualsAndHashCode(doNotUseGetters = true)
    public static class Authorization {
        private String resourceName;
        private Set<String> accessRoles = Set.of();
        private String issuerUri;
        private String audience;
        private Set<String> allowedDelegationClients = Set.of();
        private List<EndpointAccess> endpoints = List.of();
        private String allowedOrigins;
        private String allowedMethods;
        private String allowedHeaders;

        /**
         * Returns the explicitly permitted browser origins.
         */
        public List<String> getAllowedOrigins() {
            return getList(allowedOrigins);
        }

        /**
         * Returns the explicitly permitted cross-origin HTTP methods.
         */
        public List<String> getAllowedMethods() {
            return getList(allowedMethods);
        }

        /**
         * Returns the explicitly permitted cross-origin request headers.
         */
        public List<String> getAllowedHeaders() {
            return getList(allowedHeaders);
        }

        /**
         * Parses optional CORS settings; an absent value permits no cross-origin access.
         */
        private List<String> getList(String commaDelimitedString) {
            return commaDelimitedString == null ? List.of()
                    : Arrays.stream(commaDelimitedString.split(",")).map(String::trim)
                    .filter(value -> !value.isEmpty()).toList();
        }

        /**
         * Ordered method/path permissions; configuring rules denies every unmatched request.
         */
        @Data
        public static class EndpointAccess {
            private HttpMethod method;
            private String path;
            private Set<String> roles = Set.of();
        }
    }

    @Data
    public static class DevMode {
        private Boolean expWrapperEnabled;
        private Boolean debugMode;
    }

    @Data
    public static class AuthInfo {
        private String host;
        private String uri;
        private String clientId;
        private String clientSecret;
        private String grantPassword;
        private String grantRefresh;
        private String username;
        private String password;
    }

    @Data
    public static class BasicAuthInfo {
        private String username;
        private String password;
        private String token;

    }

    @Data
    public static class UpstreamInfo {
        private String host;
        private List<String> uris;
        private String encoding;

    }

    @Data
    public static class Infinispan {
        private String clustered;

    }

    @Data
    @Accessors(chain = true)
    public static class Observability {
        @Pattern(regexp = "(?:[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*)?")
        private String metricNamePrefix = "";
    }

    @Data
    public static class Resilience {
        @Valid
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();
        @Valid
        private final Bulkhead bulkhead = new Bulkhead();

        @Data
        @Accessors(chain = true)
        public static class CircuitBreaker {
            @Min(1)
            @Max(100)
            private int failureRateThreshold = 50;
            @Min(1)
            private int slidingWindowSize = 100;
            @Min(1)
            private int minimumNumberOfCalls = 100;
            @Min(1)
            private long openStateWaitMs = 60_000;

            @AssertTrue(message = "minimum-number-of-calls must not exceed sliding-window-size")
            public boolean isMinimumNumberOfCallsWithinWindow() {
                return minimumNumberOfCalls <= slidingWindowSize;
            }
        }

        @Data
        @Accessors(chain = true)
        public static class Bulkhead {
            @Min(1)
            private int maxConcurrentCalls = 25;
        }
    }

    @Data
    @lombok.experimental.Accessors(chain = true)
    public static class ClientRecovery {
        private boolean enabled;
        @jakarta.validation.constraints.Min(1)
        @jakarta.validation.constraints.Max(300)
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Database {
        private String url;
        private String username;
        private String password;
    }
}
