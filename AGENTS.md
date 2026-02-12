# AGENTS.md

Working agreements for AI coding agents (Codex CLI, etc.).
This is a reusable master template. Keep it broadly applicable; put repo-specific rules under **Project-specific configuration**.

## Prime directive

Ship **production-ready** changes with:

- minimal risk,
- clear intent,
- verifiable behavior,
- no hidden breakage.

If you can’t verify, say so and provide exact steps the user can run.

---

## Default autonomy and safety

- Default to **read-only exploration** first (scan files, search symbols, understand invariants).
- Keep changes **inside the repo**. Don’t modify dotfiles, global configs, or user machine state.
- Avoid bypassing safeguards (approvals/sandbox) unless explicitly requested.
- Don’t make irreversible changes when requirements are unclear—ask targeted questions _or_ proceed with the safest reversible option and label assumptions.

---

## ExecPlans

When writing complex features or significant refactors, use an **ExecPlan** as defined in **`.agent/PLANS.md`**, from design through implementation.

**Planning rules:**

- `.agent/PLANS.md` is the **source of truth** for plan format and process.
- Do **not** invent a new planning framework in chat.
- If `.agent/PLANS.md` says to create/update plan artifacts, do it and keep them current while executing.

**ExecPlan storage + lifecycle:**

- The canonical rules for where ExecPlans live, how they are named, how progress is tracked, and how they are archived are defined in `.agent/PLANS.md`.
- The ExecPlan index is **required**: `.agent/execplans/INDEX.md`.
  - If missing, ask the user to supply it from the codex-starter repo (`https://github.com/Skarian/codex-starter`).

---

## Continuity Ledger (compaction-safe)

Maintain a single continuity file for this workspace: **`.agent/CONTINUITY.md`**.  
`.agent/CONTINUITY.md` is the canonical briefing designed to survive compaction; do not rely on earlier chat/tool output unless it’s reflected there.

**Non-optional initialization:**

- If `.agent/CONTINUITY.md` is missing, ask the user to supply it from the codex-starter repo (`https://github.com/Skarian/codex-starter`).

### Operating rule

- At the start of each assistant turn: **read `.agent/CONTINUITY.md` before acting**.
- Update `.agent/CONTINUITY.md` only when there is a meaningful delta in:
  - Goal / success criteria
  - Invariants / constraints
  - Decisions
  - State (Done / Now / Next)
  - Open questions
  - Working set (key paths)
  - Important tool outcomes / verification results

### Keep it bounded (anti-bloat)

- Keep `.agent/CONTINUITY.md` short and high-signal:
  - `Snapshot`: ≤ 25 lines
  - `Done (recent)`: ≤ 7 bullets
  - `Working set`: ≤ 12 paths
  - `Receipts`: keep last 10–20 entries
- If a section exceeds caps, compress older items into milestone bullets with pointers (commit/PR/log path/doc path). Don’t paste raw logs.

### Anti-drift rules

- Facts only, no transcripts.
- Every entry must include:
  - a date or ISO timestamp (e.g., `2026-01-13` or `2026-01-13T09:42Z`)
  - a provenance tag: `[USER]`, `[CODE]`, `[TOOL]`, `[ASSUMPTION]`
- If unknown, write `UNCONFIRMED` (never guess).
- If something changes, supersede it explicitly (don’t silently rewrite history).

### Decisions and incidents

- Record durable choices in `Decisions` as ADR-lite entries (e.g., `D001 ACTIVE: …`).
- For recurring weirdness, create a small, stable incident capsule:
  - Symptoms / Evidence pointers / Mitigation / Status

### In replies

- Start with a brief **Ledger Snapshot**: Goal + Now + Next + Open Questions.
- Print the full ledger only when it materially changed or the user requests it.

---

## Discussion protocol (numbered findings → agreement → execution)

Whenever you present **findings** (risks, decisions, assumptions, questions, tradeoffs, proposed changes), you must:

- Present them as a **numbered list**.
- Keep numbering stable across back-and-forth.
- The user will reply with the same numbering; iterate until **all items are agreed**.
- **Do not begin execution** (writing code / editing files) until the numbered items are resolved.

(Exploration is fine; execution waits for agreement.)

---

## Web search (always enabled)

Web search is **always available** in this environment. Use it routinely to avoid stale assumptions.

### When to search

Search early and often when you hit any of these:

- Unfamiliar library/framework/API behavior or syntax.
- Version-sensitive docs (CLIs, flags, config formats, deprecations).
- Build/lint/test failures where the error text suggests known fixes.
- Security-relevant decisions (auth, crypto, dependency CVEs, unsafe defaults).
- “I think X is true” moments. Don’t guess—verify.

### How to search (practical rules)

- Use targeted queries: error message snippet + library name + version (if known).
- Prefer primary sources:
  - official docs / reference manuals
  - release notes / changelogs
  - upstream GitHub repos/issues (especially maintainer comments)
  - standards/RFCs when applicable
- Cross-check if the first result is a random blog. Blogs are hints, not truth.
- Pay attention to dates. If guidance is old, confirm it still applies.

### How to use results

- Summarize conclusions in your own words.
- Attribute key sources in your response when they materially affect decisions.
- Record durable outcomes in `.agent/CONTINUITY.md` under `Receipts` with: timestamp + `[TOOL]` + what was learned + source pointer(s).

### Safety + confidentiality during search

- Do not paste secrets into search queries.
- Avoid searching proprietary identifiers verbatim (internal hostnames, customer names, private URLs, unreleased codenames).
  - Sanitize queries: replace with generic terms while preserving the technical shape.

---

## Baseline workflow

Start every task by determining:

1. **Goal + acceptance criteria**
2. **Constraints** (time, risk, scope, compatibility, migration needs)
3. **What must be inspected** (files, commands, tests)

Then:

- Perform read-only inspection and gather evidence.
- Use web search to validate uncertainty, version-specific behavior, and error remediation.
- Identify any work that looks like **commodity/standard functionality** and flag it for a dependency check (see “Dependencies”).
- Present findings/questions/tradeoffs as a numbered list (see “Discussion protocol”).
- Propose:
  - a short plan (2–6 bullets) for immediate steps, **and**
  - explicitly reference the longer ExecPlan process in `.agent/PLANS.md` (and any required artifacts) so it’s clear what governs complex work.
- Wait for the user to resolve numbered items; iterate until agreement.
- Execute with small, reviewable diffs.

After any **source code** edits (documentation-only changes are exempt):

- Exhaustively update documentation impacted by the change (user-facing docs + developer docs + examples + configs + migration notes, as applicable).
- Attempt to build (or run the project’s standard build) and run linting.
- Resolve errors and warnings introduced by the change.
  - If warnings/errors are clearly pre-existing, call them out explicitly (numbered list) and propose whether to fix now or defer.

---

## Code standards

### Code style: no comments

Code should be self-explanatory. Comments should be avoided as much as possible.

- Write clear, descriptive variable and function names.
- Structure code so intent is obvious.
- Only add comments if absolutely required (e.g., a non-obvious workaround or complex algorithm that cannot be simplified).
- If code needs a comment to be understood, refactor it first.

### Modularity and file size (guideline, not religion)

- Prefer small, cohesive modules with stable seams.
- A soft signal: if a file is drifting past ~300 LOC, consider splitting.
  Not a hard cap—cohesion beats arbitrary slicing—but “giant file” should always be a deliberate choice.

### Think ahead (entrypoints stay stable)

- Don’t write code you already know will need rework without designing for the likely change now.
- Keep entrypoints stable; isolate logic behind small modules/functions so future shifts don’t require widespread edits.

### Fail fast by default

- Don’t add “default fallbacks” that hide failures during development.
- If something fails, let it fail loudly and diagnostically so it can be fixed.
- If resilience/fallbacks are truly required, implement them explicitly with:
  - clear triggers,
  - observable behavior (logs/metrics),
  - tests that prove they work.

### Error handling rules

- No empty try/catch blocks. Ever.
- Don’t swallow errors. Either handle them meaningfully or propagate with context.
- Avoid blanket `except Exception` / `catch (Throwable)` patterns unless there’s a strong, documented reason.

### UI/product rule

- Design UI for the end-user, not for the schema.

---

## Dependencies and “don’t reinvent the wheel”

- Don’t reinvent the wheel. Prefer mature, well-maintained open-source libraries when appropriate.

### Standard functionality dependency check (required)

Whenever introducing functionality that is likely standardized/commodity, do this before implementing from scratch:

- Ask the user (as part of the numbered findings list) whether they want to use a library/package/crate.
- If the answer is “yes” or “maybe”, research candidates via web search and include a short evaluation in the proposal.

The evaluation must cover (briefly, but concretely):

- Maintenance health (recent releases, activity, issue responsiveness)
- Adoption signals (stars/downloads are weak alone; prefer real usage/endorsement)
- License fit
- Security posture (known CVEs, track record, dependency surface)
- Compatibility (runtime/toolchain versions, platform support)
- Integration cost (API ergonomics, transitive deps, configuration burden)

Then present a recommendation (or 2–3 viable options) in the numbered list and ask for selection.

### Adding dependencies (general rules)

- When adding a dependency:
  - justify it (what problem it solves, why built-in code isn’t enough),
  - consider licensing and security risk.
- If there are multiple plausible libraries or a dependency is a significant decision: ask the user and help them qualify the choice.
- Record the chosen dependency decision in `.agent/CONTINUITY.md` (Decision + Receipt).

---

## Editing files

- Make the smallest safe change that solves the issue.
- Preserve existing style, conventions, and architecture unless the task is explicitly a refactor.
- Prefer patch-style edits (small, reviewable diffs) over full-file rewrites.
- Don’t mix concerns: keep “mechanical” formatting changes separate from logic changes when possible.

---

## Host package installation policy (required)

- Codex must never install system packages on the host (no `brew`, `apt`, `yum`, `pacman`, `pipx`, global `npm`, etc.) unless explicitly instructed.
- Prefer project-local tooling:
  - language/package-manager native installs (lockfile-respecting),
  - repo scripts (`make`, `just`, `task`, `npm run`, etc.),
  - local virtualenvs / local toolchains under the repo.

If tooling is missing, propose options and request explicit permission before touching the host.

---

## Secrets and sensitive data

- Never print secrets (tokens, private keys, credentials) to terminal output.
- Do not request users paste secrets.
- Avoid commands that might expose secrets (dumping env vars broadly, `cat ~/.ssh/*`, etc.).
- Redact sensitive strings in any displayed output.

---

## Definition of done

A task is done when:

- the requested change is implemented or the question is answered,
- impact is explained (what changed, where, why),
- documentation is updated exhaustively for impacted areas,
- verification is provided:
  - build attempted (when source code changed),
  - linting run (when source code changed),
  - errors/warnings addressed (or explicitly listed and agreed as out-of-scope),
  - plus tests/typecheck as applicable,
- follow-ups are listed if anything was intentionally left out,
- `.agent/CONTINUITY.md` is updated if the change materially affects goal/state/decisions.

---

## Project-specific configuration

### Reference repositories (`reference/`)

- Store third-party source repos used for research or integration under `reference/` as git submodules.
- Add new references with: `git submodule add <repo-url> reference/<repo-name>`.
- Keep submodules pinned to a specific commit (default submodule behavior). Do not rely on floating branches for builds or planning.
- Treat `reference/` repos as upstream mirrors: do not make local product changes inside them unless explicitly required.
- When updating a reference, record why the bump is needed and verify compatibility before changing the pinned commit.
