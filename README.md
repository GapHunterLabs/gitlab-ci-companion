# GitLab CI Companion

IntelliJ-family plugin. Static syntax and structural checks for
`.gitlab-ci.yml` — catch mistakes before running a real pipeline, not
after.

## Why it exists

Born from real evidence in JetBrains Marketplace reviews of "GitLab
CICD — Pipelines & Jobs, Builds Run Cancel Retry View Log" (26,891
downloads, paid), not assumptions:

- `org.gitlab4j.api.GitLabApiException: Field 'kind' doesn't exist on
  type 'CiJob', Field 'downstreamPipeline' doesn't exist on type
  'CiJob'...` — the plugin breaks against newer self-hosted GitLab
  instances after API schema changes, reported repeatedly across
  different GitLab versions (14.6.1, 14.10.5).
- "each time I need to add PAT to this plugin. And when I'm adding the
  PAT for the second gitlab, then the first becomes useless" — no
  per-project/multi-instance credential scoping.

## Why built this way

- **Zero network calls by default; live pipeline status is opt-in.**
  Both complaints above trace back to the same root cause: depending
  on a live GitLab API surface that can drift out from under the
  plugin (schema changes) or needs credential management the plugin
  gets wrong (multi-instance PATs). Every static check still runs
  purely against the `.gitlab-ci.yml` text open in the editor, with no
  network call, exactly as in v0.1 — that promise never changes.
  Configure a GitLab instance + Personal Access Token in Settings and
  a "GitLab CI" tool window (bottom) shows real pipelines/jobs for the
  project's detected remote. The pipeline/job parser reads only the
  fields it actually displays and ignores anything unknown or missing
  — confirmed necessary, not just tidy, against a real response
  captured from `gitlab.com/api/v4` (a job object carries full nested
  user/commit objects) — the direct fix for the schema-drift
  `GitLabApiException` complaint above. Tokens are stored one per
  GitLab instance via the IDE's own credential store, never a shared
  field — the direct fix for the "adding a second PAT breaks the
  first" complaint. Design history in `future/v0.2-pipeline-status/README.md`.
- **v1 scope cuts, deliberate:** only GitLab instances at the root of
  their domain (`https://host/api/v4/...`) — self-hosted installs
  mounted under a subpath aren't supported yet. No job log viewer;
  double-clicking a pipeline opens it in the browser instead.
- **Filename/path-based detection, never content-sniffed.** Detects
  `.gitlab-ci.yml` and any `.yml`/`.yaml` under a `.gitlab/` directory —
  no `FileTypeOverrider`, no risk of the FileType-desync class of bug
  documented in this workspace's `ansible-companion/KNOWN_ISSUES.md`
  (Round 3).
- **Every check is a real, verifiable structural fact**, computed from
  the bundled YAML plugin's own PSI — undeclared stage references,
  jobs missing `script:`/`trigger:`/`extends:`, `rules:` entries using
  a key GitLab doesn't recognize, `only:`/`except:` combined with
  `rules:` (GitLab silently ignores the former when the latter is
  present — a real, easy-to-miss surprise), and duplicate job names.
- **Every rule independently toggleable**, same discipline as API
  Security Companion's settings — no rule is ever mandatory.

## Usage

Open any `.gitlab-ci.yml` (or a `.yml`/`.yaml` under `.gitlab/`) — checks
run automatically as part of the editor's normal highlighting pass.
Disable individual checks under Settings > Tools > GitLab CI Companion.

For live pipeline status: Settings > Tools > GitLab CI Companion > add
a GitLab instance URL + a Personal Access Token (`api`/`read_api`
scope), then open the "GitLab CI" tool window (bottom) — it detects
the project's GitLab remote automatically from `.git/config`.

## Enterprise / Team Licensing

Need enterprise features, custom pipeline validation rules, or team
licensing? Contact us at **kennyj.diazm@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
