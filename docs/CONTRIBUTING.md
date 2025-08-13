# Contributing

- Use conventional commits (feat:, fix:, docs:, chore:, refactor:, test:, build:).
- Create a feature branch and PR with a concise description.
- For any security change, add tests under `backend/src/test/...`.
- Run `docker compose up` (Kafka), `mvn -q -DskipTests package`, and `npm run build` before PR.
