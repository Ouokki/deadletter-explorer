# Roadmap

## v0.1 (MVP ✅)
- [x] List DLQ topics by pattern (`*-DLQ`)  
- [x] Fetch last N messages (configurable per partition)  
- [x] Basic replay to target topic with throttle + header allow-list  
- [x] React UI: topic list, messages table, replay modal  
- [x] Docker Compose stack (Kafka + Zookeeper)  
- [x] RBAC + OIDC (Keycloak) secure profile  
- [x] Docs, quickstart, smoke tests with Testcontainers  

---

## v0.2 (Security & Safety)
- [ ] RBAC enforcement: viewer / triager / replayer roles  
- [ ] Redaction rules (JSONPath → mask/remove/hash)  
- [ ] Dead-letter redaction UI with preview before save  
- [ ] Replay dry-run mode with diff summary  
- [ ] Audit log (user, source/target, replay count, checksum, throttle)  

---

## v0.3 (Scale & UX)
- [ ] Offset pagination + partition selector / merged view  
- [ ] Saved searches, JSON queries (JMESPath/JSONata)  
- [ ] Bulk replay with idempotency header support  
- [ ] Micrometer metrics: replayed, errors, throughput  
- [ ] Saved redaction policies + per-topic selector  
- [ ] Policy tester (sample payload → redacted preview/diff)  

---

## v0.4 (Ecosystem & Integration)
- [ ] Helm chart for Kubernetes  
- [ ] GitHub Action: one-click demo stack  
- [ ] Pluggable deserializers (JSON / Avro / Protobuf / JSON Schema)  
- [ ] Optional Schema Registry integration (Confluent, Apicurio)  
- [ ] Sample DLQ generators (poison pills, schema drift)  
- [ ] Redaction presets (PII: email, phone, credit card, IBAN)  
- [ ] Env-aware policies (different rules for dev/staging/prod)  

---

## v0.5 (Advanced Features)
- [ ] Advanced search: by key, by time window, regex/full-text in JSON  
- [ ] Replay sandbox mode (temp topic + test consumer)  
- [ ] Visual payload diff (original vs redacted vs replayed)  
- [ ] Alerting hooks: Slack, MS Teams, email  
- [ ] Export DLQ messages to S3, Elasticsearch, Postgres  
- [ ] AI-assisted debugging (optional):  
  - Summarize failing messages (e.g., “missing required field `amount`”)  
  - Natural-language queries (“show me failed messages with amount > 10000”)  

---

## v1.0 (Enterprise-Grade Release)
- [ ] Enterprise compliance pack (GDPR/PCI templates, audit export)  
- [ ] Policy marketplace (share redaction/replay plugins)  
- [ ] Multi-cluster support (switch clusters from UI)  
- [ ] Replay transformations (JSONPath mutations before replay)  
- [ ] Chaos / policy tester (auto-generate failing messages → validate rules)  
- [ ] Hosted SaaS version (multi-tenant Dead Letter Explorer)  