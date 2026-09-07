# Releasing

Maintainer runbook for publishing a new adapter version. Publishers do not need
anything on this page — see [README.md](README.md) for integration instructions.

## Before you start

- The matching Velocity Ads SDK version (`io.velocity:ads-sdk:<sdkVersion>`) must
  already be available on Maven Central.
- The version follows the 4-segment MAX convention
  `<sdkMajor>.<sdkMinor>.<sdkPatch>.<adapterBuild>` (e.g. `0.10.0.0`).

## Steps

1. Create a branch named `release/<version>` (e.g. `release/0.10.0.0`).
2. Bump `VERSION_NAME` in `gradle.properties`.
3. Add a `## [<version>] - YYYY-MM-DD` entry at the top of `CHANGELOG.md`.
4. Push the branch and open a PR to `main`.
5. In **Actions → Release – Publish to Maven Central**, click **Run workflow** with
   the release branch selected, enter the version, and set **Dry run** to `true`.
   The workflow refuses to run from any branch other than `release/<version>`.
6. A dry run builds, tests, and stages the artifacts to Maven Central without
   tagging or releasing. Drop the resulting staging deployment in the
   [Maven Central Portal](https://central.sonatype.com/publishing/deployments).
7. Re-run the workflow with **Dry run** set to `false`. The publish job waits for
   approval from the `production-release` GitHub Environment; tagging and the
   GitHub Release run only after the publish succeeds.
8. Merge the release PR.

## Secrets

The workflow's *Validate required secrets* step fails early and names any secret
that is missing. Configure them under **Settings → Secrets and variables →
Actions**:

| Secret | Used for |
|---|---|
| `SIGNING_KEY_ID`, `SIGNING_KEY`, `SIGNING_PASSWORD` | Signing Maven artifacts |
| `CENTRAL_PORTAL_TOKEN_USER`, `CENTRAL_PORTAL_TOKEN_PASSWORD` | Maven Central Portal authentication |
| `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `GPG_SIGNING_KEY_ID`, `GPG_TAGGER_NAME`, `GPG_TAGGER_EMAIL` | Signing the release git tag |
