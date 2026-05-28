<p align="center">
  <!-- TODO: Replace with PocketFM logo when available -->
  <img src="static/appsmith_logo_white.png" alt="PocketFM AppSmith" width="350">
</p>

<h2 align="center">PocketFM AppSmith</h2>

<p align="center">
  <strong>AppSmith CE, supercharged for PocketFM</strong><br>
  Enterprise SSO, granular access control, internal API auth &mdash; all unlocked. No license. No upgrade prompts.
</p>

---

## What Is This?

This is PocketFM's fork of [AppSmith Community Edition](https://github.com/appsmithorg/appsmith). We took the open-source low-code platform and bolted on the enterprise features we needed &mdash; OIDC single sign-on, super admin restrictions, service-to-service API key auth &mdash; without paying for an enterprise license.

Everything runs on our own infrastructure. The upstream CE codebase is the foundation; our changes live on top.

## Features We Added

| Feature | What It Does |
|---------|-------------|
| **OIDC SSO** | JumpCloud / generic OIDC login with auto-configured Spring Security registration |
| **OAuth2 Token Injection** | `{{APPSMITH_USER_OAUTH2_ACCESS_TOKEN}}` placeholder injects logged-in user's OAuth2 token into datasource API calls |
| **Super Admin Restrictions** | Workspace creation, datasource editing, user invites, and role changes locked to super admins only |
| **Internal API Key Auth** | `X-API-Key` header for service-to-service calls (IAM, automation) &mdash; bypasses session auth |
| **Workspace Membership API** | `GET /api/v1/workspaces/{id}/is-member?email=...` &mdash; open endpoint for backend authorization checks |
| **Auto-Add Super Admins** | All super admin users automatically added as Administrators to every new workspace |
| **Workspace/App ID Vars** | `{{appsmith.workspaceId}}` and `{{appsmith.appId}}` available in datasource headers for backend routing |

## What We Modified

| Area | What Changed |
|------|-------------|
| **EE Feature Flags** | Force-enabled `license_gac_enabled`, `license_oidc_enabled`, `license_saml_enabled` in both client and server |
| **MongoDB Compatibility** | Added 15 EE enum stubs + fault-tolerant `AclPermission` converter to prevent CE crashes on EE-populated MongoDB |
| **Plugin Error Handling** | Null guards for EE-only plugins that have no JAR in CE &mdash; prevents NPE on home page |
| **Admin UI** | Hidden EE upgrade pages (Branding, Audit Logs, Provisioning); replaced Access Control with informational page |

## CI/CD Pipeline

Two separate workflows build dev and prod images:

```
develop ──push──> Build Dev Image
     |
     PR
     |
    main ──merge──> Build Prod Image
```

| Trigger | Branch | Image Tags |
|---------|--------|------------|
| Push to `develop` | `develop` | `:dev`, `:dev-<sha>` |
| PR merge to `main` | `main` | `:latest`, `:<sha>`, `:v<semver>` |

**Registry:** `ghcr.io/pocket-fm/appsmith-ce`

Both workflows build multi-arch images (amd64 + arm64) with parallel jobs: server (Maven) + client (Yarn) + RTS in parallel, then Docker package.

## Branching Strategy

| Branch | Purpose | Protection |
|--------|---------|------------|
| `develop` | Development &mdash; all commits land here | None (direct push) |
| `main` | Production &mdash; PRs only from `develop` | Protected (require review) |

## Deployment

| Environment | URL | Image |
|-------------|-----|-------|
| **QA** | `https://appsmith-qa.pocketfm.org` | `ghcr.io/pocket-fm/appsmith-ce:dev` |
| **Prod** | `https://appsmith-prod.pocketfm.org` | `ghcr.io/pocket-fm/appsmith-ce:latest` |

Infrastructure: EKS (arm64 Graviton nodes), Helm chart via ArgoCD, secrets from AWS Secrets Manager via External Secrets Operator.

---

## About AppSmith

Appsmith is an open-source low-code platform for building custom internal applications &mdash; dashboards, admin panels, customer 360, IT automation, and service management tools. Learn more on the [AppSmith website](https://www.appsmith.com).

[![Appsmith in 100 secs](/static/images/appsmith-introduction-video-tile.png)](https://www.youtube.com/watch?v=jhyDI0e1o08)

## Installation

| Method | Documentation |
|--------|--------------|
| [![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)](https://docs.appsmith.com/getting-started/setup/installation-guides/docker) | [Docker](https://docs.appsmith.com/getting-started/setup/installation-guides/docker) (*Recommended*) |
| [![Kubernetes](https://img.shields.io/badge/kubernetes-%23326ce5.svg?style=for-the-badge&logo=kubernetes&logoColor=white)](https://docs.appsmith.com/getting-started/setup/installation-guides/kubernetes) | [Kubernetes](https://docs.appsmith.com/getting-started/setup/installation-guides/kubernetes) |
| [![AWS](https://img.shields.io/badge/AWS-%23FF9900.svg?style=for-the-badge&logo=amazon-aws&logoColor=white)](https://docs.appsmith.com/getting-started/setup/installation-guides/aws-ami) | [AWS AMI](https://docs.appsmith.com/getting-started/setup/installation-guides/aws-ami) |

For other options, see the [Installation Guides](https://docs.appsmith.com/getting-started/setup/installation-guides).

## Development

To build and run locally, see [Setup for local development](https://github.com/appsmithorg/appsmith/blob/master/contributions/CodeContributionsGuidelines.md#-setup-for-local-development).

## Learning Resources

- [Documentation](https://docs.appsmith.com)
- [Tutorials](https://docs.appsmith.com/getting-started/tutorials)
- [Videos](https://www.youtube.com/appsmith)
- [Templates](https://www.appsmith.com/templates)

---

## Built On

This project is built on [AppSmith Community Edition](https://github.com/appsmithorg/appsmith), licensed under the [Apache License 2.0](https://github.com/appsmithorg/appsmith/blob/release/LICENSE). Huge thanks to the AppSmith team and their contributors for building the foundation.
