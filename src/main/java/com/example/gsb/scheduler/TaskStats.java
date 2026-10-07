package com.example.gsb.scheduler;

/**
 * 单个任务的运行统计。
 */
public final class TaskStats {

    private final String taskId;
    private long runCount;
    private long yieldCount;
    private long stepCount;

    TaskStats(String taskId) {
        this.taskId = taskId;
    }

    public String taskId() {
        return taskId;
    }

    /** 任务被调度器恢复（派发）执行的次数。 */
    public long runCount() {
        return runCount;
    }

    /** 任务在让出点挂起的次数。 */
    public long yieldCount() {
        return yieldCount;
    }

    /** 任务累计执行的工作步数。 */
    public long stepCount() {
        return stepCount;
    }

    /** 平均每次被恢复后连续执行的工作步数。 */
    public double averageStepsPerRun() {
        return runCount == 0 ? 0.0 : (double) stepCount / runCount;
    }

    void recordRun() {
        runCount++;
    }

    void recordYield() {
        yieldCount++;
    }

    void recordSteps(long n) {
        stepCount += n;
    }

    @Override
    public String toString() {
        return "TaskStats{taskId=" + taskId
                + ", runCount=" + runCount
                + ", yieldCount=" + yieldCount
                + ", stepCount=" + stepCount
                + ", avgStepsPerRun=" + averageStepsPerRun() + '}';
    }
}
