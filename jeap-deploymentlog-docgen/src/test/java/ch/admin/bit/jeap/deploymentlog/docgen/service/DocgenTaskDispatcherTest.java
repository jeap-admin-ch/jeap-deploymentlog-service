package ch.admin.bit.jeap.deploymentlog.docgen.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocgenTaskDispatcherTest {

    private DocgenTaskDispatcher dispatcher;

    @AfterEach
    void shutdownDispatcher() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    @Test
    void liveDeploymentOvertakesQueuedRepairTask() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(10, 10);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(3);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("blocker", blockingTask(
                blockerStarted, releaseBlocker, executionOrder, completed));
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.submitRepair("repair", recordingTask("repair", executionOrder, completed));
        dispatcher.submitLive("deployment", recordingTask("deployment", executionOrder, completed));

        releaseBlocker.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("blocker", "deployment", "repair");
    }

    @Test
    void liveDeploymentPromotesAndReplacesQueuedRepairForSameDeployment() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(10, 10);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("blocker", blockingTask(
                blockerStarted, releaseBlocker, executionOrder, completed));
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.submitRepair("deployment:1", recordingTask("repair", executionOrder, completed));
        dispatcher.submitLive("deployment:1", recordingTask("deployment", executionOrder, completed));

        releaseBlocker.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("blocker", "deployment");
    }

    @Test
    void backgroundTaskRunsAfterConfiguredLiveTaskBurst() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(10, 2);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(5);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("blocker", blockingTask(
                blockerStarted, releaseBlocker, executionOrder, completed));
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.submitRepair("repair", recordingTask("repair", executionOrder, completed));
        dispatcher.submitLive("deployment:1", recordingTask("deployment:1", executionOrder, completed));
        dispatcher.submitLive("deployment:2", recordingTask("deployment:2", executionOrder, completed));
        dispatcher.submitLive("deployment:3", recordingTask("deployment:3", executionOrder, completed));

        releaseBlocker.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly(
                "blocker", "deployment:1", "deployment:2", "repair", "deployment:3");
    }

    @Test
    void liveDeploymentDisplacesNewestRepairWhenQueueIsFull() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(2, 10);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(3);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("blocker", blockingTask(
                blockerStarted, releaseBlocker, executionOrder, completed));
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.submitRepair("repair:1", recordingTask("repair:1", executionOrder, completed));
        dispatcher.submitRepair("repair:2", recordingTask("repair:2", executionOrder, completed));

        dispatcher.submitLive("deployment", recordingTask("deployment", executionOrder, completed));
        releaseBlocker.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("blocker", "deployment", "repair:1");
    }

    @Test
    void liveDeploymentDoesNotDiscardNonRepairBackgroundTaskWhenQueueIsFull() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(1, 10);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("blocker", blockingTask(
                blockerStarted, releaseBlocker, executionOrder, completed));
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.submitBackground("manual-batch", recordingTask("manual-batch", executionOrder, completed));

        assertThatThrownBy(() -> dispatcher.submitLive("deployment", () -> { }))
                .isInstanceOf(TaskRejectedException.class);
        releaseBlocker.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("blocker", "manual-batch");
    }

    @Test
    void backgroundUpdateSubmittedWhileSameKeyIsRunningQueuesOneFollowUp() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(10, 10);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("documentation-structure", () -> {
            firstStarted.countDown();
            await(releaseFirst);
            executionOrder.add("first");
            completed.countDown();
        });
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        dispatcher.submitBackground("documentation-structure",
                recordingTask("superseded-follow-up", executionOrder, completed));
        dispatcher.submitBackground("documentation-structure",
                recordingTask("latest-follow-up", executionOrder, completed));
        releaseFirst.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("first", "latest-follow-up");
    }

    @Test
    void reservesOneFollowUpForRunningTaskWhenRegularQueueIsFull() throws InterruptedException {
        dispatcher = new DocgenTaskDispatcher(1, 10);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(3);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        dispatcher.submitBackground("documentation-structure", () -> {
            firstStarted.countDown();
            await(releaseFirst);
            executionOrder.add("first");
            completed.countDown();
        });
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.submitBackground("other", recordingTask("other", executionOrder, completed));

        dispatcher.submitBackground("documentation-structure",
                recordingTask("follow-up", executionOrder, completed));
        releaseFirst.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("first", "other", "follow-up");
    }

    private static Runnable blockingTask(CountDownLatch started, CountDownLatch release,
                                         List<String> executionOrder, CountDownLatch completed) {
        return () -> {
            started.countDown();
            await(release);
            executionOrder.add("blocker");
            completed.countDown();
        };
    }

    private static Runnable recordingTask(String name, List<String> executionOrder, CountDownLatch completed) {
        return () -> {
            executionOrder.add(name);
            completed.countDown();
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting in test task", ex);
        }
    }
}
