# Zipkin Docker Setup

The application no longer starts Zipkin as an in-process sidecar.
Run Zipkin externally using Docker.

## Start Zipkin

From the project root:

```powershell
docker compose -f docker-compose.zipkin.yml up -d
```

Check status:

```powershell
docker compose -f docker-compose.zipkin.yml ps
```

Zipkin UI:

- `http://localhost:9411`

## Application Configuration

Set these environment variables when running the app:

- `TRACING_ENABLED=true`
- `ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans`
- `ZIPKIN_UI_URL=http://localhost:9411`

If the app itself runs in Docker on the same network, use:

- `ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans`
- `ZIPKIN_UI_URL=http://zipkin:9411`

Optional port override:

- `ZIPKIN_PORT=9411`

## Stop Zipkin

```powershell
docker compose -f docker-compose.zipkin.yml down
```
