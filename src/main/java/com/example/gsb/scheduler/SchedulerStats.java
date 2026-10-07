package com.example.gsb.scheduler;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 调度器整体统计快照。 */
public record SchedulerStats(long rounds, List<YieldRecord> yieldTrace, List<TaskStats> taskStats) {

    public TaskStats taskStats(String taskName) {
        return taskStatsByName().get(taskName);
    }

    public Map<String, TaskStats> taskStatsByName() {
        return taskStats.stream().collect(Collectors.toUnmodifiableMap(TaskStats::taskName, Function.identity()));
    }
}
