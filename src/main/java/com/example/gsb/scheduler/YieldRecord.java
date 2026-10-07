package com.example.gsb.scheduler;

/**
 * 一次让出事件，是全局执行轨迹中的一条记录。
 * 记录发生时的调度轮次、任务名与让出点 id，可用于复现执行路径。
 */
public record YieldRecord(long round, String taskName, String yieldPointId) {

    @Override
    public String toString() {
        return "round=" + round + " " + taskName + "@" + yieldPointId;
    }
}
