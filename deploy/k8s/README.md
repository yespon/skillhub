# SkillHub Kubernetes Deployment

This directory contains a Harbor-ready Kubernetes deployment baseline for SkillHub.

## Prerequisites

- Kubernetes cluster with an NGINX Ingress controller
- Harbor project `harbor.ruijie.com.cn/skillhub`
- External PostgreSQL, Redis, and S3-compatible object storage
- A TLS certificate secret for `skillhub.ruijie.com.cn` if HTTPS is enabled

## 1. Build and Push Images to Harbor

Use a fixed tag instead of `latest`.

You can use the helper script for the same flow:

```bash
HARBOR_PASSWORD='***' deploy/k8s/deploy.sh all
```

```bash
export REGISTRY=harbor.ruijie.com.cn
export PROJECT=skillhub
export TAG=release-r0.1.0

docker login harbor.ruijie.com.cn -u admin

docker build -t $REGISTRY/$PROJECT/skillhub-server:$TAG -f server/Dockerfile server
docker push $REGISTRY/$PROJECT/skillhub-server:$TAG

docker build -t $REGISTRY/$PROJECT/skillhub-web:$TAG -f web/Dockerfile web
docker push $REGISTRY/$PROJECT/skillhub-web:$TAG
```

If you change the tag, update both deployment manifests before applying them.

## 2. Prepare Runtime Values

Review and update these files before deployment:

- `configmap.yaml`
- `secret.yaml`
- `secret.yaml.example`
- `02-secret.example.yml`
- `ingress.yaml`
- `backend-deployment.yaml`
- `frontend-deployment.yaml`

At minimum, replace:

- PostgreSQL URL, username, and password
- Redis hostname and port
- S3 endpoint, public endpoint, bucket, access key, and secret key
- SourceID OAuth client ID, client secret, redirect URI
- Harbor image tags if needed

Create a real secret file from the example, or preferably render it from environment variables so credentials are not committed to Git:

```bash
cp deploy/k8s/secret.yaml.example deploy/k8s/secret.yaml
```

If you are using the `02-secret.yml` layout for local deployment, keep the real file under `.dev/` so it stays ignored by Git:

```bash
cp deploy/k8s/02-secret.example.yml .dev/02-secret.yml
kubectl apply -f .dev/02-secret.yml
```

Do not place real credentials in `deploy/k8s/02-secret.yml`.

Preferred secret rendering flow:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://k8s-bj-pro-nodeports.ruijie.com.cn:32642/skillhub'
export SPRING_DATASOURCE_USERNAME='replace-me'
export SPRING_DATASOURCE_PASSWORD='replace-me'
export SKILLHUB_STORAGE_S3_ENDPOINT='https://tos-s3-cn-beijing.volces.com'
export SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT='https://tos-s3-cn-beijing.volces.com'
export SKILLHUB_STORAGE_S3_BUCKET='ruijie-skillhub'
export SKILLHUB_STORAGE_S3_ACCESS_KEY='replace-me'
export SKILLHUB_STORAGE_S3_SECRET_KEY='replace-me'
export SKILLHUB_STORAGE_S3_REGION='cn-beijing'

deploy/k8s/render-secret.sh stdout > deploy/k8s/secret.local.yaml
kubectl apply -f deploy/k8s/secret.local.yaml
```

Validate the Kubernetes config before rollout:

```bash
./scripts/validate-k8s-external-deps.sh
```

If you are running the check from a machine that can reach your external PostgreSQL, Redis, and S3 endpoints, add:

```bash
CHECK_NETWORK=true ./scripts/validate-k8s-external-deps.sh
```

This repository now defaults to external services:

- App domain: `https://skillhub.ruijie.com.cn`
- PostgreSQL service: `k8s-bj-pro-nodeports.ruijie.com.cn:32642`
- Redis service: `k8s-bj-pro-nodeports.ruijie.com.cn:32209`
- S3 endpoint: `https://tos-s3-cn-beijing.volces.com`

## 3. Create Namespace and Harbor Pull Secret

```bash
kubectl apply -f deploy/k8s/namespace.yaml

read -s HARBOR_PASSWORD
kubectl -n skillhub create secret docker-registry harbor-regcred \
  --docker-server=harbor.ruijie.com.cn \
  --docker-username=admin \
  --docker-password="$HARBOR_PASSWORD"
unset HARBOR_PASSWORD
```

If the secret already exists, replace it with:

```bash
kubectl -n skillhub delete secret harbor-regcred
kubectl -n skillhub create secret docker-registry harbor-regcred \
  --docker-server=harbor.ruijie.com.cn \
  --docker-username=admin \
  --docker-password="$HARBOR_PASSWORD"
```

## 4. Apply Kubernetes Manifests

```bash
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -f deploy/k8s/services.yaml
kubectl apply -f deploy/k8s/backend-deployment.yaml
kubectl apply -f deploy/k8s/frontend-deployment.yaml
kubectl apply -f deploy/k8s/ingress.yaml
```

## 5. Verify Rollout

```bash
kubectl -n skillhub get pods
kubectl -n skillhub get svc
kubectl -n skillhub get ingress

kubectl -n skillhub rollout status deployment/skillhub-server
kubectl -n skillhub rollout status deployment/skillhub-web

kubectl -n skillhub logs deployment/skillhub-server --tail=200
kubectl -n skillhub logs deployment/skillhub-web --tail=200
```

## 6. Functional Checks

After ingress is ready, verify:

```bash
curl -k https://skillhub.ruijie.com.cn/api/v1/auth/methods
curl -k https://skillhub.ruijie.com.cn/
curl -k https://skillhub.ruijie.com.cn/actuator/health
```

Expected checks:

- Login page opens through ingress
- OAuth is the default tab
- Only `锐捷SSO` is shown in OAuth login options
- `/api/v1/auth/methods` returns `oauth-sourceid`

## Notes

- This baseline assumes your cluster does not provide persistent volumes for bundled data services.
- The Harbor password should only be used at `docker login` time or when creating the Kubernetes pull secret. Do not store it in Git.
- If you do not want bootstrap admin, keep `bootstrap-admin-enabled` as `false`.
- Update `configmap.yaml` and `secret.yaml` to point at your external PostgreSQL, Redis, and object storage endpoints before rollout.