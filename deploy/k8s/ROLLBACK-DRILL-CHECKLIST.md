# SkillHub Rollback Drill Checklist

Use this checklist to rehearse a failed release and confirm that rollback verification is fast, repeatable, and operator-friendly.

## 1. Goal

This drill is not for routine production release execution.

Use it to verify that your team can:

- recognize a bad rollout quickly
- recover with `safe-rollout.sh rollback`
- confirm service health after rollback using a standard script

## 2. Recommended Environment

Run this drill in a production-like environment before relying on it in production.

Recommended targets:

- staging cluster with the same ingress controller type
- pre-production cluster with the same Harbor, PostgreSQL, Redis, and S3 topology

Avoid running a deliberate-failure drill in production unless it is explicitly approved in a maintenance window.

## 3. Preconditions

Before the drill:

- cluster currently healthy
- `bash deploy/k8s/safe-rollout.sh precheck` passes
- `bash deploy/k8s/safe-rollout.sh diff` reviewed
- operator has access to `kubectl`, `jq`, and the release repository
- current secret file is available locally as `deploy/k8s/02-secret.yml` or passed explicitly

## 4. Safe Failure Injection Options

Pick one failure mode in a non-production environment:

1. Bad image tag on a single deployment manifest copy
2. Invalid config value that prevents backend startup
3. Invalid secret value that breaks datasource or OAuth client initialization

Rules:

- change only one variable at a time
- record exactly what was changed
- restore the manifest or secret source immediately after the drill

## 5. Drill Procedure

1. Record current deployment revisions.

```bash
kubectl -n skillhub rollout history deployment/skillhub-scanner
kubectl -n skillhub rollout history deployment/skillhub-server
kubectl -n skillhub rollout history deployment/skillhub-web
```

2. Execute the rollout using the normal path.

```bash
CHECK_NETWORK=true bash deploy/k8s/safe-rollout.sh all
```

3. Confirm the rollout fails for the injected reason.

4. Capture the rollback artifact directory printed by the script.

5. If auto rollback did not fully recover the environment, run manual rollback.

```bash
ROLLBACK_ARTIFACT_DIR=deploy/k8s/snapshots/rollback-skillhub-XXXXXX \
  bash deploy/k8s/safe-rollout.sh rollback
```

6. Run the standard rollback health check.

```bash
bash deploy/k8s/rollback-health-check.sh
```

## 6. Pass Criteria

The drill passes only when all of the following are true:

- all three deployments become healthy again
- `bash deploy/k8s/rollback-health-check.sh` exits successfully
- ingress home page is reachable again
- `/api/v1/auth/methods` still contains `oauth-sourceid`
- backend `/actuator/health` returns `UP`
- operator can identify the failure cause and the rollback artifact directory from terminal output

## 7. Evidence to Keep

Record the following in the change ticket or ops knowledge base:

- drill date and cluster name
- injected failure type
- failing command output
- rollback artifact directory
- output of `bash deploy/k8s/rollback-health-check.sh`
- final deployment revisions after recovery

## 8. Exit Conditions

Stop the drill and escalate if any of these occur:

- rollback artifacts do not restore ConfigMap, Secret, Service, or Ingress cleanly
- rollback completes but deployments do not recover
- public endpoints stay unavailable after rollback
- operators cannot explain which revision or config source was restored

## 9. Follow-up

After each drill:

- remove the injected bad config or image tag
- restore the intended manifest state in Git
- update `deploy/k8s/SRE-RELEASE-RUNBOOK.md` if the team learned a missing operational step
- repeat the drill after major rollout-script changes