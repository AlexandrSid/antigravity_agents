# Messaging Layer Extension Guide: Adding New Broker Slots

> **Layer**: `messaging`  
> **Purpose**: Standardized procedure for onboarding message brokers (Kafka, RabbitMQ, SQS, Redis Streams) into `.ai/guidelines/messaging/slots/`.

---

## 1. Quick Onboarding Process
1. Copy `slots/TEMPLATE.md` to `slots/<broker>.md` (e.g. `slots/rabbitmq.md`).
2. Fill in topic/exchange naming, consumer ACK pattern, and DLQ retry configuration.
3. Wire into `SPEC.md` for event-driven features.
