<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitLab CI Companion Changelog

## [Unreleased]

## [0.1.5]

### Added

- Review/star CTA: after 10 distinct real findings across any of the 5
  static checks (stage reference, script presence, rules syntax,
  only/except-vs-rules conflict, duplicate job name), a one-time
  notification asks whether to rate the plugin on Marketplace, with a
  permanent "Don't ask again" option. Standard mechanism used
  catalog-wide since 2026-08-24 (`CONSTITUTION.md` §7.2), rolled out to
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

[Unreleased]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.5...HEAD
[0.1.5]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/gitlab-ci-companion/commits/0.1.0
