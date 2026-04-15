# SkillHub Install Verification - 2026-04-05

## Environment

- Cluster context: `ai-gpu-k8s`
- Namespace: `skillhub`
- Backend image: `harbor.ruijie.com.cn/skillhub/skillhub-server:20260405@sha256:bc729aa4a6ee3770486415007a1608750965cafbdc6f19a6a11a839c392621ab`
- Frontend image: `harbor.ruijie.com.cn/skillhub/skillhub-web:20260405`
- Scanner image: `harbor.ruijie.com.cn/skillhub/skillhub-scanner:20260405`

## Installed Components

- `deployment/skillhub-server`
- `deployment/skillhub-web`
- `deployment/skillhub-scanner`
- `service/skillhub-server`
- `service/skillhub-web`
- `service/skillhub-scanner`
- `ingress/skillhub-ingress`
- `configmap/skillhub-config`
- `secret/skillhub-secret`
- `secret/harbor-regcred`
- `secret/skillhub-tls`

## Runtime Configuration Verified

The cluster config now enables the split-deployment scanner path:

- `SKILLHUB_SECURITY_SCANNER_ENABLED=true`
- `SKILLHUB_SECURITY_SCANNER_URL=http://skillhub-scanner:8000`
- `SKILLHUB_SECURITY_SCANNER_MODE=upload`

Verified from the running `skillhub-server` pod.

## Functional Verification

### 1. Core service health

- `skillhub-server` rollout completed successfully.
- `skillhub-web` rollout completed successfully.
- `skillhub-scanner` rollout completed successfully.
- In-cluster scanner health check from the server pod returned `{"status":"healthy","version":"0.2.0",...}`.

### 2. Live publish and scan verification

A real end-to-end publish flow was executed against the live cluster backend through port-forwarded HTTP API access.

Steps performed:

1. Registered a local account through `POST /api/v1/auth/local/register`.
2. Confirmed the account is a `MEMBER` of the `global` namespace through `GET /api/v1/me/namespaces`.
3. Published a minimal valid skill package to `global` through `POST /api/v1/skills/global/publish`.

Observed result:

- Published skill id: `16`
- Published slug: `scanner-smoke-1775355264-c`
- Published version id: `20`
- Published version: `1.0.0`
- Publish response status: `PENDING_REVIEW`
- Version detail status after fetch: `PENDING_REVIEW`

Interpretation:

- In this codebase, scanner-enabled publish first triggers the scan pipeline and only reaches `PENDING_REVIEW` after scan completion.
- The observed `PENDING_REVIEW` outcome therefore confirms that the live publish request traversed the scanner path successfully.

Relevant implementation references:

- [server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/security/SecurityScanService.java](server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/security/SecurityScanService.java)
- [server/skillhub-app/src/main/java/com/iflytek/skillhub/stream/ScanTaskConsumer.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/stream/ScanTaskConsumer.java)

### 3. Audit API verification after redeploy

All three runtime images were rebuilt with tag `20260405` and redeployed to the live cluster.

During backend rollout, two release issues had to be corrected before the service could start cleanly:

1. Flyway version `35`-`38` in the branch did not match the live database history, so the repo migration sequence was realigned to the already-applied live versions and `security_audit` / `notification` migrations were moved to `V39`-`V42`.
2. The backend Docker build was packaging stale Maven outputs, so `server/Dockerfile` was changed from `./mvnw package -DskipTests -B` to `./mvnw clean package -DskipTests -B` to avoid duplicate Flyway migrations inside the jar.

After the corrected backend image was deployed, the audit route was verified again:

- Unauthenticated `GET /api/v1/skills/16/versions/20/security-audit` now returns HTTP `401 Authentication required`.
- A normal `MEMBER` user returns HTTP `403 Forbidden`.
- A temporary `SKILL_ADMIN` session returns HTTP `200` with business payload `{"code":0,"msg":"security_audit.found","data":[]}`.

Direct database verification for `skill_version_id=20` returned zero rows from `security_audit`.

Interpretation:

- The previous `500` was fixed. The route is now live in the deployed backend image and protected by the expected authz rules.
- The empty `data: []` result for skill `16`, version `20` is current data state, not a routing or deployment failure.

The route implementation is here:

- [server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SecurityAuditController.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SecurityAuditController.java)

Operational conclusion:

- Scanner execution chain: verified working.
- Security audit query API on the running cluster image: verified working after redeploy.
- Historical publish `skill 16 / version 20`: no persisted audit rows currently exist.

### 4. Fresh publish verification after runtime fix

To verify that newly published versions now persist audit data end-to-end, a second real publish was executed against the corrected runtime image.

Observed result:

- Published skill id: `21`
- Published slug: `scanner-postfix-1775375385`
- Published version id: `25`
- Published version: `1.0.0`
- Final version status: `PENDING_REVIEW`
- Audit query as publisher: HTTP `200`

Audit payload summary:

- `scanId`: `24e0a216-9df9-41bd-8ff1-ad7c0baa89da`
- `verdict`: `SAFE`
- `findingsCount`: `2`
- `maxSeverity`: `INFO`

The returned findings were informational policy checks from the scanner:

1. `MANIFEST_INVALID_NAME` because the smoke-test skill name intentionally used uppercase words and spaces.
2. `MANIFEST_MISSING_LICENSE` because the smoke-test manifest intentionally omitted a `license` field.

Direct database verification confirmed that this audit record was persisted:

- `security_audit.id = 5`
- `skill_version_id = 25`
- `scan_id = 24e0a216-9df9-41bd-8ff1-ad7c0baa89da`
- `verdict = SAFE`
- `findings_count = 2`

Interpretation:

- The post-fix runtime not only exposes the audit route, it also stores and returns audit results for newly published versions.
- The remaining empty result on historical `version 20` is isolated to that historical row set and no longer blocks end-to-end scanner verification.
- Fresh publish `skill 21 / version 25`: audit row persisted and returned successfully.

## Residual Environment Notes

- `ingress-nginx-controller` external IP was still pending at the time of verification.
- Public ingress behavior should be rechecked after the LoadBalancer address is assigned.

## Recommended Next Actions

1. Recheck public ingress once the external IP is allocated.
2. If you want a cleaner smoke artifact, publish one more package with a lowercase-hyphenated `name` and an explicit `license` field so the scanner result is fully clean rather than `SAFE` with informational findings.