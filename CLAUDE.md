# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This repository is currently **empty** except for the spec document
(`Тестовое-задание-ИИ-агенты.pdf`). No Maven project, source tree, or git
history exists yet. Everything below is the *target* architecture and
process defined by that spec — treat it as the contract to build against,
not as a description of existing code. Once the project is scaffolded,
update this file to point at real commands and drop the speculative framing.

## What this project is

A mini mail assistant ("Kolya"-style) that:
1. Polls an Outlook inbox for unread mail.
2. Runs each unread message through an LLM tool-calling loop, using the
   mail body as the request.
3. Replies to the sender by email.

One email = one unit of dialogue. One instance, one mailbox — no
multi-user, no OAuth/SSO, no web panel.

## Required stack

- **Java 8** (exactly 8), **Maven**, final artifact is a **fat-jar** via `maven-shade-plugin`.
- LLM access over **HTTP** (okhttp or equivalent).
- Config in **YAML** (jackson or snakeyaml).
- Outlook via **COM/JACOB** (`net.sf.jacob-project:jacob:1.20`), native lib
  `jacob-1.20.x64.dll` expected on `PATH` on the Windows test machine.
- Tests: **JUnit 4**. Logging: **slf4j/logback**.

## Dev machine (macOS/Apple Silicon) vs. grading machine (Windows/Intel)

Development happens on an Apple Silicon Mac; grading and the live JACOB
demo happen on a Windows x64 machine. This split is a real source of bugs
that won't show up in local testing — treat the following as hard rules,
not suggestions:

- **`OutlookMailChannel` cannot be exercised locally at all**, not even
  manually. JACOB's static initializer fails outside Windows the same way
  it does on Linux (this is why it's excluded from the test classpath —
  see below). Every other class must be fully covered by tests against
  `MockMailChannel`/mocks; `OutlookMailChannel` only gets validated on the
  Windows box, whenever that access happens. Keep it as thin as possible
  (COM calls + mapping to `Msg`/`MailChannel`) so there's little logic left
  to be wrong when it finally runs for the first time.
- **JDK 8 on Apple Silicon needs an explicit non-default distribution** —
  there's no official Oracle/OpenJDK 8 macOS arm64 build. Use Azul Zulu JDK
  8 (aarch64) or an x86_64 JDK 8 under Rosetta 2. Don't assume a plain
  `brew install openjdk@8` works.
- **Force UTF-8 everywhere, explicitly, don't rely on the platform
  default.** macOS defaults to UTF-8; Java 8 on Windows with a Russian
  locale commonly defaults to `windows-1251`. The test mailbox content is
  Cyrillic. Any file/stream I/O that doesn't pin a charset (seen-store,
  JSON reminder store, audit log, logback encoders) will look fine in
  local tests and mangle Cyrillic text on the grading machine. Always use
  `StandardCharsets.UTF_8` explicitly in readers/writers, and set
  `<argLine>-Dfile.encoding=UTF-8</argLine>` in surefire plus
  `-Dfile.encoding=UTF-8` in the run command / manifest.
- **Pin the clock's time zone explicitly.** The injectable `Clock` for
  `current_datetime()` must use a `ZoneId` from config, not
  `Clock.systemDefaultZone()` — otherwise behavior depends on which
  machine (dev vs. grading) it's running on.
- **Never hardcode path separators.** Use `Paths.get(...)` /
  `File.separator` for `store.path` and friends.
- The fat-jar itself is portable (plain bytecode) — building it on Mac and
  running it on Windows under a matching Java 8 major version is not a
  concern by itself, but this should still be verified at least once
  before the defense, not assumed.

## Commands (once the Maven project exists)

- `mvn test` — must be **green without Outlook installed** (this is the CI
  path). JACOB's static initializer calls `System.exit` on non-Windows, so
  the `jacob` artifact must be excluded from the test classpath via
  `classpathDependencyExcludes` in the surefire plugin config.
- `mvn package` — produces the runnable fat-jar.
- Run a single test: `mvn test -Dtest=ClassName#methodName`.

## Architecture

### Mail channel
- `MailChannel` interface: at minimum `List<Msg> fetchUnread()` and
  `void reply(Msg, String body)`.
- Two implementations: `OutlookMailChannel` (real, via JACOB COM) and
  `MockMailChannel` (for tests — this is what all non-live tests must run
  against).
- **Idempotency is a hard requirement**: each message is processed exactly
  once, and this survives a process restart. Dedup key must be a stable
  message identifier (Outlook `EntryID` / `Message-ID`) — never
  subject/body. A seen-store persisted to disk backs this. A poll loop that
  replies to the same email on every cycle is considered a core defect, not
  a nice-to-have gap.

### LLM
- `LlmClient` interface: `chat(messages, tools) -> response`. Implementations:
  real HTTP client and a mock (for deterministic tests).
- API key/secret comes **only** from an environment variable; the env var
  *name* is configured in YAML, never the secret itself.

### Tools (≥2 required)
Suggested set — deterministic and unit-testable:
- `current_datetime()` — via an injectable `Clock`, so tests are deterministic.
- `add_reminder(text, dueIso)` — writes to a local JSON store.
- `find_items(query)` — searches that store.

Tool contracts can be designed freely as long as they stay deterministic
and testable.

### Tool loop
- Bounded by `agent.maxSteps`.
- Must tolerate malformed/hallucinated `tool_call`s: return an error to the
  model and terminate cleanly — never crash the loop.

### Config (YAML)
Keys: `llm.endpoint`, `llm.model`, `llm.apiKeyEnv`, `llm.timeoutMs`,
`agent.maxSteps`, `store.path`, `mail.pollSeconds`, `mail.profile`,
`mail.folder`. No secrets in code or in git, ever.

### Graceful fallback
- LLM unavailable/timeout → a clear fallback reply email, or skip + WARN
  log. Never surface a stack trace to the end user.
- COM error → WARN and keep the loop alive; move on to the next message.

### PII and security
- Email bodies contain personal data — never log raw message bodies/PII;
  mask if logging is needed at all.
- Secrets only from environment variables.
- Append-only audit log of actions taken (which message was processed,
  which tool calls were made). A hash-chain for tamper-evidence is
  preferred (mirrors the `HmacSigner` pattern from the reference "Kolya"
  project).

### Logging
Structured event-keys (e.g. `agent_mail_seen`, `agent_tool_call tool=...`,
`llm_failed ...`). No PII in logs, ever.

## Explicitly out of scope

Real Telegram, Confluence, calendar integrations, DPAPI/cookies,
RAG/embeddings, any datastore beyond a JSON file, multi-tenancy,
OAuth/SSO, a web panel, deployment beyond the fat-jar.

## Required working process

This assignment grades *how* the code is built, not just the result —
follow this even when it feels slower:

- **Plan-first**: write a short plan/issue list in `PLAN.md` before writing
  code.
- **TDD**: a failing test precedes every piece of production code; the
  red→green transition must be visible as separate commits in git history.
- **Check docs before using a library** (Context7 or official docs) rather
  than relying on training-data assumptions about API shape.
- **Atomic commits**: many small, meaningful commits — not one large
  AI-authored dump.
- **security-review before calling anything done**: self-review for leaked
  secrets, PII in logs, and injection risk via tool arguments.
- **Verify before claiming done**: actually run `mvn test` / `mvn package`
  and show the output — don't assert something works without evidence.

## What "done" requires

- `mvn package` produces a runnable fat-jar.
- `mvn test` is green on a machine without Outlook installed.
- Both `OutlookMailChannel` (JACOB) and `MockMailChannel` exist.
- ≥2 tools, tool-loop verified against the mock LLM.
- Idempotency (seen-store) verified, including surviving a restart.
- Config-driven, secrets from env only, nothing secret committed.
- Graceful fallback on both LLM and COM failures.
- Structured logs with no PII.
- Append-only audit log of actions.
- `PLAN.md`, a Claude Code session export, and `README.md` (build/run/test
  instructions plus a "how I worked with AI" section) are all present.
