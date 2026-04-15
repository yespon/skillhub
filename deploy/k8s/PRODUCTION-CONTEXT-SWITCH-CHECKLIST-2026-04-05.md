# Production Context Switch Checklist - 2026-04-05

Use this checklist before switching `kubectl` to the production cluster.

This is a release gate, not a generic ops note.

## Stop Rule

Do not switch the Kubernetes context to production until GitHub Copilot explicitly sends this sentence:

`现在可以切换到生产 context。`

If that sentence has not been sent, the default action is `do not switch`.

## Temporary Fallback For Small Data Sets

If the team does not have cloud platform backup permissions yet, but does have direct PostgreSQL, Redis, and S3 connection credentials, a temporary pre-release local archive may be used as a short-term fallback only when the production data set is confirmed very small.

Use `deploy/k8s/TEMPORARY-CONNECTION-CREDS-BACKUP-2026-04-05.md` for that path.

This fallback reduces release risk, but it does not satisfy the long-term production standard of provider-backed backup, PITR, and formal recovery ownership.

## Required Inputs

Have these ready before the switch:

- target release commit or tag
- target image tags for `skillhub-server`, `skillhub-web`, and `skillhub-scanner`
- operator access to production `kubectl`
- operator access to the cloud provider console, API, or platform team that controls PostgreSQL, Redis, and object storage recovery
- the real production secret source used by `deploy/k8s/02-secret.yml`

## Managed PostgreSQL Gate

All items must be true:

- automated backup retention window is known
- PITR capability is confirmed
- manual pre-release snapshot or equivalent on-demand backup is available
- restore entry point is known: console, API, or DBA/platform ticket
- restore target can be an isolated non-production instance or database
- at least one recent restore rehearsal has completed successfully, or the current release window explicitly includes that rehearsal before rollout

Record before switching:

- backup retention period
- PITR granularity
- recovery owner
- restore initiation path

## Managed Redis Gate

All items must be true:

- snapshot or backup retention is known
- restore initiation path is known
- session loss policy is explicitly accepted or rejected
- scanner Redis Stream reconciliation steps are defined
- the team knows how to identify and repair versions stuck in `SCANNING`

Record before switching:

- backup retention period
- restore owner
- whether session invalidation is acceptable
- queue reconciliation owner

## Object Storage Gate

All items must be true:

- bucket versioning or equivalent provider-side recovery is enabled
- deleted or overwritten object recovery window is known
- restore initiation path is known
- the team knows how to reconcile restored objects with database metadata

Record before switching:

- bucket name
- region
- recovery owner
- latest recoverable point or retention policy

## Release Control Gate

All items must be true:

- release operator is identified
- rollback approver is identified
- DBA or platform contact is available during the window
- storage owner is available during the window
- current production ConfigMap and Secret source of truth is confirmed outside temporary rollout snapshots
- target RPO and RTO are explicitly approved

Minimum baseline if not otherwise approved:

- PostgreSQL: RPO <= 15 minutes, RTO <= 60 minutes
- Redis scanner queue state: RPO <= 15 minutes
- object storage: no unapproved permanent object loss

## Technical Precheck Gate

These should be ready to run immediately after the context switch, not discovered afterward:

- `bash scripts/validate-k8s-external-deps.sh`
- `CHECK_NETWORK=true bash scripts/validate-k8s-external-deps.sh`
- `bash deploy/k8s/safe-rollout.sh precheck`
- `bash deploy/k8s/safe-rollout.sh diff`

## Switch Decision

Switch to the production context only when all gates above are satisfied and the explicit go-signal has been sent.

If even one gate is unknown, treat the environment as not ready for production rollout.

If the team is temporarily relying on the connection-credential fallback archive, that dependency must be explicitly called out before the go-signal is sent.