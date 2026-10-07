package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 取消语义：取消标志在让出点（调度边界）生效，取消后任务体后续部分不再执行。 */
class CancellationTest {

    @Test
    void cancellingBeforeFirstDispatchPreventsAnyExecution() {
        List<String> events = new ArrayList<>();
        CooperativeTask task = CooperativeTask.builder("victim")
                .step(ctx -> events.add("s1"))
                .yieldPoint("y1")
                .step(ctx -> events.add("s2"))
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder().addTask(task).build();

        scheduler.cancel("victim");
        scheduler.run();

        assertThat(events).isEmpty();
        assertThat(scheduler.statusOf("victim")).isEqualTo(TaskStatus.CANCELLED);
        assertThat(scheduler.stats().taskStats("victim").executions()).isZero();
    }

    @Test
    void cancellingSuspendedTaskStopsItAtNextDispatchBoundary() {
        List<String> events = new ArrayList<>();
        CooperativeTask victim = CooperativeTask.builder("victim")
                .step(ctx -> events.add("v1"))
                .yieldPoint("v-y1")
                .step(ctx -> events.add("v2"))
                .build();
        AtomicReference<CooperativeScheduler> ref = new AtomicReference<>();
        CooperativeTask killer = CooperativeTask.builder("killer")
                .step(ctx -> ref.get().cancel("victim"))
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN)
                .addTask(victim)
                .addTask(killer)
                .build();
        ref.set(scheduler);

        scheduler.run();

        // victim 在 v-y1 挂起后被取消，恢复调度时直接终止，v2 永不执行
        assertThat(events).containsExactly("v1");
        assertThat(scheduler.statusOf("victim")).isEqualTo(TaskStatus.CANCELLED);
        assertThat(scheduler.statusOf("killer")).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void cancellationRequestedDuringOwnDispatchTakesEffectAtNextYieldPoint() {
        List<String> events = new ArrayList<>();
        AtomicReference<CooperativeScheduler> ref = new AtomicReference<>();
        CooperativeTask task = CooperativeTask.builder("self")
                .step(ctx -> {
                    events.add("s1");
                    ref.get().cancel("self");
                })
                .yieldPoint("y1")
                .step(ctx -> events.add("s2"))
                .yieldPoint("y2")
                .step(ctx -> events.add("s3"))
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder().addTask(task).build();
        ref.set(scheduler);

        scheduler.run();

        // 让出点 y1 处检测到取消标志：y1 被记录，但 s2 / s3 不再执行
        assertThat(events).containsExactly("s1");
        assertThat(scheduler.statusOf("self")).isEqualTo(TaskStatus.CANCELLED);
        assertThat(scheduler.yieldHistoryOf("self")).containsExactly("y1");
    }

    @Test
    void stepCanObserveCancellationFlagCooperatively() {
        List<String> events = new ArrayList<>();
        AtomicReference<CooperativeScheduler> ref = new AtomicReference<>();
        CooperativeTask task = CooperativeTask.builder("coop")
                .step(ctx -> {
                    ref.get().cancel("coop");
                    // 长步骤内部可通过 TaskContext 协作式地提前退出
                    if (ctx.isCancelled()) {
                        return;
                    }
                    events.add("unreachable");
                })
                .yieldPoint("y1")
                .step(ctx -> events.add("after-yield"))
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder().addTask(task).build();
        ref.set(scheduler);

        scheduler.run();

        assertThat(events).isEmpty();
        assertThat(scheduler.statusOf("coop")).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void cancellingOneTaskDoesNotAffectOthers() {
        List<String> events = new ArrayList<>();
        CooperativeTask a = CooperativeTask.builder("a")
                .step(ctx -> events.add("a1"))
                .yieldPoint("a-y1")
                .step(ctx -> events.add("a2"))
                .build();
        CooperativeTask b = CooperativeTask.builder("b")
                .step(ctx -> events.add("b1"))
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .addTask(a)
                .addTask(b)
                .build();

        scheduler.cancel("a");
        scheduler.run();

        assertThat(events).containsExactly("b1");
        assertThat(scheduler.statusOf("a")).isEqualTo(TaskStatus.CANCELLED);
        assertThat(scheduler.statusOf("b")).isEqualTo(TaskStatus.COMPLETED);
    }
}
