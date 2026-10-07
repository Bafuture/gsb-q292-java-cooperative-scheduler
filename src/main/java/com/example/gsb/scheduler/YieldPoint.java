package com.example.gsb.scheduler;

import java.util.Objects;

/**
 * 显式让出点。任务执行到让出点时挂起，把线程交还给调度器，
 * 由调度器决定何时恢复。让出点携带稳定 id，因此执行路径可枚举、可复现。
 */
public final class YieldPoint {

    private final String id;

    public YieldPoint(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("yield point id must not be blank");
        }
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof YieldPoint other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "YieldPoint(" + id + ")";
    }
}
