# AI File Repository

A small document system with version history, trash recovery, permission
workflows, and a React management UI.

## Structure

- `frontend`: React, TypeScript, and Vite.
- `file_management`: document, repository, version, trash, and application-level
  permission APIs.
- `permission`: permission records and state transitions.
- `script`: schema migrations and local start/stop scripts.
- `docker-compose.yml`: local Kafka broker for batch permission propagation.

## Permission architecture

```text
Frontend
   │
   ▼
PermissionManagementController (file_management)
   ├─ validates target, tenant, user, and MANAGEABLE access
   ├─ enriches permission rows with user information
   └─ PermissionIntegration ──HTTP──▶ permission service ──▶ permission DB
```

Permissions are identified by `(targetType, targetId, userId)`. Targets can be
`FILE` or `KNOWLEDGE_REPOSITORY`; levels are hierarchical
(`READABLE < WRITABLE < MANAGEABLE`), and records move through
`PENDING`, `APPROVED`, `REJECTED`, or `REVOKED`.

The permission service is treated as an internal service. Authorization belongs
at the `file_management` boundary; production deployment should also isolate or
authenticate direct permission-service calls. A missing permission returns a
business `404`, which the integration layer interprets as “no permission”
instead of a system failure.

Permission durations are sent as milliseconds: 1 day, 3 days, 30 days, or
365 days.

## Base64 now, S3 later

Images currently remain inside document content as Base64 markers. This keeps a
version snapshot self-contained and avoids an external storage dependency, but
Base64 increases payload size, duplicates image data across versions, and puts
pressure on the database and application memory.

Moving images to S3 would keep only object keys and metadata in the database,
enable direct or pre-signed uploads, and improve caching and delivery. The
tradeoff is distributed consistency: upload authorization, orphan cleanup,
object lifecycle, and migration of existing Base64 versions must be handled.

## Local development

Requirements: Java 17, Node.js 22+, MySQL, and Docker.

```bash
export DB_USERNAME=root
export DB_PASSWORD=your-local-password

docker compose up -d
./script/start-all.sh
```

The services use MySQL databases `file_management` and `permission_system`.
The UI runs at `http://127.0.0.1:5173`; APIs run on ports `8087` and `8086`.

```bash
cd frontend && npm test && npm run build
cd ../permission && ./mvnw test
cd ../file_management && ./mvnw test
```
