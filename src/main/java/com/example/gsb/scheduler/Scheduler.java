package com.example.gsb.scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协作式轻量任务调度器（单线程）。
 *
 * <p>工作模型：
 * <ul>
 *   <li>任务体由工作步骤与显式让出点交错组成；</li>
 *   <li>每轮调度恢复一个任务，它从上次挂起处连续执行工作步骤，
 *       直到遇到下一个让出点挂起，或执行完毕；</li>
 *   <li>调度策略决定下一个恢复谁：轮转或固定优先级（同级轮转）；</li>
 *   <li>取消只在让出点检查：任务被挂起后若已被取消，恢复时不再执行
 *       任务体的任何后续部分。</li>
 * </ul>
 */
public final class Scheduler {

    private final SchedulerConfig config;

    private final List<CooperativeTask> tasks = new ArrayList<>();
    private final Map<String, CooperativeTask> tasksById = new LinkedHashMap<>();
    private final Set<String> cancellationRequests = new HashSet<>();
    private final Map<String, Long> lastRunRound = new LinkedHashMap<>();
    private final Map<String, TaskStats> stats = new LinkedHashMap<>();
    private final List<PathEntry> executionPath = new ArrayList<>();

    private long rounds;

    public Scheduler(SchedulerConfig config) {
        this.config = config;
    }

    /** 提交一个任务。 */
    public void submit(CooperativeTask task) {
        if (tasksById.containsKey(task.id())) {
            throw new IllegalArgumentException("duplicate task id: " + task.id());
        }
        tasks.add(task);
        tasksById.put(task.id(), task);
        lastRunRound.put(task.id(), 0L);
        stats.put(task.id(), new TaskStats(task.id()));
    }

    /**
     * 请求取消任务。若任务尚未完成，则立即转入 CANCELLED；
     * 若任务正挂起，其任务体后续部分永不再执行。
     */
    public void cancel(String taskId) {
        CooperativeTask task = tasksById.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + taskId);
        }
        cancellationRequests.add(taskId);
        if (task.state() != TaskState.COMPLETED) {
            task.transitionTo(TaskState.CANCELLED);
        }
    }

    public boolean isCancellationRequested(String taskId) {
        return cancellationRequests.contains(taskId);
    }

    public TaskState stateOf(String taskId) {
        return requireTask(taskId).state();
    }

    /** 累计调度轮次（派发次数）。 */
    public long totalRounds() {
        return rounds;
    }

    /** 完整执行路径：按发生顺序记录每个经过的让出点。 */
    public List<PathEntry> executionPath() {
        return List.copyOf(executionPath);
    }

    public SchedulerStats snapshotStats() {
        return new SchedulerStats(rounds, stats);
    }

    /**
     * 执行一轮调度：选择并恢复一个任务，直到它让出或完成。
     *
     * @return 是否有任务被执行；无就绪任务时返回 false
     */
    public boolean step() {
        CooperativeTask next = pickNextRunnable();
        if (next == null) {
            return false;
        }
        dispatch(next);
        return true;
    }

    /** 一直调度到没有任何就绪任务（全部完成或取消）。 */
    public void run() {
        while (step()) {
            // 继续下一轮
        }
    }

    private CooperativeTask pickNextRunnable() {
        CooperativeTask best = null;
        for (CooperativeTask candidate : tasks) {
            if (!isRunnable(candidate)) {
                continue;
            }
            if (best == null || compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean isRunnable(CooperativeTask task) {
        TaskState state = task.state();
        return (state == TaskState.READY || state == TaskState.SUSPENDED)
                && !cancellationRequests.contains(task.id());
    }

    /**
     * 比较两个就绪任务，较小者先被调度。
     * 固定优先级：优先级高者优先，同级按"最久未被调度"轮转；
     * 轮转：一律按"最久未被调度"，提交顺序作为确定性兜底。
     */
    private int compare(CooperativeTask a, CooperativeTask b) {
        if (config.policy() == SchedulingPolicy.FIXED_PRIORITY && a.priority() != b.priority()) {
            return Integer.compare(b.priority(), a.priority());
        }
        long ra = lastRunRound.getOrDefault(a.id(), 0L);
        long rb = lastRunRound.getOrDefault(b.id(), 0L);
        if (ra != rb) {
            return Long.compare(ra, rb);
        }
        // 从未被调度或同一轮（不会发生）时，列表顺序决定先后，保证确定性。
        return 0;
    }

    private void dispatch(CooperativeTask task) {
        rounds++;
        lastRunRound.put(task.id(), rounds);
        TaskStats taskStats = stats.get(task.id());
        taskStats.recordRun();

        task.transitionTo(TaskState.RUNNING);
        TaskContext context = new TaskContext(this, task.id());

        long stepsThisRun = 0;
        while (task.hasMore()) {
            // 取消只在让出点（恢复点）检查：每次准备执行下一段任务体之前确认。
            if (cancellationRequests.contains(task.id())) {
                task.transitionTo(TaskState.CANCELLED);
                taskStats.recordSteps(stepsThisRun);
                return;
            }
            TaskStep step = task.nextStep();
            if (step instanceof TaskStep.Yield yield) {
                taskStats.recordSteps(stepsThisRun);
                taskStats.recordYield();
                task.recordYield(yield.point());
                executionPath.add(new PathEntry(task.id(), yield.point(), rounds));
                // 让出点之后已无剩余步骤：任务直接完成，不再空转一轮。
                task.transitionTo(task.hasMore() ? TaskState.SUSPENDED : TaskState.COMPLETED);
                return;
            }
            ((TaskStep.Work) step).action().accept(context);
            stepsThisRun++;
        }
        taskStats.recordSteps(stepsThisRun);
        task.transitionTo(TaskState.COMPLETED);
    }

    private CooperativeTask requireTask(String taskId) {
        CooperativeTask task = tasksById.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + taskId);
        }
        return task;
    }
}
