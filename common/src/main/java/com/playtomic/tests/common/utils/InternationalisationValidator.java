package com.playtomic.tests.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Checks currency codes and locale tags against values known to the running JRE. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InternationalisationValidator {

    private static final Set<String> VALID_CURRENCY_CODES = new HashSet<>();
    private static final Set<String> VALID_LOCALE_TAGS = new HashSet<>();

    static {
        // Automatically harvest all universally known global currencies from the JRE Runtime
        for (Currency currency : Currency.getAvailableCurrencies()) {
            VALID_CURRENCY_CODES.add(currency.getCurrencyCode().toUpperCase());
        }
        // Automatically harvest all universally known standard locales and language tags
        for (Locale locale : Locale.getAvailableLocales()) {
            if (!locale.toLanguageTag().equals("und")) { // Filter out undefined placeholders
                VALID_LOCALE_TAGS.add(locale.toLanguageTag().toLowerCase());
            }
        }
    }

    /**
     * Checks if a currency code complies with the official ISO 4217 standard specification.
     */
    public static boolean isValidIsoCurrency(String currencyCode) {
        if (currencyCode == null) return false;
        return VALID_CURRENCY_CODES.contains(currencyCode.toUpperCase());
    }

    /**
     * Checks if a language/dialect tag complies with standard IETF BCP 47 criteria.
     */
    public static boolean isValidLocaleTag(String localeTag) {
        if (localeTag == null) return false;
        // Normalizes input format validation sweeps (e.g., matching 'en-GB' vs 'EN-gb')
        return VALID_LOCALE_TAGS.contains(localeTag.replace('_', '-').toLowerCase());
    }
}
