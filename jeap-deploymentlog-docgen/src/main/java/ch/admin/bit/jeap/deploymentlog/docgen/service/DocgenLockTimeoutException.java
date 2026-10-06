package ch.admin.bit.jeap.deploymentlog.docgen.service;

public final class DocgenLockTimeoutException extends IllegalStateException {
    public DocgenLockTimeoutException(String lockName) {
        super("Unable to acquire lock " + lockName + " before timeout");
    }
}
