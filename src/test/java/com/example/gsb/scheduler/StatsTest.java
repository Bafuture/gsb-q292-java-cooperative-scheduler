package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import org.junit.jupiter.api.Test;

/** 统计：调度轮次、每任务执行次数、让出次数与平均每次连续执行步数。 */
class StatsTest {

    @Test
    void collectsRoundsAndPerTaskStatistics() {
        CooperativeTask a = CooperativeTask.builder("A")
                .step(ctx -> { })
                .step(ctx -> { })
                .yieldPoint("a-y1")
                .step(ctx -> { })
                .build();
        CooperativeTask b = CooperativeTask.builder("B")
                .step(ctx -> { })
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN)
                .addTask(a)
                .addTask(b)
                .build();

        scheduler.run();

        SchedulerStats stats = scheduler.stats();
        // 轮次：A(挂起) -> B(完成) -> A(完成)
        assertThat(stats.rounds()).isEqualTo(3);

        TaskStats aStats = stats.taskStats("A");
        assertThat(aStats.executions()).isEqualTo(2);
        assertThat(aStats.yields()).isEqualTo(1);
        assertThat(aStats.totalSteps()).isEqualTo(3);
        assertThat(aStats.avgStepsPerExecution()).isEqualTo(1.5, offset(1e-9));
        assertThat(aStats.yieldPointHistory()).containsExactly("a-y1");

        TaskStats bStats = stats.taskStats("B");
        assertThat(bStats.executions()).isEqualTo(1);
        assertThat(bStats.yields()).isZero();
        assertThat(bStats.totalSteps()).isEqualTo(1);
        assertThat(bStats.avgStepsPerExecution()).isEqualTo(1.0, offset(1e-9));

        assertThat(stats.taskStatsByName()).containsOnlyKeys("A", "B");
        assertThat(stats.yieldTrace()).containsExactly(new YieldRecord(1, "A", "a-y1"));
    }

    @Test
    void zeroExecutionTaskReportsZeroAverage() {
        CooperativeTask never = CooperativeTask.builder("never")
                .step(ctx -> { })
                .build();
        CooperativeTask cancelled = CooperativeTask.builder("cancelled")
                .step(ctx -> { })
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .addTask(cancelled)
                .addTask(never)
                .build();

        scheduler.cancel("cancelled");
        scheduler.runRounds(1); // 只处理 cancelled（被取消，不计执行）

        TaskStats stats = scheduler.stats().taskStats("never");
        assertThat(stats.executions()).isZero();
        assertThat(stats.avgStepsPerExecution()).isZero();
    }

    @Test
    void runRoundsReturnsActuallyExecutedRounds() {
        CooperativeTask task = CooperativeTask.builder("t")
                .step(ctx -> { })
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder().addTask(task).build();

        assertThat(scheduler.runRounds(10)).isEqualTo(1);
        assertThat(scheduler.runRounds(10)).isZero();
        assertThat(scheduler.stats().rounds()).isEqualTo(1);
    }
}
