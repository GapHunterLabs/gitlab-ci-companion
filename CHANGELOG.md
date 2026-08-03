<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitLab CI Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Static structural checks for `.gitlab-ci.yml`: undeclared stage
  references, jobs missing script:/trigger:/extends:, malformed rules:
  entries, only:/except: combined with rules:, and duplicate job names.
- Zero network calls — every check runs against the file already open in
  the editor.

[Unreleased]: https://github.com/GapHunterLabs/gitlab-ci-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/gitlab-ci-companion/commits/0.1.0
