package com.example.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool implements CustomExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final BlockingQueue<Runnable>[] queues;
    private final List<Worker> workers = new ArrayList<>();
    private final AtomicInteger nextQueueIndex = new AtomicInteger(0);
    private volatile boolean isShutdown = false;
    private volatile boolean isShutdownNow = false;
    private final RejectedExecutionHandler rejectionHandler;
    private final ThreadFactory threadFactory;

    private final Object lock = new Object();
    private int activeWorkers = 0;
    private final AtomicInteger totalTasksSubmitted = new AtomicInteger(0);
    private final AtomicInteger totalTasksCompleted = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
                            int queueSize, int minSpareThreads,
                            RejectedExecutionHandler rejectionHandler, ThreadFactory threadFactory) {
        if (corePoolSize < 0 || maxPoolSize <= 0 || maxPoolSize < corePoolSize || keepAliveTime < 0)
            throw new IllegalArgumentException();
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.rejectionHandler = rejectionHandler;
        this.threadFactory = threadFactory;

        queues = new BlockingQueue[maxPoolSize];
        for (int i = 0; i < maxPoolSize; i++) {
            queues[i] = new ArrayBlockingQueue<>(queueSize);
        }

        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
        ensureMinSpareThreads();
    }

    private void addWorker() {
        synchronized (lock) {
            if (activeWorkers >= maxPoolSize) return;
            Worker w = new Worker(activeWorkers);
            workers.add(w);
            w.start();
            activeWorkers++;
        }
    }

    private void ensureMinSpareThreads() {
        synchronized (lock) {
            while (activeWorkers < maxPoolSize && countIdleWorkers() < minSpareThreads) {
                addWorker();
            }
        }
    }

    private long countIdleWorkers() {
        return workers.stream().filter(Worker::isIdle).count();
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException();
        if (isShutdown || isShutdownNow) {
            rejectionHandler.rejectedExecution(command, this);
            return;
        }

        totalTasksSubmitted.incrementAndGet();

        int idx = nextQueueIndex.getAndIncrement() % activeWorkers;
        BlockingQueue<Runnable> queue = queues[idx];

        boolean offered = queue.offer(command);
        if (offered) {
            log("[Pool] Task accepted into queue #" + idx + ": " + command);
        } else {
            synchronized (lock) {
                if (activeWorkers < maxPoolSize) {
                    addWorker();
                    int newIdx = activeWorkers - 1;
                    if (queues[newIdx].offer(command)) {
                        log("[Pool] Task accepted into queue #" + newIdx + " (new worker): " + command);
                        return;
                    }
                }
            }
            log("[Rejected] Task " + command + " was rejected due to overload!");
            rejectionHandler.rejectedExecution(command, this);
        }

        checkMinSpareThreads();
    }

    private void checkMinSpareThreads() {
        synchronized (lock) {
            if (activeWorkers >= maxPoolSize) return;
            long idleWorkers = countIdleWorkers();
            if (idleWorkers < minSpareThreads) {
                addWorker();
                log("[Pool] Created new worker due to minSpareThreads policy. Active workers: " + activeWorkers);
            }
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) throw new NullPointerException();
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        log("[Pool] Shutdown initiated. Waiting for workers to finish...");
    }

    @Override
    public void shutdownNow() {
        isShutdownNow = true;
        log("[Pool] Shutdown now initiated. Interrupting all workers.");
        synchronized (lock) {
            for (Worker w : workers) {
                w.shutdown();
            }
            workers.clear();
            activeWorkers = 0;
        }
    }

    public boolean isShutdown() {
        return isShutdown || isShutdownNow;
    }

    public boolean isTerminated() {
        synchronized (lock) {
            return (isShutdown || isShutdownNow) && activeWorkers == 0;
        }
    }

    public int getActiveWorkers() {
        synchronized (lock) {
            return activeWorkers;
        }
    }

    public int getSubmittedTasks() {
        return totalTasksSubmitted.get();
    }

    public int getCompletedTasks() {
        return totalTasksCompleted.get();
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private class Worker extends Thread {
        private final int queueId;
        private volatile boolean running = true;
        private volatile boolean idle = true;

        Worker(int queueId) {
            this.queueId = queueId;
            Thread t = threadFactory.newThread(this);
            this.setName(t.getName());
            this.setDaemon(false);
        }

        @Override
        public void run() {
            log("[Worker] " + getName() + " started.");
            while (running && !isShutdownNow) {
                try {
                    idle = true;
                    Runnable task = queues[queueId].poll(keepAliveTime, timeUnit);
                    idle = false;
                    if (task != null) {
                        log("[Worker] " + getName() + " executes " + task);
                        task.run();
                        totalTasksCompleted.incrementAndGet();
                    } else {
                        synchronized (lock) {
                            // Завершаем лишние потоки по таймауту
                            if (activeWorkers > corePoolSize) {
                                log("[Worker] " + getName() + " idle timeout, stopping.");
                                running = false;
                                activeWorkers--;
                                workers.remove(this);
                                break;
                            }
                            // При shutdown выходим, только если все очереди пусты
                            if (isShutdown && allQueuesEmpty()) {
                                running = false;
                                activeWorkers--;
                                workers.remove(this);
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    if (isShutdownNow) {
                        break;
                    }
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log("[Worker] " + getName() + " caught exception: " + e.getMessage());
                }
            }
            log("[Worker] " + getName() + " terminated.");
        }

        private boolean allQueuesEmpty() {
            for (BlockingQueue<Runnable> q : queues) {
                if (!q.isEmpty()) return false;
            }
            return true;
        }

        public boolean isIdle() {
            return idle;
        }

        public void shutdown() {
            running = false;
            this.interrupt();
        }
    }
}
