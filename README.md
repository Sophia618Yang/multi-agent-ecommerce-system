# Multi-Agent E-Commerce Growth Copilot

A Java-based AI product prototype for personalized e-commerce recommendations, inventory-aware merchandising, and conversion-focused marketing copy.

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=java)](java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot)](java/pom.xml)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-OpenAI%20Compatible-111111)](java/pom.xml)

## Product Summary

E-commerce teams often run recommendations, campaign copy, and inventory checks as separate workflows. That creates a familiar product problem: users see products they cannot buy, promotional messages miss the user's intent, and growth teams struggle to connect AI changes to measurable conversion impact.

This project prototypes a **Multi-Agent E-Commerce Growth Copilot** that coordinates four specialized Java agents to produce one shoppable, personalized response:

| Product capability | Agent owner | Business purpose |
| --- | --- | --- |
| User intent understanding | User Profile Agent | Convert behavior signals into segments, RFM scores, and shopping preferences. |
| Product selection | Product Recommendation Agent | Recall and rank products for the current user and shopping scene. |
| Availability guardrail | Inventory Agent | Filter unavailable products and surface low-stock purchase constraints. |
| Conversion messaging | Marketing Copy Agent | Generate segment-aware marketing copy with basic compliance checks. |

The project is framed as an AI product case study with a working Java backend. It shows the product problem, MVP boundaries, system architecture, measurement plan, and implementation path.

## Why This Matters

Recommendation quality is only one part of the commerce conversion funnel. A useful AI product should connect user intent, item relevance, real inventory, personalized messaging, and experiment tracking in one workflow.

| Product question | System response |
| --- | --- |
| Who is the shopper and what are they likely trying to do? | Real-time profile and segment inference. |
| Which products are most relevant right now? | Multi-strategy recall plus LLM-assisted re-ranking. |
| Can the shopper actually purchase these items? | Inventory filtering before the final response. |
| What message should accompany each recommendation? | Segment-aware copy generated after final product selection. |
| How do we know if the system works? | Stable A/B assignment and response-level experiment metadata. |

## Architecture

```text
Client / commerce surface
        |
        v
Spring Boot Recommendation API
        |
        v
Supervisor Orchestrator
        |
        +--> User Profile Agent ------+
        |                             |
        +--> Product Rec Agent -------+--> Aggregator --> Marketing Copy Agent --> A/B Service
        |                             |
        +--> Inventory Agent ---------+
        |
        v
Personalized response:
products + marketing copy + experiment group + agent telemetry
```

The implementation uses Java 17, Spring Boot, Spring AI, and `CompletableFuture` for parallel agent execution.

| Layer | Implementation |
| --- | --- |
| API | Spring Boot REST controller under `java/src/main/java/com/ecommerce/config/RecommendationController.java` |
| Agent orchestration | Supervisor pattern with `CompletableFuture` |
| LLM integration | Spring AI OpenAI-compatible chat client |
| Data contracts | Java model classes with Lombok builders |
| Experimentation | Hash-based A/B bucket assignment |
| Reliability | Agent retries, fallback results, latency tracking |

More detail: [docs/architecture.md](docs/architecture.md)

## Product Decisions

### 1. Supervisor Over Fully Autonomous Agents

For commerce, reliability and latency matter more than open-ended autonomy. The supervisor pattern gives the product team a predictable workflow: profile, recommend, check inventory, generate copy, and return measurement metadata.

### 2. Inventory As A First-Class Guardrail

Recommendation systems can optimize engagement while still hurting the shopping experience if they promote unavailable items. This design treats inventory as a product constraint, not a downstream cleanup step.

### 3. Personalized Copy After Final Product Selection

Marketing copy is generated after product filtering so the message reflects products the user can actually buy. This avoids over-promising on items that later get removed.

### 4. Experimentation Built Into The Response

The API returns an experiment group with every recommendation response, creating a clear path from model or prompt changes to conversion analysis.

## Success Metrics

Primary product metrics:

| Metric | Why it matters |
| --- | --- |
| CTR on recommended products | Measures relevance and surface quality. |
| CVR after recommendation click | Measures purchase intent and downstream fit. |
| Revenue per recommendation session | Connects personalization to business value. |
| Out-of-stock exposure rate | Measures whether the system avoids bad recommendations. |
| Copy engagement lift | Measures the incremental value of personalized messaging. |

Operational metrics:

| Metric | Target direction |
| --- | --- |
| P95 recommendation latency | Lower is better; parallel execution should keep responses usable. |
| Agent failure rate | Lower is better; each agent has retry and fallback behavior. |
| Fallback usage rate | Should be visible by agent type. |
| Experiment sample balance | Confirms traffic assignment is healthy. |

## API Preview

Run the Java API, then call the recommendation endpoint:

```bash
curl -X POST http://localhost:8080/api/v1/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "u001",
    "scene": "homepage",
    "numItems": 5,
    "context": {
      "device": "mobile",
      "trafficSource": "email_campaign"
    }
  }'
```

Example response shape:

```json
{
  "requestId": "7f9c...",
  "userId": "u001",
  "products": [],
  "marketingCopies": [],
  "experimentGroup": "treatment_llm",
  "agentResults": {},
  "totalLatencyMs": 1820.4
}
```

Additional endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/health` | Service health check. |
| `POST /api/v1/recommend` | Supervisor-based recommendation flow. |
| `GET /api/v1/experiments` | Experiment configuration preview. |

## Run Locally

### Java API

```bash
cd java
mvn spring-boot:run
```

Optional environment variables:

```bash
export ECOM_LLM_API_KEY="your_api_key"
export ECOM_LLM_BASE_URL="https://api.minimax.chat/v1"
export ECOM_LLM_MODEL="MiniMax-M1"
```

### Docker Compose

```bash
docker compose up -d
```

The compose file starts optional Redis and MySQL services for future feature-store and catalog persistence work. The current Java prototype uses an in-memory H2 database by default.

## Repository Map

```text
.
├── java/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ecommerce/
│       │   ├── agent/          # Domain agents
│       │   ├── config/         # REST controller and LLM configuration
│       │   ├── model/          # Request, response, product, and profile models
│       │   ├── orchestrator/   # Supervisor orchestration
│       │   └── service/        # A/B assignment service
│       └── resources/
│           └── application.yml
├── docs/
│   └── architecture.md
└── docker-compose.yml
```

## Roadmap

Near-term improvements:

- Replace mock product data with a real catalog ingestion pipeline.
- Add an admin view for experiment setup, prompt versioning, and agent health.
- Add human review controls for high-risk campaign copy.
- Expand metric events from aggregate counters to user-level funnel analytics.
- Add offline evaluation for ranking quality before online experiments.

Longer-term opportunities:

- Merchant-facing assistant for campaign planning and SKU selection.
- Real-time offer optimization based on margin, stock, and predicted intent.
- Multi-market localization for copy tone, compliance, and merchandising rules.
- Retrieval layer for product detail pages, reviews, and policy constraints.
