package ch.admin.bit.jeap.deploymentlog.domain;

/**
 * Interface to be implemented by a persistence provider to access @{@link Component}s
 */
public interface ComponentRepository {

    Component save(Component component);
}
