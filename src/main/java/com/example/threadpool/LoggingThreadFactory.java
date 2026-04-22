package com.example.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public LoggingThreadFactory(String poolName) {
        this.namePrefix = poolName + "-worker-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
        log("[ThreadFactory] Creating new thread: " + t.getName());
        t.setUncaughtExceptionHandler((th, ex) -> {
            System.err.println("[ThreadFactory] Uncaught exception in " + th.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        });
        return t;
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
