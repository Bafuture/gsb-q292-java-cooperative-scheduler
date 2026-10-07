package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 公平性验证：等优先级任务在轮转策略下不得被长期饿死。
 * 不变式：任意检查点上，任意两个任务的已执行轮次数之差不超过 1。
 */
class FairnessTest {

    private static final int TASK_COUNT = 4;
    private static final int SEGMENTS = 50;

    private static CooperativeScheduler newFairScheduler() {
        CooperativeScheduler.Builder builder = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN);
        for (int t = 0; t < TASK_COUNT; t++) {
            CooperativeTask.Builder task = CooperativeTask.builder("T" + t);
            for (int i = 1; i <= SEGMENTS; i++) {
                task.step(ctx -> { });
                if (i < SEGMENTS) {
                    task.yieldPoint("y" + i);
                }
            }
            builder.addTask(task.build());
        }
        return builder.build();
    }

    @Test
    void executionCountsNeverDivergeByMoreThanOne() {
        CooperativeScheduler scheduler = newFairScheduler();
        List<String> names = List.of("T0", "T1", "T2", "T3");

        // 每个完整循环后检查一次：任何任务都不会被落下超过一轮
        while (scheduler.hasActiveTasks()) {
            scheduler.runRounds(TASK_COUNT);
            SchedulerStats stats = scheduler.stats();
            long min = names.stream().mapToLong(n -> stats.taskStats(n).executions()).min().orElseThrow();
            long max = names.stream().mapToLong(n -> stats.taskStats(n).executions()).max().orElseThrow();
            assertThat(max - min)
                    .as("等优先级任务执行次数之差不得超过 1（min=%d, max=%d）", min, max)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void everyTaskGetsScheduledWithinOneFullCycle() {
        CooperativeScheduler scheduler = newFairScheduler();

        // 只跑一个完整循环：每个任务都必须已被调度，证明没有任务被无限推迟
        scheduler.runRounds(TASK_COUNT);

        SchedulerStats stats = scheduler.stats();
        for (int t = 0; t < TASK_COUNT; t++) {
            assertThat(stats.taskStats("T" + t).executions())
                    .as("任务 T%d 在一个循环内必须得到执行", t)
                    .isEqualTo(1);
        }
    }

    @Test
    void allTasksCompleteWithEqualExecutionCounts() {
        CooperativeScheduler scheduler = newFairScheduler();
        scheduler.run();

        SchedulerStats stats = scheduler.stats();
        assertThat(stats.rounds()).isEqualTo((long) TASK_COUNT * SEGMENTS);
        for (int t = 0; t < TASK_COUNT; t++) {
            TaskStats taskStats = stats.taskStats("T" + t);
            assertThat(taskStats.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(taskStats.executions()).isEqualTo(SEGMENTS);
            assertThat(taskStats.yields()).isEqualTo(SEGMENTS - 1);
        }
    }
}
