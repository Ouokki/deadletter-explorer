# Architecture Overview

- **Backend**: Spring Boot 3.3 (WebFlux), Java 21. Uses Kafka AdminClient to discover topics and KafkaConsumer to read tail of DLQs (seek to end-endN). Replay via KafkaProducer.
- **Frontend**: React + Vite + TS. Calls REST API to list topics, view messages, trigger replay.
- **Infra**: Docker Compose (ZooKeeper + Kafka + keycloak).

