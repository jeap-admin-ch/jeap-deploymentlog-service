package ch.admin.bit.jeap.deploymentlog.docgen.service;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class DeploymentAsyncExecutorConfiguration {

    public static final String ASYNC_THREADPOOL_TASK_EXECUTOR = "asyncThreadpoolDocgenExecutor";

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder()
            .contextRegistry(ContextRegistry.getInstance())
            .build();

    @Bean(name = ASYNC_THREADPOOL_TASK_EXECUTOR)
    ThreadPoolTaskExecutor asyncDocGenThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        // Run max. 10 generating threads. Some threads might wait for a few seconds if the documentation generator
        // for a certain system is currently busy.
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(512);
        executor.setThreadNamePrefix("AsyncDocgen-");
        executor.setTaskDecorator(DeploymentAsyncExecutorConfiguration::passTracingContextToThread);
        executor.initialize();
        return executor;
    }

    private static Runnable passTracingContextToThread(Runnable runnable) {
        return SNAPSHOT_FACTORY.captureAll(new Object[0]).wrap(runnable);
    }
}
