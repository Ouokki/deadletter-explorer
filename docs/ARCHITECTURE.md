# Architecture Overview

- **Backend**: Spring Boot 3.3 (WebFlux), Java 21. Uses Kafka AdminClient to discover topics and KafkaConsumer to read tail of DLQs (seek to end-endN). Replay via KafkaProducer.
- **Frontend**: React + Vite + TS. Calls REST API to list topics, view messages, trigger replay.
- **Infra**: Docker Compose (ZooKeeper + Kafka).

## DLQ Discovery
Pattern match `*-DLQ` by default (configurable `dle.dlqPattern`).

## Fetch Last N Messages
For each partition:
1. Get end offset.
2. Seek to `max(end - N, beginning)`.
3. Poll and return key/value/headers/offset/timestamp.

## Replay
POST with message(s), target topic, throttle (msgs/sec), and header allow-list.
