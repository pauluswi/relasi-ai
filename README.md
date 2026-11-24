# RELASI AI

![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![JDK-only bootstrap](https://img.shields.io/badge/bootstrap-JDK%20only-brightgreen?logo=openjdk&logoColor=white)
![Showcase status](https://img.shields.io/badge/status-planning-blue)
![Mock mode](https://img.shields.io/badge/run%20mode-offline%20mocks-informational)

**A mock-first, offline Cognitive CRM showcase for synthetic banking workflows.**

RELASI AI is planned as an enterprise CRM demonstration combining customer and case management with AI assistance, knowledge retrieval, and controlled tool use. Its primary showcase mode is designed to run locally without a database, Docker, network access, credentials, or external systems.

> **Project status:** Development step 1 is implemented as a local application shell. CRM and AI workflows are still planned; see the [development plan](docs/DEVELOPMENT_PLAN.md).

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

Requirements: a JDK 25 installation. No Maven, Node.js, database, Docker, credentials, or network connection is needed for this starter shell.

```sh
./run.sh
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080). The server binds to loopback only. To use another port:

```sh
RELASI_PORT=8090 ./run.sh
```

The script compiles the Java sources with the JDK, bundles local resources, and starts the application. Press `Ctrl+C` to stop it. Build output is temporary and ignored by Git.

### Starter endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/health` | Reports local mock runtime status. |
| `GET /api/v1/demo-users` | Lists the seeded local demo identities. |
| `GET /api/v1/session` | Returns a selected demo identity and customer scope; pass its ID in the `X-Demo-User` header. |

The demo identity selector is a local development convenience, not production authentication. The initial slice has no customer records or CRM operations yet.

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

The starter uses Java 25 and the JDK's built-in HTTP server to avoid framework downloads or build tools in the first-run path. The UI is a bundled static page. Future modules should preserve the same single-process, dependency-free run goal; see the [architecture](docs/arc42.md) and [development plan](docs/DEVELOPMENT_PLAN.md).

## Safety and scope

- Use synthetic demonstration data only.
- Enforce user and customer access scope in the application, including assistant and tool calls.
- AI recommendations remain advisory.
- A write operation requires an authorized user's explicit confirmation and is audited.
- Production banking integration, real customer data, real payment execution, and autonomous customer communication are out of scope.

