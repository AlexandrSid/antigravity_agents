# Universal Messaging & Event-Driven Meta-Standard

> **Layer**: `messaging`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: Event-driven pipelines, asynchronous workers, and pub/sub messaging topologies.

---

## 1. Architectural Purpose
This meta-standard defines universal rules for asynchronous message processing, topic/queue topology, consumer idempotency, and error handling.
It ensures zero message loss, prevents infinite retry storms, and guarantees idempotent message processing.

---

## 2. Universal Messaging Invariants

### 2.1. Delivery Semantics & Idempotent Consumer
1. All message consumers must be designed under the assumption of **At-Least-Once Delivery**.
2. Every message must carry an explicit business identifier or UUID (e.g. `eventId`, `idempotencyKey`).
3. Consumers must record processed message identifiers or employ natural database unique constraints to safely discard duplicate events.

### 2.2. Dead Letter Queue (DLQ) & Error Handling
1. Unrecoverable failures (e.g. malformed JSON, schema violation) must route immediately to a Dead Letter Queue (DLQ) after a capped number of retries.
2. Recoverable failures (e.g. transient network timeouts) must utilize exponential backoff with jitter.
3. Unhandled exceptions must NEVER block the consumer partition/queue indefinitely.

### 2.3. Event Schema Evolution
1. Message payloads must be versioned.
2. Changes to event schemas must be backward-compatible (adding optional fields is allowed; removing or altering existing fields is forbidden without schema migration).

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

| Slot Identifier | Format | Description |
|---|---|---|
| `TOPIC_AND_QUEUE_NAMING` | Markdown rules | Naming conventions for topics, exchanges, and dead-letter queues. |
| `CONSUMER_ACK_STRATEGY` | Code block | Manual acknowledgement / commit pattern after business transaction. |
| `DLQ_AND_RETRY_CONFIG` | Code block | Concrete retry and DLQ routing definition. |

---

## 4. Agent Consumption Protocol
1. Read `SPEC.md` (`## Applicable Technical Standards`) to identify the messaging broker slot.
2. Apply consumer acknowledgement and DLQ configuration from `slots/<tech>.md`.
