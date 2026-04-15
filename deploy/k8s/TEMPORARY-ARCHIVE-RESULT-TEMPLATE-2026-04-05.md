# Temporary Archive Result Template - 2026-04-05

Use this template to report the result of the temporary pre-release archive flow.

## Archive

- backup directory:
- tarball path:
- checksum file path:

## PostgreSQL Export

- dump path:
- export succeeded: yes or no

## Redis Export

- mode: `rdb` or `best-effort`
- key export path:
- stream export path:
- notes:

## S3 Export

- mode: `bucket-mirror` or `exact-key-fallback`
- client: `aws-cli` or `mc`
- bucket mirror path:
- export succeeded: yes or no

## PostgreSQL Restore Validation

- validation report path:
- restore succeeded: yes or no
- `flyway_rows`:
- `flyway_latest`:
- `user_account_rows`:
- `namespace_member_rows`:
- `skill_version_rows`:
- `security_audit_rows`:
- `skill_version_stats_rows`:

## Release Decision Input

- current production data is still very small: yes or no
- fallback archive is being used instead of provider-backed backup: yes or no
- known residual risk accepted: yes or no