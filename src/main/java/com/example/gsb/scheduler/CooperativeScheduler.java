package com.example.gsb.scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单线程协作式轻量任务调度器。
 *
 * <p>核心语义：
 * <ul>
 *   <li>任务以「步骤 + 让出点」的可恢复序列编写，调度器在让出点挂起任务，并决定恢复顺序。</li>
 *   <li>两种可配置策略：{@link SchedulingPolicy#FIXED_PRIORITY} 与 {@link SchedulingPolicy#ROUND_ROBIN}。</li>
 *   <li>每个任务经过的让出点都会被记录，全局轨迹与单任务轨迹均可枚举，用于复现执行路径。</li>
 *   <li>取消只在让出点（调度边界）生效：被取消的任务不再执行任务体的任何后续部分。</li>
 *   <li>统计调度轮次、每任务执行/让出次数、累计步数与平均每次连续执行步数。</li>
 * </ul>
 *
 * <p>局限：协作式调度无法抢占正在运行的 step；任务不主动让出就会独占线程直到结束。
 */
public final class CooperativeScheduler {

    private final SchedulingPolicy policy;
    private final Map<String, TaskState> tasks = new LinkedHashMap<>();
    private final Deque<TaskState> readyQueue = new ArrayDeque<>();
    private final List<YieldRecord> yieldTrace = new ArrayList<>();
    private long rounds;

    private CooperativeScheduler(Builder builder) {
        this.policy = builder.policy;
        for (CooperativeTask task : builder.tasks) {
            if (tasks.containsKey(task.name())) {
                throw new SchedulerException("duplicate task name: " + task.name());
            }
            TaskState state = new TaskState(task);
            tasks.put(task.name(), state);
            readyQueue.addLast(state);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public SchedulingPolicy policy() {
        return policy;
    }

    public TaskStatus statusOf(String taskName) {
        return requireTask(taskName).status;
    }

    /** 请求取消任务。标志在下一个让出点（调度边界）被检查并生效。 */
    public void cancel(String taskName) {
        requireTask(taskName).cancelRequested = true;
    }

    public boolean isCancelled(String taskName) {
        return requireTask(taskName).cancelRequested;
    }

    /** 该任务经过让出点的有序 id 列表（即该任务的执行路径）。 */
    public List<String> yieldHistoryOf(String taskName) {
        return Collections.unmodifiableList(new ArrayList<>(requireTask(taskName).yieldHistory));
    }

    /** 全局让出事件轨迹，可据此逐轮复现执行路径。 */
    public List<YieldRecord> yieldTrace() {
        return Collections.unmodifiableList(new ArrayList<>(yieldTrace));
    }

    /** 持续调度直到所有任务终止（完成 / 取消 / 失败）。返回已执行的调度轮次。 */
    public long run() {
        return runRounds(Long.MAX_VALUE);
    }

    /**
     * 最多调度 {@code maxRounds} 轮。每轮选取一个就绪任务执行到下一个让出点或结束。
     * 提前终止条件：所有任务均已终止。返回实际执行的轮次数。
     */
    public long runRounds(long maxRounds) {
        if (maxRounds < 0) {
            throw new IllegalArgumentException("maxRounds must not be negative");
        }
        long executed = 0;
        while (executed < maxRounds) {
            TaskState state = select();
            if (state == null) {
                break;
            }
            rounds++;
            executed++;
            dispatch(state, rounds);
            if (policy == SchedulingPolicy.ROUND_ROBIN
                    && (state.status == TaskStatus.READY || state.status == TaskStatus.SUSPENDED)) {
                readyQueue.addLast(state);
            }
        }
        return executed;
    }

    /** 调度器是否还有就绪/挂起任务（false 表示全部终止）。 */
    public boolean hasActiveTasks() {
        for (TaskState state : tasks.values()) {
            if (state.status == TaskStatus.READY || state.status == TaskStatus.SUSPENDED) {
                return true;
            }
        }
        return false;
    }

    public SchedulerStats stats() {
        List<TaskStats> perTask = new ArrayList<>(tasks.size());
        for (TaskState state : tasks.values()) {
            perTask.add(new TaskStats(
                    state.task.name(),
                    state.status,
                    state.task.priority(),
                    state.executions,
                    state.yields,
                    state.totalSteps,
                    List.copyOf(state.yieldHistory)));
        }
        return new SchedulerStats(rounds, List.copyOf(yieldTrace), List.copyOf(perTask));
    }

    private TaskState select() {
        return switch (policy) {
            case ROUND_ROBIN -> selectRoundRobin();
            case FIXED_PRIORITY -> selectFixedPriority();
        };
    }

    /**
     * 轮转：就绪队列 FIFO，取队首执行一轮；仍就绪的任务回到队尾。
     * 每个就绪任务在一个完整循环中必然得到执行，因此等优先级任务不会被饿死。
     */
    private TaskState selectRoundRobin() {
        return readyQueue.pollFirst();
    }

    /** 固定优先级：选择优先级最高的就绪任务；同优先级按注册顺序（FIFO）打破平手。 */
    private TaskState selectFixedPriority() {
        TaskState best = null;
        for (TaskState state : readyTasks()) {
            if (best == null || state.task.priority() > best.task.priority()) {
                best = state;
            }
        }
        return best;
    }

    private List<TaskState> readyTasks() {
        List<TaskState> ready = new ArrayList<>();
        for (TaskState state : tasks.values()) {
            if (state.status == TaskStatus.READY || state.status == TaskStatus.SUSPENDED) {
                ready.add(state);
            }
        }
        return ready;
    }

    /**
     * 一次调度（一轮）：从游标位置执行，直到遇到第一个让出点或任务结束。
     * 取消检查发生在两处调度边界：调度开始时、到达让出点时。
     */
    private void dispatch(TaskState state, long round) {
        if (state.cancelRequested) {
            state.status = TaskStatus.CANCELLED;
            return;
        }
        state.executions++;
        List<CooperativeTask.Element> elements = state.task.elements();
        while (state.cursor < elements.size()) {
            CooperativeTask.Element element = elements.get(state.cursor);
            state.cursor++;
            if (element instanceof CooperativeTask.StepElement step) {
                state.totalSteps++;
                try {
                    step.action().run(state);
                } catch (Exception e) {
                    state.status = TaskStatus.FAILED;
                    throw new SchedulerException(
                            "task '" + state.task.name() + "' failed at step " + state.totalSteps, e);
                }
            } else if (element instanceof CooperativeTask.YieldElement y) {
                state.yields++;
                state.yieldHistory.add(y.yieldPoint().id());
                yieldTrace.add(new YieldRecord(round, state.task.name(), y.yieldPoint().id()));
                if (state.cancelRequested) {
                    state.status = TaskStatus.CANCELLED;
                    return;
                }
                state.status = TaskStatus.SUSPENDED;
                return;
            }
        }
        state.status = TaskStatus.COMPLETED;
    }

    private TaskState requireTask(String taskName) {
        TaskState state = tasks.get(Objects.requireNonNull(taskName, "taskName"));
        if (state == null) {
            throw new SchedulerException("unknown task: " + taskName);
        }
        return state;
    }

    /** 任务运行时状态与统计；同时作为 {@link TaskContext} 传给步骤。 */
    private static final class TaskState implements TaskContext {

        private final CooperativeTask task;
        private int cursor;
        private TaskStatus status = TaskStatus.READY;
        private boolean cancelRequested;
        private long executions;
        private long yields;
        private long totalSteps;
        private final List<String> yieldHistory = new ArrayList<>();

        private TaskState(CooperativeTask task) {
            this.task = task;
        }

        @Override
        public boolean isCancelled() {
            return cancelRequested;
        }

        @Override
        public String taskName() {
            return task.name();
        }
    }

    /** 调度器构建器。 */
    public static final class Builder {

        private SchedulingPolicy policy = SchedulingPolicy.ROUND_ROBIN;
        private final List<CooperativeTask> tasks = new ArrayList<>();

        /** 配置调度策略，默认 {@link SchedulingPolicy#ROUND_ROBIN}。 */
        public Builder policy(SchedulingPolicy policy) {
            this.policy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder addTask(CooperativeTask task) {
            tasks.add(Objects.requireNonNull(task, "task"));
            return this;
        }

        public CooperativeScheduler build() {
            return new CooperativeScheduler(this);
        }
    }
}
