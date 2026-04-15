package com.example.member.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "member.seller-auto-promotion")
public record SellerAutoPromotionProperties(
        Duration delay,
        Duration pollInterval,
        Duration lockTtl,
        Duration pendingTtl
) {

    public SellerAutoPromotionProperties {
        delay = delay == null ? Duration.ofMinutes(30) : delay;
        pollInterval = pollInterval == null ? Duration.ofSeconds(10) : pollInterval;
        lockTtl = lockTtl == null ? Duration.ofSeconds(30) : lockTtl;
        pendingTtl = pendingTtl == null ? delay.plusDays(1) : pendingTtl;
    }
}
