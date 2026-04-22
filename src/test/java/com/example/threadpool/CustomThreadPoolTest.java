package com.example.threadpool;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CustomThreadPoolTest {

    @Test
    void testBasicSubmit() throws Exception {
        CustomThreadPool pool = new CustomThreadPool(2, 4, 5, TimeUnit.SECONDS,
                5, 1, new CallerRunsPolicy(), new LoggingThreadFactory("Test"));
        Future<Integer> future = pool.submit(() -> 42);
        assertEquals(42, future.get());
        pool.shutdown();
    }

    @Test
    void testExecuteMultipleTasks() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(2, 4, 5, TimeUnit.SECONDS,
                5, 1, new CallerRunsPolicy(), new LoggingThreadFactory("Test"));
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < taskCount; i++) {
            pool.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(taskCount, counter.get());
        pool.shutdown();
    }

    @Test
    void testRejectionWithAbortPolicy() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(1, 1, 5, TimeUnit.SECONDS,
                1, 0, new AbortPolicy(), new LoggingThreadFactory("Test"));
        pool.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        });
        pool.execute(() -> {}); // занимает очередь
        assertThrows(RuntimeException.class, () -> pool.execute(() -> {}));
        Thread.sleep(600);
        pool.shutdown();
    }

    @Test
    void testCallerRunsPolicy() {
        CustomThreadPool pool = new CustomThreadPool(1, 1, 5, TimeUnit.SECONDS,
                1, 0, new CallerRunsPolicy(), new LoggingThreadFactory("Test"));
        pool.execute(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        });
        pool.execute(() -> {}); // занимает очередь
        String mainThread = Thread.currentThread().getName();
        String[] executedThread = new String[1];
        pool.execute(() -> executedThread[0] = Thread.currentThread().getName());
        assertEquals(mainThread, executedThread[0]);
        pool.shutdown();
    }

    @Test
    void testShutdownWaitsForCompletion() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(2, 2, 5, TimeUnit.SECONDS,
                10, 0, new CallerRunsPolicy(), new LoggingThreadFactory("Test"));
        int taskCount = 5;
        CountDownLatch latch = new CountDownLatch(taskCount);
        for (int i = 0; i < taskCount; i++) {
            pool.execute(latch::countDown);
        }
        pool.shutdown();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(pool.isTerminated() || pool.getCompletedTasks() == taskCount);
    }

    @Test
    void testShutdownNowInterrupts() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(2, 2, 5, TimeUnit.SECONDS,
                10, 0, new CallerRunsPolicy(), new LoggingThreadFactory("Test"));
        AtomicInteger interrupted = new AtomicInteger();
        pool.execute(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
            }
        });
        Thread.sleep(100);
        pool.shutdownNow();
        assertTrue(pool.isTerminated() || interrupted.get() > 0);
    }

    @Test
    void testMinSpareThreads() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(1, 3, 1, TimeUnit.SECONDS,
                10, 2, new CallerRunsPolicy(), new LoggingThreadFactory("Test"));
        Thread.sleep(300);
        assertTrue(pool.getActiveWorkers() >= 2);
        pool.shutdown();
    }

    @Test
    void testKeepAliveTimeShrinksThreads() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(
                1, 3, 500, TimeUnit.MILLISECONDS,
                1,          // queueSize = 1, чтобы гарантированно вызывать переполнение
                0,
                new CallerRunsPolicy(),
                new LoggingThreadFactory("Test")
        );

        // Отправляем 3 долгие задачи подряд. Из-за queueSize=1 очередь быстро заполнится,
        // и пул будет вынужден создавать новые потоки вплоть до maxPoolSize.
        for (int i = 0; i < 3; i++) {
            pool.execute(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}
            });
        }

        // Даём время на создание потоков (core=1, должно стать 3)
        Thread.sleep(200);
        int expandedSize = pool.getActiveWorkers();
        assertTrue(expandedSize >= 2, "Pool should have expanded to at least 2 workers, but was " + expandedSize);

        // Ждём завершения задач и таймаут keepAliveTime
        Thread.sleep(1000);
        int shrunkSize = pool.getActiveWorkers();
        assertTrue(shrunkSize < expandedSize,
                "Workers should shrink after keepAliveTime. Before: " + expandedSize + ", after: " + shrunkSize);
        assertTrue(shrunkSize <= 2, "Should have at most 2 workers, but was " + shrunkSize);

        pool.shutdown();
    }
}
