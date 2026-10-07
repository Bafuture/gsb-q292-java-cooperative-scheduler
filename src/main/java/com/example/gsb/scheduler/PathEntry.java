package com.example.gsb.scheduler;

/**
 * 执行路径中的一条记录：某个任务在第几轮调度中经过了哪个让出点。
 * 整个路径序列可用于复现一次调度的完整执行轨迹。
 */
public record PathEntry(String taskId, YieldPoint yieldPoint, long round) {
}
