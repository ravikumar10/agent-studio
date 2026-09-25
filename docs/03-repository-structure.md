# 03 — Repository Structure

```text
agent-studio/
├── AGENTS.md
├── README.md
├── pom.xml
├── libs/
│   ├── core-domain/
│   ├── runtime-api/
│   ├── capability-api/
│   ├── model-api/
│   ├── policy-api/
│   ├── observability-api/
│   └── test-fixtures/
├── services/
│   ├── control-plane/
│   ├── runtime-service/
│   ├── tool-gateway/
│   ├── model-gateway/
│   ├── agent-gateway/
│   └── temporal-worker/
├── runtimes/
│   ├── runtime-config/
│   ├── runtime-embabel/
│   ├── runtime-remote-http/
│   └── runtime-a2a/
├── providers/
│   ├── persistence-postgres/
│   ├── cache-redis/
│   ├── artifact-s3/
│   ├── artifact-minio/
│   ├── secrets-vault/
│   ├── secrets-kubernetes/
│   └── identity-oidc/
├── ui/studio-web/
├── mcp/example-logistics-mcp/
├── contracts/{schemas,openapi}/
├── deploy/{docker,compose,helm,profiles}/
└── examples/
```

## Boundaries
`core-domain` is pure Java and has no Spring/Embabel/Temporal/cloud dependencies.  
`runtime-api` contains stable runtime contracts.  
`control-plane` contains registries/lifecycle only.  
`runtime-service` owns run lifecycle/context/policy/orchestration entry.  
`tool-gateway` owns MCP/API invocation.  
`model-gateway` owns provider model selection/invocation.  
`agent-gateway` owns local/remote agent routing.  
`temporal-worker` contains workflows/activities.  
`runtime-embabel` is the only module allowed to depend on Embabel.

Each deployable service/managed worker builds an independent container image.
