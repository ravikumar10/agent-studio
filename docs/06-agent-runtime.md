# 06 — Agent Runtime Specification

## Stable adapter contract
Conceptually:
```java
interface AgentRuntimeAdapter {
  boolean supports(RuntimeType type);
  AgentExecutionResult execute(AgentVersion agent, AgentExecutionRequest request, ExecutionContext context);
}
```

## Runtime types
`CONFIG`: generic platform agent.  
`EMBABEL`: managed Java worker using Embabel.  
`REMOTE_HTTP`: remote internal JSON contract.  
`REMOTE_A2A`: remote A2A v1 adapter.

## Lifecycle
Resolve version -> authorize -> build context -> execute adapter -> enforce budgets -> route all external calls via gateways -> validate output -> persist semantic events/usage -> return.

## Embabel rules
Preferred JVM agent framework for sophisticated managed agents, isolated in `runtime-embabel`. Do not persist Embabel-native domain objects in platform APIs. Governed tools route through Tool Gateway. Embabel does not own multi-day durability. Do not make platform A2A interoperability dependent on Embabel's A2A module.

## Python/runtime neutrality
Python agents execute out-of-process using internal HTTP+JSON initially. Framework semantics never enter core contracts.

## Delegation
Typed capability request with input schema, expected output schema, deadline and child budget. The model never selects arbitrary endpoint addresses.

## Hard stop controls
Harness enforces max model calls, tool calls, delegations/depth, tokens/cost, deadline and cancellation outside the prompt.

## Idempotency
Every external invocation has runId/taskId/invocationId. Side effects require idempotency or domain-safe semantics.
