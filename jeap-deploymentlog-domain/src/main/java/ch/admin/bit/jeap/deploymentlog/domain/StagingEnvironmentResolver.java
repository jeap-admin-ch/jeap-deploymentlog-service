package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidStagingConfigurationException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidStagingRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class StagingEnvironmentResolver {

    private final EnvironmentRepository environmentRepository;
    private final StagingProperties properties;

    public Environment resolveStartEnvironment() {
        return resolveConfiguredOrFlagged(properties.getStartEnvironment(), Environment::isDevelopment,
                "start environment", "development=true");
    }

    public Environment resolveDefaultFinalDeploymentEnvironment() {
        return resolveConfiguredOrFlagged(properties.getDefaultFinalDeploymentEnvironment(), Environment::isProductive,
                "default final deployment environment", "productive=true");
    }

    public void validateFinalDeploymentEnvironments(Collection<String> requestedEnvironmentNames) {
        if (requestedEnvironmentNames == null || requestedEnvironmentNames.isEmpty()) {
            return;
        }

        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String requestedName : requestedEnvironmentNames) {
            if (requestedName == null || requestedName.isBlank()) {
                throw new InvalidStagingRequestException("finalDeploymentEnvironments must not contain blank values");
            }
            normalizedNames.add(normalize(requestedName));
        }

        List<String> unknownNames = new ArrayList<>();
        for (String name : normalizedNames) {
            if (environmentRepository.findByName(name).isEmpty()) {
                unknownNames.add(name);
            }
        }
        if (!unknownNames.isEmpty()) {
            throw new InvalidStagingRequestException(
                    "Unknown final deployment environment(s): " + String.join(", ", unknownNames));
        }
    }

    public List<Environment> relevantEnvironments() {
        Environment start = resolveStartEnvironment();
        Environment end = resolveDefaultFinalDeploymentEnvironment();
        if (start.getStagingOrder() > end.getStagingOrder()) {
            throw new InvalidStagingConfigurationException("Start stage must not follow end stage");
        }
        List<Environment> stages = new ArrayList<>();
        environmentRepository.findAll().forEach(stage -> {
            if (stage.getStagingOrder() >= start.getStagingOrder()
                    && stage.getStagingOrder() <= end.getStagingOrder()) {
                stages.add(stage);
            }
        });
        stages.sort(Comparator.comparingInt(Environment::getStagingOrder));
        for (int i = 1; i < stages.size(); i++) {
            if (stages.get(i - 1).getStagingOrder() == stages.get(i).getStagingOrder()) {
                throw new InvalidStagingConfigurationException("Relevant stages must have distinct stagingOrder values");
            }
        }
        return List.copyOf(stages);
    }

    private Environment resolveConfiguredOrFlagged(String configuredName,
                                                    Predicate<Environment> fallbackPredicate,
                                                    String purpose,
                                                    String fallbackDescription) {
        if (configuredName != null) {
            if (configuredName.isBlank()) {
                throw new InvalidStagingConfigurationException(
                        "Configured %s must not be blank".formatted(purpose));
            }
            String normalizedName = normalize(configuredName);
            return environmentRepository.findByName(normalizedName)
                    .orElseThrow(() -> new InvalidStagingConfigurationException(
                            "Configured %s '%s' does not exist".formatted(purpose, normalizedName)));
        }

        List<Environment> candidates = new ArrayList<>();
        environmentRepository.findAll().forEach(environment -> {
            if (fallbackPredicate.test(environment)) {
                candidates.add(environment);
            }
        });
        if (candidates.size() != 1) {
            throw new InvalidStagingConfigurationException(
                    "Cannot determine %s: expected exactly one environment with %s, found %d"
                            .formatted(purpose, fallbackDescription, candidates.size()));
        }
        return candidates.getFirst();
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
