# DeadLetter Explorer (DLE)

Browse, triage, redact, and replay messages from Kafka Dead Letter Queues (DLQs).  
**MVP scope:** list DLQ topics, inspect recent messages, and replay selected messages safely.

## Why
DLQs accumulate opaque messages. Debugging and reprocessing is slow and risky. DLE gives teams a safe UI + API to inspect payloads/headers and replay with guardrails.

## Features (MVP)
- Discover `*-DLQ` topics automatically.
- List recent messages (key/value/headers/timestamp/partition/offset).
- Safe replay to a target topic with throttling and optional header allow‑list.
- Simple React UI; Spring Boot (WebFlux) backend.
- Docker Compose for Kafka + app.
- Apache-2.0 licensed.

> Security note: The MVP runs without auth for simplicity. A `secure` Spring profile (Keycloak/OIDC) will be added in v0.2. **Do NOT expose MVP to the public internet.**

## Quickstart

### Prereqs
- JDK 21+, Node 18+, Docker Desktop

### 1) Start Kafka
```bash
cd infra
cp .env.example .env
docker compose up -d
```

### 2) Backend (API)
```bash
cd backend
./mvnw spring-boot:run
# API at http://localhost:8080
```

### 3) Frontend (UI)
```bash
cd frontend
npm install
npm run dev
# UI at http://localhost:5173
```

### 4) Try it
- Produce a message into a topic like `orders-DLQ` (see `infra/kafka-scripts.md`).
- Open the UI → pick a DLQ → inspect messages → replay to a target topic.

## Modules
- `backend/` — Spring Boot 3.3, WebFlux, Kafka Admin/Consumer
- `frontend/` — React + Vite + TypeScript
- `infra/` — Docker Compose for Kafka/ZooKeeper

## Roadmap
See [`docs/ROADMAP.md`](docs/ROADMAP.md). For planned issues, see [`docs/ISSUES_BACKLOG.md`](docs/ISSUES_BACKLOG.md).

## License
Apache-2.0
