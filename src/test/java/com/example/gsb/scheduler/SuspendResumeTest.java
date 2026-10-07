package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 挂起 / 恢复语义：任务在让出点挂起，恢复时从让出点之后继续，不重复执行已完成的步骤。 */
class SuspendResumeTest {

    @Test
    void suspendsAtYieldPointAndResumesAfterIt() {
        List<String> events = new ArrayList<>();
        CooperativeTask taskA = CooperativeTask.builder("A")
                .step(ctx -> events.add("a1"))
                .yieldPoint("a-y1")
                .step(ctx -> events.add("a2"))
                .yieldPoint("a-y2")
                .step(ctx -> events.add("a3"))
                .build();
        CooperativeTask taskB = CooperativeTask.builder("B")
                .step(ctx -> events.add("b1"))
                .yieldPoint("b-y1")
                .step(ctx -> events.add("b2"))
                .build();

        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN)
                .addTask(taskA)
                .addTask(taskB)
                .build();

        scheduler.runRounds(1);
        assertThat(events).containsExactly("a1");
        assertThat(scheduler.statusOf("A")).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(scheduler.statusOf("B")).isEqualTo(TaskStatus.READY);

        scheduler.runRounds(1);
        assertThat(events).containsExactly("a1", "b1");

        scheduler.runRounds(1);
        // A 恢复后从 a-y1 之后继续：a2 执行且 a1 没有重复执行
        assertThat(events).containsExactly("a1", "b1", "a2");
        assertThat(scheduler.statusOf("A")).isEqualTo(TaskStatus.SUSPENDED);

        scheduler.run();
        assertThat(events).containsExactly("a1", "b1", "a2", "b2", "a3");
        assertThat(scheduler.statusOf("A")).isEqualTo(TaskStatus.COMPLETED);
        assertThat(scheduler.statusOf("B")).isEqualTo(TaskStatus.COMPLETED);
        assertThat(scheduler.hasActiveTasks()).isFalse();
    }

    @Test
    void taskWithoutYieldPointRunsToCompletionInOneRound() {
        List<String> events = new ArrayList<>();
        CooperativeTask task = CooperativeTask.builder("solo")
                .step(ctx -> events.add("s1"))
                .step(ctx -> events.add("s2"))
                .build();
        CooperativeScheduler scheduler = CooperativeScheduler.builder().addTask(task).build();

        long rounds = scheduler.run();
        assertThat(rounds).isEqualTo(1);
        assertThat(events).containsExactly("s1", "s2");
        assertThat(scheduler.statusOf("solo")).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void duplicateYieldPointIdsWithinOneTaskAreRejected() {
        assertThatThrownBy(() -> CooperativeTask.builder("dup")
                .yieldPoint("y")
                .yieldPoint("y")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate yield point id");
    }
}
