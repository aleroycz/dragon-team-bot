# 🤝 Contributing to Dragon Team Discord Bot

> Thank you for your interest in contributing! This project is open source and welcomes contributions from anyone. Every bit of help — bug reports, feature suggestions, code, or documentation — is appreciated.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Branch Strategy](#branch-strategy)
- [Commit Convention](#commit-convention)
- [Pull Request Process](#pull-request-process)
- [Code Standards](#code-standards)
- [Reporting Issues](#reporting-issues)

---

## 📜 Code of Conduct

We want this to be a welcoming and inclusive project for everyone. By participating, you agree to uphold the following standards.

### ✅ Expected Behaviour

- Be respectful and kind in all interactions
- Give constructive, actionable feedback in code reviews
- Accept feedback gracefully and engage with it in good faith
- Communicate openly about blockers, decisions, and trade-offs
- Write code as if the next person to read it is a colleague you respect
- Document your intent — not just what the code does, but why
- Raise concerns early rather than letting issues accumulate

### ❌ Unacceptable Behaviour

- Dismissive, condescending, or hostile communication of any kind
- Harassment of any form — public or private
- Deliberately obfuscating code or leaving unexplained hacks without a tracked issue
- Committing secrets, credentials, or sensitive data to the repository
- Introducing breaking changes without prior discussion

### ⚖️ Enforcement

Violations may result in a warning, temporary ban, or permanent removal from the project depending on severity. Reports can be made by opening an issue marked `[conduct]` or by contacting the maintainer directly.

---

## 🚀 Getting Started

1. **Fork** the repository and clone your fork locally
2. Copy `application.properties.example` to `application.properties` and fill in your local values
3. Run `./gradlew bootRun` to start the bot locally
4. Verify everything starts correctly before making any changes

> ⚠️ Never run against a production Discord server or production database during development. Use a dedicated test server and test database.

### Prerequisites

- Java 25+
- Gradle 9+
- A MySQL database (local or cloud)
- A Discord bot application with the following intents enabled:
    - `GUILD_MEMBERS`
    - `GUILD_MESSAGES`
    - `MESSAGE_CONTENT`
    - `GUILD_VOICE_STATES`

---

## 🌿 Branch Strategy

| Branch pattern      | Purpose                                                |
|---------------------|--------------------------------------------------------|
| `main`              | Production-ready code — protected, no direct push      |
| `dev`               | Integration branch — all features merge here first     |
| `feat/<name>`       | New features                                           |
| `fix/<name>`        | Bug fixes                                              |
| `refactor/<name>`   | Refactoring with no behaviour change                   |
| `chore/<name>`      | Dependency updates, config changes, tooling            |
| `hotfix/<name>`     | Critical production fixes — branches from `main`       |

All branches must be created from `dev` unless it is a `hotfix`, which branches from `main` and is merged back into both `main` and `dev`.

---

## 📝 Commit Convention

This project follows the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <short summary>

[optional body]

[optional footer]
```

### Types

| Type       | When to use                                              |
|------------|----------------------------------------------------------|
| `feat`     | A new feature                                            |
| `fix`      | A bug fix                                                |
| `refactor` | Code change that is neither a fix nor a feature          |
| `chore`    | Build process, dependency, or tooling changes            |
| `docs`     | Documentation only changes                               |
| `test`     | Adding or updating tests                                 |
| `perf`     | Performance improvement                                  |
| `ci`       | CI/CD configuration changes                              |

### Rules

- Summary must be lowercase and imperative mood ("add", not "added" or "adds")
- No period at the end of the summary line
- Keep the summary under 72 characters
- Use the body to explain **why**, not what — the diff already shows what changed
- Reference issue numbers in the footer where applicable (`Closes #42`)

### Examples

```
feat(voting): add force close command for active sessions
fix(consent): prevent duplicate consent form on repeated voice join
refactor(sanctions): extract warn escalation into dedicated handler
chore(deps): bump JDA to 6.4.1
docs(readme): update slash command table with new moderation commands
```

---

## 🔍 Pull Request Process

1. **Fork** the repo and create your branch from `dev`
2. **Fill in the PR description** — what changed, why, and how to test it
3. **Before requesting review, ensure:**
    - The bot compiles and starts without errors
    - No secrets, tokens, or credentials are in the diff
    - New configuration properties are documented in `application.properties.example`
    - Relevant README sections are updated if the feature is user-facing
4. **Request a review** — at least one maintainer must approve before merging
5. **Address all review comments** before merging
6. **Delete your branch** after merging

---

## 🧹 Code Standards

### General

- Java 25 — use modern language features where they improve clarity
- Follow existing package and class naming conventions
- No raw types, no unchecked casts without justification
- Prefer `Optional` over returning `null` from service methods
- Use `@Transactional` on all service methods that write to the database
- Always handle JDA async callbacks — never fire-and-forget without error handling

### Spring

- All beans via `@Component`, `@Service`, `@Repository` — no manual instantiation
- Inject all dependencies via constructor (`@RequiredArgsConstructor`) — no field injection
- All sensitive config values via `@Value` — never hardcoded
- Use `@Scheduled` for recurring tasks — document the interval in comments

### JDA

- Always null-check guilds, channels, and members before use
- Use `.queue()` for all JDA async operations — never `.complete()` on the main thread
- After `deferReply()`, always respond via `event.getHook()` — never `event.reply()` again
- Ephemeral replies for all staff-only and error responses
- Log all significant Discord interactions at `INFO`, failures at `ERROR`

### Logging

- Use `@Slf4j` — no `System.out.println` or `printStackTrace` in committed code
- Prefix log messages with `[ClassName]` or a relevant context tag
- Never log sensitive data — credentials, tokens, or private user content

### Database

- All entities must have a primary key with `GenerationType.IDENTITY` for MySQL compatibility
- Add `@Index` annotations for columns used in frequent queries
- Use `@CreationTimestamp` and `@UpdateTimestamp` for audit fields
- Never call `findAll()` and filter in memory when a repository query will do

---

## 🐛 Reporting Issues

If you find a bug or have a suggestion:

1. Check whether the issue already exists in the [issue tracker](../../issues)
2. If not, open a new issue with:
    - A clear title describing the problem
    - Steps to reproduce
    - Expected vs actual behaviour
    - Relevant logs or screenshots
    - The environment you are running in

For security vulnerabilities, please **do not open a public issue** — open a [GitHub Security Advisory](../../security/advisories/new) instead or contact the maintainer directly.

---

## 📄 License

By contributing to this project, you agree that your contributions will be licensed under the same license as the project. See [LICENSE](./LICENSE) for details.

---

*Dragon Team Discord Bot — Open Source with ❤️*