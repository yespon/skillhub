# SkillHub SRE Release Runbook

This runbook is for every production rollout of SkillHub on Kubernetes.

## 1. Scope

Use this document whenever any of the following change:

- backend image
- frontend image
- scanner image
- Kubernetes manifests under `deploy/k8s/`
- runtime configuration in `01-configmap.yml`
- runtime secrets in `02-secret.yml`

## 2. Release Principles

- Never apply manifests manually out of order during production rollout.
- Never expose Secret diffs in shared terminals or CI logs unless explicitly approved.
- Always run precheck and diff before apply.
- Capture the rollback artifact directory when the rollout fails or when `KEEP_ROLLBACK_ARTIFACTS=true` is used.
- If the rollout touches ConfigMap or Secret, treat it as a config release, not only an image release.

## 3. Required Inputs

Prepare these before the window starts:

- target image tags for `skillhub-server`, `skillhub-web`, and `skillhub-scanner`
- updated manifests in Git working tree or checked-out release commit
- current production `deploy/k8s/02-secret.yml` on the operator machine
- kubectl context pointing to the production cluster
- `jq` installed on the operator machine
- Harbor pull secret `harbor-regcred` already present in namespace `skillhub`
- ingress TLS secret already present in namespace `skillhub`

## 4. Pre-Window Checklist

Run from repository root:

```bash
git status --short
kubectl config current-context
kubectl -n skillhub get secret harbor-regcred
kubectl -n skillhub get secret skillhub-tls
kubectl -n skillhub get deploy,svc,ingress
```

Confirm:

- no unintended local edits are mixed into the release
- kubectl context is the intended production cluster
- Harbor pull secret exists
- ingress TLS secret exists
- current workloads are healthy before rollout begins

## 5. Configuration Validation

Validate manifests and external dependencies:

```bash
bash scripts/validate-k8s-external-deps.sh
CHECK_NETWORK=true bash scripts/validate-k8s-external-deps.sh
```

Interpretation:

- run without `CHECK_NETWORK` when operating from a jump box without external network reachability
- run with `CHECK_NETWORK=true` when the operator machine can reach PostgreSQL, Redis, and S3 endpoints
- this validation does not probe the public ingress URL; public checks happen after rollout
- S3 network validation treats `401` / `403` / `405` as reachable responses

## 6. Diff Review

Run rollout diff without exposing Secret contents:

```bash
bash deploy/k8s/safe-rollout.sh precheck
bash deploy/k8s/safe-rollout.sh diff
```

Only if a controlled review of Secret changes is explicitly required:

```bash
DIFF_SECRETS=true bash deploy/k8s/safe-rollout.sh diff
```

Review items:

- image tag changes are correct
- ConfigMap changes match approved release scope
- Service and Ingress changes are intentional
- no unexpected resource deletions appear in diff

## 7. Production Rollout

Preferred command:

```bash
CHECK_NETWORK=true bash deploy/k8s/safe-rollout.sh all
```

What the script does:

- validates configuration
- checks required cluster secrets
- creates a pre-release snapshot under `deploy/k8s/snapshots/`
- creates restricted rollback backups for ConfigMap, Secret, Service, and Ingress
- applies resources in safe order
- patches deployment template checksum annotations so config-only and secret-only releases force new pods
- waits for scanner, backend, and frontend rollout status
- auto-restores managed resources and undoes only the deployment revisions changed by the failed rollout

During execution:

- do not run parallel `kubectl apply` commands
- do not edit ConfigMap or Secret manually in another terminal
- keep the terminal output available until rollout completion is confirmed

## 8. Immediate Verification

After the rollout reports success, run:

```bash
kubectl -n skillhub get pods
kubectl -n skillhub rollout status deployment/skillhub-scanner
kubectl -n skillhub rollout status deployment/skillhub-server
kubectl -n skillhub rollout status deployment/skillhub-web

kubectl -n skillhub logs deployment/skillhub-scanner --tail=200
kubectl -n skillhub logs deployment/skillhub-server --tail=200
kubectl -n skillhub logs deployment/skillhub-web --tail=200

curl -k https://skillhub.ruijie.com.cn/api/v1/auth/methods
curl -k https://skillhub.ruijie.com.cn/
kubectl -n skillhub exec deploy/skillhub-server -- wget -qO- http://127.0.0.1:8080/actuator/health
```

Success criteria:

- all three deployments are `successfully rolled out`
- scanner health endpoint is stable from logs
- backend startup completes without Flyway, datasource, Redis, or OAuth config errors
- frontend serves the login page through ingress
- `/api/v1/auth/methods` returns `oauth-sourceid`
- backend `/actuator/health` reports `UP`

## 9. Failure Handling

If `safe-rollout.sh all` fails:

- stop and read the failing resource from terminal output
- record the rollback artifact directory printed by the script
- verify whether auto rollback completed cleanly
- check rollout status and logs for all deployments

Standard post-rollback confirmation:

```bash
bash deploy/k8s/rollback-health-check.sh
```

Mandatory checks after an auto rollback:

```bash
kubectl -n skillhub get pods
kubectl -n skillhub get configmap skillhub-config -o yaml
kubectl -n skillhub get secret skillhub-secret -o yaml > /tmp/skillhub-secret-post-rollback.yaml
```

If managed resources were not fully restored, run manual rollback using the retained artifact directory:

```bash
ROLLBACK_ARTIFACT_DIR=deploy/k8s/snapshots/rollback-skillhub-XXXXXX \
  bash deploy/k8s/safe-rollout.sh rollback
```

If the failure is caused by a bad image but config is correct, you may also inspect deployment history:

```bash
kubectl -n skillhub rollout history deployment/skillhub-scanner
kubectl -n skillhub rollout history deployment/skillhub-server
kubectl -n skillhub rollout history deployment/skillhub-web
```

For supervised rehearsal of this flow in a production-like environment, use `deploy/k8s/ROLLBACK-DRILL-CHECKLIST.md`.

## 10. Post-Release Actions

After successful verification:

- save terminal log or release transcript in the change ticket
- record deployed image tags
- record whether ConfigMap or Secret changed in this release
- record rollback artifact directory if backups were intentionally preserved
- notify stakeholders that release verification is complete

## 11. Break-Glass Options

Only use these when explicitly approved:

- `DIFF_SECRETS=true` to diff Secret changes in terminal output
- `INCLUDE_SECRETS_IN_SNAPSHOT=true` to include live Secret objects in snapshot files
- `KEEP_ROLLBACK_ARTIFACTS=true` to preserve rollback backups after a successful rollout

Treat any generated files containing Secrets as sensitive operational material.