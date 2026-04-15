# Production Release Record 2026-04-06

## Summary

- Production context: `ai-platform-k8s`
- Namespace: `skillhub`
- Rollout command: `CHECK_NETWORK=true bash deploy/k8s/safe-rollout.sh apply`
- Result: rollout succeeded
- Rollback artifact directory: `deploy/k8s/snapshots/rollback-skillhub-7TYLyA/`

## Released Images

- `harbor.ruijie.com.cn/skillhub/skillhub-server:20260406`
  - digest: `sha256:aa4516e5b9d9d8b915093c76fddad9f8f2a8eeb9d89ed016b81f13b7a138e497`
- `harbor.ruijie.com.cn/skillhub/skillhub-web:20260405`
  - digest: `sha256:2ff11f01cfd8d106aa11ba934ce76ac6f314d3fc7774a3f9b709d60b4c877f83`
- `harbor.ruijie.com.cn/skillhub/skillhub-scanner:20260405`
  - digest: `sha256:cb6b9ce1c154baace3c96dfecf217f8624bf0a7a8f92e3ab0314071cb0af0d22`

## Pre-release Corrections

- Recreated missing production image pull secret `harbor-regcred` from the operator host Docker config.
- Narrowed manifest drift before apply to avoid unintended production behavior changes:
  - preserved live bootstrap admin values
  - preserved live SourceID namespace-sync and OSDS values
  - kept frontend replicas at `2`
  - kept backend probes on `/actuator/health`
  - removed unapproved ingress rate-limit annotation drift

## Post-release Verification

- Backend health check returned `UP` from `/actuator/health`.
- Frontend homepage returned `HTTP 200`.
- `/api/v1/auth/methods` included `oauth-sourceid`.
- Scanner pod became healthy and served `/health` successfully.
- SourceID entry redirect returned `HTTP 302` to the expected authorization endpoint.
- Existing production skill detail path remained healthy.

## Production Smoke Publish

- Authenticated with local admin account and verified `SUPER_ADMIN` role.
- Published smoke package to namespace `global`.
- Publish response succeeded with:
  - `skillId=23`
  - `slug=prod-smoke-20260406134708`
  - `version=1.0.0`
  - `status=PUBLISHED`
  - `fileCount=2`
- Verified detail response for the smoke skill:
  - `visibility=PRIVATE`
  - `status=ACTIVE`
  - `headlineVersion.id=27`
  - `headlineVersion.status=PUBLISHED`
- Archived the smoke skill after validation.

## Security Scan Finding

- Querying `GET /api/v1/skills/23/versions/27/security-audit` returned `data: []`.
- Direct production database check confirmed `skill_version.id=27` existed but had `0` active `security_audit` rows.
- This is consistent with the current code path, not with a broken production scanner chain:
  - `SkillPublishService` sets `autoPublish = forceAutoPublish || isSuperAdmin`
  - `SUPER_ADMIN` publishes therefore create versions directly in `PUBLISHED`
  - `securityScanService.triggerScan(...)` is only called inside the `if (!autoPublish)` branch
- Impact:
  - normal review-gated publishes are designed to go through `PENDING_REVIEW -> SCANNING -> PENDING_REVIEW/SCAN_FAILED`
  - `SUPER_ADMIN` direct publishes currently bypass both review task creation and security scan triggering

## Current Release Judgment

- Production rollout: successful
- Core login, web, backend, ingress, and publish flows: verified
- Known functional gap: `SUPER_ADMIN` direct publish is not covered by the current security scanning workflow
- Required follow-up: product and backend decision on whether direct publish should
  - also enter scan gating before becoming visible, or
  - remain direct publish but create and persist asynchronous audit results without changing published status