# Codex — Start Here

You are implementing Enterprise Agent Studio from specification.

## Choose the correct starting mode

- Existing implementation present: inspect it and continue the next incomplete phase from `docs/rebuild/00-current-state.md`.
- Source tree lost: follow `docs/rebuild/16-disaster-recovery.md` and `docs/rebuild/17-ai-coding-prompts.md`.
- Brand-new implementation: implement **Phase 0 only** from `docs/18-implementation-plan.md`.

Always read root `AGENTS.md`, architecture, repository structure, and the complete rebuild guide before editing.

### Suggested prompt
> Implement Phase 0 from docs/18-implementation-plan.md. Follow AGENTS.md strictly. Create the Maven multi-module skeleton and React/TypeScript UI skeleton matching docs/03-repository-structure.md. Add build/test/lint configuration, Dockerfiles, local Compose dependencies and Helm skeletons. Verify builds locally and write exact commands/results to BUILD_STATUS.md. Do not add Embabel, Temporal or MCP feature code yet.

### Phase 1 prompt
> Implement Phase 1 only. Create core-domain and control-plane CRUD/lifecycle for AgentDefinition, immutable AgentVersion, Capability and ModelProfile. Use PostgreSQL/Flyway. Follow schemas/API specs. Add tests for tenant isolation and version immutability. Do not add Embabel, Temporal or MCP yet.

If an ambiguity appears, choose the option preserving adapter boundaries and record the decision in an ADR.

After each phase, copy `docs/rebuild/RECOVERY_PROGRESS.template.md` to `docs/rebuild/RECOVERY_PROGRESS.md` and record the commit, migrations, commands, limitations, and next prompt without secrets.
