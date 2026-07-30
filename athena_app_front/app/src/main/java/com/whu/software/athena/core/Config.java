package com.whu.software.athena.core;

public final class Config {

    private Config() {
    }

    public static final String API_KEY = getEnvOrEmpty("ATHENA_DEEPSEEK_API_KEY");
    public static final String BASE_URL = "https://api.deepseek.com/v1/chat/completions";
    public static final String MODEL = "deepseek-chat";

    private static String getEnvOrEmpty(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
