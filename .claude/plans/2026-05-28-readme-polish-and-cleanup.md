# README Polish, Repo Cleanup & Fixes

**Date:** 2026-05-28
**Status:** Approved — implementing

---

## Tasks

### 1. README Updates

**Remove from features table:**
- Workspace Membership API
- Internal API Key Auth

**Add to features:**
- RBAC: AppSmith as RBAC source for IAM — workspace membership and role management APIs

**Header:**
- Title: "PocketFM x AppSmith"
- Show both logos: PocketFM logo (`static/pocketfm_logo.webp`) + AppSmith logo (`static/appsmith_logo_white.png`)

**About AppSmith section:**
- Remove the logo/image placeholder
- Keep only the clickable video link (plain text, no thumbnail)

**Visual makeover:**
- Emoji section headers
- Shields.io badges (branch, registry, environment)
- Clean dividers
- Professional and attractive layout

### 2. Fix Main Branch Pipeline

`main` branch was created before workflows were committed. Merge `develop` into `main` so `build-prod-image.yml` exists on `main`.

### 3. Clean Up Stale Branches

1,582 branches inherited from upstream. Delete all except:
- `develop` (our dev branch)
- `main` (our prod branch)
- `release` (upstream reference — keep to see upstream workflow configs)
- `backup/*` (our backups)
- Keep 1 upstream feature branch as reference (e.g., `FEATURE/workflows-editor`)

### 4. SRE Message

Final message with all asks: PAT, K8s secrets, default branch, branch protection.

---

## Execution Order

| # | Task | Status |
|---|------|--------|
| 1 | Rewrite README.md (all fixes + visual makeover) | TODO |
| 2 | Update CLAUDE.md (dev vs prod image details) | TODO |
| 3 | Merge develop into main (fix pipeline) | TODO |
| 4 | Bulk delete stale branches (keep 5-6) | TODO |
| 5 | Commit + push | TODO |
| 6 | Provide SRE message | TODO |
