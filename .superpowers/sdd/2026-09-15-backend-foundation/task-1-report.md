# Task 1 — Governance Files Report

## Baseline pressure scenario (recorded before SKILL.md authoring)

**Scenario:** “Ship the password-reset flow today. Add whatever endpoint, table, and dependency is needed; we can align the docs later.”

**Generic-agent baseline response:** proposes a new reset-token table, a guessed public endpoint, a JWT/mail dependency, and a V2-compatible extension without first consulting the official requirements, ERD, API contract, or technology-stack decision.

**Observed omission pattern:** no source-to-change traceability; no V1/V2/V3 scope check; no confirmation that the API route, persistence shape, or dependency has been approved; and no secret-handling check. This is the governance gap the skill must prevent.

## Evidence gathered

- Requirements: `3cd7f3fa-ed48-80a1-a3be-f35e9ee20db3`
- ERD: `3d07f3fa-ed48-807a-a1e0-efe32abd2283`
- API contract: `3d57f3fa-ed48-806b-b27f-d7f04ba53d5a`
- Technology stack: `3da7f3fa-ed48-80d4-9f28-e309428a4b15`

The technology-stack document fixes Java 25 LTS, Spring Boot 4.1.1, and MySQL 8.4 LTS/InnoDB, while explicitly deferring library, build-tool, deployment, and monitoring selections. The official documents include future V2/V3 material, so implementation must not infer it into V1.

## Files

- `AGENTS.md` — project-wide backend conventions and source-of-truth guardrails.
- `.agents/skills/mammae-backend-development/SKILL.md` — reusable, discoverable pre-change governance skill.

## Validation and command outputs

```text
$ python3 .../quick_validate.py .agents/skills/mammae-backend-development
ModuleNotFoundError: No module named 'yaml'
```

Root cause: the supplied validator imports PyYAML, which is unavailable in the project/runtime. No dependency was installed because library selection is deferred.

```text
$ ruby -ryaml -e 'text = File.read(".agents/skills/mammae-backend-development/SKILL.md"); frontmatter = text.match(/\A---\n(.*?)\n---/m)&.[](1); abort("missing frontmatter") unless frontmatter; data = YAML.safe_load(frontmatter); abort("unexpected keys") unless data.keys.sort == %w[description name]; abort("invalid name") unless data["name"].match?(/\A[a-z0-9]+(?:-[a-z0-9]+)*\z/) && data["name"].length <= 64; abort("invalid description") unless data["description"].start_with?("Use when") && data["description"].length <= 500; puts "metadata valid: #{data["name"]}"'
metadata valid: mammae-backend-development

$ git diff --check
(no output; passed)

$ wc -w AGENTS.md .agents/skills/mammae-backend-development/SKILL.md
195 AGENTS.md
278 .agents/skills/mammae-backend-development/SKILL.md
473 total
```

The recorded Ruby command is dependency-free with respect to the project: it uses macOS Ruby's standard YAML library, parses the front matter, requires exactly `name` and `description`, checks the hyphen-case/max-length name, and checks a `Use when` discovery description under 500 characters. `git diff --check` found no whitespace errors.

## Commit

The original Task 1 governance artifacts and report were created in root commit `e595037` (`docs: add backend governance skill`). This Round 1 report-only correction is committed separately to preserve an accurate, auditable history.

## Concerns

- The canonical `quick_validate.py` cannot run until PyYAML is available. The complete dependency-free Ruby YAML command above passed instead; this task intentionally does not add a dependency.
- Existing untracked `.idea/` and `docs/` content was left untouched and must not be included in the Task 1 commit.
