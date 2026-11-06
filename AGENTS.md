# Repository Guidelines

## Project Structure & Module Organization

SwiftRoute contains several applications:
- `services/`: six Java 21 Maven services, plus the Python `routing-engine-service` and an `ml-training-service` directory. Java code and tests live under `src/main` and `src/test`; routing tests live under `tests/`.
- `mobile-app/`: Flutter application; implementation lives in `lib/`, tests in `test/`, and images/icons in `assets/`.
- `web-admin/`: Next.js and TypeScript administration application, with source in `src/` and static assets in `public/`.
- `scripts/`: database initialization, migration, and demo utilities. `docs/` contains architecture and database documentation; `GLOSSARY.md` defines domain terminology.

## Build, Test, and Development Commands

Run commands from the indicated directory:
- Root: `mvn test` tests the Java reactor; `mvn package` builds its modules. Use JDK 21.
- Root: `docker compose up --build` starts the configured local stack; configure local environment values first.
- `web-admin/`: `npm ci`, then `npm run dev` for development. Run `npm run lint` and `npm run build` before submitting web changes.
- `mobile-app/`: `flutter pub get`, `flutter run`, `flutter analyze`, and `flutter test` install dependencies, launch, analyze, and test.
- `services/routing-engine-service/`: install `requirements.txt` into a virtual environment, then run `python -m pytest tests`.

## Coding Style & Naming Conventions

Match surrounding files and keep changes scoped. Use four-space indentation for new Java/Python code and two spaces for Dart/TypeScript. Java classes use PascalCase and packages use lowercase; Python modules and Dart filenames use snake_case. Follow existing kebab-case TypeScript filenames. Use `dart format` and Flutter lints for Dart, and the configured ESLint rules for web code.

## Testing Guidelines

Add regression coverage for changed behavior. Follow existing Java `*Test`/`*Tests`, Python `test_*.py` (pytest), and Flutter `*_test.dart` conventions. The web package has no test script; report lint/build results and relevant manual checks. Report commands actually run and any required databases or services.

## Commit & Pull Request Guidelines

Recent commits use Conventional Commit prefixes such as `feat:` and `chore:`. Keep commits focused. Pull requests should describe behavior, affected applications, linked issues, verification, and configuration or migration requirements. Include screenshots for visual changes.

## Configuration & Agent Instructions

Read `agent.md` before changes and `web-admin/AGENTS.md` before web work. Follow their applicable governance and framework instructions. Preserve existing worktree changes. Use `.env.example` for configuration names and keep credentials and local database files out of commits.