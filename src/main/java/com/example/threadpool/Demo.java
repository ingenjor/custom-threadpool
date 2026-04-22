package com.example.threadpool;

import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(
                2,                  // corePoolSize
                4,                  // maxPoolSize
                5, TimeUnit.SECONDS,// keepAliveTime
                5,                  // queueSize
                1,                  // minSpareThreads
                new CallerRunsPolicy(),
                new LoggingThreadFactory("MyPool")
        );

        System.out.println("=== Submitting 15 tasks ===");
        for (int i = 1; i <= 15; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("Task " + taskId + " started on " + Thread.currentThread().getName());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Task " + taskId + " interrupted");
                }
                System.out.println("Task " + taskId + " finished");
            });
        }

        Thread.sleep(2000);
        System.out.println("\n=== Submitting 5 more tasks (will trigger rejection?) ===");
        for (int i = 16; i <= 20; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("Task " + taskId + " started on " + Thread.currentThread().getName());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Task " + taskId + " finished");
            });
        }

        Thread.sleep(8000);
        System.out.println("\n=== Initiating shutdown ===");
        pool.shutdown();

        while (!pool.isTerminated()) {
            Thread.sleep(500);
        }
        System.out.println("Pool terminated. Total tasks completed: " + pool.getCompletedTasks() +
                " out of " + pool.getSubmittedTasks());
    }
}
