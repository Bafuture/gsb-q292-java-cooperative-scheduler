package com.example.gsb.scheduler;

/** 任务生命周期状态。 */
public enum TaskStatus {
    /** 已提交，尚未被调度过。 */
    READY,
    /** 在让出点挂起，等待再次被调度。 */
    SUSPENDED,
    /** 正常执行完毕。 */
    COMPLETED,
    /** 在让出点检测到取消标志，已终止。 */
    CANCELLED,
    /** 任务体抛出未捕获异常，已终止。 */
    FAILED
}
