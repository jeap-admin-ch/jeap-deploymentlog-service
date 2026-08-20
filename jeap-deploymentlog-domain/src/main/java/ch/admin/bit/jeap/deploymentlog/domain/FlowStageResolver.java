package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidFlowStageConfigurationException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidFlowStageRequestException;
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
public class FlowStageResolver {

    private final EnvironmentRepository environmentRepository;
    private final FlowStageProperties properties;

    public Environment resolveStartEnvironment() {
        return resolveConfiguredOrFlagged(properties.getStartEnvironment(), Environment::isDevelopment,
                "start environment", "development=true");
    }

    public Environment resolveDefaultFinalDeploymentEnvironment() {
        return resolveConfiguredOrFlagged(properties.getDefaultFinalDeploymentEnvironment(), Environment::isProductive,
                "default final deployment environment", "productive=true");
    }

    public Environment resolveEffectiveFinalDeploymentEnvironment(Collection<String> requestedEnvironmentNames) {
        if (requestedEnvironmentNames == null || requestedEnvironmentNames.isEmpty()) {
            return resolveDefaultFinalDeploymentEnvironment();
        }

        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String requestedName : requestedEnvironmentNames) {
            if (requestedName == null || requestedName.isBlank()) {
                throw new InvalidFlowStageRequestException("finalDeploymentEnvironments must not contain blank values");
            }
            normalizedNames.add(normalize(requestedName));
        }

        List<Environment> environments = new ArrayList<>();
        List<String> unknownNames = new ArrayList<>();
        for (String name : normalizedNames) {
            environmentRepository.findByName(name)
                    .ifPresentOrElse(environments::add, () -> unknownNames.add(name));
        }
        if (!unknownNames.isEmpty()) {
            throw new InvalidFlowStageRequestException(
                    "Unknown final deployment environment(s): " + String.join(", ", unknownNames));
        }

        int highestOrder = environments.stream()
                .map(Environment::getStagingOrder)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<Environment> highestEnvironments = environments.stream()
                .filter(environment -> environment.getStagingOrder() == highestOrder)
                .toList();
        if (highestEnvironments.size() > 1) {
            throw new InvalidFlowStageRequestException(
                    "Ambiguous final deployment environments with stagingOrder %d: %s".formatted(
                            highestOrder,
                            highestEnvironments.stream().map(Environment::getName).sorted().toList()));
        }
        return highestEnvironments.getFirst();
    }

    private Environment resolveConfiguredOrFlagged(String configuredName,
                                                    Predicate<Environment> fallbackPredicate,
                                                    String purpose,
                                                    String fallbackDescription) {
        if (configuredName != null) {
            if (configuredName.isBlank()) {
                throw new InvalidFlowStageConfigurationException(
                        "Configured %s must not be blank".formatted(purpose));
            }
            String normalizedName = normalize(configuredName);
            return environmentRepository.findByName(normalizedName)
                    .orElseThrow(() -> new InvalidFlowStageConfigurationException(
                            "Configured %s '%s' does not exist".formatted(purpose, normalizedName)));
        }

        List<Environment> candidates = new ArrayList<>();
        environmentRepository.findAll().forEach(environment -> {
            if (fallbackPredicate.test(environment)) {
                candidates.add(environment);
            }
        });
        if (candidates.size() != 1) {
            throw new InvalidFlowStageConfigurationException(
                    "Cannot determine %s: expected exactly one environment with %s, found %d"
                            .formatted(purpose, fallbackDescription, candidates.size()));
        }
        return candidates.getFirst();
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
