package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidSystemGroupNameException;

import java.util.Locale;

record SystemGroupName(String value, String normalized) {

    static SystemGroupName of(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidSystemGroupNameException();
        }
        String sanitizedName = name.trim();
        return new SystemGroupName(sanitizedName, sanitizedName.toLowerCase(Locale.ROOT));
    }
}
