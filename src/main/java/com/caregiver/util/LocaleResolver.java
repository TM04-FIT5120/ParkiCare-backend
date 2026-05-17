package com.caregiver.util;

import java.util.Set;

public final class LocaleResolver {

    public static final String EN = "en";
    public static final String ZH_CN = "zh-CN";
    public static final String MS_MY = "ms-MY";

    public static final Set<String> TRANSLATABLE_LOCALES = Set.of(ZH_CN, MS_MY);

    private LocaleResolver() {
    }

    /**
     * Normalizes Accept-Language to en | zh-CN | ms-MY.
     */
    public static String normalize(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return EN;
        }
        String lang = acceptLanguage.trim().toLowerCase();
        if (lang.startsWith("zh")) {
            return ZH_CN;
        }
        if (lang.startsWith("ms")) {
            return MS_MY;
        }
        return EN;
    }

    public static boolean isEnglish(String locale) {
        return EN.equals(locale);
    }

    public static String toQwenLanguageName(String locale) {
        return switch (locale) {
            case ZH_CN -> "Simplified Chinese";
            case MS_MY -> "Malay";
            default -> "English";
        };
    }
}
