---
name: "verify"
description: "Build, launch, and drive ShelfLife's real backend/frontend to observe a change end-to-end"
user-invocable: true
disable-model-invocation: false
---

## Build & launch

Backend (Spring Boot, H2 file-backed at `backend/data/shelflife.mv.db` — this is real,
accumulated dev data, not a throwaway fixture):

```sh
cd backend && nohup ./mvnw spring-boot:run > /tmp/backend_run.log 2>&1 &
# poll until it answers:
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/links | grep -q 200 && break
  sleep 2
done
```

`./mvnw spring-boot:run` spawns **two** `java.exe` processes on Windows (the Maven wrapper
launcher and the actual Spring Boot JVM). Killing only one leaves the server answering. Find both
and kill both:

```sh
wmic process where "name='java.exe'" get ProcessId,CommandLine
taskkill //F //PID <wrapper-pid>
taskkill //F //PID <app-pid>
```

Frontend production build (proxies `/api` to `:8080` in dev via Vite config; confirms the actual
shipped bundle, not just source):

```sh
cd frontend && npm run build   # tsc -b && vite build
grep -o "<new-feature-strings>" dist/assets/index-*.js | sort -u   # sanity: bundle contains them
```

## The gotcha this project will keep hitting: schema changes vs. real data

Every prior feature (001-003) only added **nullable** columns, so Hibernate's
`ddl-auto=update` could `ALTER TABLE ... ADD COLUMN` them onto the existing, non-empty
`backend/data/shelflife.mv.db` with zero issue. **The first `NOT NULL` column with no
`columnDefinition` default breaks this silently** — H2 (like most DBs) refuses
`ALTER TABLE ... ADD COLUMN x NOT NULL` on a populated table because existing rows have nothing to
satisfy the constraint. Hibernate only *warns* (`GenerationTarget encountered exception accepting
command`), doesn't fail startup, and every subsequent query referencing the new column then throws
`Column "X" not found` — a broken app that *looks* like it started fine.

**Unit tests never catch this.** `@DataJpaTest`/`@SpringBootTest` always start from an empty
in-memory or fresh H2 instance, where Hibernate does `CREATE TABLE` (column included from the
start), not `ALTER TABLE`. This class of bug is only observable by booting against the real,
already-populated `data/shelflife.mv.db` file — exactly what this SKILL's launch step does and
`mvn test` cannot.

**The fix**, when adding a new `NOT NULL` boolean/etc. column: give it an explicit DB-level
default via `columnDefinition`, e.g.
`@Column(nullable = false, columnDefinition = "boolean not null default false")`, not just a Java
field initializer (`private boolean x = false;` only helps newly-constructed entities in the JVM —
it does nothing for the generated `ALTER TABLE` DDL against existing rows).

**Check for this on every feature that adds a persisted field**: boot against the real data file
(not a fresh/deleted one) and confirm no `CommandAcceptanceException` / `Column ... not found`
appears in the log, and that `GET /api/links` (or whichever endpoint touches the new column)
returns real pre-existing rows, not a 500.

## Drive it (API surface — no browser tool available in this environment)

React is a thin client over the REST API; without a headless-browser tool, the highest-fidelity
surface reachable here is the same `fetch()` calls the UI makes:

```sh
# create, then exercise the full pin/favorites/unpin/delete lifecycle:
curl -s -X POST http://localhost:8080/api/links -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
curl -s -X POST http://localhost:8080/api/links/<id>/pin
curl -s http://localhost:8080/api/links/favorites   # expiresAt must be null while pinned
curl -s -X POST http://localhost:8080/api/links/<id>/unpin
curl -s http://localhost:8080/api/links | grep <id> # fresh expiresAt, back in active list
curl -s -X DELETE http://localhost:8080/api/links/<id>
```

Bash tool calls do **not** share shell state — capture an id from one call's output and splice it
literally into the next command, or do the whole sequence in one Bash invocation with a shell
variable. Losing the id between calls silently 404s every subsequent step.

Graveyard-state and long-elapsed-time scenarios (e.g., "pin a link that's already 40 days
overdue in the graveyard") aren't reachable via the HTTP API alone (no way to backdate
`savedAt`/`expiresAt` through `POST /api/links`) — those are covered by
`LinkRepositoryTest`/`LinkServiceTest`, which construct `Link` entities directly with arbitrary
timestamps. Treat that as complementary coverage, not a gap to route around live.

`spring.h2.console.enabled=false` in `application.properties` — the H2 console isn't available for
ad-hoc live inspection; rely on the REST API and the persisted `.mv.db` file's query results
instead.
