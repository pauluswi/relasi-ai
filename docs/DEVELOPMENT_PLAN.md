# RELASI AI — Development Plan

> **Status:** Active roadmap; M0–M6 have implementation slices, and M7 is in progress. The primary showcase profile is mock-only and requires no database, Docker, network access, credentials, or external systems. Dates and effort estimates are intentionally omitted until team capacity is known.
> **Inputs:** [REQUIREMENT.md](REQUIREMENT.md) and [arc42.md](arc42.md).

## 1. Delivery Goal

Deliver a locally runnable banking CRM showcase where staff can manage synthetic customer and service data, use an AI assistant grounded in that data and bundled banking knowledge, inspect its execution trace, and explicitly confirm AI-assisted writes.

The default experience should start with one command on a developer machine with the supported Java runtime installed. All services are in-process; data is synthetic and held in memory; the app works offline. Prefer a modular monolith and bundled/static UI to minimize prerequisites. No database or container platform is part of the primary run path.

## 2. Run-Mode Principles

- **Mock by default:** CRM, LLM, NLP, embeddings/retrieval, payments, identity, audit, and MCP tool execution use deterministic local implementations.
- **No dependencies beyond the app runtime:** No database, Docker, network connection, API key, external identity provider, model server, or separate MCP server is needed.
- **One-command start:** A clean checkout should build and launch the complete showcase with a documented command. If frontend compilation needs Node, compile/bundle the static assets as part of the build so Node is not a runtime prerequisite.
- **Configuration stays small:** Safe defaults should make the app work immediately. Optional configuration uses one local config file or a small set of environment variables. Do not require secrets for mock mode.
- **Resettable state:** Seed synthetic fixtures deterministically on startup and provide a visible reset-to-demo-state action. Explain that in-memory changes and audit history reset on restart.
- **Interfaces preserve replaceability:** Keep provider and tool boundaries, but do not implement external adapters as a prerequisite to the showcase.
- **Offline acceptance:** All documented demo scenarios must pass with network access disabled.

## 3. Scope Priorities

### Required showcase capabilities

- Customer, contact, product, interaction, and case data with search and Customer 360.
- Case creation, assignment, lifecycle, history, and SLA display.
- Demo identities and role/customer-scope authorization enforced in the app.
- Deterministic NLP and mock LLM behind provider-neutral interfaces.
- Bundled knowledge corpus, in-process retrieval, source references, and abstention.
- MCP-compatible tool schemas and discovery backed by an in-process registry.
- Explicit confirmation, reauthorization, and audit for writes.
- Authorized AI execution trace, mock provider indicators, and repeatable synthetic fixtures.
- Dynamics-style and Salesforce-style CRM adapters sharing the same in-memory contract.
- One-command startup and all six demonstration scenarios without external dependencies.

### Deferred or excluded from the primary run mode

- Persistent database, Docker/Compose, cloud deployment, external identity, real MCP transport/server, and external model or CRM integrations.
- Real vector database, Redis, Kubernetes, payments, real customer data, autonomous customer communication, and production banking integrations.
- Leads, opportunities, and campaigns until the core cognitive CRM flows are complete.

## 4. Milestones

Each milestone leaves the app runnable in mock mode and maintains the no-dependency guarantee.

### M0 — Zero-setup foundation and contracts — started

**Work:**

- Confirm supported Java runtime and build command. Choose a UI packaging approach that needs no separate runtime service.
- Define the initial customer, contact, product, case, interaction, knowledge, audit, and trace models.
- Define application API/error contracts, correlation IDs, and provider interfaces.
- Define seeded demo users, roles, and customer scopes; use in-process demo authentication without an identity server.
- Define deterministic fixture and reset conventions, and document which data resets on restart.
- Choose an in-process tool registry with MCP-compatible schemas; no network MCP server for the default profile.
- Choose lexical or deterministic mock-vector retrieval behind a replaceable interface.

**Exit:** A clean checkout builds and starts with one documented command and no database, Docker, network, credentials, or service installation.

**Implemented foundation:** `./run.sh` compiles with JDK 25 and starts a loopback-only JDK HTTP server. It serves a bundled page and exposes health, demo-user, and selected-session endpoints. Demo roles and customer scopes are seeded in process. `RELASI_PORT` is the only runtime setting; 8080 is the default. Maven, Node, database, Docker, and network access are not needed. CRM records and assistant features remain for later milestones.

### M1 — Customer 360 vertical slice — started

**Work:**

- Implement in-memory repositories for customers, contacts, products, interactions, and cases with validation and search.
- Seed representative synthetic customers, segments, products, account summaries, and risk indicators.
- Build Customer 360 and a responsive customer search/profile UI.
- Enforce role and customer scope on the server; mask sensitive fields in UI and traces.
- Implement the first provider-neutral CRM contract and a Dynamics-style in-memory adapter.

**Demo/exit:** Start the app, choose a seeded user, search a customer, and inspect profile, products, interaction history, and cases. Restarting restores the original fixtures; a visible reset control can be added with mutable workflows.

**Implemented slice:** Four deterministic synthetic Customer 360 fixtures are served by a provider-neutral `CrmAdapter` and a Dynamics-style in-memory implementation. Search is limited to each demo user's customer scope and returns summary fields; detail requests apply the same scope and return `404` for unknown or unauthorized IDs. The bundled UI provides identity selection, search, profile, contacts, products, interactions, and case snapshots. Registration, profile updates, status management, reset controls, and broader verification remain outstanding.

### M2 — Case and interaction operations — started

**Work:**

- Implement supported case categories, priorities, statuses, assignment, SLA, escalation, resolution, and event history in memory.
- Build service queues, case detail, assignment, status updates, and interaction timelines.
- Support all required simulated channels and optional interaction-to-case links.
- Audit case writes and validate lifecycle transitions.

**Demo/exit:** A service officer reviews a customer, creates and assigns a case, updates status, and sees the case timeline and related interactions.

**Implemented slice:** In-memory case and interaction services provide seeded history, validated creation, status transitions, assignment, overdue SLA escalation, customer-linked interaction recording, and append-only event history. Writes produce in-memory audit events; supervisor/administrator access is required for assignment, escalation, and audit viewing. Customer service officers can create cases, change status, and record interactions. Every operation checks the user's customer scope. The UI exposes these workflows on the Customer 360 page. Queue filtering and broader verification remain to be completed.

### M3 — Mock AI and NLP — started

**Work:**

- Implement deterministic, in-process LLM and NLP providers behind interfaces.
- Classify required intents, sentiment, and Indonesian/English language; extract required entities.
- Add a customer-aware assistant UI and typed response model.
- Validate structured output, show mock-provider status, and distinguish errors from abstention.

**Demo/exit:** The payment complaint example returns repeatable intent, sentiment, amount, date, suggested category, and priority with a visible NLP trace.

**Implemented slice:** `POST /api/v1/assistant/respond` runs deterministic intent, sentiment, language, and entity extraction, then calls the mock LLM adapter. It can summarize an in-scope selected customer's products, cases, and recent interactions. The response includes typed NLP results, the mock-provider identifier, and per-stage trace timing; the bundled UI displays the answer, extracted fields, and trace. This service-assistance path does not verify transactions; policy questions use the separate M4 RAG flow.

### M4 — Knowledge and RAG — started

**Work:**

- Bundle versioned synthetic banking documents as application resources or fixtures, including BI-FAST handling.
- Implement chunking, mock embeddings if useful, local lexical/deterministic retrieval, ranking, thresholds, and context assembly.
- Return evidence and generated answer as separate fields with stable source references.
- Add a clear abstention when evidence is insufficient and include retrieval stages in the trace.

**Demo/exit:** BI-FAST question is answered with source references; unsupported questions abstain without invented citations. This works offline.

**Implemented slice:** Four synthetic banking guidance documents are bundled as versioned Markdown resources. Startup parses their metadata, chunks their content, and indexes them in memory. Deterministic lexical retrieval ranks passages and returns stable document/chunk references, snippets, and relevance scores. Policy/procedure requests pass bounded retrieved passages to the mock LLM; the response keeps citations separate from generated text. If retrieval returns no passage above the threshold, generation is skipped and the assistant abstains. The UI lists the corpus and displays citations and excerpts. Document upload, editing, and richer retrieval evaluation remain future work.

### M5 — MCP-compatible tools and confirmed writes — implemented; verification pending

**Work:**

- Implement in-process tool discovery/invocation using MCP-compatible definitions and JSON schemas.
- Provide `search_customer`, `get_customer_360`, `get_customer_cases`, `get_payment_status`, `search_knowledge`, `create_service_case`, `update_case_status`, `assign_case`, and `get_customer_products`.
- Apply authorization, schema validation, read/write classification, and audit at the tool boundary.
- Add case proposal, review, and confirmation flow. Bind confirmation to the exact proposal and reauthorize at execution.
- Add an MCP/tool explorer that shows the schemas and mock execution results.

**Demo/exit:** Read tools operate within the user's scope. A case is not created until the authorized user confirms the displayed proposal; confirmation and result are auditable.

**Implemented slice:** The in-process registry discovers all ten tools, including the nine initial required tool names and a scope-limited unresolved payment complaint search, with MCP-compatible JSON schemas. It validates required/enum/length/unknown fields, separates read and write operations, applies role and customer-scope checks, and audits discovery and invocation. Write calls prepare immutable previews and issue five-minute, actor-bound, one-use confirmation tokens; execution rechecks authorization and uses only the stored arguments. The tool explorer invokes reads and confirmed writes. Imperative assistant case-creation requests prepare a case preview through `create_service_case`; no record changes before confirmation. Remote MCP transport remains outside the zero-dependency run mode.

### M6 — Interchangeable mocks and execution trace — implemented; verification pending

**Work:**

- Add Salesforce-style adapter implementing the same in-memory CRM contract as the Dynamics-style adapter.
- Demonstrate switching adapters without changing domain or UI behavior.
- Complete trace display for input, NLP, retrieval/citations, model call, tool discovery/calls, results, final answer, and duration.
- Apply role controls and masking to trace content; show all integrations as mock/local.

**Demo/exit:** Adapter switching preserves outcomes; authorized users can inspect the full trace, and restricted customer data stays out of unauthorized results.

**Implemented slice:** `RELASI_CRM_ADAPTER` selects the Dynamics-style or Salesforce-style in-memory adapter profile; both expose the same provider-neutral contract and deterministic fixtures. The health endpoint and UI identify the selected profile. Assistant traces now identify the local CRM profile and include explicit skipped stages for unused tools plus a final-response event; assistant-prepared case proposals include the adapter and confirmation outcome stages. Trace details contain only request metadata and authorized customer identifiers, not contact or account secrets.

### M7 — Showcase polish and release readiness — started

**Work:**

- Provide one-command start, clear startup errors, visible reset action, and reliable reset on restart.
- Document supported runtime, launch command, seeded users, configuration, sample prompts, expected results, and troubleshooting.
- Add automated checks for domain behavior, authorization, adapters, retrieval/source attribution, tool schemas, write confirmation, and end-to-end scenarios.
- Verify all six requirement scenarios in offline mode from a fresh checkout.
- Review performance, accessibility, masking, and error behavior for the local showcase.

**Demo/exit:** A fresh supported machine can launch the whole app without Docker, a database, network access, credentials, or separately running services. All six scenarios are repeatable from seeded data.

**Implemented slice:** `./run.sh` now reports missing or unsupported JDK versions with an actionable message; invalid port configuration is reported clearly, and an occupied port suggests changing `RELASI_PORT`. Supervisors and administrators can reset mutable in-memory cases, interactions, pending confirmations, and audit history to seeded state from the UI. README documentation now lists demo identities/scopes, adapter and port configuration, sample prompts, expected outcomes, and common startup/access troubleshooting. Automated tests, fresh-checkout scenario verification, accessibility review, and performance review remain pending.

**Dashboard slice:** The bundled UI provides a responsive, scope-aware operational dashboard with customer/case/interaction KPIs, case-status and customer-segment charts, a prioritized active-case queue, and recent interactions. It reads only through existing authorization-checked endpoints and refreshes after identity changes and case/interaction writes. The data is synthetic and calculated in the browser; no analytics service or external chart library is required.

**Scenario slice:** The assistant now produces rule-based, advisory next-best-action guidance from authorized profile, case, product, and interaction context, with a synthetic product review source. A new read-only MCP tool searches unresolved payment complaints only within the caller's customer scope; the assistant groups matches by customer and returns structured case summaries. Synthetic unresolved payment cases and interactions make both flows repeatable. Automated tests and full six-scenario verification remain pending.

### Optional M8 — Sales and campaign features

Add synthetic leads, opportunities, campaigns, campaign responses, and product recommendations after the core CRM + AI showcase is stable. Keep recommendations advisory and fixture-backed.

## 5. Demonstration Scenario Mapping

| Requirement scenario | Milestones | Expected outcome |
|---|---|---|
| 1. AI Customer 360 Assistant | M1, M3, M5 | Authorized CRM context summarized with supporting data references. |
| 2. Intelligent Complaint Classification | M3 | Intent, entities, sentiment, category, and priority shown deterministically. |
| 3. AI Knowledge Assistant | M4 | Grounded answer with local source references, or abstention. |
| 4. AI-Assisted Case Creation | M2, M5 | Reviewable proposal, explicit confirmation, tool execution, and case reference. |
| 5. Next Best Action | M1, M3, M4, M7 | Advisory recommendation with evidence from profile, products, interactions, and synthetic product guidance. |
| 6. Conversational Banking CRM | M2, M3, M5, M7 | In-scope unresolved payment complaints summarized from local fixtures. |

M6 and M7 make the trace, adapter comparison, and zero-setup behavior visible across all scenarios.

## 6. Cross-Cutting Practices

| Concern | Practice |
|---|---|
| Authorization | Seed demo identities/roles; enforce customer scope in REST, assistant orchestration, and every tool call. |
| Synthetic data | Generate and bundle fixtures only; never require or accept real customer data for the showcase. |
| State/reset | Keep repositories replaceable; expose reset-to-known-state; clearly state that in-memory changes and audit history do not survive restart. |
| Audit and trace | Keep local and in-memory for the demo; mask content, scope access, and include actor, action, result, and correlation ID. |
| Configuration | Mock mode and safe fixture set are defaults; no secrets or network settings are needed. Fail fast with actionable messages for invalid optional settings. |
| Failure behavior | Distinguish unavailable mock/provider, denied access, invalid input, and insufficient evidence. CRM screens remain usable if AI is disabled. |
| UI delivery | Prefer one app process and bundled assets so no separate dev server is needed for demo runtime. |

## 7. Planning and Definition of Done

For each feature, record requirement references, acceptance criteria, data/contract changes, authorization/audit/masking impacts, automated verification, and documentation updates.

Prioritize in this order: zero-setup foundation and security; Customer 360/cases/interactions; deterministic NLP/AI; grounded RAG; authorized MCP tools and confirmed writes; trace and adapter comparison; optional sales capabilities.

A feature is done when it meets its acceptance criteria, has server-side authorization and validation, audits sensitive operations, uses deterministic synthetic fixtures, has appropriate automated verification, updates relevant documentation, and works offline in the default profile without a database, Docker, or external service.

## 8. Decisions Closed in M0

1. **Runtime:** JDK 25; `./run.sh` compiles and launches without Maven or dependency downloads.
2. **UI packaging:** Static HTML served by the same JDK HTTP server process.
3. **Demo identity:** Seeded local identities selected with `X-Demo-User`; role and customer-scope mapping is in-process. This is explicitly a development convenience, not production authentication.
4. **Network boundary:** Bind to `127.0.0.1` by default. `RELASI_PORT` configures the port.

Retrieval implementation and reset authorization remain open until their respective milestones.

