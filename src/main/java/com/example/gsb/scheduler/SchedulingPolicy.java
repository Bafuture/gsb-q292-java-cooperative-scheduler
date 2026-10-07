package com.example.gsb.scheduler;

/** 调度策略。 */
public enum SchedulingPolicy {
    /**
     * 固定优先级：永远优先调度就绪任务中优先级值最大者；
     * 同优先级任务之间按轮转方式调度，避免同级饿死。
     */
    FIXED_PRIORITY,
    /** 轮转：所有就绪任务无视优先级，严格轮流获得调度机会。 */
    ROUND_ROBIN
}
