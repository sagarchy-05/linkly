# curtli

A production-grade URL shortener. Paste up to 20 links at a time, get back tidy short codes, and have your history quietly remembered in your browser.

Live at **[curtli.com](https://curtli.com)**.

```
https://docs.google.com/spreadsheets/d/1aB2cD3eF4gH5iJ6kL7mN8oP9qR0sT/edit#gid=0
                                  ↓
                          curtli.com/aB3xK9p
```

---

## What it does

- **Shortens URLs** with optional custom aliases (`curtli.com/launch-2026`).
- **Tracks clicks** without blocking the redirect — analytics flow asynchronously through a Redis Stream into a Postgres time-series table.
- **Rate limits** writes via a Redis-backed token bucket (separate buckets for single and bulk endpoints).
- **Honors expiry** — links can self-destruct after N days (1–3650), or stay permanent.
- **Survives Redis outages** on the read path — falls back to Postgres, never 500s on a transient cache hiccup.
- **Ships with a frontend** — single-page, vanilla JS, dark mode, lives at `src/main/resources/static/`.

## Tech stack

- **Java 21 + Spring Boot 3.3** — Web, Data JPA, Data Redis, Validation, Actuator, Scheduling
- **PostgreSQL 16** as the source of truth, schema managed by **Flyway**
- **Redis 7** as cache + rate-limit token bucket + click event stream
- **Resilience4j** for circuit breakers, **Lombok** for boilerplate, **springdoc-openapi** for Swagger UI
- **Testcontainers** for integration tests
- **Vanilla HTML/CSS/JS** for the frontend (no build step)

## Architecture at a glance

### Redirect path (hot, must stay fast)

```
GET /{shortCode}
   │
   ├─ RateLimitFilter           (skipped — only fires on POST /api/shorten*)
   ├─ ResolverService
   │     ├─ Redis GET link:{code}            ──hit──> return long URL
   │     └─ Postgres SELECT (cache miss)     ──hit──> cache, return
   ├─ ClickDebouncer            (10s SETNX per shortCode+IP — drops duplicates)
   ├─ ClickEventPublisher       @Async, fire-and-forget XADD to "click_events"
   └─ 302 Found  ←─────  Cache-Control: no-store
```

Both Redis and the publisher fail-open: redirects never break because analytics infra is sick.

### Click pipeline (cold, eventually consistent)

```
ClickEventPublisher → Redis Stream "click_events"
                          │
                          ▼
ClickEventConsumer  (@Scheduled, polls every ~1s, configurable)
   ├─ XREADGROUP up to N records
   ├─ ClickAggregator.flush()
   │     ├─ UPDATE links.click_count += N        (lifetime total)
   │     └─ UPSERT click_stats(link_id, hour)    (hourly time-series)
   └─ XACK each record
```

`consumer-name` reads from `$HOSTNAME` so multi-replica deployments share work via Redis Streams' consumer group semantics.

### Write path

`POST /api/shorten` validates, then:

- **Custom alias**: try to `INSERT`, catch `DataIntegrityViolationException` → "alias already taken" (avoids the TOCTOU race of pre-checking).
- **Auto code**: random 7-char Base62 via `SecureRandom`. 62⁷ ≈ 3.5 trillion possible values — collision retry only ever fires in pathological cases. Then cache the result with TTL bounded by the link's own expiry, so cached entries can't outlive expired links.

## Quick start

You need Docker installed. That's it.

```bash
cp .env.example .env
docker compose up --build
```

Then open **http://localhost:8080/**.

Three containers come up: Postgres, Redis, and the Spring Boot app. Flyway runs the migrations on first boot. The app waits on healthchecks so you can `docker compose up` cleanly.

## Configuration

All knobs come from environment variables (see `.env.example`):

| Variable | Default | What it controls |
|---|---|---|
| `POSTGRES_DB`, `DB_USERNAME`, `DB_PASSWORD` | `curtli`, `postgres`, `postgres` | Postgres credentials |
| `HOST_DB_PORT` | `5433` | Postgres port on the host |
| `HOST_REDIS_PORT` | `6379` | Redis port on the host |
| `HOST_APP_PORT` | `8080` | App port on the host |
| `CURTLI_BASE_URL` | `http://localhost:8080` | Used to build `shortUrl` in responses |
| `ASYNC_CORE_POOL` / `ASYNC_MAX_POOL` / `ASYNC_QUEUE_CAPACITY` | `8` / `32` / `3000` | Click publisher thread pool |
| `STREAM_POLL_DELAY` / `STREAM_BATCH_SIZE` / `STREAM_BLOCK_MILLIS` | `1000` / `1500` / `250` | Consumer tuning |
| `RATE_LIMIT_BULK_MAX` / `RATE_LIMIT_BULK_MINUTES` | (env-defined) | Bulk shorten rate limit per IP |
| `BULK_BATCH_SIZE` | (env-defined) | Max URLs per bulk request |

Single-shorten rate limit is `10/minute/IP` (hard-coded in `application.yaml`).

## API

| Method | Path | Body | Purpose |
|---|---|---|---|
| `POST` | `/api/shorten` | `ShortenRequest` | Single shorten |
| `POST` | `/api/bulk-shorten` | `List<ShortenRequest>` | Bulk (partial-success shape) |
| `GET` | `/{shortCode}` | — | 302 redirect (or 404 / 410) |
| `GET` | `/actuator/health` | — | Liveness + readiness |
| `GET` | `/actuator/prometheus` | — | Metrics |
| `GET` | `/swagger-ui.html` | — | API docs |

### `ShortenRequest`

```json
{
  "longUrl": "https://example.com/...",
  "customAlias": "my-link",        // optional, [a-zA-Z0-9_-]{3,16}
  "expiresInDays": 30               // optional, null = permanent, 1..3650
}
```

### `BulkShortenResponse` (partial success)

```json
{
  "successful": [{ "shortCode": "aB3xK9p", "shortUrl": "...", "longUrl": "..." }],
  "failed":     [{ "longUrl": "...", "attemptedAlias": "taken", "errorMessage": "Alias already taken" }]
}
```

Returns `200` for any-success, `400` only if every URL in the batch failed.

## Data model

Two tables (so far):

- **`links`** — short code, long URL, expiry, lifetime click counter, soft-delete flag.
- **`click_stats`** — `(link_id, bucket_hour, click_count)` with a unique constraint enabling `INSERT ... ON CONFLICT DO UPDATE` aggregation.

Flyway migrations live in `src/main/resources/db/migration/`.

## Project layout

```
src/
├── main/
│   ├── java/com/sagar/curtli/
│   │   ├── CurtliApplication.java
│   │   ├── config/       AsyncConfig, RateLimitConfig, RedisConfig, OpenApiConfig
│   │   ├── controller/   RedirectController, ShortenerController
│   │   ├── consumer/     ClickEventConsumer, ClickAggregator
│   │   ├── domain/       Link, ClickStat (JPA entities)
│   │   ├── dto/          ShortenRequest/Response, BulkShortenResponse, BulkError
│   │   ├── encoding/     Base62 (random + encode/decode)
│   │   ├── exception/    GlobalExceptionHandler + typed exceptions
│   │   ├── filter/       RateLimitFilter
│   │   ├── repository/   LinkRepository, ClickStatRepository
│   │   └── service/      Shortener, Resolver, ClickEventPublisher, ClickDebouncer, RateLimiter, GeoIp
│   └── resources/
│       ├── application.yaml
│       ├── db/migration/  V1__init.sql, V2__add_click_stats.sql
│       ├── scripts/       token_bucket.lua (atomic rate-limit decisions)
│       └── static/        index.html, styles.css, app.js  (landing page)
└── test/                  Testcontainers config, app context test
```

## Design notes worth knowing

- **Cache-aside, not write-through.** Postgres is the source of truth; Redis is best-effort with a TTL bounded by the link's own expiry. Cache writes log-and-continue on failure.
- **Analytics is best-effort.** The async publisher pool drops on overload (`RejectedExecutionHandler` no-ops). Better to lose a click count than to slow down a redirect.
- **No TOCTOU on alias allocation.** We don't pre-check `existsByShortCode` for custom aliases — we just attempt the insert and catch the unique-constraint violation. Two concurrent requests for the same alias result in exactly one success.
- **Random Base62, not sequential.** `Base62.encode(id)` would give early users codes like `/1`, `/2` — enumerable, ugly, leak the link count. Instead, each code is 7 random Base62 chars from `SecureRandom`, giving a 3.5T keyspace.
- **Idle-driven consumer**, not idle-detecting. `@Scheduled(fixedDelay)` runs on the Spring scheduler thread on a fixed cadence — it has nothing to do with server load. Don't confuse "fires when free" with "fires on a timer."
- **Frontend stays on the client.** Link history lives in `localStorage`; there's no anonymous session token, no `user_id` plumbing. Clearing the browser clears the history.

## License

Source-available, no specific license attached yet.
