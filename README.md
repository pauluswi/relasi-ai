# RELASI AI

**A mock-first, offline Cognitive CRM showcase for synthetic banking workflows.**

RELASI AI is planned as an enterprise CRM demonstration combining customer and case management with AI assistance, knowledge retrieval, and controlled tool use. Its primary showcase mode is designed to run locally without a database, Docker, network access, credentials, or external systems.

> **Project status:** Planning and architecture documentation are in place; the application itself is not implemented yet. There is currently no UI or launch command. The roadmap defines the intended one-command, offline experience.

## What the showcase will demonstrate

- Customer search and a consolidated Customer 360 profile.
- Service cases, assignments, SLA, history, and customer interactions.
- Deterministic mock AI and NLP for customer service inquiries.
- Local banking knowledge retrieval with source references and abstention when evidence is missing.
- MCP-compatible tool discovery and execution through an in-process registry.
- Explicit user confirmation before AI-assisted write operations.
- An inspectable AI execution trace and interchangeable Dynamics-style and Salesforce-style CRM mocks.

All banking records and payment information are synthetic. The project does not connect to real banking systems, execute payments, or require a live AI provider.

## Quick start

The application is not runnable yet. A one-command start process will be added as part of the implementation. The target is to require only the supported Java runtime and to start the complete showcase offline with deterministic in-memory data.

## Intended mock-mode behavior

- CRM, LLM, NLP, payment status, identity, retrieval, audit, and tool execution use in-process mock implementations.
- Seed data is deterministic and can be restored to its initial demonstration state.
- Mutable data and audit history may reset when the application restarts.
- Safe defaults work without API keys or configuration. Optional settings will be documented in one place.
- No database, Docker, network service, or separate MCP server is part of the primary run mode.

## Documentation

- [Requirements](docs/REQUIREMENT.md) — scope, users, functional and quality requirements, and acceptance criteria.
- [Architecture (arc42)](docs/arc42.md) — target system context, building blocks, runtime flows, decisions, and risks.
- [Development plan](docs/DEVELOPMENT_PLAN.md) — milestone sequence, demo outcomes, and completion criteria.

## Demonstration scenarios

The planned release will support these repeatable scenarios using local fixtures:

1. Summarize a customer's relationship with the bank.
2. Classify a payment complaint and extract relevant details.
3. Answer a banking procedure question with local source references.
4. Prepare and confirm creation of a service case.
5. Recommend an evidence-backed next action for a customer.
6. Find and summarize unresolved payment complaints within the user's access scope.

## Technology direction

The requirements propose Java 25 and Spring Boot for the backend, with a responsive TypeScript-based CRM interface. Implementation choices will prioritize a single-process local run and avoid runtime dependencies beyond the application itself. See the [development plan](docs/DEVELOPMENT_PLAN.md) for decisions that remain open.

## Safety and scope

- Use synthetic demonstration data only.
- Enforce user and customer access scope in the application, including assistant and tool calls.
- AI recommendations remain advisory.
- A write operation requires an authorized user's explicit confirmation and is audited.
- Production banking integration, real customer data, real payment execution, and autonomous customer communication are out of scope.

