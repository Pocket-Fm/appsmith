# CI/CD, Branching & README Overhaul Plan

**Date:** 2026-05-28
**Status:** Draft — awaiting approval

---

## Problem Statement

Single branch (`develop`) producing one image, two forks building independently, upstream AppSmith README, no dev/prod separation. Need: two-image pipeline, org fork consolidation, private GHCR auth for K8s, and rebranded README.

---

## 1. Branching Strategy

### Branch Names

| Branch | Purpose | Image Tag | Protection |
|--------|---------|-----------|------------|
| `develop` | Day-to-day development | `:dev`, `:dev-<sha>` | None (direct push OK) |
| `main` | Production-ready code. PRs only from `develop` | `:latest`, `:<sha>`, `:v<semver>` | **Protected** (require PR + approval) |

### Why `main` Instead of `release`?

- `release` currently exists and is protected, but it's **19 commits behind** `develop`
- `release` has 7 upstream AppSmith workflows hardcoded to trigger on it — renaming avoids conflicts
- `main` is industry-standard for production branches
- We create `main` fresh from current `develop` HEAD — starts fully in sync, no merge needed

### Migration Steps

1. Create `main` branch from `develop` HEAD (already in sync)
2. Set branch protection on `main` (require PR, no direct push)
3. `release` branch can be left as-is (don't delete — upstream workflows reference it, harmless)
4. Set `main` as default branch on GitHub (production branch opens by default, same as `release` today)

### Promotion Flow

```
develop ──PR──► main
     │                   │
     ▼                   ▼
 :dev image         :latest / :v1.2.3 image
 (QA/staging)       (production)
```

---

## 2. CI/CD Pipeline — Two Workflows

### 2a. Dev Image Workflow (`build-dev-image.yml`)

- **Trigger:** `push` to `develop`
- **Registry:** `ghcr.io/pocket-fm/appsmith-ce`
- **Tags:**
  - `ghcr.io/pocket-fm/appsmith-ce:dev`
  - `ghcr.io/pocket-fm/appsmith-ce:dev-<sha>`
- **Client build flag:** `REACT_APP_ENVIRONMENT=DEVELOPMENT`
- **Jobs:** Same 4-job structure (server, client, rts in parallel, then package)

### 2b. Prod Image Workflow (`build-prod-image.yml`)

- **Trigger:** `push` to `main` (fires when a PR from `develop` merges into `main`)
- **Registry:** `ghcr.io/pocket-fm/appsmith-ce`
- **Tags:**
  - `ghcr.io/pocket-fm/appsmith-ce:latest`
  - `ghcr.io/pocket-fm/appsmith-ce:<sha>`
  - `ghcr.io/pocket-fm/appsmith-ce:v<version>` (if `workflow_dispatch` tag input provided)
- **Client build flag:** `REACT_APP_ENVIRONMENT=PRODUCTION`
- `workflow_dispatch` with optional semver tag input

### 2c. Upstream AppSmith Workflows — No Conflict

By using `main` (new branch) instead of `release`, the 7 upstream workflows that trigger on `release` branch push will **never fire** for our prod builds. No need to delete or modify them.

### 2d. Old Workflow Cleanup

- **Delete** `build-pocketfm-image.yml` (replaced by two new workflows)

---

## 3. Fork Consolidation — Pocket-Fm/appsmith as Primary

### Current to Target

| Current | Target |
|---------|--------|
| `origin` = `appsmithorg/appsmith` | `upstream` = `appsmithorg/appsmith` (fetch only) |
| `fork` = `Pocket-Fm/appsmith` | `origin` = `Pocket-Fm/appsmith` (**primary**) |
| `personal` = `akash-pocketfm/appsmith` | **Remove** — stop pushing, archive later |

### CI Auth (Non-Issue)

`secrets.GITHUB_TOKEN` in GitHub Actions automatically has `packages:write` for the repo that owns the workflow. Workflows in `Pocket-Fm/appsmith` can push to `ghcr.io/pocket-fm/*` with zero extra config.

---

## 4. Private GHCR Auth for Kubernetes

### Why This Is Needed

Current setup pulls from `ghcr.io/akash-pocketfm/appsmith-ce` — this is a **public** GHCR package (no `imagePullSecrets` in infra, pods pull fine). When switching to `ghcr.io/pocket-fm/appsmith-ce` (private), K8s pods will get `ImagePullBackOff` without auth.

### Important: App Secrets vs Image Pull Secrets — Two Different Things

The existing ExternalSecret in the wrapper chart syncs **application env vars** (encryption keys, DB URLs, OAuth creds) from AWS Secrets Manager into a K8s `Opaque` secret. The pod reads these as environment variables at runtime.

Image pull auth is completely separate. It's a `kubernetes.io/dockerconfigjson` secret that the **kubelet** (not the app) uses **before** the container even starts, to authenticate with the container registry and download the image.

| | App Secrets (existing) | Image Pull Secret (new) |
|---|---|---|
| **Purpose** | Env vars for the app | Auth to download the Docker image |
| **K8s Secret type** | `Opaque` | `kubernetes.io/dockerconfigjson` |
| **Used by** | Container at runtime | Kubelet before container starts |
| **Current source** | AWS Secrets Manager via ESO | Does not exist yet |

You cannot mix them — adding a GHCR token to the existing AWS Secrets Manager path would just create another env var, not an image pull credential.

### Recommended: SRE Creates K8s Secret Directly

Simplest approach. SRE runs `kubectl` commands to create a docker-registry secret in each namespace:

```bash
# QA namespace
kubectl create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=<BOT_USERNAME> \
  --docker-password=<PAT> \
  --docker-email=platform@pocketfm.com \
  -n appsmith-qa

# Prevent ArgoCD from deleting it during sync
kubectl annotate secret ghcr-pull-secret \
  argocd.argoproj.io/compare-options=IgnoreExtraneous \
  -n appsmith-qa

# Prod namespace (same commands, different namespace)
kubectl create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=<BOT_USERNAME> \
  --docker-password=<PAT> \
  --docker-email=platform@pocketfm.com \
  -n appsmith-prod

kubectl annotate secret ghcr-pull-secret \
  argocd.argoproj.io/compare-options=IgnoreExtraneous \
  -n appsmith-prod
```

**Why this works:** Simple, 4 commands total, no new infra patterns. The ArgoCD annotation prevents it from being pruned during sync.

**Downside:** Not GitOps — the secret lives outside the infra repo. If the PAT expires, SRE must manually recreate. Acceptable for now since PATs can be set to never expire (or long-lived).

**Future upgrade path:** If we want full GitOps later, we create a separate ExternalSecret that syncs GHCR creds from a dedicated AWS Secrets Manager path into a `dockerconfigjson`-type K8s secret. But that needs a new AWS secret path + IRSA policy update — more SRE work for minimal gain right now.

### Then: Our Infra PR — Tell The Pod to Use The Secret

Regardless of how the secret is created, we update the Helm values to reference it:

**File:** `overlays/appsmith/qa/values.yaml` — change `_image` block:
```yaml
appsmith:
  _image:
    registry: ghcr.io
    repository: pocket-fm/appsmith-ce
    tag: dev
    pullPolicy: Always
  imagePullSecrets:
    - name: ghcr-pull-secret
```

**File:** `overlays/appsmith/prod/values.yaml` — change `_image` block:
```yaml
appsmith:
  _image:
    registry: ghcr.io
    repository: pocket-fm/appsmith-ce
    tag: latest
    pullPolicy: Always
  imagePullSecrets:
    - name: ghcr-pull-secret
```

**One thing to verify:** The upstream AppSmith Helm chart (the `appsmith-3.6.9.tgz` subchart) must pass `imagePullSecrets` to the pod spec in its deployment/statefulset template. Most popular charts do this. We verify by inspecting the packed subchart in the infra repo — no SRE needed, just a file read.

### What Needs SRE

| Task | Who |
|------|-----|
| Create GitHub PAT (classic, `read:packages`, from bot account) | **SRE** |
| Create K8s `docker-registry` secret in `appsmith-qa` and `appsmith-prod` namespaces | **SRE** |
| Annotate secrets to prevent ArgoCD pruning | **SRE** |
| Protect `main` branch on `Pocket-Fm/appsmith` | **SRE** |
| Set default branch to `main` on `Pocket-Fm/appsmith` (requires admin) | **SRE** |
| ArgoCD sync after our infra PR merges | **SRE** / auto-sync |

### Draft SRE Message

```
Hi SRE team,

We're migrating AppSmith container images from my personal fork
(ghcr.io/akash-pocketfm/appsmith-ce) to the PocketFM org fork
(ghcr.io/pocket-fm/appsmith-ce).

The org GHCR package is private, so K8s needs credentials to
pull the image. Here's what we need:

1. GitHub PAT (Classic) — please create from a bot/service
   account with read:packages scope, scoped to the Pocket-Fm
   org. This token lets K8s authenticate with GHCR to pull
   our private Docker image.

2. K8s image pull secret — once the PAT is created, please
   run these commands in both namespaces:

   # QA
   kubectl create secret docker-registry ghcr-pull-secret \
     --docker-server=ghcr.io \
     --docker-username=<BOT_USERNAME> \
     --docker-password=<PAT> \
     --docker-email=platform@pocketfm.com \
     -n appsmith-qa

   kubectl annotate secret ghcr-pull-secret \
     argocd.argoproj.io/compare-options=IgnoreExtraneous \
     -n appsmith-qa

   # Prod
   kubectl create secret docker-registry ghcr-pull-secret \
     --docker-server=ghcr.io \
     --docker-username=<BOT_USERNAME> \
     --docker-password=<PAT> \
     --docker-email=platform@pocketfm.com \
     -n appsmith-prod

   kubectl annotate secret ghcr-pull-secret \
     argocd.argoproj.io/compare-options=IgnoreExtraneous \
     -n appsmith-prod

3. GitHub repo settings on Pocket-Fm/appsmith (requires admin):

   a. Set default branch to "main" (currently "release").
      This is the production branch — should open by default when
      anyone visits the repo (same behavior as "release" today).
      Settings > General > Default branch > main

   b. Add branch protection on "main" branch (production):
      require PR review, no direct push.

   The "main" branch already exists — we just pushed it.

4. ArgoCD sync — after we merge our infra PR (updating image
   refs + adding imagePullSecrets to Helm values), please sync
   appsmith-qa and appsmith-prod.

We'll raise the infra PR ourselves. Just need items 1-3 from
your side before we can test.

Timeline: non-urgent, this week if possible.

Thanks!
```

---

## 5. README Overhaul

### Approach

Keep all relevant AppSmith content (what AppSmith is, installation docs, learning resources, dev setup links). Remove only what's irrelevant to us (contributor avatars, AppSmith Agents promo, community/Discord links, AppSmith Cloud signup). Add our PocketFM-specific sections on top.

### What We Keep From Original README

- AppSmith description (what it is, what it does)
- Installation methods table (Docker, K8s, AWS)
- Development setup link
- Learning resources (docs, tutorials, videos, templates)

### What We Remove

- Contributor avatars section
- AppSmith Agents promo section
- Contributing guide / Code of Conduct links
- Discord / Community Portal / support email
- AppSmith Cloud signup link
- YouTube subscriber/view badges
- AppSmith logo (replaced with PocketFM branding)

### What We Add (PocketFM Sections)

- PocketFM header + logo + tagline
- "What Is This?" — fork context, why we forked
- "Features Added" — table of 7 features (OIDC, API key auth, etc.)
- "What We Modified" — table of 4 changes (EE flags, MongoDB compat, etc.)
- "CI/CD Pipeline" — diagram + image tags table
- "Branching Strategy" — develop to main flow
- "Deployment" — QA + Prod URLs, image refs
- "Built On" — credits to upstream AppSmith with link

### Final Structure

```
# PocketFM AppSmith (header + logo)
> Tagline

## What Is This?
## Features Added
## What We Modified
## CI/CD Pipeline
## Branching Strategy
## Deployment

--- (separator)

## About AppSmith (kept from original)
## Installation (kept from original)
## Development (kept from original)
## Learning Resources (kept from original)
## Built On / Credits
```

---

## 6. Execution Order

### Phase 1 — Code Changes (We Do, This Repo)

| # | Task | Status |
|---|------|--------|
| 1 | Write `build-dev-image.yml` (triggers on `develop` push) | TODO |
| 2 | Write `build-prod-image.yml` (triggers on `main` push) | TODO |
| 3 | Delete old `build-pocketfm-image.yml` | TODO |
| 4 | Write new `README.md` (PocketFM branding) | TODO |
| 5 | Update `.claude/CLAUDE.md` with new branching/CI docs | TODO |

### Phase 2 — SRE Dependencies (Send Message, Wait)

| # | Task | Owner | Status |
|---|------|-------|--------|
| 6 | Create GitHub PAT (`read:packages`, bot account) | SRE | TODO |
| 7 | Create K8s `ghcr-pull-secret` in `appsmith-qa` + `appsmith-prod` namespaces | SRE | TODO |
| 8 | Annotate secrets with ArgoCD ignore annotation | SRE | TODO |
| 9 | Create + protect `main` branch on `Pocket-Fm/appsmith` | SRE | TODO |

### Phase 3 — Infra PR (We Do, After SRE Completes Phase 2)

| # | Task | Status |
|---|------|--------|
| 10 | Verify AppSmith subchart supports `imagePullSecrets` in its templates | TODO |
| 11 | PR to infra repo: Update Helm values (image to `pocket-fm/appsmith-ce`, add `imagePullSecrets`) | TODO |

### Phase 4 — Cutover

| # | Task | Owner | Status |
|---|------|-------|--------|
| 12 | Create `main` branch from `develop` HEAD | Us | TODO |
| 13 | Set `main` as default branch on GitHub | SRE | TODO |
| 14 | ArgoCD sync for QA + Prod | SRE/auto | TODO |
| 15 | Update local git remotes (rename fork to origin, origin to upstream, remove personal) | Us | TODO |
| 16 | Stop pushing to personal fork | Us | TODO |

---

## Decision Points Still Open

1. **Upstream workflows:** Delete all 44 irrelevant ones, or just leave them? (Using `main` instead of `release` means they won't fire for our prod builds anyway)
