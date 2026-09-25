# Codex — Start Here

You are implementing Enterprise Agent Studio from specification.

## First task
Implement **Phase 0 only** from `docs/18-implementation-plan.md`. Read root `AGENTS.md`, architecture and repository structure first. Do not implement business features yet.

### Suggested prompt
> Implement Phase 0 from docs/18-implementation-plan.md. Follow AGENTS.md strictly. Create the Maven multi-module skeleton and React/TypeScript UI skeleton matching docs/03-repository-structure.md. Add build/test/lint configuration, Dockerfiles, local Compose dependencies and Helm skeletons. Verify builds locally and write exact commands/results to BUILD_STATUS.md. Do not add Embabel, Temporal or MCP feature code yet.

### Phase 1 prompt
> Implement Phase 1 only. Create core-domain and control-plane CRUD/lifecycle for AgentDefinition, immutable AgentVersion, Capability and ModelProfile. Use PostgreSQL/Flyway. Follow schemas/API specs. Add tests for tenant isolation and version immutability. Do not add Embabel, Temporal or MCP yet.

If an ambiguity appears, choose the option preserving adapter boundaries and record the decision in an ADR.
