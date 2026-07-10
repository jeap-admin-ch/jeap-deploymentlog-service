package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.domain.exception.*;
import ch.admin.bit.jeap.deploymentlog.jira.JiraUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestControllerAdvice
public class RestResponseExceptionHandler {

    @ExceptionHandler(DeploymentNotFoundException.class)
    public ResponseEntity<String> handleDeploymentNotFoundException(DeploymentNotFoundException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidDeploymentStateForUpdateException.class)
    public ResponseEntity<String> handleInvalidDeploymentStateForUpdateException(InvalidDeploymentStateForUpdateException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SystemNotFoundException.class)
    public ResponseEntity<String> handleSystemNotFoundException(SystemNotFoundException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EnvironmentNotFoundException.class)
    public ResponseEntity<String> handleEnvironmentNotFoundException(EnvironmentNotFoundException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ComponentNotFoundException.class)
    public ResponseEntity<String> handleComponentNotFoundException(ComponentNotFoundException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DeploymentPageNotFoundException.class)
    public ResponseEntity<String> handleDeploymentPageNotFoundException(DeploymentPageNotFoundException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(JiraUnavailableException.class)
    public ResponseEntity<String> handleJiraUnavailableException(JiraUnavailableException ex) {
        // ERROR level: jira being unavailable or rejecting the deployment log service itself is a problem
        // the platform team must check - unlike not found jira issues, which are reported to the pipeline
        // as a check result and logged at WARN level only.
        log.error(ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Passes rest client errors of synchronously called upstream systems through to the caller, e.g.
     * confluence errors when creating a blogpost via {@link BlogpostController}. Jira errors of the
     * ready-for-deploy check never reach this handler - they are wrapped in {@link JiraUnavailableException}.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleRestClientResponseException(RestClientResponseException ex) {
        String errorSummary = "Rest client request failed: %s %s %s %s".formatted(
                ex.getStatusCode().value(),
                ex.getStatusText(),
                ex.getMessage(),
                ex.getResponseBodyAsString(StandardCharsets.UTF_8));
        log.warn(errorSummary, ex);
        return new ResponseEntity<>(errorSummary, ex.getStatusCode());
    }

    @ExceptionHandler(AliasNameAlreadyDefinedException.class)
    public ResponseEntity<String> handleAliasAlreadyDefinedException(AliasNameAlreadyDefinedException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SystemNameAlreadyDefinedException.class)
    public ResponseEntity<String> handleSystemNameAlreadyDefinedException(SystemNameAlreadyDefinedException ex) {
        log.warn(ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
}
