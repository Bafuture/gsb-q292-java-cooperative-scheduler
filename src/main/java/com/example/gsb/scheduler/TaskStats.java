package com.example.gsb.scheduler;

import java.util.List;

/**
 * 单个任务的统计快照。
 *
 * @param executions           该任务被调度（恢复执行）的次数
 * @param yields               该任务经过让出点的次数
 * @param totalSteps           累计执行的 step 数
 * @param yieldPointHistory    该任务经过让出点的有序 id 列表（执行路径）
 */
public record TaskStats(
        String taskName,
        TaskStatus status,
        int priority,
        long executions,
        long yields,
        long totalSteps,
        List<String> yieldPointHistory) {

    /** 平均每次连续执行的步数 = 累计 step 数 / 调度次数；未被调度过时为 0。 */
    public double avgStepsPerExecution() {
        return executions == 0 ? 0.0 : (double) totalSteps / executions;
    }
}
