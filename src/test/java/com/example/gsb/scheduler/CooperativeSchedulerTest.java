package com.example.gsb.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CooperativeSchedulerTest {

    private static CooperativeTask.Builder threeStepTask(String id, List<String> log, String tag) {
        return CooperativeTask.builder(id)
                .work(ctx -> log.add(tag + "-s1"))
                .yieldAt("after-s1")
                .work(ctx -> log.add(tag + "-s2"))
                .yieldAt("after-s2")
                .work(ctx -> log.add(tag + "-s3"));
    }

    // ---- 要求 1：挂起 / 恢复 -------------------------------------------------

    @Test
    void suspendsAtYieldPointsAndResumesByScheduler() {
        List<String> log = new ArrayList<>();
        CooperativeTask task = CooperativeTask.builder("t")
                .work(ctx -> log.add("s1"))
                .yieldAt("y1")
                .work(ctx -> log.add("s2"))
                .yieldAt("y2")
                .work(ctx -> log.add("s3"))
                .build();

        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        scheduler.submit(task);

        assertThat(scheduler.step()).isTrue();
        assertThat(log).containsExactly("s1");
        assertThat(task.state()).isEqualTo(TaskState.SUSPENDED);
        assertThat(task.executedYieldPoints()).containsExactly(YieldPoint.of("y1"));

        assertThat(scheduler.step()).isTrue();
        assertThat(log).containsExactly("s1", "s2");
        assertThat(task.state()).isEqualTo(TaskState.SUSPENDED);

        assertThat(scheduler.step()).isTrue();
        assertThat(log).containsExactly("s1", "s2", "s3");
        assertThat(task.state()).isEqualTo(TaskState.COMPLETED);
        assertThat(scheduler.step()).isFalse();
        assertThat(task.executedYieldPoints())
                .containsExactly(YieldPoint.of("y1"), YieldPoint.of("y2"));
    }

    // ---- 要求 2：轮转策略 ----------------------------------------------------

    @Test
    void roundRobinInterleavesEqualPriorityTasks() {
        List<String> log = new ArrayList<>();
        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        scheduler.submit(threeStepTask("A", log, "A").build());
        scheduler.submit(threeStepTask("B", log, "B").build());
        scheduler.submit(threeStepTask("C", log, "C").build());

        scheduler.run();

        assertThat(log).containsExactly(
                "A-s1", "B-s1", "C-s1",
                "A-s2", "B-s2", "C-s2",
                "A-s3", "B-s3", "C-s3");
        assertThat(scheduler.stateOf("A")).isEqualTo(TaskState.COMPLETED);
        assertThat(scheduler.stateOf("B")).isEqualTo(TaskState.COMPLETED);
        assertThat(scheduler.stateOf("C")).isEqualTo(TaskState.COMPLETED);
    }

    // ---- 要求 2：固定优先级策略 ----------------------------------------------

    @Test
    void fixedPriorityPrefersHighPriorityButRoundRobinsAtSamePriority() {
        List<String> log = new ArrayList<>();
        Scheduler scheduler = new Scheduler(SchedulerConfig.fixedPriority());
        scheduler.submit(threeStepTask("high", log, "high").priority(10).build());
        scheduler.submit(threeStepTask("low-1", log, "low1").priority(1).build());
        scheduler.submit(threeStepTask("low-2", log, "low2").priority(1).build());

        scheduler.run();

        // 高优先级任务全部完成之前，低优先级任务一步都不得执行。
        assertThat(log).startsWith("high-s1", "high-s2", "high-s3");
        // 两个同优先级的低优先级任务之间轮转，互不饿死。
        assertThat(log.subList(3, log.size()))
                .containsExactly("low1-s1", "low2-s1", "low1-s2", "low2-s2",
                        "low1-s3", "low2-s3");
    }

    // ---- 要求 4：取消 --------------------------------------------------------

    @Test
    void cancelledTaskNeverRunsItsRemainingBody() {
        List<String> log = new ArrayList<>();
        CooperativeTask victim = CooperativeTask.builder("victim")
                .work(ctx -> log.add("victim-s1"))
                .yieldAt("y1")
                .work(ctx -> log.add("victim-s2-MUST-NOT-RUN"))
                .yieldAt("y2")
                .work(ctx -> log.add("victim-s3-MUST-NOT-RUN"))
                .build();
        CooperativeTask killer = CooperativeTask.builder("killer")
                .work(ctx -> {
                    log.add("killer-cancels-victim");
                    ctx.cancel("victim");
                })
                .build();

        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        scheduler.submit(victim);
        scheduler.submit(killer);
        scheduler.run();

        assertThat(log).containsExactly("victim-s1", "killer-cancels-victim");
        assertThat(scheduler.stateOf("victim")).isEqualTo(TaskState.CANCELLED);
        assertThat(scheduler.stateOf("killer")).isEqualTo(TaskState.COMPLETED);
        // 受害者只执行过一段、让出一次，之后再未被恢复。
        SchedulerStats stats = scheduler.snapshotStats();
        assertThat(stats.taskStats("victim").runCount()).isEqualTo(1);
        assertThat(stats.taskStats("victim").stepCount()).isEqualTo(1);
    }

    @Test
    void cancelledBeforeStartNeverRunsAtAll() {
        List<String> log = new ArrayList<>();
        CooperativeTask task = CooperativeTask.builder("t")
                .work(ctx -> log.add("never"))
                .build();
        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        scheduler.submit(task);
        scheduler.cancel("t");
        scheduler.run();

        assertThat(log).isEmpty();
        assertThat(scheduler.stateOf("t")).isEqualTo(TaskState.CANCELLED);
        assertThat(scheduler.snapshotStats().taskStats("t").runCount()).isZero();
    }

    // ---- 要求 5：公平性（等优先级轮转，不长期饿死） ----------------------------

    @Test
    void roundRobinGuaranteesBoundedWaitingForEqualPriorityTasks() {
        int taskCount = 4;
        int workPerTask = 12;
        List<String> log = new ArrayList<>();

        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        for (int i = 0; i < taskCount; i++) {
            String id = "T" + i;
            CooperativeTask.Builder builder = CooperativeTask.builder(id);
            for (int s = 0; s < workPerTask; s++) {
                builder.work(ctx -> log.add(id)).yieldAt(id + "-y" + s);
            }
            scheduler.submit(builder.build());
        }
        scheduler.run();

        assertThat(log).hasSize(taskCount * workPerTask);
        for (int i = 0; i < taskCount; i++) {
            String id = "T" + i;
            assertThat(scheduler.stateOf(id)).isEqualTo(TaskState.COMPLETED);
            assertThat(scheduler.snapshotStats().taskStats(id).runCount())
                    .isEqualTo(workPerTask);
            // 同一任务相邻两次执行之间，最多只隔着 taskCount - 1 次其他派发：
            // 等待轮数有上界，不可能被长期饿死。
            List<Integer> positions = new ArrayList<>();
            for (int p = 0; p < log.size(); p++) {
                if (id.equals(log.get(p))) {
                    positions.add(p);
                }
            }
            assertThat(positions).hasSize(workPerTask);
            for (int k = 1; k < positions.size(); k++) {
                assertThat(positions.get(k) - positions.get(k - 1))
                        .as("gap between consecutive runs of %s", id)
                        .isLessThanOrEqualTo(taskCount);
            }
        }
    }

    // ---- 要求 3：让出点可枚举 + 路径可复现 ------------------------------------

    @Test
    void executionPathIsRecordedAndReproducible() {
        Supplier<Scheduler> factory = () -> {
            Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
            scheduler.submit(threeStepTask("A", new ArrayList<>(), "A").build());
            scheduler.submit(threeStepTask("B", new ArrayList<>(), "B").build());
            return scheduler;
        };

        Scheduler first = factory.get();
        Scheduler second = factory.get();
        first.run();
        second.run();

        // 相同任务、相同配置重复运行，执行路径完全一致。
        assertThat(second.executionPath()).isEqualTo(first.executionPath());

        List<PathEntry> expected = List.of(
                entry("A", "after-s1", 1),
                entry("B", "after-s1", 2),
                entry("A", "after-s2", 3),
                entry("B", "after-s2", 4));
        assertThat(first.executionPath()).containsExactlyElementsOf(expected);
    }

    @Test
    void yieldPointsAreEnumerableBothDeclaredAndExecuted() {
        List<String> log = new ArrayList<>();
        CooperativeTask task = CooperativeTask.builder("probe")
                .work(ctx -> log.add("w1"))
                .yieldAt("p1")
                .work(ctx -> log.add("w2"))
                .yieldAt("p2")
                .work(ctx -> log.add("w3"))
                .build();

        // 声明的全部让出点可枚举。
        assertThat(task.declaredYieldPoints())
                .containsExactly(YieldPoint.of("p1"), YieldPoint.of("p2"));

        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        scheduler.submit(task);
        scheduler.run();

        // 实际执行经过的让出点被逐一记录。
        assertThat(task.executedYieldPoints())
                .containsExactly(YieldPoint.of("p1"), YieldPoint.of("p2"));
        assertThat(scheduler.executionPath())
                .extracting(PathEntry::yieldPoint)
                .containsExactly(YieldPoint.of("p1"), YieldPoint.of("p2"));
    }

    private static PathEntry entry(String taskId, String point, long round) {
        return new PathEntry(taskId, YieldPoint.of(point), round);
    }

    // ---- 要求 6：统计 --------------------------------------------------------

    @Test
    void statisticsCoverRoundsRunYieldCountsAndAverageStepsPerRun() {
        CooperativeTask a = CooperativeTask.builder("A")
                // 第一次恢复连续 2 步后让出
                .work(ctx -> { }).work(ctx -> { }).yieldAt("a1")
                // 第二次恢复 1 步后让出
                .work(ctx -> { }).yieldAt("a2")
                // 第三次恢复 1 步后完成（未让出）
                .work(ctx -> { })
                .build();
        CooperativeTask b = CooperativeTask.builder("B")
                .work(ctx -> { }).yieldAt("b1")
                .work(ctx -> { })
                .build();

        Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
        scheduler.submit(a);
        scheduler.submit(b);
        scheduler.run();

        SchedulerStats stats = scheduler.snapshotStats();
        assertThat(stats.totalRounds()).isEqualTo(5);

        TaskStats aStats = stats.taskStats("A");
        assertThat(aStats.runCount()).isEqualTo(3);
        assertThat(aStats.yieldCount()).isEqualTo(2);
        assertThat(aStats.stepCount()).isEqualTo(4);
        assertThat(aStats.averageStepsPerRun()).isCloseTo(4.0 / 3, within(1e-9));

        TaskStats bStats = stats.taskStats("B");
        assertThat(bStats.runCount()).isEqualTo(2);
        assertThat(bStats.yieldCount()).isEqualTo(1);
        assertThat(bStats.stepCount()).isEqualTo(2);
        assertThat(bStats.averageStepsPerRun()).isCloseTo(1.0, within(1e-9));
    }
}
