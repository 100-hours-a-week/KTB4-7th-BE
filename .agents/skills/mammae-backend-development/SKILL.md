---
name: mammae-backend-development
description: Use when planning, implementing, reviewing, or changing the Mammae Spring backend, especially when requirements, ERD, API contracts, release scope, dependencies, secrets, or database changes may be affected.
---

# Mammae Backend Development

Use this skill for any backend-impacting task. The official Notion documents are the approval boundary, not suggestions.

## Required pre-change gate

1. Read the applicable official source before designing: requirements `3cd7f3fa-ed48-80a1-a3be-f35e9ee20db3`; ERD `3d07f3fa-ed48-807a-a1e0-efe32abd2283` for persistence; API `3d57f3fa-ed48-806b-b27f-d7f04ba53d5a` for interfaces; stack `3da7f3fa-ed48-80d4-9f28-e309428a4b15` for technology decisions.
2. State the exact source IDs supporting each affected behavior, route, table, column, and dependency.
3. If a needed item is unspecified, V2/V3-only, or conflicts across sources, stop and request an approved decision. Do not fill the gap with a plausible design.

## Fixed and deferred decisions

- Use Java 25 LTS, Spring Boot 4.1.1, and MySQL 8.4 LTS/InnoDB.
- Keep application responsibilities separated as Controller → Service → Repository.
- Libraries, build tools, deployment, and monitoring remain deferred unless a later approved document selects them. Do not introduce any of them preemptively.

## Change contract

Every implementation plan or review must include: scope (V1 only unless explicitly approved), source IDs, affected contract(s), validation/error/security behavior, test evidence, and verification command(s).

Tracked configuration may show example variable names only, such as `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `APP_SECRET`. Never commit credentials or log passwords, tokens, or personal data.

## Red flags — stop

- “The route/schema/dependency is obvious.”
- “It is backward-compatible, so add it now.”
- “Use the V2/V3 section as a head start.”
- “Put the secret in an example file for convenience.”

Each means the required source approval is missing. Ask for it before changing code or schema.
