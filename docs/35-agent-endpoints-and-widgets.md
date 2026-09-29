# Published agent endpoints and widgets

Agent Studio can expose an active agent as a trigger API, an embeddable chat widget, or both. Each agent receives one random, stable public ID. Publishing and activating a newer immutable version moves that public ID to the new version, while existing runs remain pinned to their original version.

## Configure in Studio

1. Open **Agents**, choose **Configure**, and locate **Endpoint and widget publishing**.
2. Enable publishing and select **Trigger API endpoint**, **Embeddable chat widget**, or both.
3. Choose `PUBLIC` access or `API_KEY` for endpoint-only integrations. Widgets are public because browser-delivered secrets cannot be protected.
4. Set allowed origins. Use explicit HTTPS origins in production; `*` is intended only for intentionally public agents.
5. Save the new version. Reopen its configuration to copy the generated endpoint and widget URLs.

Only the active version resolves publicly. Activating a replacement preserves the URL and routes new calls to the replacement. Deleting or disabling publishing removes public access.

## Trigger API

Synchronous request:

```bash
curl -X POST https://studio.example.com/api/public/v1/agents/AGENT_PUBLIC_ID/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-API-Key: configured-key' \
  -d '{"message":"Summarize the latest report","sessionId":"customer-42","async":false}'
```

Structured input can be supplied as `input` instead of `message`. A missing `sessionId` creates a new session. Reuse the same session ID for a conversational agent.

For asynchronous calls, set `async` to `true`. The response is `202 Accepted` with a `runId`. Poll:

```text
GET /api/public/v1/agents/AGENT_PUBLIC_ID/runs/RUN_ID
```

The public status route verifies that the run belongs to the exact published agent version; it cannot retrieve another agent's run.

## Widget

```html
<iframe
  src="https://studio.example.com/widget/AGENT_PUBLIC_ID"
  title="Support agent"
  style="width: 420px; height: 640px; border: 0"
></iframe>
```

Every widget load creates a random browser session. Messages in that widget reuse the session, while another browser or widget instance remains isolated. For explicit origins, `Content-Security-Policy: frame-ancestors` and CORS responses are derived from the configured allow-list. The intentionally public `*` mode omits `frame-ancestors`, allowing local-file previews and other opaque embedding origins.

If the pinned agent has at least one `document.*` capability, the widget includes an image/document picker. Files are encoded into `filename`, `contentType`, and `base64Content` input fields with a 15 MB browser-side limit, then processed through the normal configured OCR/document provider. The widget does not itself parse documents.

## Runtime and observability

Public requests do not bypass the platform. They resolve the tenant and pinned version internally, then invoke the normal runtime service with bounded model/tool budgets. Runs, semantic events, MCP calls, model usage, failures, and costs appear in the existing **Runs** and **Observability** views. Concurrent invocations receive unique run IDs and are processed by the version's selected in-process, Docker, or Kubernetes placement.

The database stores only a SHA-256 API-key verifier, never the plaintext key. Rotating a key means saving a new value in the publishing configuration.

## Current production-hardening gaps

- Opaque public URLs are identifiers, not authentication.
- Widgets intentionally support only public access; never embed a privileged API key in browser HTML.
- Add ingress rate limits, quotas, bot/abuse controls, audit retention, external identity, and managed key rotation before broad internet exposure.
- Public calls currently use fixed bounded runtime budgets. Organization-specific public budgets and billing policy remain future work.
