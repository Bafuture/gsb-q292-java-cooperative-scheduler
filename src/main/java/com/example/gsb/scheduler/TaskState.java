package com.example.gsb.scheduler;

/** 协作式任务的生命周期状态。 */
public enum TaskState {
    /** 已提交，尚未被调度过。 */
    READY,
    /** 正在被调度器执行（仅在一次派发期间可观察到）。 */
    RUNNING,
    /** 在让出点挂起，等待调度器恢复。 */
    SUSPENDED,
    /** 全部步骤执行完毕。 */
    COMPLETED,
    /** 被取消，任务体后续部分不会再执行。 */
    CANCELLED
}
