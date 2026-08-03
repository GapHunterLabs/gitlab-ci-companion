# v0.2 — live pipeline status / job log fetching (deliberately not built in v0.1)

**Decided 2026-08-03, at the same time v0.1 was scoped.** This is a
deliberate scope decision, not an oversight or a "ran out of time"
placeholder — see the main README's "Why built this way" section for
the full reasoning.

## Why this is out of v0.1

The real competitor complaint this plugin is a response to
(`GitLabApiException: Field 'kind' doesn't exist on type 'CiJob'...`) is
caused by depending on a live GitLab API surface that changes between
GitLab server versions, without the plugin author having tested against
that specific drift. Building a live pipeline-status/job-log feature in
one session, with no way to test it against a real, running GitLab
instance across multiple server versions, would risk reproducing that
exact failure mode — the plugin would ship confident and untested against
the actual thing it needs to be robust to.

v0.1 sidesteps this entirely by never calling a GitLab API at all: every
check is a static, local fact about the `.gitlab-ci.yml` text itself.

## What v0.2 would need, when actually built

- A real GitLab REST/GraphQL API client, version-tolerant by design (parse
  only the fields actually needed, degrade gracefully on unexpected/missing
  fields instead of throwing on schema drift — the direct fix for the
  `GitLabApiException` complaint).
- Per-project AND per-GitLab-instance credential storage (the direct fix
  for the "adding a second PAT breaks the first" complaint) — likely
  IntelliJ Platform's `PasswordSafe` API, not plaintext settings storage.
- Testing against at least 2-3 real GitLab server versions (self-hosted
  and gitlab.com), not just one, given the complaint pattern is
  specifically about version drift.
- A live pipeline status tool window (running/success/failed per job,
  clickable through to job logs).

None of this is designed in detail yet — this file exists only to record
that it was consciously deferred, not forgotten.
