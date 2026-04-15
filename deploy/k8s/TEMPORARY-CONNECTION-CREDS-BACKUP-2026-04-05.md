# Temporary Backup Plan With Connection Credentials - 2026-04-05

Use this plan only when all of the following are true:

- production data volume is very small
- the operator has service connection credentials but does not have cloud platform backup permissions
- a temporary pre-release archive is needed before a controlled rollout

This is a temporary risk-reduction measure.

It is not a substitute for provider-level backup, PITR, or official object recovery capabilities.

For the copyable operator flow, use `deploy/k8s/TEMPORARY-CONNECTION-CREDS-BACKUP-QUICKSTART-2026-04-05.md`.

For the actual export command, use `deploy/k8s/create-temporary-release-archive.sh`.

## Verdict

Yes, a temporary archive can still be produced with the current access model.

Given the current environment, the practical fallback is:

1. PostgreSQL logical export through the database connection
2. Redis best-effort export through the Redis connection
3. Full object copy of the S3 bucket through the existing access key and secret key
4. Pack all three outputs into one timestamped local archive with checksums

For a very small data set, this is usually good enough to create a pre-release rollback point, provided the team accepts the limits listed below.

## What This Temporary Archive Can Cover

### PostgreSQL

Can back up:

- table data
- schema objects inside the target database
- most application data needed for rollback of a small deployment

Typical command:

```bash
PGPASSWORD='<postgres_password>' \
pg_dump \
  -h '<postgres_host>' \
  -p '<postgres_port>' \
  -U '<postgres_user>' \
  -d '<postgres_db>' \
  -Fc \
  -f backup/postgres/skillhub.dump
```

Restore validation target:

```bash
createdb skillhub_restore_test
pg_restore -d skillhub_restore_test backup/postgres/skillhub.dump
```

### Redis

Can back up in two tiers:

Tier A, preferred if the service allows it:

```bash
redis-cli -h '<redis_host>' -p '<redis_port>' -a '<redis_password>' --rdb backup/redis/dump.rdb
```

Tier B, fallback if `--rdb` is blocked by the managed service:

- export critical keys by namespace using `SCAN`
- export scanner stream data with `XRANGE`
- record queue depth and consumer status with `XLEN`, `XINFO GROUPS`, `XINFO CONSUMERS`

Recommended minimum Redis capture for SkillHub:

- Spring Session keys
- `skillhub:scan:requests`
- idempotency keys with prefix `idempotent:`
- role version keys with prefix `user:`
- operational lock keys that affect reconciliation if present

Example commands:

```bash
redis-cli -h '<redis_host>' -p '<redis_port>' -a '<redis_password>' XLEN skillhub:scan:requests
redis-cli -h '<redis_host>' -p '<redis_port>' -a '<redis_password>' XINFO GROUPS skillhub:scan:requests
redis-cli -h '<redis_host>' -p '<redis_port>' -a '<redis_password>' XRANGE skillhub:scan:requests - + COUNT 100000 > backup/redis/scan-requests.txt
redis-cli -h '<redis_host>' -p '<redis_port>' -a '<redis_password>' --scan > backup/redis/all-keys.txt
```

### S3-Compatible Object Storage

Can back up the entire bucket content with the existing endpoint and access credentials.

Using AWS CLI:

```bash
AWS_ACCESS_KEY_ID='<s3_access_key>' \
AWS_SECRET_ACCESS_KEY='<s3_secret_key>' \
aws s3 sync \
  --endpoint-url '<s3_endpoint>' \
  s3://'<bucket_name>' \
  backup/s3/bucket
```

Using MinIO client:

```bash
mc alias set skillhub '<s3_endpoint>' '<s3_access_key>' '<s3_secret_key>'
mc mirror skillhub/'<bucket_name>' backup/s3/bucket
```

For a tiny data set, a full bucket copy is simpler and safer than trying to select objects.

If whole-bucket mirror fails with `Access Denied`, the backup flow can fall back to exporting exact object keys from PostgreSQL `skill_file.storage_key` and copying objects one by one. This works in environments where the credential has object read permission but does not have bucket listing permission.

## One Recommended Archive Layout

```text
backup-YYYYMMDD-HHMMSS/
  postgres/
    skillhub.dump
  redis/
    dump.rdb
    all-keys.txt
    scan-requests.txt
    stream-groups.txt
  s3/
    bucket/
  manifest/
    metadata.txt
    sha256.txt
```

Recommended metadata fields:

- backup timestamp
- release commit
- image tags
- operator name
- postgres endpoint and database name
- redis endpoint
- s3 endpoint and bucket
- export command results summary

## Limits You Must Accept

### PostgreSQL Limits

- no provider-level PITR from this method
- no guaranteed backup of roles or globals unless the account has extra privileges
- restore speed is slower than provider snapshot restore for large data sets

### Redis Limits

- managed Redis may block `--rdb`
- session data may still be treated as disposable depending on incident policy
- stream recovery may require manual reconciliation even if export succeeds

### S3 Limits

- object copy is not the same as provider-side bucket version history
- deleted historical versions are not preserved unless versioning is enabled on the provider side
- restore is file copy based and may need manual consistency checks against PostgreSQL metadata

## Minimum Safe Use Conditions

You may use this temporary archive as a release gate reduction only if all items below are true:

- current production data volume is confirmed very small
- the archive is taken immediately before rollout
- the archive is stored on an operator-controlled secure path
- checksum is generated for exported files
- at least PostgreSQL restore is validated into an isolated target
- Redis incident policy explicitly accepts either session loss or manual reconciliation
- the team accepts that this is a temporary workaround, not a durable production backup strategy

## Recommended Temporary Decision

If the team lacks cloud platform permissions today, but does have valid PostgreSQL, Redis, and S3 connection credentials, then yes: create a one-time pre-release local archive using those credentials before any production rollout attempt.

This can temporarily reduce rollout risk for a very small deployment.

It should not be treated as the permanent answer for production readiness. The permanent answer remains provider-backed backup, restore, and recovery ownership.