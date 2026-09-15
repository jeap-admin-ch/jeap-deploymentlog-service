package ch.admin.bit.jeap.deploymentlog.docgen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeploymentAsyncExecutorConfiguration {

    public static final String ASYNC_THREADPOOL_TASK_EXECUTOR = "asyncThreadpoolDocgenExecutor";

    @Bean(name = ASYNC_THREADPOOL_TASK_EXECUTOR)
    DocgenTaskDispatcher docgenTaskDispatcher(
            @Value("${jeap.deploymentlog.documentation-generator.async.queue-capacity:512}") int queueCapacity,
            @Value("${jeap.deploymentlog.documentation-generator.async.live-task-burst:10}") int liveTaskBurst) {
        return new DocgenTaskDispatcher(queueCapacity, liveTaskBurst);
    }
}
