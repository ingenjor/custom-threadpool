package com.example.threadpool;

public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r, CustomThreadPool executor);
}
