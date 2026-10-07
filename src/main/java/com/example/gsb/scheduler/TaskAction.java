package com.example.gsb.scheduler;

/**
 * 任务步骤的函数式接口。与 {@link Runnable} 不同，执行时会拿到 {@link TaskContext}，
 * 因此较长的步骤内部也可以查询取消标志，做协作式的提前退出。
 */
@FunctionalInterface
public interface TaskAction {

    void run(TaskContext context) throws Exception;
}
