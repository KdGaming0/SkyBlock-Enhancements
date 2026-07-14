# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Project overview

SkyBlock Enhancements is a Fabric client mod for Minecraft (Hypixel SkyBlock) providing quality-of-life features: reminders, tooltip/price enhancements, mining tools, drop guards, slot locking, fullbright, and more. Written in Java, built with Gradle + Loom + Stonecutter.

## Build system

This repo uses **Stonecutter** (`dev.kikugie.stonecutter`) for multi-Minecraft-version builds. Currently only one version node exists: `26.1` (backed by MC `26.1.2`), configured in `settings.gradle.kts`. Version-specific sources live under `versions/26.1/`; the root `build.gradle.kts` and `src/` are shared/active-version files that Stonecutter manages via symlink-like syncing — do not assume `src/` is a normal single-version source set when reasoning about multi-version behavior.

Key properties are centralized in `stonecutter.properties.toml` (mod id/name/group/version, per-MC-version dependency versions) rather than scattered across build scripts.

### Common commands

Run all Gradle commands through the wrapper (`./gradlew`), targeting the active Stonecutter node `26.1`:

- Build: `./gradlew build` (or `./gradlew :26.1:build`)
- Build and collect jar to `build/libs/<mod version>/`: `./gradlew buildAndCollect`
- Run the client (dev environment, shared `run/` dir): `./gradlew runClient`
- Run tests: `./gradlew test` (JUnit 5 via `junit-platform-launcher`; no test sources currently exist in the repo)
- Publish to Modrinth/CurseForge for all release versions: `./gradlew publishToAllPlatforms` (requires `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN` env vars; otherwise runs as dry-run)
- Switch/add Stonecutter version nodes: see `stonecutter.gradle.kts` and the Stonecutter docs linked there — do not hand-edit generated version folders.

The mod requires Java 25 for MC 26.1+ (Java 21 for older, unused in this repo currently) — see `requiredJava` logic in `build.gradle.kts`.

## Architecture

Package root: `com.github.kd_gaming1.skyblockenhancements`.

- **`SkyblockEnhancements.java`** — `ClientModInitializer` entry point. Wires up config, storage (`ReminderStorage`, `SlotManager` persistence, price cache), and calls `.register()`/`.init()` on each feature module. Read this file first to see what's wired together and in what order.
- **`feature/`** — one subpackage per feature (e.g. `reminder/`, `pricing/`, `slotmanage/`, `dropguard/`, `katreminder/`, `mining/`, `missingenchants/`, `potionoverlay/`, `tooltipscroll/`, `savecursorposition/`, `mapextender/`, `filter/`). Each feature is generally self-contained with its own manager/registration class plus supporting data/parsing classes.
- **`mixin/`** — Mixin classes, grouped by feature in subpackages (`dropguard/`, `itemglow/`, `potionoverlay/`, `savecursorposition/`, `slotmanage/`, `tooltipscroll/`, `access/`). Registered in `src/main/resources/skyblock_enhancements.mixins.json`. `SkyblockEnhancementsMixinPlugin` (`mixin/`) implements `IMixinConfigPlugin` and controls conditional mixin application (`shouldApplyMixin`, `acceptTargets`, etc.) — check it when a mixin needs conditional loading.
- **`command/`** — Fabric client commands: `Commands` (registration hub), `ReminderCommand` (`/remindme ...`), `DebugCommand`.
- **`gui/`** — Custom screens/components, notably `gui/reminder/` and `gui/reminder/component/` for the `/remindme gui` reminder management UI (built on `ReminderScreenState`, the most-connected class in the codebase).
- **`repo/`** — I/O and network layer: `repo/io/` (e.g. atomic file writers for JSON persistence) and `repo/network/`.
- **`util/`** — Shared helpers, including `util/tab/` (tab-list SkyBlock stat parsing) and `util/tool/` (pickaxe/tool stat extraction, `ToolStat`).
- **`config/`** — `SkyblockEnhancementsConfig`, backed by MidnightLib (`MidnightConfig`), exposed in-game via `/skyblockenhancements config` or ModMenu.

### Central/high-connectivity classes (from the knowledge graph)

These classes are touched by many features — check impact broadly before changing them: `ReminderScreenState`, `ReminderManager`, `ToolStat`, `Reminder`, `SlotManager`, `ReminderCommand`, `SkyblockStats`, `DebugCommand`, `ToolInfo`, `ModSettings`.

### Persistence

Reminders, slot locks, price cache, and profile ID cache are all persisted as JSON under the Fabric config dir (`<config>/skyblock_enhancements/`), written via atomic file writers in `repo/io/`. Look at `ReminderStorage`/`RemindersFileData` as the reference pattern for adding new persisted state.

### External integrations

- **Hypixel networking**: `net.azureaaron.hmapi` (`hm-api`) — used for location packet tracking (`HypixelLocationState`) and other Hypixel-specific protocol data.
- **Pricing**: `PriceDataFetcher` fetches AH/Bazaar price data over HTTP into `PriceStore`, consumed by `PriceTooltipEnhancement`.
- **Config UI**: MidnightLib (`MidnightConfig`) and UI Lib (`maven.modrinth:ui-lib`) for in-game config/GUI components.

# Development Workflow

## Documentation

- `CLAUDE.md`: Project architecture and design overview.
- `IMPLEMENTATION_LOG.md`: Record of implementation decisions and reasoning. Keep entries concise (maximum 500 lines total).

## Workflow

### 1. Investigate First

Before writing any code, investigate the requested feature or reported issue.

- If any requirement is unclear, **stop and ask for clarification** before proceeding. Do not make assumptions. It is better to clarify early than to implement the wrong solution.
- Explain your findings briefly after the investigation.
- If the investigation naturally splits into independent tasks (for example, tracing a rendering bug, auditing mixin order, and checking overlay lifecycle), suggest running parallel sub-agents, with one agent handling each independent task. Only suggest this when it provides a meaningful speed or quality improvement.

### 2. Present a Plan

After completing the investigation:

- Present a short implementation plan or specification.
- Explain how you intend to solve the problem.
- Wait for explicit approval before writing any code.

### 3. Implement

Once approval has been given:

- Implement the planned changes.
- Keep the implementation focused on the approved scope.
- Avoid unrelated refactoring unless it is required to complete the task safely.

### 4. Validate

Before considering the work complete:

- Run all relevant tests.
- Run:

```bash
./gradlew build
```

to ensure the project builds successfully and all automated checks pass.

If manual in-game testing is required, clearly describe:

- what should be tested
- how to reproduce it
- what the expected result is

Wait for the user to complete manual testing and report back with the results before finalizing the task.

### 5. Documentation

After all testing has passed:

- Add a concise entry to `IMPLEMENTATION_LOG.md` describing:
  - what changed
  - why the change was made
  - any notable implementation decisions

- Run:

```bash
graphify update .
```

to keep the project graph up to date.

- If the work fixes a bug or introduces a user-visible feature, update `CHANGELOG.md` with a short, non-technical description suitable for end users. Avoid implementation details and internal terminology.

## General Principles

- Investigate before implementing.
- Ask for clarification instead of assuming.
- Do not write code before approval.
- Validate all changes before considering the task complete.
- Keep documentation up to date with every completed change.

### Scope Control

Only modify files that are necessary for the requested change. Avoid unrelated formatting changes, refactoring, or file reorganizations unless explicitly requested or required to complete the task safely.

### Preserve Existing Behavior

Unless the request explicitly changes existing functionality, preserve current behavior. If a proposed implementation requires changing existing behavior or introduces trade-offs, explain them in the implementation plan and wait for approval.
