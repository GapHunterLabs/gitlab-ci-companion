# Privacy Policy — GitLab CI Companion

**Effective date:** 2026-08-04

GitLab CI Companion is a Gap Hunter Labs plugin for IntelliJ Platform IDEs.
This policy is short because the plugin's design makes it short: there
is nothing to disclose beyond what's below.

## What this plugin collects

**Nothing.** GitLab CI Companion does not collect, store, transmit, or sell
any data of its own — no usage analytics, no telemetry, no crash reports,
no personally identifiable information. The plugin has no backend and no
Gap Hunter Labs server ever sees your `.gitlab-ci.yml` files, your GitLab
instance, or your credentials.

## Network access

**Zero network calls by default.** Every static check (undeclared stage
references, missing `script:`/`trigger:`/`extends:`, invalid `rules:`
keys, duplicate job names, etc.) runs entirely in-process, inside your
IDE, against the `.gitlab-ci.yml` text already open in your editor.
Nothing you write, open, or edit is ever sent anywhere by default.

**Live pipeline status is a separate, opt-in feature.** If you choose to
configure a GitLab instance URL and Personal Access Token under Settings
and open the "GitLab CI" tool window, the plugin makes REST calls
directly from your machine to *your own* GitLab instance's API
(`https://<your-host>/api/v4/...`) to show real pipeline/job status —
never through a Gap Hunter Labs server. This only happens if you set it
up yourself; the plugin never calls out on its own, and every static
check above still runs with zero network access whether or not you use
this feature.

## Credentials

Personal Access Tokens are stored one per GitLab instance, via the IDE's
own credential store — never by Gap Hunter Labs, and never shared across
instances.

## Third parties

None. GitLab CI Companion has no third-party SDKs, no analytics libraries,
no ad networks. The only outbound calls are the opt-in ones described
above, made directly to your own configured GitLab instance — never to
any Gap Hunter Labs server.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the plugin's `CHANGELOG.md`.

## Contact

Questions about this policy: **gaphunterlabs@gmail.com**
