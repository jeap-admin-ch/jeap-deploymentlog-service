package ch.admin.bit.jeap.deploymentlog.docgen.service;

final class DocgenLockTimeoutException extends IllegalStateException {
    DocgenLockTimeoutException(String lockName) {
        super("Unable to acquire lock " + lockName + " before timeout");
    }
}
