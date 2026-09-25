# 15 — Deployment and Portability

Baseline: container + Kubernetes + Helm. Support AKS, EKS, GKE, OpenShift, generic/on-prem Kubernetes.

## Topologies
1. Central platform: all components in platform cluster.
2. Central control plane + client execution plane.
3. Fully self-hosted/on-prem.
4. Hybrid.

## Client runtime edge chart
Contains lightweight runtime/gateway, Agent Gateway, optional edge Tool Gateway, model adapter for local models, policy cache/enforcer, telemetry exporter and optional Temporal worker. Does not require full Studio/control plane.

## Infrastructure SPIs
ArtifactStore: S3/Azure Blob/GCS/MinIO.  
SecretProvider: Kubernetes/Vault/cloud managers.  
Identity: OIDC.  
EventBus optional adapters.

## Local/offline profile
PostgreSQL, Redis, Temporal, MinIO, optional local model endpoint and sample MCP server.

## Air-gapped
Private registry, offline Helm, local model option, internal MCP, internal identity/artifact store, no public-service dependency.
