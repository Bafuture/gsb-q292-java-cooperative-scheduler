package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 让出点可枚举：执行路径被完整记录，且相同输入重放得到完全相同的路径。 */
class PathReproducibilityTest {

    private static CooperativeScheduler newScheduler() {
        CooperativeTask a = CooperativeTask.builder("A")
                .step(ctx -> { })
                .yieldPoint("a-1")
                .step(ctx -> { })
                .yieldPoint("a-2")
                .step(ctx -> { })
                .build();
        CooperativeTask b = CooperativeTask.builder("B")
                .step(ctx -> { })
                .yieldPoint("b-1")
                .step(ctx -> { })
                .build();
        return CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN)
                .addTask(a)
                .addTask(b)
                .build();
    }

    @Test
    void schedulerRecordsEveryYieldPointPerTask() {
        CooperativeScheduler scheduler = newScheduler();
        scheduler.run();

        assertThat(scheduler.yieldHistoryOf("A")).containsExactly("a-1", "a-2");
        assertThat(scheduler.yieldHistoryOf("B")).containsExactly("b-1");
    }

    @Test
    void globalTraceEnumeratesYieldEventsInRoundOrder() {
        CooperativeScheduler scheduler = newScheduler();
        scheduler.run();

        assertThat(scheduler.yieldTrace()).containsExactly(
                new YieldRecord(1, "A", "a-1"),
                new YieldRecord(2, "B", "b-1"),
                new YieldRecord(3, "A", "a-2"));
    }

    @Test
    void identicalInputsReproduceIdenticalPaths() {
        CooperativeScheduler first = newScheduler();
        CooperativeScheduler second = newScheduler();
        first.run();
        second.run();

        assertThat(second.yieldTrace()).isEqualTo(first.yieldTrace());
        assertThat(second.yieldHistoryOf("A")).isEqualTo(first.yieldHistoryOf("A"));
        assertThat(second.yieldHistoryOf("B")).isEqualTo(first.yieldHistoryOf("B"));
        assertThat(second.stats().rounds()).isEqualTo(first.stats().rounds());
    }

    @Test
    void traceIsConsistentWithPerTaskHistories() {
        CooperativeScheduler scheduler = newScheduler();
        scheduler.run();

        // 从全局轨迹按任务过滤，必须能还原出每个任务各自的让出路径
        List<String> aFromTrace = scheduler.yieldTrace().stream()
                .filter(r -> r.taskName().equals("A"))
                .map(YieldRecord::yieldPointId)
                .toList();
        List<String> bFromTrace = scheduler.yieldTrace().stream()
                .filter(r -> r.taskName().equals("B"))
                .map(YieldRecord::yieldPointId)
                .toList();
        assertThat(aFromTrace).isEqualTo(scheduler.yieldHistoryOf("A"));
        assertThat(bFromTrace).isEqualTo(scheduler.yieldHistoryOf("B"));
    }
}
