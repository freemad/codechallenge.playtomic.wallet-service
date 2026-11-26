package com.playtomic.tests.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Produces time-based UUIDv7 identifiers without coordinating callers. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates an RFC 9562 UUIDv7 with a millisecond timestamp and secure randomness.
     * Ordering within the same millisecond is not strictly monotonic.
     */
    public static UUID generate() {
        long epochMillis = Instant.now().toEpochMilli();
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        long mostSigBits = (epochMillis << 16) & 0xFFFFFFFFFFFF0000L;
        mostSigBits |= 0x0000000000007000L; // Version 7
        mostSigBits |= ((randomBytes[0] & 0x0FL) << 8) | (randomBytes[1] & 0xFFL);

        long leastSigBits = 0L;
        for (int i = 2; i < 10; i++) {
            leastSigBits |= ((long) (randomBytes[i] & 0xFF) << (8 * (9 - i)));
        }
        leastSigBits = (leastSigBits & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(mostSigBits, leastSigBits);
    }
}