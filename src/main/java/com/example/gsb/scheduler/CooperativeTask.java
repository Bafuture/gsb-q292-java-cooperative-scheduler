package com.example.gsb.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 可恢复的协作式任务单元。
 *
 * <p>任务体由一串 {@link TaskStep} 声明：{@code work(...)} 追加一段工作，
 * {@code yieldAt(...)} 追加一个显式让出点。调度器每次恢复任务时，
 * 从上次挂起的位置继续，顺序执行工作步骤，直到遇到下一个让出点挂起，
 * 或步骤耗尽而完成。任务的恢复位置（游标）由任务自身保存，
 * 因此任务天然是"可恢复的单元"。
 */
public final class CooperativeTask {

    private final String id;
    private final int priority;
    private final List<TaskStep> steps;
    private final List<YieldPoint> declaredYieldPoints;
    private final List<YieldPoint> executedYieldPoints = new ArrayList<>();

    private TaskState state = TaskState.READY;
    private int cursor;

    private CooperativeTask(Builder builder) {
        this.id = builder.id;
        this.priority = builder.priority;
        this.steps = List.copyOf(builder.steps);
        this.declaredYieldPoints = this.steps.stream()
                .filter(TaskStep.Yield.class::isInstance)
                .map(TaskStep.Yield.class::cast)
                .map(TaskStep.Yield::point)
                .toList();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** 优先级值，越大优先级越高；仅在固定优先级策略下生效。 */
    public int priority() {
        return priority;
    }

    public TaskState state() {
        return state;
    }

    /** 任务声明的全部让出点（可枚举），按声明顺序返回。 */
    public List<YieldPoint> declaredYieldPoints() {
        return declaredYieldPoints;
    }

    /** 任务实际执行经过的让出点，按执行顺序记录，用于复现执行路径。 */
    public List<YieldPoint> executedYieldPoints() {
        return Collections.unmodifiableList(executedYieldPoints);
    }

    void transitionTo(TaskState newState) {
        this.state = newState;
    }

    boolean hasMore() {
        return cursor < steps.size();
    }

    TaskStep nextStep() {
        return steps.get(cursor++);
    }

    void recordYield(YieldPoint point) {
        executedYieldPoints.add(point);
    }

    public static final class Builder {

        private final String id;
        private int priority;
        private final List<TaskStep> steps = new ArrayList<>();

        private Builder(String id) {
            Objects.requireNonNull(id, "task id must not be null");
            if (id.isBlank()) {
                throw new IllegalArgumentException("task id must not be blank");
            }
            this.id = id;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /** 追加一段工作步骤。 */
        public Builder work(Consumer<TaskContext> action) {
            steps.add(new TaskStep.Work(action));
            return this;
        }

        /** 追加一个显式让出点。 */
        public Builder yieldAt(String name) {
            steps.add(new TaskStep.Yield(YieldPoint.of(name)));
            return this;
        }

        /** 追加一个显式让出点。 */
        public Builder yieldAt(YieldPoint point) {
            steps.add(new TaskStep.Yield(point));
            return this;
        }

        public CooperativeTask build() {
            return new CooperativeTask(this);
        }
    }
}
