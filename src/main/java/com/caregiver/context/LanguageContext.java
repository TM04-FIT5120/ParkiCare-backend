package com.caregiver.context;

public class LanguageContext {

    private static final ThreadLocal<String> LANGUAGE = new ThreadLocal<>();

    public static void setLanguage(String language) {
        LANGUAGE.set(language);
    }

    public static String getLanguage() {
        String language = LANGUAGE.get();
        return language == null || language.isBlank() ? "en-US" : language;
    }

    public static void clear() {
        LANGUAGE.remove();
    }
}