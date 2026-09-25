package dev.agentstudio.sample;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class SampleErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,Object>> badRequest(IllegalArgumentException error) {
        return response(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Map<String,Object>> downstream(RestClientResponseException error) {
        String detail=error.getResponseBodyAsString();
        return response(HttpStatus.BAD_GATEWAY, detail.isBlank()?error.getMessage():detail);
    }

    private ResponseEntity<Map<String,Object>> response(HttpStatus status,String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status",status.value(),"error",status.getReasonPhrase(),
                "message",message==null?"unknown error":message,"timestamp",Instant.now().toString()));
    }
}
