package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlowStageConfigurationValidator implements ApplicationRunner {

    private final FlowStageProperties properties;
    private final FlowStageResolver flowStageResolver;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Deployment flow processing is disabled");
            return;
        }

        Environment startEnvironment = flowStageResolver.resolveStartEnvironment();
        Environment finalDeploymentEnvironment = flowStageResolver.resolveDefaultFinalDeploymentEnvironment();
        flowStageResolver.validateProductiveEnvironmentExists();
        log.info("Validated deployment flow stages: start environment '{}', default final deployment environment '{}'",
                startEnvironment.getName(), finalDeploymentEnvironment.getName());
    }
}
