# Kubernetes 部署指南

本文档说明如何在 Kubernetes 集群中部署 SkillHub。

## 前置条件

- Kubernetes 集群 (v1.24+)
- kubectl 已配置并连接到集群
- nginx ingress controller 已安装（可选，用于域名访问）
- 默认 StorageClass 已配置（`overlays/with-infra` 和默认 `overlays/external` 需要 PVC）

## 目录结构

```
deploy/k8s/
├── 01-configmap.yml               # ConfigMap 模板（复制到 .dev/01-configmap.yml 使用）
├── 02-secret.example.yml           # Secret 模板（复制到 .dev/02-secret.yml 使用）
├── base/                          # 基础配置（所有场景共用）
│   ├── kustomization.yaml
│   ├── configmap.yaml
│   ├── services.yaml
│   ├── backend-deployment.yaml
│   ├── frontend-deployment.yaml
│   ├── scanner-deployment.yaml
│   └── ingress.yaml
│
└── overlays/
    ├── with-infra/                # 完整部署（包含内置数据库）
    │   ├── kustomization.yaml
    │   ├── postgres-statefulset.yaml
    │   └── redis-statefulset.yaml
    │
    ├── external/                  # 外部 PostgreSQL/Redis，保留本地存储 PVC
    │   └── kustomization.yaml
    │
    ├── external-sourceid-only/    # external + 仅显示 SourceID
    │   ├── kustomization.yaml
    │   ├── configmap-external-patch.yaml
    │   ├── configmap-sourceid-patch.yaml
    │   └── ingress-host-patch.yaml
    │
    ├── external-s3/               # 外部 PostgreSQL/Redis + S3，无需本地存储 PVC
    │   ├── kustomization.yaml
    │   ├── configmap-s3-patch.yaml
    │   ├── backend-storage-patch.yaml
    │   └── delete-storage-pvc.yaml
    │
    └── external-s3-sourceid-only/ # external-s3 + 仅显示 SourceID
      ├── kustomization.yaml
      ├── configmap-external-patch.yaml
      ├── configmap-sourceid-patch.yaml
      └── ingress-host-patch.yaml
```

## 快速开始

### 1. 创建命名空间

```bash
kubectl create namespace skillhub
```

### 2. 配置 ConfigMap

```bash
cp deploy/k8s/01-configmap.yml .dev/01-configmap.yml

# 编辑 .dev/01-configmap.yml，修改非敏感配置
${EDITOR:-vi} .dev/01-configmap.yml

# 应用 ConfigMap
kubectl apply -f .dev/01-configmap.yml
```

这份模板已经包含“仅显示 SourceID，并隐藏本地用户名密码入口”的示例配置：

- `skillhub-auth-local-show-entry: "false"`
- `skillhub-access-policy-mode: PROVIDER_ALLOWLIST`
- `skillhub-access-policy-allowed-providers: sourceid`

### 3. 配置 Secret

```bash
cp deploy/k8s/02-secret.example.yml .dev/02-secret.yml

# 编辑 .dev/02-secret.yml，修改敏感配置
${EDITOR:-vi} .dev/02-secret.yml

# 应用 Secret
kubectl apply -f .dev/02-secret.yml
```

如果你沿用 `01-configmap.yml` 里的 SourceID-only 示例：

- 必填的 OAuth Secret 只有 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID` 和 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET`
- 不需要补 GitHub OAuth Secret，保持未配置即可

不要把真实凭据写入 `deploy/k8s/02-secret.yml`。

**Secret 配置项**：

| 键 | 说明 | 必填 |
|---|---|---|
| POSTGRES_PASSWORD | PostgreSQL 密码 | 是 |
| REDIS_PASSWORD | Redis 密码（with-infra 默认启用） | 是 |
| BOOTSTRAP_ADMIN_PASSWORD | 管理员密码 | 是 |
| oauth2-github-client-id | GitHub OAuth ID | 否 |
| oauth2-github-client-secret | GitHub OAuth 密钥 | 否 |
| SKILLHUB_STORAGE_S3_ACCESS_KEY | S3/OSS Access Key | 否 |
| SKILLHUB_STORAGE_S3_SECRET_KEY | S3/OSS Secret Key | 否 |
| SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID | SourceID Client ID | 否 |
| SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET | SourceID Client Secret | 否 |
| skill-scanner-llm-api-key | LLM API 密钥 | 否 |
| skill-scanner-llm-model | LLM 模型名称 | 否 |

SourceID-only 推荐最小 Secret：

- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `BOOTSTRAP_ADMIN_PASSWORD`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET`
- 若使用 S3，再补 `SKILLHUB_STORAGE_S3_ACCESS_KEY` 与 `SKILLHUB_STORAGE_S3_SECRET_KEY`

### 4. 选择部署方式

**方式一：完整部署（包含 PostgreSQL + Redis）**

适合全新环境，自动部署数据库：

```bash
kubectl apply -k overlays/with-infra/
```

**方式二：使用外部 PostgreSQL / Redis，保留本地存储 PVC**

适合已有 PostgreSQL 和 Redis 的环境：

1. 修改 `base/configmap.yaml` 中的数据库和 Redis 配置：
```yaml
postgres-host: your-postgres-host
postgres-port: "5432"
postgres-db: skillhub
postgres-user: skillhub
redis-host: your-redis-host
redis-port: "6379"
```

2. 在 `.dev/02-secret.yml` 中填入数据库和 Redis 密码：
```yaml
POSTGRES_PASSWORD: your-postgres-password
REDIS_PASSWORD: your-redis-password
```

3. 部署：
```bash
kubectl apply -k overlays/external/
```

4. 可选：部署前运行外部依赖预检：
```bash
./scripts/validate-k8s-external-deps.sh
CHECK_NETWORK=true ./scripts/validate-k8s-external-deps.sh
```

**方式三：使用外部 PostgreSQL / Redis + S3（无 PVC）**

适合已有 PostgreSQL、Redis 和对象存储，且不希望依赖集群默认 StorageClass 的环境。

1. 修改 `base/configmap.yaml` 中的数据库、Redis 和 S3 配置：
```yaml
postgres-host: your-postgres-host
postgres-port: "5432"
postgres-db: skillhub
postgres-user: skillhub
redis-host: your-redis-host
redis-port: "6379"
skillhub-storage-provider: s3
skillhub-storage-s3-endpoint: https://your-s3-endpoint
skillhub-storage-s3-bucket: your-bucket
skillhub-storage-s3-region: your-region
```

2. 在 `.dev/02-secret.yml` 中填入数据库、Redis 和 S3 凭据：
```yaml
POSTGRES_PASSWORD: your-postgres-password
REDIS_PASSWORD: your-redis-password
SKILLHUB_STORAGE_S3_ACCESS_KEY: your-access-key
SKILLHUB_STORAGE_S3_SECRET_KEY: your-secret-key
```

3. 可选：部署前运行外部依赖预检：
```bash
./scripts/validate-k8s-external-deps.sh
CHECK_NETWORK=true ./scripts/validate-k8s-external-deps.sh
```

4. 部署：
```bash
kubectl apply -k overlays/external-s3/
```

该 overlay 会强制 `skillhub-storage-provider=s3`，并移除后端本地存储挂载与 `skillhub-storage-pvc`，因此不再要求默认 StorageClass。

**方式四：使用外部 PostgreSQL / Redis，并仅显示 SourceID**

适合企业 SSO 场景，需要隐藏本地用户名密码入口，并只允许锐捷 SSO，同时继续使用本地存储 PVC。

1. 修改 `overlays/external-sourceid-only/configmap-external-patch.yaml`：
```yaml
postgres-host: your-postgres-host
postgres-port: "5432"
redis-host: your-redis-host
redis-port: "6379"
```

2. 修改 `overlays/external-sourceid-only/configmap-sourceid-patch.yaml`：
```yaml
skillhub-public-base-url: "https://your-domain.com"
oauth2-sourceid-redirect-uri: "https://your-domain.com/login/oauth2/code/sourceid"
oauth2-sourceid-authorization-uri: "https://your-sourceid-domain/oauth2.0/authorize"
oauth2-sourceid-token-uri: "https://your-sourceid-domain/oauth2.0/accessToken"
oauth2-sourceid-user-info-uri: "https://your-sourceid-domain/oauth2.0/profile"
```

3. 修改 `overlays/external-sourceid-only/ingress-host-patch.yaml`，确保 host 与 `skillhub-public-base-url` 使用同一域名。

4. 在 `.dev/02-secret.yml` 中填入：
```yaml
POSTGRES_PASSWORD: your-postgres-password
REDIS_PASSWORD: your-redis-password
BOOTSTRAP_ADMIN_PASSWORD: your-admin-password
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID: your-sourceid-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET: your-sourceid-client-secret
```

5. 部署：
```bash
kubectl apply -k overlays/external-sourceid-only/
```

这个 overlay 在 `external` 基础上额外做三件事：

- 隐藏本地用户名密码入口
- 将 OAuth 准入策略限制为 `sourceid`
- 提供与 `skillhub-public-base-url` 对齐的 ingress host patch

**方式五：使用外部 PostgreSQL / Redis + S3，并仅显示 SourceID**

适合企业 SSO 场景，需要隐藏本地用户名密码入口，并只允许锐捷 SSO。

1. 修改 `overlays/external-s3-sourceid-only/configmap-external-patch.yaml`：
```yaml
postgres-host: your-postgres-host
postgres-port: "5432"
redis-host: your-redis-host
redis-port: "6379"
skillhub-storage-s3-endpoint: https://your-s3-endpoint
skillhub-storage-s3-bucket: your-bucket
skillhub-storage-s3-region: your-region
```

2. 修改 `overlays/external-s3-sourceid-only/configmap-sourceid-patch.yaml`：
```yaml
skillhub-public-base-url: "https://your-domain.com"
oauth2-sourceid-redirect-uri: "https://your-domain.com/login/oauth2/code/sourceid"
oauth2-sourceid-authorization-uri: "https://your-sourceid-domain/oauth2.0/authorize"
oauth2-sourceid-token-uri: "https://your-sourceid-domain/oauth2.0/accessToken"
oauth2-sourceid-user-info-uri: "https://your-sourceid-domain/oauth2.0/profile"
```

3. 修改 `overlays/external-s3-sourceid-only/ingress-host-patch.yaml`，确保 host 与 `skillhub-public-base-url` 使用同一域名。

4. 在 `.dev/02-secret.yml` 中填入：
```yaml
POSTGRES_PASSWORD: your-postgres-password
REDIS_PASSWORD: your-redis-password
SKILLHUB_STORAGE_S3_ACCESS_KEY: your-access-key
SKILLHUB_STORAGE_S3_SECRET_KEY: your-secret-key
BOOTSTRAP_ADMIN_PASSWORD: your-admin-password
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID: your-sourceid-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET: your-sourceid-client-secret
```

5. 部署：
```bash
kubectl apply -k overlays/external-s3-sourceid-only/
```

这个 overlay 在 `external-s3` 基础上额外做三件事：

- 隐藏本地用户名密码入口
- 将 OAuth 准入策略限制为 `sourceid`
- 提供与 `skillhub-public-base-url` 对齐的 ingress host patch

### 5. 验证部署

```bash
# 检查 Pod 状态
kubectl get pods -n skillhub

# 等待所有 Pod 就绪
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 6. 访问服务

**方式一：端口转发（推荐本地测试）**

```bash
# 前端
kubectl port-forward svc/skillhub-web -n skillhub 8080:80

# 后端 API
kubectl port-forward svc/skillhub-server -n skillhub 8081:8080
```

访问 http://localhost:8080

**方式二：Ingress 域名访问**

修改 `base/ingress.yaml` 或对应 overlay 中的 ingress patch：
```yaml
spec:
  rules:
    - host: your-domain.com  # 修改为你的域名
```

```bash
kubectl apply -k overlays/with-infra/  # 或 overlays/external/、overlays/external-sourceid-only/、overlays/external-s3/、overlays/external-s3-sourceid-only/
```

## 部署架构

export REGISTRY=harbor.ruijie.com.cn
export PROJECT=skillhub
export TAG=release-r0.1.1

docker login harbor.ruijie.com.cn -u admin

docker build -t $REGISTRY/$PROJECT/skillhub-server:$TAG -f server/Dockerfile server
docker push $REGISTRY/$PROJECT/skillhub-server:$TAG

docker build -t $REGISTRY/$PROJECT/skillhub-web:$TAG -f web/Dockerfile web
docker push $REGISTRY/$PROJECT/skillhub-web:$TAG

docker build -t $REGISTRY/$PROJECT/skillhub-scanner:$TAG -f scanner/Dockerfile scanner
docker push $REGISTRY/$PROJECT/skillhub-scanner:$TAG
```

If you change the tag, update the backend, frontend, and scanner deployment manifests before applying them.

```
┌─────────────────────────────────────────────────────────────┐
│                        skillhub namespace                    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ skillhub-web│  │skillhub-    │  │ skillhub-scanner    │  │
│  │   (前端)    │  │  server     │  │    (扫描器)         │  │
│  │   :80       │  │  (后端)     │  │     :8000           │  │
│  └─────────────┘  │   :8080     │  └─────────────────────┘  │
│                   └──────┬──────┘                            │
│                          │                                   │
│         ┌────────────────┴────────────────┐                  │
│         │         with-infra only          │                 │
│         │  ┌─────────────┐  ┌───────────┐ │                 │
│         │  │  postgres-0 │  │  redis-0  │ │                 │
│         │  │   :5432     │  │   :6379   │ │                 │
│         │  └─────────────┘  └───────────┘ │                 │
│         └─────────────────────────────────┘                 │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              PersistentVolumeClaims                      │ │
│  │  - skillhub-storage-pvc (10Gi, external-s3 不创建)      │ │
│  │  - postgres-data-0 (10Gi) - with-infra only             │ │
│  │  - redis-data-0 (5Gi) - with-infra only                 │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 配置说明

部署前请检查以下文件：

- `configmap.yaml`
- `02-secret.example.yml`
- `ingress.yaml`
- `backend-deployment.yaml`
- `frontend-deployment.yaml`

- `01-configmap.yml` — non-sensitive configuration (URLs, ports, feature flags; optional SourceID namespace-sync / OSDS are disabled by default)
- `02-secret.example.yml` — template for secret values; copy to `deploy/k8s/02-secret.yml`
- `05-ingress.yml` — domain and TLS settings
- `deploy/k8s/SRE-RELEASE-RUNBOOK.md` — repeated production release procedure for SREs
- `deploy/k8s/PRODUCTION-CONTEXT-SWITCH-CHECKLIST-2026-04-05.md` — one-page gate before switching `kubectl` to the production cluster
- `deploy/k8s/INSTALL-VERIFICATION-2026-04-05.md` — live install and scanner verification record for the current cluster rollout
- `deploy/k8s/create-temporary-release-archive.sh` — temporary pre-release archive script when only direct PostgreSQL, Redis, and S3 connection credentials are available
- `deploy/k8s/verify-temporary-postgres-restore.sh` — isolated PostgreSQL restore validation for a temporary archive

### ConfigMap 配置项

| 键 | 默认值 | 说明 |
|---|---|---|
| postgres-host | postgres | PostgreSQL 主机地址 |
| postgres-port | 5432 | PostgreSQL 端口 |
| postgres-db | skillhub | PostgreSQL 数据库名 |
| postgres-user | skillhub | PostgreSQL 用户名 |
| redis-host | redis | Redis 主机地址 |
| redis-port | 6379 | Redis 端口 |
| skillhub-public-base-url | 空 | 对外访问 URL，用于 OAuth 回调和前端运行时配置 |
| storage-base-path | /var/lib/skillhub/storage | 技能存储路径 |
| skillhub-storage-provider | local | 存储类型（local/s3） |
| skillhub-storage-s3-endpoint | 空 | S3/OSS Endpoint |
| skillhub-storage-s3-bucket | skillhub | S3/OSS Bucket |
| skillhub-storage-s3-region | us-east-1 | S3/OSS Region |
| skillhub-storage-s3-force-path-style | false | MinIO 等兼容存储设为 true |
| skillhub-storage-s3-auto-create-bucket | false | 是否自动创建 Bucket |
| skillhub-storage-s3-presign-expiry | PT10M | 预签名下载链接有效期 |
| skill-scanner-enabled | true | 是否启用扫描器 |
| skill-scanner-url | http://skillhub-scanner:8000 | 扫描器地址 |
| skill-scanner-mode | upload | 扫描模式 |
| oauth2-sourceid-provider | sourceid | SourceID 提供方标识 |
| oauth2-sourceid-authorization-grant-type | authorization_code | SourceID 授权模式 |
| oauth2-sourceid-redirect-uri | 空 | SourceID OAuth 回调地址 |
| oauth2-sourceid-client-name | SourceID | SourceID 登录按钮名称 |
| oauth2-sourceid-authorization-uri | 空 | SourceID 授权地址 |
| oauth2-sourceid-token-uri | 空 | SourceID Token 地址 |
| oauth2-sourceid-user-info-uri | 空 | SourceID 用户信息地址 |
| oauth2-sourceid-user-name-attribute | id | SourceID 用户唯一标识字段 |
| bootstrap-admin-enabled | true | 是否创建默认管理员 |
| bootstrap-admin-user-id | docker-admin | 管理员用户 ID |
| bootstrap-admin-username | admin | 管理员用户名 |
| bootstrap-admin-display-name | Platform Admin | 管理员显示名称 |
| bootstrap-admin-email | admin@example.com | 管理员邮箱 |
| skillhub-api-upstream | http://skillhub-server:8080 | 前端代理到后端的集群内地址 |
| session-cookie-secure | false | HTTPS 环境设为 true |
| skillhub-auth-local-show-entry | true | 是否展示本地用户名密码入口 |
| skillhub-access-policy-mode | OPEN | OAuth 准入策略模式，限制 Provider 时用 PROVIDER_ALLOWLIST |
| skillhub-access-policy-allowed-providers | 空 | 允许的 OAuth Provider 列表，多个值用逗号分隔 |

### Secret 配置项

| 键 | 说明 | 必填 |
|---|---|---|
| POSTGRES_PASSWORD | PostgreSQL 密码 | 是 |
| REDIS_PASSWORD | Redis 密码（with-infra 默认启用） | 是 |
| BOOTSTRAP_ADMIN_PASSWORD | 管理员密码 | 是 |
| oauth2-github-client-id | GitHub OAuth ID | 否 |
| oauth2-github-client-secret | GitHub OAuth 密钥 | 否 |
| SKILLHUB_STORAGE_S3_ACCESS_KEY | S3/OSS Access Key | 否 |
| SKILLHUB_STORAGE_S3_SECRET_KEY | S3/OSS Secret Key | 否 |
| SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID | SourceID Client ID | 否 |
| SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET | SourceID Client Secret | 否 |
| skill-scanner-llm-api-key | LLM API 密钥 | 否 |
| skill-scanner-llm-model | LLM 模型名称 | 否 |

### 存储配置

**本地存储（默认）**

默认使用本地文件存储，数据保存在 PVC `skillhub-storage-pvc` 中。

**S3/OSS 存储**

生产环境建议使用 S3 兼容的对象存储：

1. 修改 ConfigMap：
```yaml
skillhub-storage-provider: s3
```

2. 在 Secret 中添加：
```yaml
SKILLHUB_STORAGE_S3_ACCESS_KEY: your-access-key
SKILLHUB_STORAGE_S3_SECRET_KEY: your-secret-key
```

3. `base/backend-deployment.yaml` 已经读取这些键，无需额外修改 Deployment。

### 持久化存储

| PVC | 大小 | 说明 |
|-----|------|------|
| skillhub-storage-pvc | 10Gi | 技能文件存储 |
| postgres-data-0 | 10Gi | PostgreSQL 数据（with-infra only） |
| redis-data-0 | 5Gi | Redis 数据（with-infra only） |

## 镜像说明

| 组件 | 镜像 |
|---|---|
| 后端服务 | ghcr.io/iflytek/skillhub-server:latest |
| 前端服务 | ghcr.io/iflytek/skillhub-web:latest |
| 扫描器 | ghcr.io/iflytek/skillhub-scanner:latest |
| PostgreSQL | postgres:16-alpine |
| Redis | redis:7-alpine |

## 默认管理员

首次启动时，如果 `bootstrap-admin-enabled` 为 `true`，系统会自动创建管理员账户：

- 用户名：`admin`
- 密码：在 Secret 的 `BOOTSTRAP_ADMIN_PASSWORD` 中配置

**安全建议**：首次登录后，请立即修改默认密码。

## 常见问题

### Pod 一直 Pending

```bash
# 检查 PVC 是否绑定
kubectl get pvc -n skillhub

# 检查节点资源
kubectl describe node <node-name>
```

### 镜像拉取失败

如果镜像私有，需要创建拉取凭证：

```bash
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<GitHub用户名> \
  --docker-password=<GitHub Token> \
  -n skillhub

- PostgreSQL host, port, database name, username, and password
- Redis hostname, port, and password if your Redis requires authentication
- S3 endpoint, bucket, region, access key, and secret key
- SourceID OAuth client ID, client secret, redirect URI
- Harbor image tags if needed

Create a real secret file from the example. Keep the real file ignored by Git:

```bash
cp deploy/k8s/02-secret.example.yml deploy/k8s/02-secret.yml
# Edit deploy/k8s/02-secret.yml with real credentials
```

Or render it from environment variables:

```bash
export SPRING_DATASOURCE_PASSWORD='replace-me'
export SKILLHUB_STORAGE_S3_ACCESS_KEY='replace-me'
export SKILLHUB_STORAGE_S3_SECRET_KEY='replace-me'
export OAUTH2_SOURCEID_CLIENT_ID='replace-me'
export OAUTH2_SOURCEID_CLIENT_SECRET='replace-me'
export OSDS_SYSID='replace-me'
export OSDS_ACCESS_KEY_SECRET='replace-me'

deploy/k8s/render-secret.sh stdout > deploy/k8s/02-secret.yml
```

If you use the rendered-secret flow, keep `deploy/k8s/02-secret.yml` on the operator machine so `validate-k8s-external-deps.sh` and `safe-rollout.sh` can read the same file. Alternatively, run `bash scripts/validate-k8s-external-deps.sh deploy/k8s/01-configmap.yml /path/to/secret.yml` and `SECRET_FILE=/path/to/secret.yml bash deploy/k8s/safe-rollout.sh ...` so both steps use the same secret source.

Do not place real credentials in files tracked by Git.

Validate the Kubernetes config before rollout:

```bash
bash scripts/validate-k8s-external-deps.sh
```

### 数据库连接失败

```bash
CHECK_NETWORK=true bash scripts/validate-k8s-external-deps.sh
```

### 查看日志

```bash
kubectl apply -f deploy/k8s/00-namespace.yml

# 前端日志
kubectl logs -l app.kubernetes.io/name=skillhub-web -n skillhub -f

# 扫描器日志
kubectl logs -l app.kubernetes.io/name=skillhub-scanner -n skillhub -f
```

## 清理

```bash
# 删除所有资源
kubectl delete -k overlays/with-infra/  # 或 overlays/external/

# 删除命名空间
kubectl delete namespace skillhub
```



## 3.1 Safe Incremental Rollout Script (Recommended for Production)

Use the rollout helper to run precheck + diff + incremental apply with health checks:

```bash
# Full pipeline: validation + kubectl diff + incremental rollout
CHECK_NETWORK=true bash deploy/k8s/safe-rollout.sh all

# If the rollout fails and leaves rollback artifacts behind, restore from that directory
ROLLBACK_ARTIFACT_DIR=deploy/k8s/snapshots/rollback-skillhub-XXXXXX \
  bash deploy/k8s/safe-rollout.sh rollback
```

The script will:

- validate external dependency configuration using `scripts/validate-k8s-external-deps.sh`
- verify required cluster prerequisites such as `harbor-regcred` and ingress TLS secret
- export a pre-release snapshot of current cluster objects to `deploy/k8s/snapshots/` (Secrets excluded by default)
- run `kubectl diff` on rollout manifests before apply (Secret diff is opt-in)
- apply manifests in safe order (base resources -> scanner -> backend -> frontend -> ingress)
- patch deployment template checksum annotations so ConfigMap/Secret-only releases still trigger new pod rollouts
- back up current ConfigMap/Secret/Service/Ingress resources before apply
- restore backed-up ConfigMap/Secret/Service/Ingress resources and undo only the deployment revisions changed by the failed rollout

Notes:

- `NAMESPACE` is fixed to `skillhub` for the checked-in manifests; do not override it.
- `DIFF_SECRETS=true` will include the Secret manifest in `kubectl diff`; keep it off unless you are in a controlled environment.
- `INCLUDE_SECRETS_IN_SNAPSHOT=true` will add live Secret objects to the snapshot file; this is break-glass only and should be handled as sensitive output.
- Successful rollouts clean up rollback backups by default. Failed rollouts keep the backup directory and print its path for manual recovery.
- A step-by-step release procedure is documented in `deploy/k8s/SRE-RELEASE-RUNBOOK.md`.
- Use `deploy/k8s/PRODUCTION-CONTEXT-SWITCH-CHECKLIST-2026-04-05.md` before changing `kubectl` to the production cluster.
- For cloud-managed PostgreSQL, Redis, and S3-compatible storage, production rollout still requires a separate provider-backed backup and restore plan; Kubernetes rollback artifacts are not a substitute. See `deploy/k8s/PRODUCTION-READINESS-2026-04-05.md`.
- If cloud platform backup permissions are not available and the live data set is still very small, a temporary pre-release local archive can be created with `deploy/k8s/create-temporary-release-archive.sh`. See `deploy/k8s/TEMPORARY-CONNECTION-CREDS-BACKUP-QUICKSTART-2026-04-05.md`.
- The current cluster install verification record is documented in `deploy/k8s/INSTALL-VERIFICATION-2026-04-05.md`.
- Standard rollback confirmation is automated in `deploy/k8s/rollback-health-check.sh`.
- Controlled rehearsal steps are documented in `deploy/k8s/ROLLBACK-DRILL-CHECKLIST.md`.

## 4. Apply Kubernetes Manifests

```bash
kubectl apply -f deploy/k8s/01-configmap.yml
kubectl apply -f deploy/k8s/02-secret.yml
kubectl apply -f deploy/k8s/06-services.yaml
kubectl apply -f deploy/k8s/03-01-scanner-deployment.yaml
kubectl apply -f deploy/k8s/03-backend-deployment.yml
kubectl apply -f deploy/k8s/04-frontend-deployment.yml
kubectl apply -f deploy/k8s/05-ingress.yml
```

## 5. Verify Rollout

```bash
kubectl -n skillhub get pods
kubectl -n skillhub get svc
kubectl -n skillhub get ingress

kubectl -n skillhub rollout status deployment/skillhub-scanner
kubectl -n skillhub rollout status deployment/skillhub-server
kubectl -n skillhub rollout status deployment/skillhub-web

kubectl -n skillhub logs deployment/skillhub-scanner --tail=200
kubectl -n skillhub logs deployment/skillhub-server --tail=200
kubectl -n skillhub logs deployment/skillhub-web --tail=200
```

## 6. Functional Checks

After ingress is ready, verify:

```bash
curl -k https://skillhub.ruijie.com.cn/api/v1/auth/methods
curl -k https://skillhub.ruijie.com.cn/
kubectl -n skillhub exec deploy/skillhub-server -- wget -qO- http://127.0.0.1:8080/actuator/health
```

Expected checks:

- Login page opens through ingress
- OAuth is the default tab
- Only `锐捷SSO` is shown in OAuth login options
- `/api/v1/auth/methods` returns `oauth-sourceid`
- backend `/actuator/health` returns `UP`

## Notes

- This baseline assumes your cluster does not provide persistent volumes for bundled data services.
- The Harbor password should only be used at `docker login` time or when creating the Kubernetes pull secret. Do not store it in Git.
- If you do not want bootstrap admin, keep `bootstrap-admin-enabled` as `false`.
- Update `01-configmap.yml` and `02-secret.yml` to point at your external PostgreSQL, Redis, and object storage endpoints before rollout.
