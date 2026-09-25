package dev.agentstudio.runtime.api;

import dev.agentstudio.domain.AgentVersion;

public interface AgentRuntimeAdapter {
    boolean supports(AgentVersion.RuntimeType runtimeType);
    AgentExecutionResult execute(AgentVersion agent, AgentExecutionRequest request, ExecutionContext context);
}
