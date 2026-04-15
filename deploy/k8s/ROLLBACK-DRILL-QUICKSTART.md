# SkillHub Rollback Drill Quickstart

Use this quickstart when you want one copyable command sequence for a supervised rollback drill.

Do not run this in production unless the window is explicitly approved.

## 1. Verify Target Cluster

```bash
kubectl config current-context
kubectl -n skillhub get deploy
kubectl -n skillhub get ingress,svc,pods
```

Expected result:

- `skillhub-scanner`, `skillhub-server`, and `skillhub-web` exist
- pods are currently healthy before the drill starts

## 2. Validate Current Release Inputs

```bash
cd /root/workspace/projects/skillhub

bash scripts/validate-k8s-external-deps.sh
CHECK_NETWORK=true bash scripts/validate-k8s-external-deps.sh

bash deploy/k8s/safe-rollout.sh precheck
bash deploy/k8s/safe-rollout.sh diff
```

## 3. Inject One Controlled Failure

Choose exactly one failure mode in a non-production environment:

1. Change one deployment image tag to a known non-existent tag.
2. Change one config value so backend startup fails.
3. Change one secret value so datasource or OAuth client initialization fails.

Record what you changed before continuing.

## 4. Run the Rollout

```bash
cd /root/workspace/projects/skillhub

CHECK_NETWORK=true bash deploy/k8s/safe-rollout.sh all
```

Expected result:

- rollout fails for the injected reason
- script prints the rollback artifact directory if rollback is needed for recovery

## 5. If Needed, Run Manual Rollback

Replace the placeholder directory with the one printed by the failed rollout:

```bash
cd /root/workspace/projects/skillhub

ROLLBACK_ARTIFACT_DIR=deploy/k8s/snapshots/rollback-skillhub-XXXXXX \
  bash deploy/k8s/safe-rollout.sh rollback
```

## 6. Confirm Health After Rollback

```bash
cd /root/workspace/projects/skillhub

bash deploy/k8s/rollback-health-check.sh
```

Optional when the public URL differs from `deploy/k8s/01-configmap.yml`:

```bash
cd /root/workspace/projects/skillhub

PUBLIC_BASE_URL=https://skillhub.ruijie.com.cn \
  bash deploy/k8s/rollback-health-check.sh
```

## 7. Capture Evidence

```bash
kubectl -n skillhub rollout history deployment/skillhub-scanner
kubectl -n skillhub rollout history deployment/skillhub-server
kubectl -n skillhub rollout history deployment/skillhub-web
```

Keep the following:

- failing rollout output
- rollback artifact directory
- output of `bash deploy/k8s/rollback-health-check.sh`
- final deployment revisions after recovery

## 8. Restore Intended State

After the drill:

- revert the injected bad image, config, or secret source
- rerun `bash deploy/k8s/safe-rollout.sh diff`
- confirm the working tree matches the intended release state before the next real rollout

## 9. Stop Conditions

Stop and escalate immediately if:

- rollback does not restore healthy deployments
- `bash deploy/k8s/rollback-health-check.sh` fails
- public endpoints stay unavailable after rollback
- the rollback artifact directory is missing or unreadable