# Kafka scripts

List topics:
```bash
docker compose exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --list
```

Create a DLQ topic for demo:
```bash
docker compose exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --create --topic orders-DLQ
```

Produce a bad message:
```bash
docker compose exec -it kafka kafka-console-producer.sh --bootstrap-server kafka:9092 --topic orders-DLQ
# then type a line and hit Enter
```

Consume:
```bash
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic orders-DLQ --from-beginning
```
