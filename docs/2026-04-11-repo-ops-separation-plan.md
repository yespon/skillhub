# Repo Ops Separation Plan

## Background

After promoting the replay branch to the new `dev` baseline, the repository now contains four distinct classes of automation:

1. Product delivery workflows
2. Fork maintenance workflows
3. Repository operations workflows
4. External documentation integrations

These classes should not be treated equally. Product delivery and fork maintenance belong to the baseline. Repository operations and external integrations should be explicitly isolated so that baseline maintenance does not inherit unrelated governance or community-specific behavior.

## Classification

### Keep In Baseline

- `.github/workflows/pr-tests.yml`
- `.github/workflows/pr-e2e.yml`
- `.github/workflows/publish-images.yml`
- `.github/workflows/deploy-docs.yml`
- `docs/skillhub/`
- `scripts/rebase-upstream.sh`
- `scripts/check-customization-drift.sh`

These files directly support build, test, release, documentation delivery, or upstream-sync maintenance.

### Keep As Fork-Maintenance Capability

- `.github/workflows/sync-upstream.yml`
- `docs/19-customization-branch-workflow.md`
- `CLAUDE.md`
- `AGENTS.md`

These files document or automate the current upstream-mirror plus customization operating model. They are part of the engineering baseline as long as the fork model remains active.

### Default Disabled, Pending Extraction

- `.github/workflows/claim-issue-reward.yml`
- `.github/workflows/statistic-member-reward.yml`

These workflows are not required for product runtime, CI quality gates, release publication, or fork synchronization. They are governance and incentive automation. They should not run automatically in the default repository state.

Current action in this phase:

- switch both workflows to manual-only triggering
- preserve scripts and templates for later extraction

### Extract To Repo Ops Package Or Separate Repository

- `.github/scripts/share-reward.ts`
- `.github/scripts/count-reward.ts`
- `.github/scripts/type.ts`
- `.github/ISSUE_TEMPLATE/reward-task.yml`

Recommended target:

- a separate `repo-ops` repository, or
- a dedicated `.github` governance repository if the organization standardizes issue and reward automation centrally

## Migration Steps

### Phase 1

- keep reward data model and scripts in-tree
- disable automatic reward execution
- document the separation boundary

### Phase 2

- move reward scripts and issue template into a dedicated repo-ops location
- replace local copies with references or submodule/package consumption only if still needed
- remove reward workflows from the product repository after extraction is validated

### Phase 3

- review `deepwiki.yml`
- remove it if the DeepWiki crawl is not part of active documentation operations

## Decision Rules

- If a workflow is required to ship, test, deploy, or maintain the fork baseline, keep it in the main repository.
- If a workflow automates community incentives, finance-like tagging, or repository governance, move it out of the main baseline.
- If an integration cannot block product delivery and has no current owner, disable it before deciding whether to remove it.

## Immediate Outcome

As of 2026-04-11:

- reward workflows are moved to manual-only mode
- baseline maintenance scripts are cleaned and validated
- docs, CI, image publication, and upstream-sync workflows remain baseline capabilities