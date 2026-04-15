package com.example.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "member.email")
public record EmailProperties(
        String provider,
        String fromAddress,
        String fromName
) {

    public EmailProperties {
        provider = normalize(provider, "logging");
        fromAddress = normalize(fromAddress, "no-reply@todaylunch.local");
        fromName = normalize(fromName, "TodayLunch");
    }

    public boolean usesSmtp() {
        return "smtp".equalsIgnoreCase(provider);
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
