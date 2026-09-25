# Models, bounded agent loop, and responses

## Model onboarding

Connections support OpenAI, Anthropic, and OpenAI-compatible endpoints. The backend owns validation: check URL/provider, authenticate with the submitted key, and verify the selected model before enabling Save. Store the encrypted key under the user/organization profile and return only masked metadata. Model profiles reference a connection and model ID; agents reference only the logical profile.

## Config-agent loop

The current loop is intentionally bounded:

1. Parse the chat/task input.
2. Ask the configured model for the shortest capability plan only when needed.
3. Execute each selected attached capability at most once within the iteration/tool budget.
4. Cache eligible read-only tool results by tenant, capability, and exact input fingerprint.
5. Require MCP/tool evidence when tool capabilities are attached; do not let the model invent the requested data.
6. Synthesize a response from user input and grounded tool results.
7. Emit semantic events, never hidden chain-of-thought.

## Response contract

The final response is a single ordered document with blocks such as Markdown, table, chart, image, code, file, and notice. Multiple chart blocks stay inside the same assistant message. Vega-Lite specs are bounded and downloadable as PNG, SVG, CSV, or JSON. Intermediate tool/model results remain trace events and are not shown as separate assistant answers.

Every model invocation records profile, provider, model, token usage, estimated cost, and call policy under the run ID.
