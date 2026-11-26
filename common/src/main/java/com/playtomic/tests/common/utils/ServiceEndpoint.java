package com.playtomic.tests.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;

/** Validates service base URIs before they are used to construct outbound requests. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceEndpoint {
    /** Checks for an HTTPS service origin or, when allowed, a loopback HTTP origin without embedded credentials. */
    public static boolean isSafe(URI uri, boolean allowLoopbackHttp) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            return false;
        }
        return "https".equalsIgnoreCase(uri.getScheme())
                || allowLoopbackHttp && "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                || "[::1]".equals(uri.getHost()) || "::1".equals(uri.getHost()));
    }
}
