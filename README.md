# DeadLetter Explorer (DLE)

Browse, triage, redact, and replay messages from Kafka Dead Letter Queues (DLQs).  
Think of it as a **flight recorder for Kafka DLQs** — making dead letters explorable and safely re‑processable.

---

## 📖 Docs

### Architecture
- **Backend**: Spring Boot 3.3 (WebFlux)  
  - `DlqAdminService`: discovers DLQ topics (`*-DLQ`).  
  - `DlqConsumerService`: fetches last N messages.  
  - `DlqProducerService`: replays with throttling + header allow‑list.  
  - `SecurityConfig`: Keycloak/OIDC with roles (`viewer`, `triager`, `replayer`).  
- **Frontend**: React + Vite + TailwindCSS  
  - Keycloak authentication (Auth Code + PKCE).  
  - Components: TopicList, MessagesTable, TargetTopicBar.  
- **Infra**: Docker Compose (Kafka, ZooKeeper, Keycloak).  
  - Scripts: `infra/kafka-scripts.md` (create topics, produce bad messages).  
  - `scripts/setup_keycloak.sh`: bootstraps realm, roles, and demo users.

### API Endpoints
- `GET /api/dlq/topics` → list DLQ topics.  
- `GET /api/dlq/messages?topic=...&limit=N` → fetch recent messages.  
- `POST /api/dlq/replay` → replay selected messages to a safe target topic.

### Security
- Profiles:  
  - `default` → no auth (local demo).  
  - `secure` → OIDC (Keycloak).  
- JWT roles resolved from `realm_access` + `resource_access.dle-api.roles`.

---

## 🎬 Demo

### 1. Spin up infra
```bash
cd infra
cp .env.example .env
docker compose up -d
```

### 2. Initialize Keycloak
```bash
./scripts/setup_keycloak.sh
```
Creates:
- Realm `dle`
- Clients: `dle-api` (backend), `dle-frontend` (SPA)
- Users:  
  - `alice / pass` → triager + viewer  
  - `bob / pass` → viewer only

### 3. Start backend
```bash
cd backend
./mvnw spring-boot:run
# API at http://localhost:8080
```

### 4. Start frontend
```bash
cd frontend
npm install
npm run dev
# UI at http://localhost:5173
```

### 5. Try it out
- Produce into `orders-DLQ`:
  ```bash
  docker compose exec kafka kafka-console-producer.sh --bootstrap-server kafka:9092 --topic orders-DLQ
  ```
- Login as `alice` → browse topics → inspect messages → replay to `orders`.

---

## ⚠️ Caveats

- **MVP**: local only, no clustering.  
- **Do not expose without auth**. Always enable the `secure` profile in production.  
- **Replay caution**: only whitelisted headers preserved.  
- **Persistence**: Kafka & Keycloak data use Docker volumes; check `infra/docker-compose.yml`.  
- **Roadmap**: pagination, filters, redact/edit before replay (see [`docs/ROADMAP.md`](docs/ROADMAP.md)).

---

## 📜 License

Apache-2.0
