package com.example.gsb.scheduler;

import java.util.function.Consumer;

/**
 * 任务体的组成单元：要么是一段工作（Work），要么是一个让出标记（Yield）。
 * 调度器恢复任务时，连续执行 Work 步骤，直到遇到 Yield 标记挂起，
 * 或步骤序列耗尽而完成。
 */
public sealed interface TaskStep {

    /** 一段不可再细分的工作。 */
    record Work(Consumer<TaskContext> action) implements TaskStep {
        public Work {
            java.util.Objects.requireNonNull(action, "action must not be null");
        }
    }

    /** 显式让出点：执行到此处任务挂起。 */
    record Yield(YieldPoint point) implements TaskStep {
        public Yield {
            java.util.Objects.requireNonNull(point, "point must not be null");
        }
    }
}
