package com.example.gsb.scheduler;

/**
 * 工作步骤执行时的上下文，提供取消相关的协作能力。
 */
public final class TaskContext {

    private final Scheduler scheduler;
    private final String taskId;

    TaskContext(Scheduler scheduler, String taskId) {
        this.scheduler = scheduler;
        this.taskId = taskId;
    }

    /** 当前正在执行的任务 id。 */
    public String taskId() {
        return taskId;
    }

    /** 当前任务是否已被请求取消。工作步骤可据此提前合作式退出。 */
    public boolean cancelled() {
        return scheduler.isCancellationRequested(taskId);
    }

    /** 请求取消另一个（或当前）任务。取消在让出点生效。 */
    public void cancel(String otherTaskId) {
        scheduler.cancel(otherTaskId);
    }

    /** 请求取消当前任务。 */
    public void cancelSelf() {
        scheduler.cancel(taskId);
    }
}
