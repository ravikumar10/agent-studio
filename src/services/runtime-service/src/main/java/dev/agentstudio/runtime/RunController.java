package dev.agentstudio.runtime;

import dev.agentstudio.runtime.RunModels.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequestMapping("/api/v1/runs")
public class RunController {
    private final RunService service; private final RunStore store; private final RunEventStream stream;
    public RunController(RunService service,RunStore store,RunEventStream stream){this.service=service;this.store=store;this.stream=stream;}
    @GetMapping public List<RunView> list(@RequestHeader("X-Tenant-Id")String tenant){return store.list(required(tenant));}
    @PostMapping public ResponseEntity<RunView> start(@RequestHeader("X-Tenant-Id")String tenant,@RequestBody StartRunRequest request){RunView v=service.start(required(tenant),request);return ResponseEntity.status(request.async()?HttpStatus.ACCEPTED:HttpStatus.OK).body(v);}
    @GetMapping(path="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String tenantId){return stream.subscribe(required(tenantId));}
    @GetMapping("/{id}") public RunView get(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String id){return store.get(required(tenant),id);}
    @PostMapping("/{id}/cancel") public ResponseEntity<Void> cancel(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String id){return service.cancel(required(tenant),id)?ResponseEntity.accepted().build():ResponseEntity.status(HttpStatus.CONFLICT).build();}
    @GetMapping("/{id}/events") public List<EventView> events(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String id){return store.events(required(tenant),id);}
    private String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("X-Tenant-Id is required");return v;}
}
