package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 两种可配置调度策略的执行顺序。 */
class SchedulingPolicyTest {

    private static CooperativeTask yieldingTask(String name, int priority, List<String> events, int segments) {
        CooperativeTask.Builder builder = CooperativeTask.builder(name).priority(priority);
        for (int i = 1; i <= segments; i++) {
            String label = name + i;
            builder.step(ctx -> events.add(label));
            if (i < segments) {
                builder.yieldPoint(name + "-y" + i);
            }
        }
        return builder.build();
    }

    @Test
    void fixedPriorityRunsHigherPriorityTaskToCompletionFirst() {
        List<String> events = new ArrayList<>();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.FIXED_PRIORITY)
                .addTask(yieldingTask("low", 1, events, 2))
                .addTask(yieldingTask("high", 10, events, 2))
                .addTask(yieldingTask("mid", 5, events, 2))
                .build();

        scheduler.run();

        // 高优先级任务即使挂起，恢复时仍优先于低优先级任务
        assertThat(events).containsExactly("high1", "high2", "mid1", "mid2", "low1", "low2");
    }

    @Test
    void fixedPriorityBreaksTiesByRegistrationOrder() {
        List<String> events = new ArrayList<>();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.FIXED_PRIORITY)
                .addTask(yieldingTask("first", 5, events, 2))
                .addTask(yieldingTask("second", 5, events, 2))
                .build();

        scheduler.run();

        assertThat(events).containsExactly("first1", "first2", "second1", "second2");
    }

    @Test
    void roundRobinInterleavesEqualPriorityTasks() {
        List<String> events = new ArrayList<>();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN)
                .addTask(yieldingTask("A", 0, events, 3))
                .addTask(yieldingTask("B", 0, events, 3))
                .addTask(yieldingTask("C", 0, events, 3))
                .build();

        scheduler.run();

        assertThat(events).containsExactly("A1", "B1", "C1", "A2", "B2", "C2", "A3", "B3", "C3");
    }

    @Test
    void roundRobinIgnoresPriority() {
        List<String> events = new ArrayList<>();
        CooperativeScheduler scheduler = CooperativeScheduler.builder()
                .policy(SchedulingPolicy.ROUND_ROBIN)
                .addTask(yieldingTask("low", 1, events, 2))
                .addTask(yieldingTask("high", 100, events, 2))
                .build();

        scheduler.run();

        assertThat(events).containsExactly("low1", "high1", "low2", "high2");
    }

    @Test
    void policyIsConfigurableAndDefaultsToRoundRobin() {
        assertThat(CooperativeScheduler.builder().build().policy())
                .isEqualTo(SchedulingPolicy.ROUND_ROBIN);
        assertThat(CooperativeScheduler.builder().policy(SchedulingPolicy.FIXED_PRIORITY).build().policy())
                .isEqualTo(SchedulingPolicy.FIXED_PRIORITY);
    }
}
