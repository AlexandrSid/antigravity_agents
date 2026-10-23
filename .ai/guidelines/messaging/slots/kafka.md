# Apache Kafka Messaging Slot Implementation

> **Layer**: `messaging`  
> **Implements**: `.ai/guidelines/messaging/META.md`  
> **Target**: Apache Kafka / SmallRye Reactive Messaging / Spring Kafka

---

## 1. TOPIC_AND_QUEUE_NAMING
- Main Topic: `{domain}.{entity-plural}.{event-type}.v{version}` (e.g., `linkshortener.links.created.v1`)
- DLQ Topic: `{domain}.{entity-plural}.{event-type}.dlq.v{version}`
- Consumer Group ID: `{service-id}-{consumer-purpose}`

## 2. CONSUMER_ACK_STRATEGY
- Explicit manual commit or post-processing ack after persistence commit:
```java
// SmallRye / MicroProfile Reactive Messaging
@Incoming("links-in")
@Acknowledgment(Acknowledgment.Strategy.MANUAL)
public CompletionStage<Void> process(Message<LinkCreatedEvent> msg) {
    return handleEvent(msg.getPayload())
        .thenCompose(v -> msg.ack());
}
```

## 3. DLQ_AND_RETRY_CONFIG
- Max Retries: 3 with backoff.
- Failure Handler: Route unprocessable records to Dead Letter Queue topic.
