# Roadmap

## v0.1 (MVP)
- [x] List DLQ topics by pattern `*-DLQ`
- [x] Fetch last N messages (configurable)
- [x] Basic replay to target topic with throttle and header allow-list
- [x] React UI: topic list, messages table, replay modal
- [x] Docker Compose for Kafka

## v0.2 (Security & Safety)
- [ ] Add Keycloak (OIDC) optional profile: `secure`
- [ ] RBAC: viewer / triager / replayer roles
- [ ] Redaction rules (PII masking) before render
- [ ] Replay dry-run + diff report

## v0.3 (Scale & UX)
- [ ] Pagination by offsets, partition selector
- [ ] Saved searches, JSON query (JMESPath/JSONata)
- [ ] Bulk replay with idempotency keys + metrics

## v0.4 (Ecosystem)
- [ ] Helm chart
- [ ] GitHub Action to spin demo stack
- [ ] Pluggable deserializers (Avro/Protobuf/JSON Schema)
