package com.example.gsb.scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可恢复的协作式任务。
 *
 * <p>任务由一串有序元素构成：{@code step}（一段同步工作）与 {@code yield}（显式让出点）。
 * 调度器每次调度从上次停下的位置继续执行，直到遇到下一个让出点（挂起）或执行到末尾（完成）。
 * 任务体不需要自己写状态机，步骤间的恢复位置就是让出点。
 *
 * <p>注意：协作式调度无法打断一个正在运行的 step，只有 step 之间的让出点才是挂起/取消边界。
 */
public final class CooperativeTask {

    sealed interface Element permits StepElement, YieldElement {
    }

    record StepElement(TaskAction action) implements Element {
    }

    record YieldElement(YieldPoint yieldPoint) implements Element {
    }

    private final String name;
    private final int priority;
    private final List<Element> elements;

    private CooperativeTask(String name, int priority, List<Element> elements) {
        this.name = name;
        this.priority = priority;
        this.elements = List.copyOf(elements);
    }

    public String name() {
        return name;
    }

    /** 数值越大优先级越高。 */
    public int priority() {
        return priority;
    }

    List<Element> elements() {
        return elements;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** 任务构建器：按执行顺序追加步骤与让出点。 */
    public static final class Builder {

        private final String name;
        private int priority;
        private final List<Element> elements = new ArrayList<>();
        private final Set<String> yieldIds = new HashSet<>();

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("task name must not be blank");
            }
            this.name = name;
        }

        /** 设置固定优先级（数值越大优先级越高），默认 0。 */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * 追加一段同步工作。一个 step 在一次调度中执行完毕，中间不可被挂起。
         * 长工作请拆成多个 step 并在其间插入让出点。
         */
        public Builder step(TaskAction action) {
            if (action == null) {
                throw new IllegalArgumentException("step action must not be null");
            }
            elements.add(new StepElement(action));
            return this;
        }

        /** 在当前位置插入显式让出点，id 在同一任务内必须唯一。 */
        public Builder yieldPoint(String id) {
            return yieldPoint(new YieldPoint(id));
        }

        /** 在当前位置插入显式让出点。 */
        public Builder yieldPoint(YieldPoint yieldPoint) {
            if (yieldPoint == null) {
                throw new IllegalArgumentException("yield point must not be null");
            }
            if (!yieldIds.add(yieldPoint.id())) {
                throw new IllegalArgumentException(
                        "duplicate yield point id within task '" + name + "': " + yieldPoint.id());
            }
            elements.add(new YieldElement(yieldPoint));
            return this;
        }

        public CooperativeTask build() {
            return new CooperativeTask(name, priority, elements);
        }
    }
}
