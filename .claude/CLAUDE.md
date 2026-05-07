# PocketFM AppSmith CE Fork

This is PocketFM's fork of AppSmith Community Edition with enterprise features unlocked and custom access controls.

---

## Git Remotes

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` | `github.com/appsmithorg/appsmith` | Upstream AppSmith (read-only, for syncing) |
| `fork` | `github.com/Pocket-Fm/appsmith` | PocketFM org fork — **source of truth** |
| `personal` | `github.com/akash-pocketfm/appsmith` | Personal fork — backup, also builds images |

### Branches

| Branch | Purpose |
|--------|---------|
| `pocketfm-main` | **Main working branch** — all code changes go here directly |
| `release` | PocketFM's protected branch — do NOT push directly (branch protection rules) |

> **IMPORTANT for Claude:** Always work on and commit to `pocketfm-main`. Do NOT use feature branches. Push directly to `pocketfm-main` on both `fork` and `personal` remotes after every change.

### CI / Docker Images

The build workflow (`build-pocketfm-image.yml`) triggers on **push to `pocketfm-main`**. The `release` branch is protected and stays untouched.

Both repos build independently:

| Repo | Docker Image | Status |
|------|-------------|--------|
| `Pocket-Fm/appsmith` | `ghcr.io/pocket-fm/appsmith-ce:latest` | Org image (for QA/prod) — owner lowercased in workflow |
| `akash-pocketfm/appsmith` | `ghcr.io/akash-pocketfm/appsmith-ce:latest` | Personal backup (public) |

**Workflow to push changes and trigger builds:**
```bash
# 1. Make sure you're on pocketfm-main
git checkout pocketfm-main

# 2. Commit changes
git add <files> && git commit -m "..."

# 3. Push to BOTH remotes — this triggers CI builds on both repos
git push fork pocketfm-main
git push personal pocketfm-main
```

`workflow_dispatch` is also available but only works on the repo where `pocketfm-main` is the default branch (currently `akash-pocketfm/appsmith`).

---

## Summary of All Changes (15 commits, 29 files)

### 1. Unlock EE Feature Flags in CE

**File:** `app/client/src/ce/entities/FeatureFlag.ts`
- Force `license_gac_enabled`, `license_oidc_enabled`, `license_saml_enabled` to `true` in CE
- These flags gate Granular Access Control, OIDC, and SAML features in the UI

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/services/ce/FeatureFlagServiceCEImpl.java`
- Server-side: force the same 3 feature flags to `true` in CE

### 2. OIDC SSO Support (JumpCloud / Generic OIDC)

**File:** `app/server/appsmith-server/src/main/resources/application-ce.properties`
- Added Spring Security OAuth2 client registration for OIDC provider
- Properties: client-id, client-secret, scopes, redirect-uri, authorization-uri, token-uri, userinfo-uri, jwk-set-uri
- Client ID defaults to `missing_value_sentinel` (same pattern as Google/GitHub — prevents Spring from auto-configuring when unconfigured)
- All provider URIs default to `https://missing_value_sentinel` (non-empty) to prevent Spring's `ClientRegistration` validation from crashing at startup when OIDC is not configured
- `client-authentication-method` defaults to `client_secret_post` (JumpCloud requires POST body, Spring defaults to Basic header) — overridable via `APPSMITH_OAUTH2_OIDC_CLIENT_AUTH_METHOD`

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/services/ce/OrganizationServiceCEImpl.java`
- Added `System.getenv("APPSMITH_OAUTH2_OIDC_CLIENT_ID")` check to `getOrganizationConfiguration()`
- When set, adds `"oidc"` to `thirdPartyAuths` list — this controls which SSO buttons appear on login page

**File:** `app/client/src/ce/constants/SocialLogin.tsx`
- Added `OidcSocialLoginButtonProps` with URL `/oauth2/authorization/oidc`, `oidc.svg` logo, label "Sign in with OIDC"
- Added to `SocialLoginButtonPropsList` keyed as `"oidc"`

**File:** `app/client/src/ce/pages/AdminSettings/config/authentication.tsx`
- Added OIDC admin config form with fields: Client ID, Client Secret, Authorization URL, Token URL, User Info URL, JWK Set URL, Scopes, Username Attribute
- Added SAML admin config form with fields: Redirect URL (ACS), SP Entity ID, IdP Metadata URL, IdP SSO URL, X.509 Certificate

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/constants/EnvVariables.java`
- Added 8 OIDC env vars and 4 SAML env vars to the whitelist
- This enum gates which env vars the admin UI can save — without these entries, saves fail with "Bad request"

### 3. OAuth2 Access Token Injection for Datasource API Calls

**File:** `app/server/appsmith-interfaces/src/main/java/com/appsmith/external/dtos/ExecuteActionDTO.java`
- Added `oAuth2AccessToken` field to carry the user's OAuth2 token through action execution

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/solutions/ce/ActionExecutionSolutionCEImpl.java`
- Extracts OAuth2 access token from Spring Security session (`OAuth2AuthorizedClient`)
- Sets it on `ExecuteActionDTO` before action execution

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/helpers/ce/ActionExecutionSolutionHelperCEImpl.java`
- Substitutes `{{APPSMITH_USER_OAUTH2_ACCESS_TOKEN}}` placeholder in API action URLs and headers with the actual OAuth2 token
- Allows datasources to use the logged-in user's OAuth2 token for API calls

### 4. Super Admin-Only Restrictions

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/helpers/ce/WorkspaceServiceHelperCEImpl.java`
- Workspace creation restricted to super admins via `UserUtilsCE.isCurrentUserSuperUser()`

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/datasources/base/DatasourceServiceCEImpl.java`
- Datasource creation and editing restricted to super admins

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/solutions/ce/UserAndAccessManagementServiceCEImpl.java`
- User invitation restricted to super admins (wrapper around `doInviteUsers()`)

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/services/ce/UserWorkspaceServiceCEImpl.java`
- Member role change/removal restricted to super admins (wrapper around `doUpdatePermissionGroupForMember()`)

**Pattern used:** All restrictions follow the same pattern:
```java
return userUtils.isCurrentUserSuperUser()
    .flatMap(isSuperUser -> {
        if (Boolean.TRUE.equals(isSuperUser)) {
            return doActualWork(...);
        }
        return Mono.error(new AppsmithException(AppsmithError.UNAUTHORIZED_ACCESS));
    });
```

**Dependency injection chain** (each file needed `UserUtilsCE` added to constructor):
- `*CEImpl.java` → `*CECompatibleImpl.java` → `*Impl.java` (the Spring `@Component`)

### 5. EE MongoDB Data Compatibility

**Problem:** MongoDB was initially populated by the EE image. CE code crashes when deserializing EE-only enum values.

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/acl/AclPermission.java`
- Added 15 EE-only enum constant stubs (non-functional, prevent deserialization crashes):
  `CREATE_PERMISSION_GROUPS`, `CREATE_USER_GROUPS`, `CREATE_WORKSPACES`, `ORGANIZATION_MANAGE_ALL_USERS`, `READ_ORGANIZATION_AUDIT_LOGS`, `WORKSPACE_CREATE_ENVIRONMENT`, `WORKSPACE_CREATE_PACKAGE`, `WORKSPACE_CREATE_WORKFLOW`, `WORKSPACE_DELETE_ENVIRONMENTS`, `WORKSPACE_DELETE_PACKAGES`, `WORKSPACE_EXPORT_PACKAGES`, `WORKSPACE_EXPORT_WORKFLOWS`, `WORKSPACE_PUBLISH_PACKAGES`, `WORKSPACE_READ_ENVIRONMENTS`, `WORKSPACE_READ_PACKAGES`

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/converters/StringToAclPermissionConverter.java` (NEW)
- Fault-tolerant `@ReadingConverter` — returns `null` instead of throwing for unknown `AclPermission` values
- Belt-and-suspenders approach: even if new EE permissions appear in MongoDB, they won't crash CE

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/configurations/MongoConfig.java`
- Registered `StringToAclPermissionConverter` in `mongoCustomConversions()`

### 6. Plugin Error Handling

**File:** `app/server/appsmith-server/src/main/java/com/appsmith/server/plugins/base/PluginServiceCEImpl.java`
- Added null guards in 3 places where `pluginManager.getPlugin()` returns null for EE-only plugins:
  1. `loadTemplatesFromPlugin()` — returns `Collections.emptyMap()`
  2. `getConfigInputStream()` — returns null
  3. `loadPluginResourceGivenPluginAsMap()` — returns `Collections.emptyMap()`
- Changed `getFormConfig()` error handling from `onErrorMap(Exceptions::unwrap)` to `onErrorReturn(new HashMap<>())` for graceful degradation

**MongoDB cleanup also required:** Delete EE-only plugins from MongoDB:
```javascript
db.plugin.deleteMany({packageName: {$in: ["appsmith-agent-plugin"]}})
```

### 7. Admin Settings UI Customizations

**File:** `app/client/src/ce/pages/AdminSettings/config/index.ts`
- Hidden EE-only upgrade pages: Branding, Audit Logs, Provisioning
- Re-enabled Access Control (UserListing) with informational page

**File:** `app/client/src/ce/pages/AdminSettings/config/userlisting.ts`
- Replaced `AccessControlUpgradePage` with `AccessControlInfoPage`
- Shows workspace role descriptions and guidance instead of EE upgrade prompt

### 8. Workspace ID / App ID Global Template Variables

**Purpose:** Workspace names are NOT unique in AppSmith. Use workspace ID and app ID to uniquely identify the calling context in datasource headers.

**Files:**
- `app/client/src/ce/entities/DataTree/types.ts` — added `workspaceId: string` and `appId: string` to `AppsmithEntity` interface
- `app/client/src/selectors/dataTreeSelectors.ts` — populated `workspaceId: currentWorkspace.id` and `appId: currentApplication?.id || ""`

**Usage in datasource headers:**
```
X-AppSmith-Workspace-ID: {{appsmith.workspaceId}}
X-AppSmith-App-ID: {{appsmith.appId}}
```

### 9. Open Workspace Membership Check API

**Purpose:** Allows backend services to verify if a user (identified by email from their OAuth2 token) is a member of the AppSmith workspace that made the API call.

**Endpoint:**
```
GET /api/v1/workspaces/{workspaceId}/is-member?email=user@example.com
```
- **No authentication required** (open endpoint — whitelisted in `SecurityConfig.java`)
- Returns `{ "data": true/false }`

**Curl:**
```bash
curl "https://appsmith.example.com/api/v1/workspaces/{workspaceId}/is-member?email=user@pocketfm.com"
# → {"responseMeta":{"status":200,"success":true},"data":true}
```

**Files:**
- `app/server/appsmith-server/src/main/java/com/appsmith/server/services/ce/UserWorkspaceServiceCE.java` — added `isMemberByEmail` to interface
- `app/server/appsmith-server/src/main/java/com/appsmith/server/services/ce/UserWorkspaceServiceCEImpl.java` — implemented: finds user by email → gets workspace permission groups (null ACL = bypass auth) → checks `assignedToUserIds`
- `app/server/appsmith-server/src/main/java/com/appsmith/server/controllers/ce/WorkspaceControllerCE.java` — added `GET /{workspaceId}/is-member` endpoint
- `app/server/appsmith-server/src/main/java/com/appsmith/server/configurations/SecurityConfig.java` — whitelisted `WORKSPACE_URL + "/*/is-member"` in `permitAll` matchers

**End-to-end flow:**
1. Configure datasource header: `X-AppSmith-Workspace-ID: {{appsmith.workspaceId}}`
2. Backend receives request with OAuth2 token + workspace ID header
3. Backend validates OAuth2 token → extracts `userEmail`
4. Backend calls: `GET /api/v1/workspaces/{workspaceId}/is-member?email={userEmail}`
5. If `true` → allow. Otherwise → return 403.

### 10. Auto-Add Super Admins to New Workspaces

**Purpose:** When any workspace is created, all super admin users are automatically added as Administrators. Ensures super admins always have visibility and control over every workspace.

**Files:**
- `app/server/appsmith-server/src/main/java/com/appsmith/server/services/ce/WorkspaceServiceCEImpl.java` — injected `UserUtilsCE`, modified `generatePermissionsForDefaultPermissionGroups()` to fetch instance admin permission group's `assignedToUserIds` (all super admin IDs) and merge them with the workspace creator's ID into the admin permission group
- `app/server/appsmith-server/src/main/java/com/appsmith/server/services/WorkspaceServiceImpl.java` — propagated `UserUtils` constructor param to `super()`

**How it works:**
1. During workspace creation, `generatePermissionsForDefaultPermissionGroups()` calls `userUtils.getInstanceAdminPermissionGroup()`
2. Extracts all super admin user IDs from the instance admin permission group's `assignedToUserIds`
3. Merges super admin IDs with the workspace creator's ID into a `HashSet`
4. Sets the merged set as `adminPermissionGroup.setAssignedToUserIds(allAdminIds)`
5. Evicts permission cache for all affected users (creator + all super admins)

**Edge cases handled:**
- Instance admin PG returns empty → `defaultIfEmpty(Set.of())`, creator still added
- `assignedToUserIds` is null → null check in `.map()`
- Creator is already a super admin → `HashSet` deduplicates

### 11. CI/CD Pipeline

**File:** `.github/workflows/build-pocketfm-image.yml` (NEW)
- Multi-stage GitHub Actions workflow: server-build (Maven) → client-build (Yarn) → rts-build → Docker package
- Builds multi-arch image (amd64 + arm64) via `docker buildx`
- Publishes to `ghcr.io/akash-pocketfm/appsmith-ce:latest`
- Triggered via `workflow_dispatch`

---

## Architecture Notes

### CE/EE Class Pattern
AppSmith uses a 3-tier inheritance pattern:
```
*CEImpl.java          ← CE logic lives here (modify this)
  ↓ extends
*CECompatibleImpl.java ← pass-through (update constructor only)
  ↓ extends
*Impl.java            ← Spring @Component (update constructor only)
```

### Key APIs and Patterns
- **Super user check:** `UserUtilsCE.isCurrentUserSuperUser()` returns `Mono<Boolean>` — the ONLY correct way
- **Feature flags:** `FeatureFlagServiceCEImpl` for server, `FeatureFlag.ts` for client
- **Env var whitelist:** `EnvVariables.java` enum — admin UI rejects saves for any var not in this enum
- **SSO button rendering:** `OrganizationServiceCEImpl.getOrganizationConfiguration()` → `thirdPartyAuths` list → `SocialLoginButtonPropsList` in client
- **OAuth2 token flow:** Spring Security session → `OAuth2AuthorizedClient` → `ExecuteActionDTO.oAuth2AccessToken` → placeholder substitution in action execution
- **Workspace/app identity in datasources:** `{{appsmith.workspaceId}}` and `{{appsmith.appId}}` — populated in `dataTreeSelectors.ts` from Redux store
- **Membership check:** `GET /api/v1/workspaces/{id}/is-member?email=...` — open endpoint, no auth required, returns `true/false`
- **Internal API key auth:** `ApiKeyAuthFilter` — `X-API-Key` header bypasses session auth for service-to-service calls. Env var: `APPSMITH_INTERNAL_API_KEY`. Filter registered before `AUTHENTICATION` order in `SecurityConfig.java`
- **Super admin auto-add to workspaces:** `WorkspaceServiceCEImpl.generatePermissionsForDefaultPermissionGroups()` merges all super admin IDs (from instance admin PG) into new workspace's admin permission group

### MongoDB
- EE data in CE MongoDB is handled by: enum stubs + fault-tolerant converter + plugin null guards
- Permission cache key: `permissionGroupsForUser:<email><orgId>` in Redis

---

## Deployments

### Local (kind cluster)
- See `/Users/akashgupta/personal/appsmith/.claude/CLAUDE.md` for full local deployment docs
- Image: `ghcr.io/akash-pocketfm/appsmith-ce:latest`
- URL: `http://localhost:30000`

### QA (EKS via ArgoCD)
- Infra repo: `Pocket-Fm/Infra_deployments_k8s` branch `feature/PLAT-2507-appsmith-qa`
- Values: `overlays/appsmith/qa/values.yaml`
- Image: `ghcr.io/akash-pocketfm/appsmith-ce:latest`
- URL: `https://appsmith-qa.pocketfm.org`
- Secrets: AWS Secrets Manager at `qa/platform/appsmith`
- OIDC config: Done via AppSmith admin UI (not env vars in secrets)

---

## Known Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| `No enum constant AclPermission.X` | EE data in CE MongoDB | Added 15 enum stubs + fault-tolerant converter |
| Plugin NPE on home page | EE plugins in MongoDB, no JAR in CE | Null guards in PluginServiceCEImpl + delete EE plugins from MongoDB |
| OIDC "Bad request" on save | Env vars not in whitelist | Added to EnvVariables.java enum |
| OIDC button missing on login | 3 missing pieces | Spring Security registration + thirdPartyAuths + SocialLogin button |
| CI can't run on Pocket-Fm fork | `release` branch is protected, workflow not on default branch | Created `pocketfm-main` branch as CI branch; push triggers build on both repos |
| GHCR push fails on Pocket-Fm fork | `github.repository_owner` returns `Pocket-Fm` (uppercase), Docker tags must be lowercase | Added step to lowercase owner: `echo ... \| tr '[:upper:]' '[:lower:]'` in workflow |
| `authorizationUri cannot be empty` on startup | OIDC provider URIs defaulted to `""` when env vars unset; `ReactiveJwtDecoderFactory` bean triggers eager validation | Changed defaults to `https://missing_value_sentinel` in `application-ce.properties` |
| OIDC login fails: `client_secret_post` vs `client_secret_basic` | JumpCloud requires credentials in POST body; Spring defaults to `Authorization: Basic` header | Added `client-authentication-method=client_secret_post` (default) in `application-ce.properties` |
| Google login JWT clock skew (`iat` invalid) | Docker/kind VM clock drifts behind Google after Mac sleep/wake | Added `ReactiveJwtDecoderFactory<ClientRegistration>` bean in `SecurityConfig.java` with 5-min tolerance |
| OIDC login intermittent `Connection reset` | Stale Netty pooled connections to JumpCloud timeout at AWS NAT/Cloudflare | Dedicated `OIDC_CONNECTION_PROVIDER` with `maxIdleTime=60s` in `WebClientUtils.java` + decoder caching (`ConcurrentHashMap` in `idTokenDecoderFactory`) |

### 12. Internal API Key Authentication Filter

**Purpose:** Allows IAM (and other internal services) to call Appsmith's existing REST APIs without browser session/cookie auth. Used for automated workspace member management (invite, role change, removal) as part of IAM's RBAC/authz system.

**Endpoint scope:** ALL existing `/api/v1/*` endpoints — no new endpoints created. The filter is an alternative authentication path alongside session/cookie auth.

**Files:**
- `app/server/appsmith-server/src/main/java/com/appsmith/server/filters/ApiKeyAuthFilter.java` (NEW) — `WebFilter` that checks `X-API-Key` header
- `app/server/appsmith-server/src/main/java/com/appsmith/server/configurations/SecurityConfig.java` — registered filter before `AUTHENTICATION` order, added `@Value` for `appsmith.internal.apikey`
- `app/server/appsmith-server/src/main/resources/application-ce.properties` — added `appsmith.internal.apikey=${APPSMITH_INTERNAL_API_KEY:}`

**How it works:**
1. Filter runs before Spring Security's authentication filters
2. If `X-API-Key` header is absent → no-op, passes through to normal session auth
3. If `X-API-Key` matches `APPSMITH_INTERNAL_API_KEY` env var → injects `INTERNAL_SERVICE` authentication into reactive security context
4. If key is present but wrong → passes through (no rejection, normal auth runs)
5. Empty API key config (default) = filter never activates

**Safety guarantees:**
- Additive only — no existing auth code modified
- JSON requests already CSRF-exempt — no CSRF changes needed
- Empty default = disabled until explicitly configured
- Invalid keys fall through to normal auth (no 403 from filter itself)

**Env var:** `APPSMITH_INTERNAL_API_KEY` — set in AWS Secrets Manager alongside other Appsmith secrets

**Usage from IAM:**
```bash
curl -H "X-API-Key: <key>" -H "Content-Type: application/json" \
  "https://appsmith-qa.pocketfm.org/api/v1/workspaces"
```

**Important limitation:** The filter authenticates as `INTERNAL_SERVICE` principal (not a real Appsmith user). Some endpoints that check `isSuperUser()` via `sessionUserService.getCurrentUser()` may not work because there's no real user in the session. The workspace management APIs (invite, role change, membership list) work because they check permissions via ACL, not super-admin status.
