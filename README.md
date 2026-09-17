# AI File Repository

A local-first knowledge repository demo built to explore Milvus, RAG, Spring AI,
Kafka, MySQL, and a React management UI. It combines document management with
hybrid search and an AI assistant that answers from repository content.

## Demo video

**Video link:** `PASTE_DEMO_VIDEO_URL_HERE`

## Features

- **Document workspace:** create and edit documents, keep version history and
  autosaved drafts, recover deleted documents from the trash, and export to PDF.
- **Imports and previews:** upload text-based PDFs or Markdown files, preview
  Markdown, and embed images in document content. Scanned/image-only PDFs are
  not supported because OCR is not implemented.
- **Access workflows:** request and manage `READABLE`, `WRITABLE`, and
  `MANAGEABLE` permissions, including invitations, approvals, rejections,
  revocations, and in-app notifications.
- **Hybrid RAG search:** split document text asynchronously through Kafka,
  store embeddings in Milvus and chunks in MySQL, then combine vector and
  MySQL full-text results with reciprocal rank fusion. Search results mask
  content that the selected demo user cannot read.
- **AI assistant:** ask questions about repository content, request summaries,
  or expand existing material. Spring AI tools retrieve readable chunks for
  the selected demo user, and chat memory is separated by user and session.
- **React UI:** repository navigation, document editor, search, notifications,
  a demo user selector, and Chinese/English interface text.

This is a learning/demo project, not a production-ready public service. The UI
selects a user ID and the backend trusts that ID; there is no login system yet.
Do not expose the APIs to the public internet or use real confidential documents
as demo data. The RAG index lifecycle around clearing, trashing, and permanently
deleting documents also needs further hardening.

## How it fits together

```text
React UI ──▶ file_management API ──▶ MySQL (documents, versions, chunks)
                 │      │
                 │      ├──▶ permission service ──▶ MySQL (permissions)
                 │      ├──▶ Kafka ──▶ chunking/embedding worker ──▶ Milvus
                 │      └──▶ OpenAI chat and embeddings (Spring AI)
                 └──────────▶ hybrid search: Milvus + MySQL full-text
```

## Structure

- `frontend`: React, TypeScript, and Vite.
- `file_management`: document, repository, version, trash, application-level
  permission APIs, and the permission-aware AI customer-support agent.
- `permission`: permission records and state transitions.
- `script`: schema migrations and local start/stop scripts.
- `docker-compose.yml`: local Kafka broker. MySQL and Milvus are started
  separately.

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

Requirements: Java 17, Node.js 22.22+, MySQL, Milvus, Docker, and an OpenAI API
key for embeddings and AI chat. The default Milvus address is
`localhost:19530`. The two MySQL databases are `file_management` and
`permission_system`.

The repository contains incremental SQL migrations in `script/migrations/`,
but not a complete base-schema bootstrap. Prepare the base tables first, then
apply the migrations in number order for an existing installation.

```bash
cp .env.example .env
# Set DB_USERNAME, DB_PASSWORD, and OPENAI_API_KEY in .env.
# Start MySQL and Milvus separately before starting the app.

docker compose up -d  # Kafka only
./script/start-all.sh
```

`script/start-all.sh` automatically loads the root `.env` file. The file is
ignored by Git so local credentials are not committed. The UI runs at
`http://127.0.0.1:5173`; the file-management and permission APIs run on ports
`8087` and `8086`.

## Checks

```bash
cd frontend && npm test && npm run build
cd ../permission && ./mvnw test
cd ../file_management && ./mvnw test
```

The file-management application-context test requires a running Milvus
instance. See [`frontend/README.md`](frontend/README.md) for frontend-only
commands.

The AI customer-support endpoint is part of `file_management` on port `8087`.
See [`file_management/AGENT.md`](file_management/AGENT.md) for its retrieval and
permission flow.
