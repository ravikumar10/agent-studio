package dev.agentstudio.runtime;

import dev.agentstudio.runtime.api.AgentExecutionResult;
import dev.agentstudio.runtime.api.AgentRuntimeAdapter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/runtime-executions")
class InternalWorkerController {
    private final List<AgentRuntimeAdapter> adapters;
    private final String token;

    InternalWorkerController(List<AgentRuntimeAdapter> adapters,
                             @Value("${runtime.worker-token:local-worker-token-change-me}") String token) {
        this.adapters = adapters;
        this.token = token;
    }

    @PostMapping
    AgentExecutionResult execute(@RequestHeader("X-Internal-Worker-Token") String supplied,
                                 @RequestBody IsolatedWorkerClient.WorkerInvocation invocation) {
        if (!constantTimeEquals(token, supplied)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid worker token");
        AgentRuntimeAdapter adapter = adapters.stream().filter(candidate -> candidate.supports(invocation.agent().runtimeType()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "worker has no compatible runtime adapter"));
        return adapter.execute(invocation.agent(), invocation.request(), invocation.context());
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null || expected.length() != supplied.length()) return false;
        int result = 0;
        for (int i = 0; i < expected.length(); i++) result |= expected.charAt(i) ^ supplied.charAt(i);
        return result == 0;
    }
}
