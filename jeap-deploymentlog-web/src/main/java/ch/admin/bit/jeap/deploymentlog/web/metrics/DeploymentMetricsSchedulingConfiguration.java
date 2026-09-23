package ch.admin.bit.jeap.deploymentlog.web.metrics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class DeploymentMetricsSchedulingConfiguration {

    static final String METRICS_TASK_SCHEDULER = "deploymentLogMetricsTaskScheduler";

    @Bean(METRICS_TASK_SCHEDULER)
    ThreadPoolTaskScheduler deploymentLogMetricsTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("deploymentlog-metrics-");
        return scheduler;
    }
}
