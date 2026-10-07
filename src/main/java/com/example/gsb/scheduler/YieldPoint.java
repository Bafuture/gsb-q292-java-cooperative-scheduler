package com.example.gsb.scheduler;

import java.util.Objects;

/**
 * 显式让出点。任务执行到让出点时挂起，由调度器决定何时恢复。
 * 让出点具备可枚举的名称，调度器会记录每个任务实际经过的让出点序列，
 * 用于复现执行路径。
 */
public record YieldPoint(String name) {

    public YieldPoint {
        Objects.requireNonNull(name, "yield point name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("yield point name must not be blank");
        }
    }

    public static YieldPoint of(String name) {
        return new YieldPoint(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
