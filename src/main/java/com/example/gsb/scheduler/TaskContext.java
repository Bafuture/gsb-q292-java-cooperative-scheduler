package com.example.gsb.scheduler;

/**
 * 任务运行时上下文，由调度器在每次调度时传入。
 * 任务体可通过它查询取消标志，在长步骤内部做协作式取消检查。
 */
public interface TaskContext {

    /** 当前任务是否已被请求取消。 */
    boolean isCancelled();

    /** 当前任务名。 */
    String taskName();
}
