package dev.agentstudio.control;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> badRequest(Exception e) { return Map.of("timestamp", Instant.now(), "status", 400, "error", e.getMessage()); }
    @ExceptionHandler(DuplicateKeyException.class) @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> conflict(Exception e) { return Map.of("timestamp", Instant.now(), "status", 409, "error", "resource already exists"); }
}
