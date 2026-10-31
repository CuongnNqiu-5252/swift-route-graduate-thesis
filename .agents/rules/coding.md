---
trigger: always_on
---

# SwiftRoute — AI Coding Agent Working Rules

This file applies to all AI agents (Claude Code or equivalent) assisting with code in this repository. Read it before starting any task.

---

## 1. General Working Principles

* **If asked to do a specific task → do it directly.** Do not ask for confirmation if the request is clear enough to implement.
* **Explain clearly after completing the code**: state what was changed, where it was changed, and why the chosen approach was used — do not simply dump code and stop.
* **Do not expand the scope unnecessarily** (scope creep) beyond what was requested. If you identify related work that may be useful, **suggest it** rather than doing it automatically.
* **Do not modify the original scaffold structure** (initialized with `spring init`, `create-next-app`, or `flutter create`) unless explicitly requested — do not change `pom.xml`, `package.json`, or the default directory structure unless it is necessary for the current task.
* **When the request is ambiguous**: make the most reasonable assumption, **state the assumption clearly in the response**, and proceed — only stop to ask a question when making the wrong assumption could cause significant consequences (e.g. changing a schema that already contains data or changing an agreed-upon architecture).
* **If the request conflicts with the existing design** (schema, state machine, API contract already defined in `docs/`) → **warn before making changes**. Do not silently modify an existing design without explaining it.
* Always **follow existing patterns in the codebase** — if there is already a similar example (e.g. a sample Controller/Service), follow the same structure for new components instead of inventing a different organizational approach.

---

## 2. Language

| Type                                           | Language                                                                                               |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Code comments, Javadocs, docstrings            | **English**                                                                                            |
| Variable, function, class, and package names   | **English**, meaningful, avoid unclear abbreviations                                                   |
| Commit messages                                | **English**, following Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`) |
| Explanations/discussions with the user in chat | **Vietnamese**                                                                                         |
| Design documentation in `docs/`                | Vietnamese (keep consistent with the thesis documentation)                                             |

---

## 3. General Project Conventions (All Services)

* **IDs**: Use UUIDs for all entities; do not use auto-incrementing IDs such as `Long`.
* **JSON fields**: Use `snake_case` instead of `camelCase` — configure Jackson `SnakeCaseStrategy` for every Spring Boot service.
* **Timestamps**: Use UTC in ISO-8601 format — do not manually add/subtract time zones in the backend. Vietnam time conversion should be handled at the presentation/client layer.
* **Standard response wrapper**: `{ "data": {...}, "error": { "code", "message" } | null, "timestamp": "..." }`
* **Database migrations**: All database schema changes must go through Flyway. Do **not** use `spring.jpa.hibernate.ddl-auto=update` outside local test environments.
* **Secrets** (JWT secrets, database passwords, payment gateway API keys, etc.): read them from environment variables, **never hardcode them**, and never commit the real `.env` file to Git.
* **All state machines** (Order status, Tenant status, Payment status, etc.) must **validate valid state transitions in the service layer**, not rely solely on database `CHECK` constraints. Database constraints are the final safety layer, not the primary place for business logic.
* **Cross-service references** (e.g. `orders.customer_id` referencing `auth_db`): do not create physical foreign keys. Use logical references only. Refer to `docs/schema-api-contract-*.md` to determine which relationships are real foreign keys and which are logical references.
* **RabbitMQ**: Use a shared topic exchange named `swiftroute.events`, with routing keys in the format `<domain>.<event>` (e.g. `order.created`, `payment.completed`).

---

## 4. Stack-Specific Conventions

### Spring Boot (Java 21, Maven)

* Standard package structure: `controller / service / repository / entity / dto (request, response) / mapper / exception / config / common`
* **Separate DTOs from Entities** — never return an Entity directly through a REST API.
* Handle errors centrally using `@RestControllerAdvice` + custom exceptions. Do not scatter `try-catch` blocks across Controllers.
* Validate input using Bean Validation (`@Valid` + annotations on DTOs), rather than manually validating inside services.
* Methods containing **two or more database write operations** must use `@Transactional`.
* Every new endpoint must include an `@Operation` annotation (springdoc-openapi) with a short description.
* Use Java `record` for DTOs whenever practical (immutable and less boilerplate than regular classes).
* Unit tests should use Mockito. Integration tests should use **Testcontainers with real PostgreSQL** — do not use H2, because Flyway migrations may rely on PostgreSQL-specific syntax such as `gen_random_uuid()`.

### Python (FastAPI) — Routing Engine, ML Training Service, Agent Services

* Use complete type hints for every function signature.
* Define request/response schemas using Pydantic (`BaseModel`), rather than raw `dict`.
* Clearly separate three layers: `api/routes.py` (endpoints) → `service/` (business logic) → `core/` (pure algorithms with no FastAPI dependency).
* Algorithms (DP, ML pruning) must be implemented in `core/` and include docstrings explaining their inputs, outputs, and computational complexity.
* Use `pytest` for testing, with test files named `test_<module>.py`.

### Next.js (App Router) + TypeScript — Web Admin

* Use the **App Router** (`src/app/`). Do not create a `pages/` directory using the legacy Pages Router.
* Put reusable components under `src/components/`, organized by domain (`src/components/dashboard/`, `src/components/orders/`, etc.).
* Call APIs through a single shared Axios instance at `src/lib/api.ts`. Do not create multiple scattered `axios.create()` instances.
* Prefer Server Components by default. Add `"use client"` only when client-side interactivity is actually required (state, event handlers, etc.).

### Flutter — Mobile App

* HTTP client: **Dio** (do not use the `http` package).
* State management: **flutter_riverpod** with the `@riverpod` annotation (`riverpod_generator`). Prefer `AsyncNotifier` for asynchronous API flows.
* Use a feature-based structure: `lib/features/<feature>/data/` (Dio client, repository) → `application/` (Riverpod providers) → `presentation/` (screens, widgets).
* After adding a new provider, always remind the user to run `dart run build_runner watch -d` to generate the `.g.dart` files.
* Store tokens using `flutter_secure_storage`. Do not use `SharedPreferences` for access or refresh tokens.

---

## 5. Security

The following points are commonly overlooked:

* **Refresh tokens**: store only their **SHA-256 hash** in the database, never the plaintext token.
* **Access tokens**: do **NOT** store them in the database; only verify the JWT signature.
* **Failed login attempts**: return the **same message** for both "incorrect password" and "email does not exist" so that the system does not reveal which email addresses are registered.
* **Payment webhooks (VNPay IPN)**: always verify the HMAC signature before processing, handle requests **idempotently** (the gateway may send the same notification multiple times), and never trust the Return URL as confirmation of payment.
* Never log sensitive information (passwords, plaintext tokens, card numbers, etc.) to the console or log files.

---

## 6. When the Agent Writes Algorithm-Related Code (DP, ML)

* Preserve the existing **API contracts** (`/optimize/batch`, `/optimize/insert`) when changing internal algorithms — other services must not be required to change.
* The `algorithm_used` field in the response must accurately reflect the algorithm currently being executed (`naive_nearest_neighbor`, `dp_ml_pruning`, etc.). This field is used for comparison in the thesis evaluation chapter.
* Do not remove baseline algorithm implementations (Naive, Genetic Algorithm, etc.) after introducing DP + ML. They must remain available for experimental comparison.

---

## 7. Before Considering a Task "Done"

* [ ] Code contains English comments for non-obvious logic.
* [ ] There is at least a minimal test for important logic (100% coverage is not required).
* [ ] No secrets or environment-specific configuration are hardcoded.
* [ ] The response format matches the shared `ApiResponse` wrapper.
* [ ] If the schema is changed → create a new Flyway migration file; never modify an already-applied migration.
* [ ] If an API is added or changed → update the corresponding contract file in `docs/` if there is any discrepancy.
