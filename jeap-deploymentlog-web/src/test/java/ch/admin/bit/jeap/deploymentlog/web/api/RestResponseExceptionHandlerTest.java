package ch.admin.bit.jeap.deploymentlog.web.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RestResponseExceptionHandlerTest {

    @Test
    void handleRestClientResponseException() {
        RestResponseExceptionHandler restResponseExceptionHandler = new RestResponseExceptionHandler();
        RestClientResponseException ex = new RestClientResponseException("msg", 400, "text", null, "body".getBytes(UTF_8), UTF_8);
        ResponseEntity<String> responseEntity = restResponseExceptionHandler.handleRestClientResponseException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals("Rest client request failed: 400 text msg body", responseEntity.getBody());
    }

}