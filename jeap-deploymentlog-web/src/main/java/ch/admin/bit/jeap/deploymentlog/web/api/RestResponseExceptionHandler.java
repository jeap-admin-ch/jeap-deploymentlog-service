package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.domain.exception.*;
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
