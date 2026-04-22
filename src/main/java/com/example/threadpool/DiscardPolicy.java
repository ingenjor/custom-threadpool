package com.example.threadpool;

public class DiscardPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, CustomThreadPool executor) {
        log("[Rejected] Task " + r + " was silently discarded.");
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
