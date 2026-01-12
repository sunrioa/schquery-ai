# Repository Guidelines

## Project Structure & Module Organization

- **Backend (Maven, Java 17, Spring Boot 3)**: multi-module build defined in `pom.xml`.
  - `SchQueryAI-application/`: main Spring Boot app (`src/main/java/cn/ling/Application.java`).
  - `SchQueryAI-ai/`: AI integration (Spring AI, vector store, search, rerank).
  - `SchQueryAI-user/`: user/login/register/JWT flow (see `SchQueryAI-user/README.md`).
  - `SchQueryAI-admin/`: admin-side APIs and services.
  - `SchQueryAI-domain/`: shared domain types (DTOs, exceptions, roles).
  - `SchQueryAI-utils/`: shared utilities.
  - `SchQueryAI-mcp-server/`: MCP server (SSE) Spring Boot app.
- **Frontend (Vue 3)**: `SchQueryAI-front/` (source in `SchQueryAI-front/src/`, build output in `SchQueryAI-front/dist/`).
- **Docs/SQL**: `docs/`, `建表语句.sql`.

## Build, Test, and Development Commands

Backend (run from repo root):

```bash
mvn clean package
mvn -pl SchQueryAI-application spring-boot:run
mvn -pl SchQueryAI-mcp-server spring-boot:run
mvn -pl SchQueryAI-application test
```

Frontend:

```bash
cd SchQueryAI-front
npm install
npm run serve
npm run build
npm run lint
```

Note: Maven is configured via `.mvn/maven.config` to use `.mvn/settings.xml` and a repo-local cache in `.mvn/m2-repo/`. If dependency resolution fails on a new machine, update the mirror/path in `.mvn/settings.xml`.

## Coding Style & Naming Conventions

- Java: 4-space indentation; packages under `cn.ling`; `PascalCase` classes, `camelCase` methods/fields, `UPPER_SNAKE_CASE` constants.
- Vue/JS: 2-space indentation and single quotes (match existing `SchQueryAI-front/src/`); components `PascalCase.vue`.
- Avoid committing build outputs and local-only files: `**/target/`, `SchQueryAI-front/dist/`, local `.env*`, and any `application-*.properties` overrides.

## Testing Guidelines

- Framework: JUnit 5 (`spring-boot-starter-test`); tests currently live in `SchQueryAI-application/src/test/java`.
- Some tests/features may require external services (MySQL/Redis/LLM/Qdrant). Call out prerequisites in the PR description and keep secrets out of the repo.

## Commit & Pull Request Guidelines

- Prefer the repo’s common commit style (Conventional Commits): `feat(scope): ...`, `fix: ...`, `refactor(ui): ...`, `docs: ...`.
- PRs should include: clear “what/why”, linked issue (if any), screenshots for UI changes, and notes for config/schema changes. Run `mvn -pl SchQueryAI-application test` and `npm run lint` before requesting review.

## Security & Configuration Tips

- Do not commit real credentials (API keys, DB/Redis/mail passwords). Use environment variables or a gitignored `application-local.yml`/`.env.local`, and keep shared configs safe by default.
