package com.moewadeul.common.transaction;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.row-lock-writes")
public record RowLockWritesProperties(
        @DefaultValue("6") int maxConcurrentWrites,
        @DefaultValue("5s") Duration permitWait) {

    public RowLockWritesProperties {
        if (maxConcurrentWrites < 1) {
            throw new IllegalArgumentException("moewadeul.row-lock-writes.maxConcurrentWrites must be positive");
        }
        if (permitWait.isNegative()) {
            throw new IllegalArgumentException("moewadeul.row-lock-writes.permitWait must not be negative");
        }
    }
}
