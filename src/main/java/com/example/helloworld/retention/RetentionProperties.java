package com.example.helloworld.retention;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retention for data this application generates. Retention for provider
 * content is configured per provider in {@code integration_providers}.
 */
@Validated
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(
        boolean enabled,
        String cron,
        @Min(1) int syncRunDays,
        @Min(1) int syncErrorDays,
        @Min(1) int auditEventDays) {
}
