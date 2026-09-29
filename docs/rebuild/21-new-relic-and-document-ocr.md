# New Relic and document OCR MCP integrations

## Registry artifacts

The approved registry publishes two MCP implementations and two skills:

- `new-relic-mcp`: `newrelic.nrql.query` and `newrelic.entities.search`;
- `new-relic-investigation`: bounded operational-investigation workflow;
- `document-ocr-mcp`: `document.ocr.extract` and `document.metadata.extract`;
- `document-ocr`: safe image, scanned PDF, PDF, and office-document extraction workflow.

These remain registry-driven. Agent Studio materializes capabilities and configuration forms only after **Sync → Pull → Promote**.

## New Relic configuration

Create a named integration profile with account ID, region, timeout, maximum rows, and a New Relic user API key. The API key is encrypted and passed to the isolated adapter as a credential header. The adapter permits only official regional NerdGraph HTTPS endpoints and read-only NRQL `SELECT` statements.

## OCR configuration

Deploy the document OCR adapter beside an isolated `apache/tika:4.0.0-full` service. Configure its internal Tika URL, allowed public source hosts, timeouts, and byte/output limits. Configure OCR languages and PDF page policy on the Tika service. Private, loopback, link-local, and unapproved URL sources are rejected.

Studio chat accepts one image or document attachment up to 10 MB. The browser sends its name, media type, and base64 bytes as transient run input. Run traces redact binary fields, and terminal run transitions remove base64 document fields from the persisted input. The adapter processes bytes in memory and returns extracted text, metadata, and a SHA-256 provenance digest without persisting the source.

## Agent binding

Attach the relevant skill and capabilities to an agent, select the named provider profile for each capability, and activate a new immutable version. The bounded planner recognizes explicit New Relic/NRQL and OCR/document intent but cannot call tools that are not attached.

## Verification

1. Verify provider health before enabling bindings.
2. Run a bounded NRQL query and confirm the run records the logical capability and sanitized request/response.
3. Upload a test image and scanned PDF with known text.
4. Confirm extracted text, truncation indicators, content type, filename, and hash.
5. Confirm private-network document URLs and non-SELECT NRQL are rejected.
