# Models, bounded agent loop, and responses

## Model onboarding

Connections support OpenAI, Anthropic, and OpenAI-compatible endpoints. The backend owns validation: check URL/provider, authenticate with the submitted key, and verify the selected model before enabling Save. Store the encrypted key under the user/organization profile and return only masked metadata. Model profiles reference a connection and model ID; agents reference only the logical profile.

## Config-agent loop

The current loop is intentionally bounded:

1. Parse the chat/task input.
2. Apply the immutable version's initial prompt, registry-pinned skills/guardrails, and bounded durable session context.
3. Ask the configured model for the shortest capability plan only when needed. The plan may choose or reorder attached capabilities according to intent and dependencies, but may never call an unattached capability.
4. Reuse sufficiently matching grounded session evidence when safe; otherwise execute each selected attached capability at most once within the iteration/tool budget.
5. Cache eligible read-only tool results by tenant, capability, and exact input fingerprint.
6. Require MCP/tool evidence when tool capabilities are attached; do not let the model invent the requested data.
7. Synthesize a response from user input, prior context, and grounded tool results.
8. Persist bounded turns/evidence and emit semantic events, never hidden chain-of-thought.

## Response contract

The final response is a single ordered document with blocks such as Markdown, table, chart, image, code, file, and notice. Multiple chart blocks stay inside the same assistant message. Vega-Lite specs are bounded and downloadable as PNG, SVG, CSV, or JSON. Intermediate tool/model results remain trace events and are not shown as separate assistant answers.

Every model invocation records profile, provider, model, token usage, estimated cost, and call policy under the run ID.

Model pricing is profile configuration under `generationParameters`: `inputCostPerMillionUsd`, `outputCostPerMillionUsd`, `pricingSource`, and `pricingAsOf`. Cost is stored as integer micro-USD. A missing price must be visible as unpriced/zero rather than silently guessed from the provider name. Migration V37 supplies audited bootstrap rates for the two legacy model IDs and backfills their historical usage; new models require explicit rates.
