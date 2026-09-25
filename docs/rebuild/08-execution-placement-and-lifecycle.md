# Execution placement and lifecycle

## Placement

- `IN_PROCESS`: lightweight CONFIG agents only.
- `DOCKER`: isolated ephemeral worker using `agent-studio/runtime-worker:local`.
- `KUBERNETES`: deployment intent requiring an applied workload adapter.
- `AUTO`: CONFIG chooses in-process; custom/code agents choose Docker.

Arbitrary developer JARs must never load into the control plane or shared runtime JVM.

## Docker lifecycle

Runtime creates a uniquely named container labelled with run ID and ephemeral status, starts it, waits for readiness, invokes the stable contract, and always stops/removes it in `finally`. Cancelling a run terminates the container and cancels the virtual-thread task. Closing an active workspace sends cancellation. Terminal jobs leave no running worker.

## Kubernetes lifecycle

Control plane stores portable YAML plans and optional CronJob intent. Cloud details live in deployment adapters/configuration. AKS workload identity annotations may be generated without leaking Azure SDK types into core contracts. Real apply/status/rollback must use governed Kubernetes capabilities and credentials.

## Stable invocation contract

All runtime adapters receive tenant/user/scopes, run ID, pinned version, deadline, budget, and input. Out-of-process frameworks return the same result/usage/semantic-event contract.
