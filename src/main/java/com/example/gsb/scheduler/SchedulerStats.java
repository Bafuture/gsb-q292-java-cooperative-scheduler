package com.example.gsb.scheduler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调度器整体统计：调度轮次 + 每个任务的执行统计。
 */
public final class SchedulerStats {

    private final long totalRounds;
    private final Map<String, TaskStats> taskStats;

    SchedulerStats(long totalRounds, Map<String, TaskStats> taskStats) {
        this.totalRounds = totalRounds;
        this.taskStats = Collections.unmodifiableMap(new LinkedHashMap<>(taskStats));
    }

    /** 调度器累计派发次数（每恢复一个任务执行到让出点/完成计一轮）。 */
    public long totalRounds() {
        return totalRounds;
    }

    /** 按任务 id 的统计，只读。 */
    public Map<String, TaskStats> taskStats() {
        return taskStats;
    }

    public TaskStats taskStats(String taskId) {
        TaskStats stats = taskStats.get(taskId);
        if (stats == null) {
            throw new IllegalArgumentException("unknown task: " + taskId);
        }
        return stats;
    }
}
