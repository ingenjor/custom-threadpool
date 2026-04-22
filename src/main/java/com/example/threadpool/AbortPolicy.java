package com.example.threadpool;

public class AbortPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, CustomThreadPool executor) {
        log("[Rejected] Task " + r + " was rejected due to overload! Aborting.");
        throw new RuntimeException("Task rejected: " + r);
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
