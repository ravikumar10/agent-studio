# Slack MCP adapter

Agent Studio includes a dedicated, Docker-deployable Spring Boot adapter for Slack. It follows the platform capability invocation contract: agents bind logical capability IDs, the runtime resolves a tenant-owned provider profile, and only the adapter communicates with Slack.

## Components

- `services/slack-mcp` implements `slack.messages.read` and `slack.messages.send`.
- `deploy/docker/slack-mcp.Dockerfile` builds a Java 21 runtime image.
- `deploy/compose/compose.yml` starts the service as `slack-mcp` on the internal Docker network.
- Flyway migrations `V26` through `V28` register the capabilities, bind existing Slack profiles, and route the operations to the local service.

The service exposes:

- `POST /tools/slack.messages.read`
- `POST /tools/slack.messages.send`
- `POST /tools/slack.verify`
- `GET /actuator/health`

## Provider configuration

Create a named integration profile with type `SLACK` and set:

- Workspace/team ID (metadata)
- Default channel ID, such as `C01234567`
- Bot token as the encrypted `botToken` credential
- Signing secret only when inbound Slack Events API support is enabled
- Runtime adapter URL `http://slack-mcp:8080` when running through the supplied Compose stack

The bot token requires Slack scopes appropriate to the operation. Sending normally requires `chat:write`; reading public channel history normally requires `channels:history`. Private channels use the corresponding private-channel scope. The bot must be a member of channels it cannot otherwise access.

## Secret flow

Credentials remain encrypted in the control-plane database. At invocation time, runtime-service decrypts the selected profile credentials and forwards them to the isolated adapter using internal integration headers. The adapter does not persist or log the token. API responses and execution events must never include credential headers.

## Invocation behavior

`slack.messages.send` accepts `channel` plus `text` or `message`. If no channel is supplied, the profile's `defaultChannel` is used. If no explicit text exists, the adapter uses the preceding pipeline result or the user prompt.

`slack.messages.read` accepts `channel` and an optional bounded `limit`, falling back to the configured default channel.

Pipeline ordering treats reads as retrieval and sends as terminal side effects. A database/report/chart agent therefore executes retrieval first, creates its response artifacts, and sends to Slack last. Sending is intentionally non-cacheable.

## Local build and verification

Run the full platform with:

```sh
./start.sh
```

Or rebuild only the affected services:

```sh
docker compose -f src/deploy/compose/compose.yml build slack-mcp runtime-service control-plane
docker compose -f src/deploy/compose/compose.yml up -d --no-deps slack-mcp runtime-service control-plane
docker compose -f src/deploy/compose/compose.yml exec -T slack-mcp wget -qO- http://localhost:8080/actuator/health
```

Connection verification calls Slack `auth.test`, validating both adapter reachability and the stored bot token without posting a message. A real read or send additionally depends on scopes, channel membership, and channel ID. Do not send a live verification message without explicit approval because it is an external side effect.
