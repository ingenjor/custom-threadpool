package com.example.threadpool;

public class CallerRunsPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, CustomThreadPool executor) {
        if (!executor.isShutdown()) {
            log("[Rejected] Task " + r + " running in caller thread.");
            r.run();
        }
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
