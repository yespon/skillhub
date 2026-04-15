# Production Readiness Review - 2026-04-05

## Verdict

SkillHub is not yet ready for direct production rollout.

The current repository is reasonably prepared for application rollout and application-level rollback, but it is not yet prepared for persistent-data recovery. The main gap is not Kubernetes rollout automation. The main gap is the absence of a production-grade backup and restore runbook for PostgreSQL, Redis, and S3-compatible object storage, plus the absence of an explicit restore drill.

## Managed Service Assumption

The target production environment is assumed to use cloud-managed services for PostgreSQL, Redis, and S3-compatible object storage.

That changes the execution method, but not the release gate:

- backup creation may come from the cloud provider instead of `pg_dump` or self-managed Redis persistence
- restore may be performed through the provider console, API, or platform ticket flow
- PITR, snapshot retention, and object version recovery can be provider-native

However, production is still blocked until those managed recovery capabilities are explicitly confirmed, recorded, and at least partially rehearsed.

If cloud platform permissions are not yet available, but the team does have direct PostgreSQL, Redis, and S3 connection credentials and the live data set is still very small, a temporary pre-release local archive can be used as a short-term fallback. See `deploy/k8s/TEMPORARY-CONNECTION-CREDS-BACKUP-2026-04-05.md`.

That fallback reduces immediate rollout risk, but it does not replace the need for provider-backed recovery ownership.

## What Is Ready

- Kubernetes rollout order, diff review, and failure rollback are documented in `deploy/k8s/SRE-RELEASE-RUNBOOK.md`.
- `deploy/k8s/safe-rollout.sh` captures pre-release cluster snapshots and restricted rollback artifacts for ConfigMap, Secret, Service, and Ingress, then rolls back only the affected deployment revisions when apply fails.
- External dependency configuration can be validated before rollout with `scripts/validate-k8s-external-deps.sh`.
- The scanner and security-audit publish path has been re-verified in the current cluster, including fresh persisted audit data.

## Production Blockers

### 1. No Persistent-Data Backup and Restore Runbook

Current docs only state that PostgreSQL backup can use `pg_dump`, which is not enough for production recovery planning.

Required before production:

- backup owner
- backup frequency
- retention period
- backup storage location
- encryption requirement
- restore steps
- restore verification checklist
- target RPO and RTO

### 2. PostgreSQL Recovery Strategy Is Under-Specified

The docs mention PostgreSQL HA at a high level, but do not define how to recover business data after a bad release, operator error, or partial migration.

Required minimum standard:

- confirm managed PostgreSQL automated backup retention window
- confirm PITR capability and the granularity available from the provider
- confirm whether a manual pre-release snapshot can be triggered on demand
- record the exact restore entry point: console, API, or DBA/platform ticket
- restore rehearsal into an isolated database before declaring production-ready

### 3. Redis Must Be Treated as Stateful, Not Disposable

Redis is not only cache in this system. It carries at least:

- Spring Session state
- distributed locks
- idempotency keys
- scanner Redis Stream workload

Scanner docs already note that Redis Stream failure can cause message loss and leave versions stuck in `SCANNING`, requiring manual repair. Production readiness therefore requires both Redis backup policy and Redis failure reconciliation steps.

Required minimum standard:

- confirm managed Redis backup policy or self-managed persistence mode
- if managed, record snapshot retention and restore method
- if self-managed, enable durable persistence appropriate for production
- define whether session loss is acceptable during restore
- define reconciliation steps for scan tasks that were in flight during Redis failure

### 4. Object Storage Recovery Strategy Is Missing

Production uses external S3-compatible storage, but the current materials do not define:

- bucket versioning requirement
- cross-bucket or cross-region replication requirement
- recovery method for accidental delete or overwrite
- how to verify object consistency against database metadata after restore

This is a blocker because skill packages are business data, not cache.

Required minimum standard:

- confirm bucket versioning or provider-side object recovery capability
- confirm retention period for deleted or overwritten objects
- confirm who can perform object recovery during an incident
- define how database metadata will be reconciled with restored objects

### 5. K8s Rollback Artifacts Are Not a Data Recovery Substitute

`safe-rollout.sh` backs up Kubernetes-managed resources, but it does not back up PostgreSQL, Redis data, or S3 object data. Successful app rollback is therefore not sufficient if the failed release also changed data.

## Required Exit Criteria Before Production Context Switch

Do not switch to the production Kubernetes context until all items below are confirmed:

1. Managed PostgreSQL backup retention, PITR window, and restore operator path are confirmed.
2. A pre-release PostgreSQL snapshot or equivalent provider-side backup can be produced on demand.
3. PostgreSQL restore has been rehearsed in a non-production target and verified.
4. Managed Redis backup or restore policy is agreed, including scanner queue reconciliation.
5. Object storage versioning or equivalent provider recovery policy is enabled and verified.
6. ConfigMap and Secret source of truth is confirmed outside transient rollout snapshots.
7. RPO and RTO are explicitly approved by the service owner.
8. The operator has provider-console or platform-team access needed to trigger emergency restore.

## Context Switch Signal

Do not switch the Kubernetes context to production until I explicitly send this sentence:

`现在可以切换到生产 context。`

I will only send that signal after the managed-service recovery prerequisites above are confirmed.

For the short operator-facing gate, use `deploy/k8s/PRODUCTION-CONTEXT-SWITCH-CHECKLIST-2026-04-05.md`.

## Recommended RPO and RTO Baseline

If the business has not yet defined targets, use the following as a minimum baseline to force a real decision:

- PostgreSQL: RPO <= 15 minutes, RTO <= 60 minutes
- Redis: RPO <= 15 minutes for scanner queue state, session loss explicitly approved if accepted
- Object storage: no unapproved permanent object loss; recovery from accidental delete must be possible

These values should be tightened or relaxed only by an explicit service-owner decision.

## Execution Plan After Context Switch

### Phase 0 - Pre-Flight Confirmation

1. Confirm current Kubernetes context is production.
2. Confirm release commit, image tags, and expected manifest diff.
3. Confirm rollback approver, DBA owner, and storage owner are available during the window.
4. Confirm backup target paths, credentials, and free space.

### Phase 1 - Data Protection Before Rollout

1. PostgreSQL
   - trigger or confirm a fresh provider-side snapshot or backup before the change window
   - confirm PITR retention window and restore granularity
   - record backup job id, timestamp, region, and retention
2. Redis
   - confirm latest managed snapshot or trigger a fresh snapshot if supported
   - capture queue health for scan streams before rollout
   - record whether session invalidation is acceptable during incident recovery
3. Object storage
   - confirm bucket versioning or provider recovery status
   - record bucket name, region, and last recoverable point or latest successful protection timestamp
4. Config and secrets
   - capture current production ConfigMap and Secret through controlled secure storage
   - do not rely only on default rollout snapshots

### Phase 2 - Restore Proof

1. Restore the latest managed PostgreSQL backup or PITR target to an isolated target if not already rehearsed during the current release cycle.
2. Verify:
   - database opens successfully
   - expected schema version exists
   - key tables return sane row counts
   - representative read queries succeed
3. If Redis restore is part of the operator capability, validate that procedure too.
4. If object storage recovery depends on provider-side versioning, validate at least one representative object recovery path.

If this phase cannot be completed, stop the production release.

### Phase 3 - Production Rollout

1. Run `bash scripts/validate-k8s-external-deps.sh`.
2. Run `CHECK_NETWORK=true bash scripts/validate-k8s-external-deps.sh` when network access allows.
3. Run `bash deploy/k8s/safe-rollout.sh precheck`.
4. Run `bash deploy/k8s/safe-rollout.sh diff`.
5. Run `CHECK_NETWORK=true bash deploy/k8s/safe-rollout.sh all`.
6. Record rollback artifact directory if one is produced.

### Phase 4 - Immediate Production Verification

1. Verify deployment rollout status for scanner, backend, and frontend.
2. Verify ingress homepage, auth methods endpoint, and backend health endpoint.
3. Run a controlled canary workflow that exercises:
   - login
   - publish or upload flow in a dedicated smoke-test namespace
   - scanner completion
   - security-audit query
4. Verify no new backend Flyway, datasource, Redis, or OAuth errors appear in logs.

### Phase 5 - Failure Decision Tree

Use application rollback only when all of the following are true:

- failure is limited to deployment, config, ingress, or stateless application behavior
- no irreversible data corruption occurred
- no database restore is required to return to a valid business state

Escalate to data recovery workflow when any of the following are true:

- a migration succeeds but produces bad business behavior that cannot be fixed by app rollback alone
- release writes invalid business data
- Redis queue loss leaves versions stuck and reconciliation cannot safely repair them
- object data is deleted or overwritten incorrectly

### Phase 6 - Data Recovery Workflow

1. Stop further release actions.
2. Put the system into a controlled maintenance mode if needed.
3. Decide the recovery point from approved backup artifacts.
4. Restore PostgreSQL to the selected point.
5. Restore object data or validate bucket version recovery if object changes are involved.
6. Restore or rebuild Redis based on the approved policy.
7. Reconcile scanner tasks and affected skill versions.
8. Run a post-restore verification checklist before reopening traffic.

## Immediate Recommendation

Do not switch the Kubernetes context to production yet.

First produce a concrete managed-service backup and restore runbook for PostgreSQL, Redis, and object storage, and complete at least one restore rehearsal. After that, the existing Kubernetes rollout process is strong enough to be used as the application deployment layer.