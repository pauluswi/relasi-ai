# RELASI AI
## Enterprise Cognitive CRM Platform

**Architecture:** Modular Microservices / API-First / AI-Enabled Enterprise Architecture

---

# 1. Executive Summary

RELASI AI is an enterprise-grade Cognitive Customer Relationship Management (CRM) platform designed to demonstrate how traditional CRM capabilities can be enhanced through Artificial Intelligence.

The platform simulates core capabilities of enterprise CRM products such as Microsoft Dynamics 365 and Salesforce while introducing an AI intelligence layer powered by Large Language Models (LLM), Natural Language Processing (NLP), Retrieval-Augmented Generation (RAG), and Model Context Protocol (MCP).

The primary objective is to demonstrate an integrated enterprise architecture that combines customer relationship management, conversational AI, knowledge intelligence, and agent-based automation.

RELASI AI uses a simulated banking environment to demonstrate practical applications in customer engagement, complaint management, customer service automation, and intelligent decision support.

The project is intended as a technical showcase for enterprise software architecture, AI integration, and banking technology.

# 2. Business Objectives

The platform aims to achieve the following objectives:

1. Demonstrate enterprise CRM capabilities inspired by Microsoft Dynamics 365 and Salesforce.
2. Demonstrate how AI can improve customer service and customer engagement.
3. Integrate multiple AI capabilities into a unified CRM platform.
4. Demonstrate secure AI-to-enterprise-system integration through MCP.
5. Demonstrate RAG-based enterprise knowledge retrieval.
6. Demonstrate how AI agents can interact with enterprise business processes.
7. Demonstrate enterprise architecture principles, including modularity, scalability, security, observability, and interoperability.

# 3. Target Users

| User | Responsibilities |
|---|---|
| CRM Administrator | Manage CRM configuration and users |
| Customer Service Officer | Handle customer complaints and inquiries |
| Relationship Manager | Manage customer relationships and opportunities |
| Contact Center Agent | Handle customer interactions |
| CRM Supervisor | Monitor service performance and team productivity |
| AI Administrator | Configure AI models, prompts and knowledge sources |
| System Administrator | Manage integration and platform configuration |

# 4. Functional Requirements

## 4.1 Customer Relationship Management

### 4.1.1 Customer 360

The platform MUST provide a unified customer profile containing:

- Customer identification and demographic information.
- Contact information.
- Customer segmentation.
- Banking product ownership.
- Account relationship summary (mock data).
- Customer interaction history.
- Open and historical service cases.
- Customer preferences.
- Customer risk indicators (simulated).
- AI-generated customer insights.

### 4.1.2 Customer Management

The platform MUST support:

- Customer registration.
- Customer profile updates.
- Customer search and filtering.
- Customer segmentation.
- Customer status management.
- Customer relationship history.

### 4.1.3 Contact Management

The platform MUST support:

- Individual contact management.
- Contact information.
- Preferred communication channels.
- Contact relationship with customers.
- Communication preferences.

## 4.2 Case and Complaint Management

The platform MUST provide service case management inspired by Dynamics 365 Customer Service and Salesforce Service Cloud.

Capabilities include:

- Create service cases.
- Assign cases to customer service officers.
- Define case categories and priorities.
- Manage case status.
- Record case descriptions.
- Maintain case history.
- Track SLA.
- Escalate overdue cases.
- Record case resolution.
- Maintain customer communication history.

Supported case categories:

- Payment complaints.
- Account inquiries.
- Loan inquiries.
- Card issues.
- Mobile banking issues.
- Transaction disputes.
- General banking inquiries.

## 4.3 Customer Interaction Management

The platform MUST maintain customer interaction history.

Supported channels:

- Web chat.
- Email (simulated).
- Telephone (simulated).
- Branch interaction (simulated).
- Mobile banking inquiry (simulated).

Each interaction MUST contain:

- Customer reference.
- Channel.
- Interaction timestamp.
- Interaction content.
- Interaction category.
- Interaction outcome.
- Related CRM case (optional).

## 4.4 Sales and Campaign Management

The platform SHOULD provide basic sales and marketing capabilities.

Features include:

- Lead management.
- Opportunity management.
- Campaign management.
- Customer segmentation.
- Campaign response tracking.
- Product recommendation.

## 4.5 AI Customer Assistant

The platform MUST provide a conversational AI assistant embedded in the CRM interface.

Capabilities:

- Answer natural-language questions.
- Understand customer context.
- Retrieve customer information through authorized CRM tools.
- Summarize customer relationships.
- Explain service case status.
- Recommend next actions.
- Retrieve relevant banking knowledge.
- Generate contextual responses.

Example:

"Give me a summary of this customer's relationship with the bank."

The assistant should combine customer information, banking products, interactions, and service history into a structured summary.

# 5. Cognitive Technology Requirements

## 5.1 Large Language Model (LLM)

The platform MUST implement an LLM abstraction layer.

The LLM layer SHOULD support:

- Text generation.
- Conversational interactions.
- Context-aware reasoning.
- Customer interaction summarization.
- Case summarization.
- Recommendation generation.
- Structured JSON output.
- Function/tool calling.

The initial implementation MUST support a mock LLM provider that simulates inference responses.

The architecture SHOULD allow future integration with real LLM providers through an interchangeable adapter.

Potential providers:

- OpenAI.
- Google Gemini.
- Anthropic Claude.
- Local LLM through Ollama.

## 5.2 Natural Language Processing (NLP)

The platform MUST implement an NLP processing module.

Capabilities:

### Intent Classification

Identify customer intents such as:

- Payment inquiry.
- Loan inquiry.
- Account inquiry.
- Transaction complaint.
- Card issue.
- Product inquiry.
- General inquiry.

### Sentiment Analysis

Classify customer sentiment:

- Positive.
- Neutral.
- Negative.
- Urgent / frustrated.

### Entity Extraction

Extract relevant entities:

- Customer identification.
- Account reference.
- Transaction reference.
- Payment amount.
- Transaction date.
- Product name.
- Complaint category.

### Language Detection

Identify supported languages:

- Indonesian.
- English.
- German (future enhancement).

NLP processing MUST return structured output that can be consumed by downstream AI services.

## 5.3 Retrieval-Augmented Generation (RAG)

The platform MUST implement a RAG architecture for enterprise knowledge retrieval.

Knowledge sources include:

- Banking product documentation.
- Banking policies.
- Customer service procedures.
- Payment processing documentation.
- Loan product information.
- Frequently asked questions.
- Complaint handling procedures.

RAG processing pipeline:

1. Receive user question.
2. Convert question into a retrieval query.
3. Generate embeddings.
4. Retrieve relevant knowledge chunks.
5. Apply relevance ranking.
6. Construct contextual prompt.
7. Invoke LLM.
8. Return grounded response with source references.

The RAG module MUST support:

- Document ingestion.
- Document chunking.
- Embedding generation (mock implementation initially).
- Vector search.
- Retrieval ranking.
- Context assembly.
- Source attribution.
- Knowledge document management.

The platform MUST distinguish retrieved information from generated information.

The assistant SHOULD abstain when reliable supporting information cannot be retrieved.

## 5.4 Model Context Protocol (MCP)

The platform MUST demonstrate MCP-based integration between AI agents and enterprise CRM capabilities.

An MCP server MUST expose controlled business tools.

Initial MCP tools:

| Tool | Description |
|---|---|
| search_customer | Search customer profiles |
| get_customer_360 | Retrieve customer 360 information |
| get_customer_cases | Retrieve customer service cases |
| get_payment_status | Retrieve mock payment status |
| search_knowledge | Search enterprise knowledge |
| create_service_case | Create a CRM service case |
| update_case_status | Update service case status |
| assign_case | Assign case to an officer |
| get_customer_products | Retrieve customer product relationships |

MCP implementation MUST demonstrate:

- Tool discovery.
- Tool definitions and JSON schemas.
- Tool invocation.
- Input validation.
- Authentication and authorization.
- Tool execution results.
- Error handling.
- Audit logging.

The platform MUST distinguish between read-only and write operations.

Write operations MUST require appropriate authorization and explicit user confirmation.

The initial implementation MAY use a local MCP server with simulated CRM data.

# 6. AI Agent Capabilities

## 6.1 Customer Service Agent

The Customer Service Agent MUST:

- Understand customer inquiries.
- Identify customer intent.
- Retrieve relevant CRM information.
- Search banking knowledge.
- Generate suggested responses.
- Recommend appropriate service actions.
- Assist with service case creation.

## 6.2 Customer Insight Agent

The Customer Insight Agent SHOULD:

- Summarize customer relationships.
- Identify customer engagement patterns.
- Analyze customer interaction sentiment.
- Identify potential customer churn signals.
- Recommend relationship management actions.

## 6.3 Case Management Agent

The Case Management Agent SHOULD:

- Analyze complaint descriptions.
- Suggest case categories.
- Recommend case priority.
- Summarize case history.
- Suggest resolution procedures.
- Assist with case assignment.

AI-generated recommendations MUST remain advisory unless explicitly approved by an authorized user.

# 7. Business Demonstration Scenarios

The following scenarios MUST be demonstrable through the application.

### Scenario 1: AI Customer 360 Assistant

User asks:

"Summarize this customer's relationship with the bank."

Expected result:

- Retrieve customer information through MCP.
- Retrieve products and interaction history.
- Generate a consolidated customer summary.
- Present relevant insights.

### Scenario 2: Intelligent Complaint Classification

Customer submits:

"I transferred IDR 5 million yesterday, but the recipient hasn't received the money."

Expected result:

- Detect payment-related intent.
- Extract transaction amount and date.
- Identify negative sentiment.
- Recommend complaint classification.
- Suggest case priority.

### Scenario 3: AI Knowledge Assistant

User asks:

"What is the procedure for handling an unsuccessful BI-FAST transaction?"

Expected result:

- Search relevant banking documentation.
- Retrieve relevant policy sections.
- Generate a contextual answer.
- Include source references.

### Scenario 4: AI-Assisted Case Creation

Customer service officer asks:

"Create a high-priority complaint case for this customer."

Expected result:

- Retrieve customer context.
- Prepare structured case information.
- Display a case creation preview.
- Request user confirmation.
- Execute the create_service_case MCP tool.
- Return the created case reference.

### Scenario 5: Next Best Action

Relationship manager asks:

"What should I do next for this customer?"

Expected result:

- Analyze customer profile.
- Review product relationships.
- Analyze interaction history.
- Retrieve relevant product information.
- Generate recommendations with explanations.

### Scenario 6: Conversational Banking CRM

User asks:

"Find customers who have unresolved payment complaints and summarize their cases."

Expected result:

- Interpret natural-language intent.
- Retrieve relevant CRM records.
- Aggregate case information.
- Generate a structured summary.

# 8. Technical Architecture Requirements

## 8.1 Architecture Principles

The solution MUST follow:

- API-first architecture.
- Modular design.
- Separation of business and AI concerns.
- Provider-independent AI abstraction.
- Secure integration.
- Domain-driven design principles.
- Observability by design.
- Extensible integration architecture.

## 8.2 Proposed Technology Stack

### Backend

- Java 25.
- Spring Boot.
- Spring Security.
- Spring Data JPA.
- REST API.
- Spring AI or an equivalent AI abstraction framework.

### Frontend

- Angular.
- TypeScript.
- Responsive enterprise dashboard.
- CRM-inspired user experience.

### Database

- PostgreSQL.
- Redis (optional caching).

### AI Infrastructure

- Mock LLM provider.
- Mock NLP engine.
- Embedding abstraction.
- Vector database abstraction.
- RAG orchestration module.
- MCP server and client integration.

### Infrastructure

- Docker.
- Docker Compose.
- Kubernetes-ready deployment.
- OpenAPI / Swagger.
- Prometheus.
- Grafana.
- Structured application logging.

## 8.3 Logical Architecture

The architecture SHOULD contain the following logical components:

1. CRM Frontend.
2. API Gateway / Backend API.
3. Customer Management Service.
4. Case Management Service.
5. Interaction Management Service.
6. CRM Integration Layer.
7. AI Orchestrator.
8. LLM Provider Adapter.
9. NLP Processing Service.
10. RAG Knowledge Service.
11. Vector Search Adapter.
12. MCP Server.
13. MCP Client / AI Tool Integration.
14. Audit and Observability Service.

The CRM domain MUST remain independent of the AI provider implementation.

# 9. Mock Integration Requirements

REL​​ASI AI MUST simulate external enterprise platforms without requiring commercial licenses or external enterprise accounts.

## 9.1 Dynamics 365 Simulation

Provide a CRM adapter that represents Dynamics 365-style operations:

- Customer retrieval.
- Contact retrieval.
- Case management.
- Customer interaction history.

## 9.2 Salesforce Simulation

Provide a CRM adapter that represents Salesforce-style operations:

- Account retrieval.
- Contact retrieval.
- Opportunity retrieval.
- Case management.

The two adapters SHOULD implement a shared CRM integration contract.

The platform MUST demonstrate how an AI application can work with different CRM providers without changing its business logic.

## 9.3 AI Provider Simulation

Mock providers MUST support deterministic responses for predefined demonstration scenarios.

The architecture MUST allow mock providers to be replaced with real implementations.

Mock implementations MUST be clearly identified in the user interface and documentation.

# 10. Security Requirements

The platform MUST implement:

- Authentication and role-based authorization.
- JWT-based API security.
- Role-based CRM access.
- Customer data access restrictions.
- MCP tool authorization.
- Input validation.
- Audit trails.
- Sensitive information masking.
- Secure configuration management.

The AI assistant MUST NOT expose customer information beyond the authenticated user's authorization scope.

The AI assistant MUST NOT execute sensitive write operations without explicit confirmation.

All customer and transaction information MUST use synthetic demonstration data.

# 11. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Performance | Standard CRM API response target under 500 ms in the local demo environment |
| Scalability | Modular services and independently replaceable AI providers |
| Reliability | Graceful handling of unavailable AI providers |
| Security | RBAC, JWT and audit logging |
| Maintainability | Clear separation of business, integration and AI layers |
| Observability | Centralized logs and metrics |
| Extensibility | Provider adapters for CRM and AI integrations |
| Portability | Docker-based local deployment |
| Explainability | AI recommendations include supporting evidence where applicable |

Performance targets are initial engineering objectives, not production service-level guarantees.

# 12. User Interface Requirements

The application SHOULD provide the following modules:

1. Executive Dashboard.
2. Customer Management.
3. Customer 360.
4. Contact Management.
5. Service Case Management.
6. Interaction History.
7. Sales and Campaign Management.
8. AI Customer Assistant.
9. Knowledge Management.
10. AI Operations Dashboard.
11. MCP Tool Explorer.
12. Integration Management.
13. Audit Logs.

The interface SHOULD resemble modern enterprise CRM applications while maintaining an independent visual identity.

# 13. Demonstration and Observability

The platform MUST provide an AI execution trace showing:

- User input.
- Intent classification result.
- Extracted entities.
- RAG retrieval results.
- Retrieved source references.
- LLM invocation.
- MCP tool discovery and execution.
- Tool execution result.
- Final AI response.
- Execution duration.

This capability is particularly important for demonstrating enterprise AI architecture and troubleshooting.

# 14. Development Phases

## Phase 1 — CRM Foundation

- Customer management.
- Customer 360.
- Contact management.
- Case management.
- Interaction history.
- CRM dashboard.

## Phase 2 — Cognitive AI Foundation

- LLM abstraction.
- Mock LLM implementation.
- NLP intent classification.
- Sentiment analysis.
- Entity extraction.
- AI conversational interface.

## Phase 3 — Enterprise Knowledge Intelligence

- Knowledge document management.
- Document ingestion.
- Embedding abstraction.
- Vector search.
- RAG pipeline.
- Source attribution.

## Phase 4 — MCP Integration

- MCP server.
- MCP client.
- Tool discovery.
- CRM tool integration.
- Read-only and write-operation authorization.
- Tool execution audit.

## Phase 5 — Intelligent CRM Agents

- Customer Service Agent.
- Customer Insight Agent.
- Case Management Agent.
- Next Best Action.
- Multi-step AI orchestration.

## Phase 6 — Enterprise Readiness

- Security hardening.
- Observability.
- Docker deployment.
- Integration documentation.
- Architecture documentation.
- Demonstration scenarios.

# 15. Acceptance Criteria

The initial release will be considered successful when:

1. Users can manage customers and customer profiles.
2. Users can view consolidated Customer 360 information.
3. Users can create and manage service cases.
4. Users can review customer interaction history.
5. The AI assistant can process natural-language inquiries.
6. NLP can identify intent, sentiment and relevant entities.
7. RAG can retrieve relevant banking knowledge with source references.
8. LLM abstraction can support deterministic mock inference.
9. MCP can discover and execute authorized CRM tools.
10. AI-assisted case creation requires explicit confirmation.
11. AI execution traces are visible to authorized users.
12. CRM adapters demonstrate interchangeable Dynamics-style and Salesforce-style integration.
13. The complete application can run locally using Docker Compose.
14. Synthetic banking data is used throughout the demonstration.

# 16. Out of Scope

The initial version will NOT implement:

- Actual Microsoft Dynamics 365 connectivity.
- Actual Salesforce connectivity.
- Production banking core integration.
- Real customer personal data.
- Real payment execution.
- Autonomous financial transactions.
- Production-grade LLM model training.
- Fully autonomous customer communication.
- Production deployment to a regulated banking environment.

# 17. Expected Deliverables

The project MUST include:

- REQUIREMENTS.md
- README.md
- arc42.md
- C4 architecture diagrams.
- Domain model documentation.
- API specifications.
- Database schema.
- MCP tool specifications.
- AI workflow documentation.
- Docker Compose configuration.
- Synthetic demonstration dataset.
- Automated tests.
- End-to-end demonstration scenarios.

# 18. Project Success Definition

RELASI AI is successful when it demonstrates how enterprise CRM systems can evolve from traditional customer record management into intelligent, context-aware and AI-assisted customer engagement platforms.

The project should showcase the ability to design and integrate enterprise applications, AI services, knowledge retrieval systems, and standardized agent-to-system communication protocols in a secure and extensible architecture.