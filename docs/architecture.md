# System Architecture

## 1. Overview

This system uses a **Supervisor + parallel agent aggregation** architecture for personalized e-commerce recommendations.

```text
User request
    |
    v
+------------------------------------------------------+
|              Supervisor Orchestrator                 |
|                                                      |
|  Phase 1: parallel                                   |
|  +-------------------+   +-------------------+       |
|  | User Profile      |   | Product Recall    |       |
|  | Agent             |   | Agent             |       |
|  +---------+---------+   +---------+---------+       |
|            |                       |                 |
|  Phase 2: parallel                 v                 |
|  +-------------------+   +-------------------+       |
|  | LLM Re-rank       |   | Inventory         |       |
|  | Agent             |   | Agent             |       |
|  +---------+---------+   +---------+---------+       |
|            |                       |                 |
|            +-----------+-----------+                 |
|                        v                             |
|  Phase 3: sequential                                 |
|  +--------------------------------+                  |
|  | Aggregator: inventory filter   |                  |
|  | + rank merge + Top N selection |                  |
|  +----------------+---------------+                  |
|                   v                                  |
|  +--------------------------------+                  |
|  | Marketing Copy Agent           |                  |
|  +----------------+---------------+                  |
|                   v                                  |
|  +--------------------------------+                  |
|  | A/B Assignment Service         |                  |
|  +----------------+---------------+                  |
+-------------------+----------------------------------+
                    |
                    v
          Personalized response
```

## 2. Agent Responsibility Matrix

| Agent | Responsibility | Input | Output | Data/tool source | Timeout |
| --- | --- | --- | --- | --- | --- |
| User Profile | Real-time signals, RFM scoring, segmentation | `userId` | `UserProfile` | Behavioral feature service, LLM | 5s |
| Product Recommendation | Multi-strategy recall and LLM re-ranking | `UserProfile`, `numItems` | `Product[]` | Catalog data, LLM | 8s |
| Inventory | Stock checks, alerts, purchase limits | `Product[]` | Available IDs, alerts, limits | Inventory service or WMS | 5s |
| Marketing Copy | Template selection, LLM copy generation, compliance filter | `UserProfile`, `Product[]` | Copy list | LLM | 10s |

## 3. Java Implementation

The Java prototype is built with:

- Java 17
- Spring Boot 3.4
- Spring AI with an OpenAI-compatible chat client
- `CompletableFuture` for parallel orchestration
- H2 as the default local database
- Lombok for model builders

Key files:

| File | Purpose |
| --- | --- |
| `java/src/main/java/com/ecommerce/config/RecommendationController.java` | REST API entry point |
| `java/src/main/java/com/ecommerce/orchestrator/SupervisorOrchestrator.java` | Multi-agent orchestration |
| `java/src/main/java/com/ecommerce/agent/BaseAgent.java` | Retry, fallback, and latency wrapper |
| `java/src/main/java/com/ecommerce/service/ABTestService.java` | Stable experiment bucket assignment |
| `java/src/main/resources/application.yml` | Runtime configuration |

## 4. Parallel Execution Strategy

Serial execution would add every agent's latency together. The supervisor instead runs independent work in parallel:

```text
Serial:
5s + 8s + 5s + 10s = 28s

Parallel:
Phase 1: max(profile, recall)
Phase 2: max(rerank, inventory)
Phase 3: copy generation
```

Dependency rules:

- Phase 1: User profiling and initial product recall do not depend on each other.
- Phase 2: Re-ranking needs the profile; inventory checking can run against the recalled products.
- Phase 3: Marketing copy waits for final products so it does not promote removed items.

## 5. Reliability Design

### Retry Strategy

- Exponential backoff: 500ms, then 1000ms.
- Maximum retries: 2.
- Each agent returns an `AgentResult`, which keeps failures isolated from the entire response.

### Fallback Strategy

| Agent | Fallback |
| --- | --- |
| User Profile | Return an active default profile. |
| Product Recommendation | Return default catalog order. |
| Inventory | Treat ranked products as usable when stock service is unavailable. |
| Marketing Copy | Return an empty copy list or generic copy. |

## 6. Experimentation

The A/B service assigns each user to a stable group with an MD5-based hash bucket:

```text
bucket = md5(userId + experimentId) % 100

0-49   -> control
50-99  -> treatment_llm
```

The response includes the experiment group so downstream analytics can join recommendation behavior to product metrics such as CTR, CVR, revenue per session, and out-of-stock exposure.

## 7. Extension Points

1. Add a new agent by extending `BaseAgent` and registering it in the supervisor.
2. Add a new experiment by extending `ABTestService`.
3. Replace mock product data with a catalog repository.
4. Replace inventory mock logic with a warehouse or inventory service.
5. Add event logging for impression, click, add-to-cart, purchase, and copy engagement.

## 8. Production Path

1. Redis or feature-store service for real-time user behavior signals.
2. Vector database for semantic product retrieval.
3. Kafka or another event stream for behavioral events.
4. Prometheus and Grafana for agent health and business metrics.
5. Kubernetes deployment with autoscaling for API and agent workloads.
