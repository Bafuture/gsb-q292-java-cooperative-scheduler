package com.example.gsb.scheduler;

/** 调度策略。 */
public enum SchedulingPolicy {
    /** 固定优先级：每次调度就绪任务中优先级最高者；同优先级按提交顺序（FIFO）。 */
    FIXED_PRIORITY,
    /** 轮转：所有就绪任务按 FIFO 循环依次执行，保证等优先级任务不被饿死。 */
    ROUND_ROBIN
}
