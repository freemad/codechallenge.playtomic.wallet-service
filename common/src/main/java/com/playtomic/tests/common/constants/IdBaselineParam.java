package com.playtomic.tests.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Wallet platform baseline identifiers.
 * Stores hard-coded references for system fallbacks, cross-cutting telemetry pipelines,
 * and data-dictionary keys without runtime reflection or database overhead.
 * Reserved hexadecimal namespaces: 000c-c000 for controls, 000c-7000 for typologies,
 * and d015-a000 for bootstrap rules. Keep these IDs aligned with the database baseline.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdBaselineParam {

    public static final UUID SYSTEM_DEFAULT_ALL_ZERO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    // =========================================================================
    // 🏢 CORPORATE TENANT WORKSPACE IDENTIFIERS
    // =========================================================================
    public static final UUID SYSTEM_GLOBAL_FALLBACK_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    // =========================================================================
    // 🛡️ REGULATORY CONTROL CONSTANTS (grc_compliance)
    // =========================================================================
    public static final UUID CONTROL_SYSTEM_FALLBACK_ID =
            UUID.fromString("00000000-0000-000c-c000-000000000001");

    public static final UUID CONTROL_AML_SANCTION_ID =
            UUID.fromString("00000000-0000-000c-c000-000000000100");

    public static final UUID CONTROL_SLA_TIMEOUT_ID =
            UUID.fromString("00000000-0000-000c-c000-000000000200");

    // =========================================================================
    // 🗂️ RISK VIOLATION TYPOLOGY CONSTANTS (grc_compliance)
    // =========================================================================
    public static final UUID TYPOLOGY_PIPELINE_MALFUNCTION_ID =
            UUID.fromString("00000000-0000-000c-7000-000000000001");

    public static final UUID TYPOLOGY_SLA_TIMEOUT_ID =
            UUID.fromString("00000000-0000-000c-7000-000000000100");

    public static final UUID TYPOLOGY_RULE_THRESHOLD_BREACH_ID =
            UUID.fromString("00000000-0000-000c-7000-000000000300");

    public static final UUID TYPOLOGY_DRI_BOOTSTRAP_HEARTBEAT =
            UUID.fromString("00000000-0000-000c-7000-000000000500");

    // =========================================================================
    // ⚡ COMPUTATION ENGINE REGISTRATION CONSTANTS (rules_engine)
    // =========================================================================
    public static final UUID DRI_BOOTSTRAP_HEARTBEAT_RULE_ID =
            UUID.fromString("00000000-0000-d015-a000-000000000001");

    // =========================================================================
    // ⚡ STRING-BASED FALLBACK CONSTANTS
    // =========================================================================

    public static final String MALFORMED_OR_NULL_TX = "MALFORMED_OR_NULL_TX";
    public static final String FALLBACK_TENANT_BIZ_ID = "FALLBACK_TENANT_BIZ_ID";
}