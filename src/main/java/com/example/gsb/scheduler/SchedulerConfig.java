package com.example.gsb.scheduler;

import java.util.Objects;

/** 调度器配置，目前包含调度策略。 */
public record SchedulerConfig(SchedulingPolicy policy) {

    public SchedulerConfig {
        Objects.requireNonNull(policy, "policy must not be null");
    }

    public static SchedulerConfig of(SchedulingPolicy policy) {
        return new SchedulerConfig(policy);
    }

    public static SchedulerConfig roundRobin() {
        return new SchedulerConfig(SchedulingPolicy.ROUND_ROBIN);
    }

    public static SchedulerConfig fixedPriority() {
        return new SchedulerConfig(SchedulingPolicy.FIXED_PRIORITY);
    }
}
