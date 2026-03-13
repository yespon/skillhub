# Contributing to SkillHub

## Scope

SkillHub is a self-hosted registry for agent skills. Contributions should
preserve the existing architecture and product direction documented in
[`docs/`](./docs).

## Before You Start

- Read [`README.md`](./README.md) for local development commands.
- Check the relevant design docs before changing behavior.
- Open an issue for non-trivial changes before sending a large pull request.

## Development Setup

Prerequisites:

- Docker and Docker Compose
- Java 21
- Node.js and `pnpm`

Start the local stack:

```bash
make dev-all
```

Useful commands:

```bash
make test
make typecheck-web
make build-web
./scripts/smoke-test.sh
```

Stop the stack:

```bash
make dev-all-down
```

## Change Guidelines

- Keep changes focused. Avoid mixing refactors with behavior changes.
- Follow existing module boundaries across `server/`, `web/`, and `docs/`.
- Add or update tests when behavior changes.
- Update docs when APIs, auth flows, deployment, or operator workflows change.
- Prefer backward-compatible changes unless the issue explicitly allows a break.

## Pull Requests

Before opening a pull request, make sure:

- The branch is rebased or merged cleanly from the target branch.
- Relevant backend tests pass.
- Frontend typecheck/build passes when frontend files changed.
- Smoke coverage is updated when operator-facing workflows change.
- The pull request description explains motivation, scope, and rollout impact.

## Commit Style

Conventional-style subjects are preferred, for example:

- `feat(auth): add local account login`
- `fix(ops): align smoke test with csrf flow`
- `docs(deploy): clarify runtime image usage`

## Reporting Security Issues

Do not open public issues for suspected security vulnerabilities.

Report them privately to the maintainers through your internal security process
or a private maintainer contact channel.
