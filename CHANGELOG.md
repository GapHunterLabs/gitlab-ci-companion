<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitLab CI Companion Changelog

## [Unreleased]

## [0.3.1]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.3.0]

### Added

- Catches the pipeline errors GitLab otherwise only reports when you push,
  with GitLab's own wording:
  - a job whose `needs:` or `dependencies:` point at a job in a later
    stage ("need X is not defined in current or prior stages") -- a job's
    stage is followed through `extends:` templates in the same file, and
    `.pre`/`.post` and GitLab's default stages are taken into account;
  - a need listed twice;
  - a circular `extends:`;
  - an `extends:` chain deeper than GitLab's limit of 10 levels.
  Anything that depends on an `include:` (a template, or the stage list
  itself) is never guessed at. Both new checks can be turned off in
  Settings > Tools > GitLab CI Companion.

### Fixed

- The description and the warning said GitLab silently ignores
  `only:`/`except:` when a job also has `rules:`. It doesn't: GitLab
  rejects the job ("may not be used with `rules`"). The check itself was
  right; its explanation now is too.
- "Job has none of script:/trigger:/extends:" no longer fires on a job
  whose script arrives through a YAML merge key (`<<: *build_job`), or on
  any job of a file with an `include:`, where a job without `script:` is
  often a partial override of an included job (e.g. adding `needs:` to a
  GitLab template's `container_scanning`).
- A file with CI/CD inputs (a `spec:` header, then `---`) was checked on
  its header instead of its pipeline: `spec` was reported as a job with no
  script, and the pipeline itself went unchecked. The pipeline after the
  header is now the one checked, as GitLab does, and a value filled in
  from `$[[ inputs.* ]]` is never judged.
- The undeclared-stage check now knows the stages GitLab always offers:
  `.pre` and `.post`, and `build`/`test`/`deploy` when the file declares
  no `stages:`. It no longer judges a file whose stage list may come from
  an `include:`, or a hidden template job, and it now quotes GitLab's own
  error ("chosen stage ... does not exist; available stages are ...").
- Files that are usually pulled in by an `include:` -- anything under
  `.gitlab/`, or a `.gitlab-ci.yml` below the project's top level -- are
  no longer judged on stages or on a missing `script:`: the stage list,
  and often the rest of a job, live in the file that includes them.
- A stage name followed by a comment (`- docker  # build images`) was read
  with the comment attached and reported as undeclared.
- A `rules:` entry using a YAML merge key (`- <<: *if-protected`) or
  `interruptible:` is no longer reported as having an unrecognized key.
- A hidden template defined twice only to hold different YAML anchors, and
  never used by `extends:`, is no longer reported as a duplicate job.
- All of the above were found by running every check over 250 real public
  pipelines; one real `only:`+`rules:` error the plugin reports correctly
  was found there too.

## [0.2.0]

### Fixed

- The script/trigger/extends presence check no longer flags a hidden
  job (name starting with `.`) -- GitLab's own documented convention
  for a template job that's never run on its own, only pulled in via
  `extends:`. Previously a real false positive on a perfectly valid
  pattern.

## [0.1.5]

### Added

- Review/star CTA: after 10 distinct real findings across any of the 5
  static checks (stage reference, script presence, rules syntax,
  only/except-vs-rules conflict, duplicate job name), a one-time
  notification asks whether to rate the plugin on Marketplace, with a
  permanent "Don't ask again" option. Standard mechanism used
  catalog-wide since 2026-08-24, rolled out to
  this plugin now.

## [0.1.4]

### Fixed

- Tool window no longer shows the generic platform icon in the sidebar —
  the real Gap Hunter Labs mark is now declared via `icon=` on
  `<toolWindow>`.

## [0.1.3]

### Fixed

- Tool window content (pipelines/jobs tables, setup prompts) was
  rendering flush against the tool window's own border, with no margin
  — fixed with an 8px empty border on the root panel.

## [0.1.2]

_No changelog entry was recorded for this release — `gradle.properties`
already showed 0.1.2 when this file was next touched (2026-08-12), with
no corresponding entry above 0.1.1. Not reconstructed retroactively to
avoid documenting unverified changes; noted here so the gap is visible
instead of silent._

## [0.1.1]

### Fixed

- Marketplace listing icon not rendering (showed a broken "plugin icon"
  placeholder) — replaced with the same icon already proven to render
  correctly on other Gap Hunter Labs listings.

## [0.1.0]

### Added

- Static structural checks for `.gitlab-ci.yml`: undeclared stage
  references, jobs missing script:/trigger:/extends:, malformed rules:
  entries, only:/except: combined with rules:, and duplicate job names.
- Zero network calls — every check runs against the file already open in
  the editor.

[Unreleased]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.3.1...HEAD
[0.3.1]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.3.0...0.3.1
[0.3.0]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.5...0.2.0
[0.1.5]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/gitlab-ci-companion/commits/0.1.0
