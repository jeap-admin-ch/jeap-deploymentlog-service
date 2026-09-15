package ch.admin.bit.jeap.deploymentlog.docgen.service;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs documentation tasks on one worker while allowing live deployment work to overtake repair and batch work.
 */
@Slf4j
public final class DocgenTaskDispatcher implements Executor {

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder()
            .contextRegistry(ContextRegistry.getInstance())
            .build();

    private final Object monitor = new Object();
    private final Map<String, Runnable> liveTasks = new LinkedHashMap<>();
    private final Map<String, Runnable> backgroundTasks = new LinkedHashMap<>();
    private final Set<String> repairTaskKeys = new HashSet<>();
    private final AtomicLong externalTaskSequence = new AtomicLong();
    private final int queueCapacity;
    private final int liveTaskBurst;
    private final Thread worker;

    private boolean acceptingTasks = true;
    private String runningTaskKey;
    private int consecutiveLiveTasks;

    DocgenTaskDispatcher(int queueCapacity, int liveTaskBurst) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Docgen queue capacity must be at least 1");
        }
        if (liveTaskBurst < 1) {
            throw new IllegalArgumentException("Docgen live-task burst must be at least 1");
        }
        this.queueCapacity = queueCapacity;
        this.liveTaskBurst = liveTaskBurst;
        this.worker = new Thread(this::runTasks, "AsyncDocgen-1");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    void submitLive(String taskKey, Runnable task) {
        submit(taskKey, task, true, false);
    }

    void submitBackground(String taskKey, Runnable task) {
        submit(taskKey, task, false, false);
    }

    void submitRepair(String taskKey, Runnable task) {
        submit(taskKey, task, false, true);
    }

    @Override
    public void execute(Runnable command) {
        submitBackground("external-" + externalTaskSequence.incrementAndGet(), command);
    }

    private void submit(String taskKey, Runnable task, boolean live, boolean repair) {
        Objects.requireNonNull(taskKey, "taskKey");
        Objects.requireNonNull(task, "task");
        Runnable contextualTask = SNAPSHOT_FACTORY.captureAll(new Object[0]).wrap(task);
        synchronized (monitor) {
            ensureAcceptingTasks();
            boolean followUpForRunningTask = taskKey.equals(runningTaskKey);
            if (!live && liveTasks.containsKey(taskKey)) {
                return;
            }
            if (live) {
                backgroundTasks.remove(taskKey);
                repairTaskKeys.remove(taskKey);
                if (!followUpForRunningTask) {
                    ensureCapacityForLiveTask(taskKey);
                }
                liveTasks.put(taskKey, contextualTask);
            } else {
                if (!backgroundTasks.containsKey(taskKey) && !followUpForRunningTask) {
                    ensureCapacity(taskKey);
                }
                backgroundTasks.put(taskKey, contextualTask);
                if (repair) {
                    repairTaskKeys.add(taskKey);
                } else {
                    repairTaskKeys.remove(taskKey);
                }
            }
            monitor.notifyAll();
        }
    }

    private void ensureCapacityForLiveTask(String taskKey) {
        if (liveTasks.containsKey(taskKey) || queuedTaskCount() < queueCapacity) {
            return;
        }
        String displacedTask = removeNewestRepairTask();
        if (displacedTask == null) {
            throw queueFull(taskKey);
        }
        log.info("Prioritizing live Docgen task {}; repair task {} remains available to the repair job",
                taskKey, displacedTask);
    }

    private void ensureCapacity(String taskKey) {
        if (queuedTaskCount() >= queueCapacity) {
            throw queueFull(taskKey);
        }
    }

    private TaskRejectedException queueFull(String taskKey) {
        return new TaskRejectedException("Docgen queue is full; rejected task " + taskKey);
    }

    private String removeNewestRepairTask() {
        String newestKey = null;
        for (String key : backgroundTasks.keySet()) {
            if (repairTaskKeys.contains(key)) {
                newestKey = key;
            }
        }
        if (newestKey != null) {
            backgroundTasks.remove(newestKey);
            repairTaskKeys.remove(newestKey);
        }
        return newestKey;
    }

    private int queuedTaskCount() {
        return liveTasks.size() + backgroundTasks.size();
    }

    private void ensureAcceptingTasks() {
        if (!acceptingTasks) {
            throw new TaskRejectedException("Docgen dispatcher is shutting down");
        }
    }

    private void runTasks() {
        while (!Thread.currentThread().isInterrupted()) {
            QueuedTask queuedTask;
            try {
                queuedTask = takeNextTask();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            if (queuedTask == null) {
                return;
            }
            try {
                queuedTask.task().run();
            } catch (Exception ex) {
                log.error("Unexpected failure in Docgen task {}", queuedTask.key(), ex);
            } finally {
                synchronized (monitor) {
                    runningTaskKey = null;
                    monitor.notifyAll();
                }
            }
        }
    }

    private QueuedTask takeNextTask() throws InterruptedException {
        synchronized (monitor) {
            while (acceptingTasks && liveTasks.isEmpty() && backgroundTasks.isEmpty()) {
                monitor.wait();
            }
            if (!acceptingTasks) {
                return null;
            }
            boolean takeLiveTask = !liveTasks.isEmpty()
                    && (backgroundTasks.isEmpty() || consecutiveLiveTasks < liveTaskBurst);
            Map<String, Runnable> selectedTasks = takeLiveTask ? liveTasks : backgroundTasks;
            Iterator<Map.Entry<String, Runnable>> iterator = selectedTasks.entrySet().iterator();
            Map.Entry<String, Runnable> entry = iterator.next();
            QueuedTask task = new QueuedTask(entry.getKey(), entry.getValue());
            iterator.remove();
            repairTaskKeys.remove(task.key());
            runningTaskKey = task.key();
            consecutiveLiveTasks = takeLiveTask ? consecutiveLiveTasks + 1 : 0;
            return task;
        }
    }

    public boolean isIdle() {
        synchronized (monitor) {
            return runningTaskKey == null && liveTasks.isEmpty() && backgroundTasks.isEmpty();
        }
    }

    @PreDestroy
    void shutdown() {
        synchronized (monitor) {
            acceptingTasks = false;
            liveTasks.clear();
            backgroundTasks.clear();
            repairTaskKeys.clear();
            monitor.notifyAll();
        }
        worker.interrupt();
    }

    private record QueuedTask(String key, Runnable task) {
    }
}
