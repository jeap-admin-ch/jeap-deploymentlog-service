package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagingConfigurationValidator implements ApplicationRunner {

    private final StagingProperties properties;
    private final StagingEnvironmentResolver stagingEnvironmentResolver;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Deployment staging processing is disabled");
            return;
        }

        Environment startEnvironment = stagingEnvironmentResolver.resolveStartEnvironment();
        Environment finalDeploymentEnvironment = stagingEnvironmentResolver.resolveDefaultFinalDeploymentEnvironment();
        stagingEnvironmentResolver.relevantEnvironments();
        log.info("Validated deployment stages: start environment '{}', default final deployment environment '{}'",
                startEnvironment.getName(), finalDeploymentEnvironment.getName());
    }
}
