# 맴매 Backend Conventions

Use `.agents/skills/mammae-backend-development/SKILL.md` before planning, changing, or reviewing backend work.

## Fixed foundation

- Java 25 LTS; Spring Boot 4.1.1; MySQL 8.4 LTS with InnoDB.
- Preserve a Controller → Service → Repository separation when application code is introduced.
- The technology-stack decision defers libraries, build tooling, deployment, and monitoring. Do not select or add them until an approved document does.

## Source of truth and scope

- Requirements `3cd7f3fa-ed48-80a1-a3be-f35e9ee20db3` define behavior and release scope.
- ERD `3d07f3fa-ed48-807a-a1e0-efe32abd2283` defines approved persistence shapes.
- API contract `3d57f3fa-ed48-806b-b27f-d7f04ba53d5a` defines approved interfaces.
- Never infer an API, table, column, route, integration, or V2/V3 feature from adjacent requirements. Stop and request an approved specification when a required contract is absent or conflicts.

## Safety and quality

- Keep secrets out of version control; use only example names such as `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `APP_SECRET` in tracked files.
- Do not log passwords, tokens, or personal data. Validate and authorize server-side; preserve the error and status behavior defined by the API contract.
- For each change, cite the applicable requirement/API/ERD identifier in the plan or PR description, add focused tests, and run the relevant verification before handoff.
