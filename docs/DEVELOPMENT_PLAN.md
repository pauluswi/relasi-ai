# RELASI AI — Development Plan

> **Status:** Proposed implementation roadmap. No schedule or effort estimates are assigned because team capacity and delivery dates have not been defined.
> **Inputs:** [REQUIREMENT.md](REQUIREMENT.md) and [arc42.md](arc42.md).

## 1. Delivery Goal

Deliver a locally runnable showcase that lets staff manage synthetic banking customers and service cases, use an AI assistant grounded in CRM and banking knowledge, inspect how the assistant reached its answer, and safely confirm AI-assisted case creation.

The plan favors a complete, demonstrable vertical slice before expanding into optional sales capabilities or production-oriented infrastructure. Begin with a modular monolith and API boundaries; split deployable services only when a concrete demonstration or operational need justifies it.

## 2. Scope Priorities

### Must deliver for the showcase

- Synthetic customer, contact, product, interaction, and case data.
- Customer search and Customer 360 with interaction and case history.
- Case creation and lifecycle operations, including assignment, priority, status, history, and SLA display.
- Authentication and role/customer-scope authorization.
- Deterministic mock LLM and structured NLP abstraction.
- Knowledge ingestion and RAG answers with source references and abstention behavior.
- MCP tool discovery and invocation with validation, authorization, audit, and explicit confirmation for writes.
- AI execution trace visible to authorized users.
- Docker Compose local setup and repeatable demonstration scenarios.
- Dynamics-style and Salesforce-style CRM adapters behind a shared integration contract.

### Defer until the core showcase works

- Leads, opportunities, campaigns, and campaign response tracking.
- Real LLM provider integrations, real CRM connectivity, real vector database, Redis, and Kubernetes.
- Production banking integrations, real customer information, payment execution, autonomous customer contact, and production deployment in a regulated environment. These are out of scope.

## 3. Work Sequence and Milestones

Each milestone should leave the project in a runnable, reviewable state. The detailed order can be adjusted when implementation reveals dependencies, but security boundaries and domain contracts should be established before AI or MCP features depend on them.

### Milestone 0 — Project baseline and contracts

**Goal:** Establish a buildable foundation and agree on boundaries before feature code grows.

**Work:**

- Confirm Java 25, Spring Boot, Angular/TypeScript, PostgreSQL, and Docker Compose versions and baseline project layout.
- Choose the initial identity approach for the local demo, define roles and customer scope, and document the mock-user flow.
- Define domain identifiers and initial data model for customer, contact, product relationship, service case, case event, interaction, knowledge source, and AI trace.
- Define REST error conventions, API versioning approach, audit event shape, and correlation ID propagation.
- Establish CRM, LLM, NLP, embedding, vector-search, and MCP integration boundaries.
- Add a decision record for MCP implementation/transport and local vector retrieval approach.

**Exit criteria:** A clean checkout can build; architecture decisions and data/API boundaries are documented; no real-data or secret dependency is required to start the application.

### Milestone 1 — CRM foundation vertical slice

**Goal:** Staff can find a synthetic customer and understand their relationship with the bank.

**Work:**

- Implement customer and contact persistence, validation, search/filtering, and status/segmentation fields.
- Implement synthetic seed data covering multiple customer segments and representative products.
- Implement Customer 360 assembly with products, account summary (mock), preferences, risk indicators (simulated), interaction history, cases, and a placeholder for insights.
- Implement the first responsive UI for customer search, customer profile, and Customer 360.
- Add role-checked API access and masking rules for sensitive synthetic fields.
- Implement provider-neutral CRM contracts and the first adapter; keep domain behavior independent from adapter DTOs.

**Exit criteria / demo:** A user can search for a seeded customer, open Customer 360, and inspect products, interactions, and cases. Data is synthetic, and access is checked server-side.

### Milestone 2 — Case and interaction operations

**Goal:** Staff can manage service work and see its history.

**Work:**

- Implement case categories, priority, status, owner, description, SLA target, escalation state, resolution, and immutable case events/history.
- Implement case queues and filters, assignment, status changes, and case detail UI.
- Implement interaction records for web chat, email, telephone, branch, and mobile banking channels; connect interactions to customers and optionally cases.
- Add validation for supported categories and lifecycle transitions.
- Add authorization and audit events for case creation, assignment, escalation, and status changes.

**Exit criteria / demo:** A service officer can review a customer's history, create and assign a case, change its status, and see the full case timeline and related interactions.

### Milestone 3 — AI and NLP foundation

**Goal:** The assistant responds predictably and exposes structured understanding.

**Work:**

- Add provider-neutral LLM and NLP interfaces with deterministic mock implementations.
- Implement intent classification, sentiment analysis, language detection for Indonesian and English, and extraction of the required entity types.
- Add a conversational assistant UI scoped to the selected customer when appropriate.
- Define a typed assistant response containing answer, intent/entities, recommendations, and trace references.
- Validate model/provider output and provide clear error/fallback behavior when an adapter is unavailable.
- Mark mock AI use visibly in the UI and documentation.

**Exit criteria / demo:** The payment complaint example is classified as payment-related, negative or urgent as appropriate, with amount/date extracted; repeated inputs produce deterministic results and an inspectable NLP trace.

### Milestone 4 — Knowledge management and RAG

**Goal:** The assistant answers policy questions with evidence and abstains when evidence is missing.

**Work:**

- Define a versioned knowledge document format with title, source, section, effective date, and ownership metadata.
- Add knowledge document management and a small synthetic banking knowledge corpus, including BI-FAST failure handling.
- Implement chunking, mock embedding abstraction, vector/search adapter, ranking, thresholding, and context assembly.
- Return retrieved evidence separately from generated answer content, with stable source references.
- Implement insufficient-evidence abstention and show source links/references in the UI.
- Add retrieval and LLM stages to execution traces.

**Exit criteria / demo:** The BI-FAST question returns an answer supported by retrieved source references; a question absent from the corpus yields a clear abstention and does not invent a policy citation.

### Milestone 5 — MCP tools and safe write flow

**Goal:** Demonstrate agent-to-CRM interaction through controlled, auditable tools.

**Work:**

- Implement MCP server/client integration and tool discovery with JSON schemas.
- Add the required tools: `search_customer`, `get_customer_360`, `get_customer_cases`, `get_payment_status`, `search_knowledge`, `create_service_case`, `update_case_status`, `assign_case`, and `get_customer_products`.
- Label and enforce read-only versus write operations; validate schemas and business rules.
- Propagate authenticated user and authorization scope to every tool call; audit discovery/invocation/result and denials.
- Build a case proposal/review/confirmation UI. Bind confirmation to the exact proposal and reauthorize when executing.
- Add an MCP tool explorer for authorized users.

**Exit criteria / demo:** Read tools can be discovered and invoked within the user's scope. AI-assisted case creation displays a preview, creates nothing before explicit confirmation, and returns an auditable case reference after confirmation.

### Milestone 6 — Integration interchangeability and execution trace

**Goal:** Make the architectural claims visible and verifiable in the application.

**Work:**

- Implement the Salesforce-style mock adapter against the same CRM integration contract as the Dynamics-style adapter.
- Add contract checks showing equivalent CRM operations across both adapters.
- Complete the authorized AI execution trace view: user input, NLP result, retrieval results and citations, model invocation, MCP discovery and calls, tool outcomes, final answer, and duration.
- Add masking and access controls to trace data; avoid retaining unnecessary prompt/customer content.
- Add integration/provider status and a clear indicator for mock providers.

**Exit criteria / demo:** Switching mock CRM adapter does not require domain or UI business-logic changes. Authorized staff can inspect a full AI trace; unauthorized users cannot access restricted traces or customer data.

### Milestone 7 — Local release readiness

**Goal:** Make the showcase reproducible for a fresh user and evaluator.

**Work:**

- Add Dockerfiles and Docker Compose for the frontend, backend, and PostgreSQL; include health checks and repeatable schema/seed initialization.
- Add configuration examples without committing credentials; make mock mode the safe default.
- Write README setup instructions, architecture and API links, MCP tool specifications, schema documentation, and troubleshooting notes.
- Add automated unit, integration, and end-to-end coverage for acceptance criteria and the demo paths.
- Verify the six demonstration scenarios end-to-end and document exact steps, sample prompts, expected evidence, and screenshots if useful.
- Review security controls, synthetic-data guarantees, error handling, and performance target in the local environment.

**Exit criteria:** A fresh local environment can start with documented commands and synthetic fixtures; all required showcase scenarios complete predictably; known limitations are documented.

### Optional Milestone 8 — Sales and campaign capabilities

**Goal:** Expand CRM breadth after the core cognitive CRM story is complete.

**Work:** Leads, opportunities, campaigns, segmentation-based campaign targeting, campaign response tracking, and product recommendations. Ensure recommendations remain evidence-backed and advisory.

**Exit criteria:** Users can demonstrate a basic lead-to-opportunity or campaign-response flow with synthetic data without compromising focus or stability of the core showcase.

## 4. Cross-Cutting Work That Starts Early

These are not final-phase add-ons; address them alongside the first implementation that needs them.

| Concern | Start by | Required practice |
|---|---|---|
| Authorization and customer scope | Milestone 0–1 | Enforce on the server and through AI/MCP paths; test negative access cases. |
| Synthetic data and masking | Milestone 1 | Use generated fixtures only; mask sensitive values in API, UI, logs, prompts, and traces. |
| Audit | Milestone 1–2 | Record security-relevant reads/writes and actor, target, time, result, and correlation ID. |
| API contracts | Milestone 0–1 | Document request/response shapes and validation; keep provider types internal. |
| Observability and AI trace | Milestone 0 foundation, completed by 6 | Correlate API, retrieval, model, and tool stages; apply RBAC and data minimization. |
| Accessibility and responsive UI | Milestone 1 onward | Keep CRM and assistant usable on supported screen sizes and keyboard navigation. |
| Failure handling | Milestone 3 onward | Make provider outage distinct from no evidence, validation failure, and denied access. |

## 5. Demonstration Scenario Mapping

| Required scenario | Primary milestone | Demonstration result |
|---|---|---|
| 1. AI Customer 360 Assistant | 1, 3, 5 | Authorized customer, product, interaction, and case context combined into a structured summary. |
| 2. Intelligent Complaint Classification | 3 | Intent, entities, sentiment, suggested category, and priority are shown. |
| 3. AI Knowledge Assistant | 4 | Grounded answer with source references, or abstention where evidence is insufficient. |
| 4. AI-Assisted Case Creation | 2, 5 | Case proposal, explicit confirmation, authorized tool execution, and returned case reference. |
| 5. Next Best Action | 1, 3, 4 | Advisory recommendation based on profile, products, interactions, and relevant knowledge with explanations. |
| 6. Conversational Banking CRM | 2, 3, 5 | Natural-language request retrieves and summarizes unresolved payment complaints within authorization scope. |

Milestones 6–7 ensure the trace, adapter interchangeability, and reproducible local delivery are present across the scenarios.

## 6. Backlog and Planning Rules

For each feature, track:

- Requirement IDs or section references from `REQUIREMENT.md`.
- User-visible outcome and acceptance criteria.
- API/domain changes and data migration needs.
- Authorization, audit, masking, and AI trace implications.
- Automated verification needed (unit, integration, contract, or end-to-end).
- Documentation and synthetic fixture updates.

Prioritize work in this order when capacity is constrained:

1. Security, synthetic-data guarantees, and foundational contracts.
2. Customer 360, cases, and interactions.
3. Deterministic AI/NLP and grounded knowledge responses.
4. Authorized MCP reads and confirmed MCP writes.
5. Traceability, adapter comparison, and local deployment polish.
6. Optional sales and campaign capabilities.

Avoid starting a broad feature until its smallest end-to-end path can be described and demonstrated. Keep each milestone reviewable and update `arc42.md` when a key interface, deployment shape, or architecture decision changes.

## 7. Definition of Done

A feature is done when:

- Its acceptance criteria are met through the intended user or API flow.
- Authorization and input validation are enforced server-side, including AI/MCP-mediated paths.
- Writes and sensitive access have the required audit events; logs and traces mask data appropriately.
- Synthetic fixtures cover the normal path and meaningful failure/denial paths.
- Automated verification appropriate to the feature is added and passes in the supported local setup.
- API, MCP, architecture, and user documentation are updated where affected.
- The feature works in the local demo and does not rely on an unconfigured external provider.

## 8. Initial Planning Decisions to Resolve

These decisions should be closed early, preferably in Milestone 0, because they affect implementation shape:

1. Local-demo identity and role setup, including how customer-level scope is represented.
2. MCP SDK/transport and how authenticated identity is propagated to tool execution.
3. Initial vector retrieval implementation and whether embeddings are deterministic mock vectors or lexical search behind the same port.
4. Angular application and repository layout (single repository versus separate frontend project directory).
5. API documentation and database migration conventions.

