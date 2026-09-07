# AGENTS.md — velocityads-android-max-adapter

Engineering guide for contributors and coding agents working on the Velocity Ads AppLovin MAX adapter for Android.

---

## ⚠️ This is a public repository

This repository is publicly visible. Every file in it — `README.md`, `CHANGELOG.md`, source code comments, commit messages, and any other documentation — can be read by anyone, including publishers, competitors, and the general public.

### What must never appear in this repo

- Internal repository names (e.g. SDK internal repos, internal tooling repos).
- Internal field names, API paths, server endpoints, or request/response structures that are not part of the public SDK surface.
- Roadmap information: future mediation platforms, future adapter plans, or any unreleased product direction.
- Naming convention strategy documents or internal architecture decisions.
- Org-level CI secrets, credentials, or values (secret *names* in `RELEASING.md` are fine; actual values, key formats, key provenance, or where the same key is reused are never acceptable).
- References to internal tools, dashboards, or services not accessible to publishers.
- Internal SDK bridge APIs (e.g. mediation/plugin bridge seams) and how telemetry or analytics are attributed. Publishers integrate through MAX; they do not need to know how the adapter talks to the SDK internally.

### What belongs here

- `README.md` — **publisher-facing only**: how to add the adapter, configure MAX, handle privacy, supported formats, version compatibility. No maintainer or release content.
- `RELEASING.md` — contributor-facing release runbook: how to bump versions, trigger the release workflow, and the *names* of required secrets.
- Adapter behaviour documentation (initialization, ad formats, error handling).
- Public-facing `CHANGELOG.md` entries describing user-visible changes.

### Rule for coding agents

Before writing or editing any file that will be committed to this repo, ask: *could a publisher or external developer read this and learn something we did not intend to disclose?* If yes, rewrite or omit it.

---

## Project overview

This library is the official AppLovin MAX **custom-network adapter** that bridges the Velocity Ads Android SDK (`io.velocity:ads-sdk`) into the MAX mediation waterfall.

- **Repository**: `velocityads-android-max-adapter` (public)
- **Published artifact**: `io.velocity:max-mediation` on Maven Central
- **Version scheme**: 4-segment (`<sdkMajor>.<sdkMinor>.<sdkPatch>.<adapterBuild>`) — the 4th segment increments for adapter-only fixes against the same SDK version
- **Minimum Android SDK**: API 24
- **Supported MAX SDK**: 13.x

---

## Module layout

```
velocityads-android-max-adapter/
├── velocity-max-adapter/                       # The adapter library module (published AAR)
│   └── src/main/java/com/applovin/mediation/adapters/
│       ├── VelocityAdsMediationAdapter.kt      # Core adapter: init, destroy, FormatAdapterContext impl
│       ├── FormatAdapterContext.kt             # Interface injected into format adapters
│       ├── VelocityInterstitialFormatAdapter.kt
│       ├── VelocityRewardedFormatAdapter.kt
│       ├── VelocityBannerFormatAdapter.kt
│       ├── VelocityInterstitialAdHandler.kt    # Translates Velocity callbacks → MAX interstitial
│       ├── VelocityRewardedAdHandler.kt        # Translates Velocity callbacks → MAX rewarded
│       ├── VelocityBannerAdHandler.kt          # Manages banner load + translates callbacks
│       ├── VelocityAdsErrorMapper.kt           # Maps VelocityAdsError → MaxAdapterError
│       └── InitCoalescer.kt                    # Coalesces concurrent init attempts
├── build.gradle                                # Root build: Nexus publish plugin + ktlint plugin
├── gradle.properties                           # VERSION_NAME, GROUP, ARTIFACT_ID
└── .github/workflows/
    ├── tests.yml                               # CI: ktlint + unit tests on PR/push to main
    └── release.yml                             # Release: validate → build+publish → tag+GitHub Release
```

---

## Adapter class name

The class registered in the MAX dashboard **Custom Network** entry is:

```
com.applovin.mediation.adapters.VelocityAdsMediationAdapter
```

Do not rename this class — it is a hard-coded string in every publisher's MAX dashboard configuration.

---

## Versioning

Version source of truth: `VERSION_NAME` in `gradle.properties`.

The version follows the MAX 4-segment convention: `<sdkMajor>.<sdkMinor>.<sdkPatch>.<adapterBuild>`. Git tags use the same 4-segment string (e.g. `0.10.0.0`).

---

## Build & verification

```bash
# Fast check — ktlint + unit tests only (~1–2 min)
./gradlew :velocity-max-adapter:build

# Verify ktlint formatting only
./gradlew :velocity-max-adapter:ktlintCheck

# Auto-fix ktlint violations
./gradlew :velocity-max-adapter:ktlintFormat
```

---

## CI workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `tests.yml` | PR / push to `main` | ktlint check + full build + unit tests |
| `release.yml` | Manual dispatch | Validate versions → build + publish to Maven Central → GPG-signed tag + GitHub Release |

The `env:` block at the top of each workflow file contains all adapter-specific values (module name, artifact ID). When creating a new mediation adapter repo, copy the workflow files and update only that block.

---

## Kotlin conventions

Follow the conventions from the Velocity Ads Android SDK `AGENTS.md`:

- Kotlin-first; all new code in Kotlin.
- `internal` for anything not part of the public adapter surface.
- No `e.printStackTrace()` — use `Log.e(TAG, message, e)`.
- Coroutines for async work; no raw `Thread`.
- Code must pass `ktlintCheck` before merging.

---

## Changelog convention

Follow [Keep a Changelog](https://keepachangelog.com). Use `### Added`, `### Changed`, `### Fixed`, `### Breaking Changes` as section headings. Write for publishers — describe user-visible behaviour, not internal implementation.

---

## Code hygiene

Delete everything that no longer reflects the current state of the codebase:

- Dead code and unused imports.
- Stale comments or narration-only comments.
- Any reference to internal repos, tools, or field names (see the **Public repository** section above).

---

## Pre-merge checklist

1. `./gradlew :velocity-max-adapter:build` passes (ktlint + unit tests).
2. No internal repo names, field names, key material details, bridge APIs, or roadmap content in any committed file.
3. `CHANGELOG.md` updated if the change is user-visible.
4. `README.md` updated if the public-facing integration instructions changed; `RELEASING.md` updated if the release procedure changed.
