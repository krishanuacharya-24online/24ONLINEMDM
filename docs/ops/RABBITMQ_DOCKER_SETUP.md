# RabbitMQ Docker Setup

The app is configured to read RabbitMQ connection settings from:

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST`

## Start RabbitMQ

From the project root:

```powershell
docker compose -f docker-compose.rabbitmq.yml up -d
```

Check status:

```powershell
docker compose -f docker-compose.rabbitmq.yml ps
```

RabbitMQ Management UI:

- `http://localhost:15672`

## App Configuration (local JVM app)

Use:

- `RABBITMQ_HOST=localhost`
- `RABBITMQ_PORT=5672`
- `RABBITMQ_USER=guest` (or your custom user)
- `RABBITMQ_PASSWORD=guest` (or your custom password)
- `RABBITMQ_VHOST=/`

## App Configuration (app in Docker network)

Use:

- `RABBITMQ_HOST=rabbitmq`
- `RABBITMQ_PORT=5672`

## Stop RabbitMQ

```powershell
docker compose -f docker-compose.rabbitmq.yml down
```
