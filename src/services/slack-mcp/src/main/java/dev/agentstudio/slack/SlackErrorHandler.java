package dev.agentstudio.slack;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class SlackErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class,SlackCallException.class})
    ResponseEntity<Map<String,Object>> handle(RuntimeException error){HttpStatus status=error instanceof SlackCallException?HttpStatus.BAD_GATEWAY:HttpStatus.BAD_REQUEST;return ResponseEntity.status(status).body(Map.of("status",status.value(),"error",status.getReasonPhrase(),"message",error.getMessage(),"timestamp",Instant.now().toString()));}
}
