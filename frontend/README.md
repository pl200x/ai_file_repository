# AI Knowledge Repository Frontend

The browser application is a Vite, React, and TypeScript project. During local
development it runs on `http://127.0.0.1:5173` and proxies relative `/api`
requests to the file-management service on port `8087`.

## Commands

- `npm run dev` starts only the frontend.
- `npm run typecheck` validates TypeScript types.
- `npm run lint` checks the frontend source.
- `npm run build` creates the production bundle in `dist/`.

From the repository root, `script/start-all.sh` builds and starts the frontend
and both Spring Boot services. `script/stop-all.sh` stops those three processes.

Kafka remains managed separately through the root `docker-compose.yml`, and the
two MySQL databases must be available before running the complete stack.
