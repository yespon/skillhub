# Temporary Connection-Credentials Backup Quickstart - 2026-04-05

Use this only when:

- production data is still very small
- cloud platform backup permissions are not available
- PostgreSQL, Redis, and S3 connection credentials are available in `deploy/k8s/01-configmap.yml` and `deploy/k8s/02-secret.yml`

## Goal

Create a pre-release local archive containing:

- PostgreSQL logical dump
- Redis RDB export when possible, otherwise a best-effort Redis state capture
- S3 bucket mirror
- metadata and checksums

## One Command

From the repository root:

```bash
bash deploy/k8s/create-temporary-release-archive.sh
```

Default output:

```text
deploy/k8s/archives/backup-YYYYMMDD-HHMMSS/
deploy/k8s/archives/backup-YYYYMMDD-HHMMSS.tar.gz
```

## Inputs

The script reads:

- `deploy/k8s/01-configmap.yml`
- `deploy/k8s/02-secret.yml`

Override paths when needed:

```bash
CONFIGMAP_FILE=/path/to/01-configmap.yml \
SECRET_FILE=/path/to/02-secret.yml \
bash deploy/k8s/create-temporary-release-archive.sh
```

Override output location when needed:

```bash
BACKUP_ROOT=/secure/path \
bash deploy/k8s/create-temporary-release-archive.sh
```

## Tooling

The script prefers local tools and falls back to Docker:

- PostgreSQL: `pg_dump` or `postgres:16`
- Redis: `redis-cli` or `redis:7-alpine`
- Object storage: `aws` or `amazon/aws-cli:2`, then `mc` or `minio/mc` as a secondary option

If neither the local tool nor Docker is available for a required step, the script stops with an error.

## Important Limits

- PostgreSQL export is a logical dump, not provider-side PITR.
- Redis fallback mode is best-effort only if RDB export is blocked by the managed service.
- S3 may run in `bucket-mirror`, `exact-key-fallback`, or `app-api-fallback` mode. The application fallback downloads files through SkillHub's own `/api/v1/skills/{namespace}/{slug}/versions/{version}/file` endpoint and requires a reachable app base URL.

Use the application fallback when direct object-store credentials can neither list the bucket nor read objects:

```bash
APP_BASE_URL=http://127.0.0.1:18080 \
bash deploy/k8s/create-temporary-release-archive.sh
```

Optional when the target environment needs caller context:

```bash
APP_BASE_URL=http://127.0.0.1:18080 \
APP_COOKIE_JAR=/path/to/cookies.txt \
APP_MOCK_USER_ID=local-admin \
bash deploy/k8s/create-temporary-release-archive.sh
```

When hidden or unpublished versions must also be exported, use the admin version file API after deploying the matching backend change and authenticating as a platform admin:

```bash
APP_BASE_URL=http://127.0.0.1:18080 \
APP_COOKIE_JAR=/path/to/admin-cookies.txt \
APP_ADMIN_FILE_API=true \
bash deploy/k8s/create-temporary-release-archive.sh
```

## Minimum Validation

Before trusting the archive as a temporary release gate, validate at least PostgreSQL restore once:

```bash
bash deploy/k8s/verify-temporary-postgres-restore.sh \
	BACKUP_DIR=deploy/k8s/archives/backup-YYYYMMDD-HHMMSS
```

The restore validation script intentionally ignores source ownership and privilege metadata during `pg_restore`, so it does not require production roles such as `skillhub` to exist in the temporary validation database.

Then record:

- backup directory
- tarball path
- checksum file path
- whether Redis used `rdb` or `best-effort` mode
- whether S3 used `bucket-mirror`, `exact-key-fallback`, or `app-api-fallback` mode
- which S3 client was used: `aws-cli`, `mc`, or `curl`
- whether PostgreSQL restore validation succeeded

Use `deploy/k8s/TEMPORARY-ARCHIVE-RESULT-TEMPLATE-2026-04-05.md` as the reporting template.