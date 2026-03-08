# Security Fixes Plan - ask2Watch

**Date**: 2026-03-08
**Based on**: security-audit.md (20 findings)
**Strategy**: GitHub Secrets + environment variables, no secrets in source code

---

## Phase 1 — Secret Management (SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-20)

Priority: CRITICAL — must be done first, everything else builds on this.

### Step 1.1: Clean application.yml

**File**: `ask2watch-backend/src/main/resources/application.yml`

Remove all hardcoded defaults. Use fail-fast `${VAR}` without fallbacks:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ask2watch
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

tmdb:
  api-key: ${TMDB_API_KEY}

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400000

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
```

### Step 1.2: Create local dev environment file

**File (new)**: `ask2watch-backend/.env.local` (gitignored)

Contains actual dev values. Each developer creates their own copy from the example.

**File (new)**: `ask2watch-backend/.env.example` (committed)

```
DB_USERNAME=ask2watch
DB_PASSWORD=<your-db-password>
TMDB_API_KEY=<your-tmdb-api-key>
JWT_SECRET=<generate-with: openssl rand -base64 32>
ANTHROPIC_API_KEY=<your-anthropic-api-key>
```

### Step 1.3: Add spring-dotenv dependency

Add `me.paulschwarz:spring-dotenv` to `pom.xml` so Spring Boot loads `.env.local` automatically for local dev. This avoids needing to export env vars manually.

### Step 1.4: Update .gitignore files

**Files**: `ask2watch-backend/.gitignore`, `ask2watch-mcp-tmdb/.gitignore`

Add:
```
.env
.env.local
.env.*.local
application-local.yml
```

### Step 1.5: Fix MCP server hardcoded credentials

**File**: `ask2watch-mcp-tmdb/index.js:25`

Replace hardcoded admin credentials with env vars:
```javascript
body: JSON.stringify({
  email: process.env.MCP_AUTH_EMAIL,
  password: process.env.MCP_AUTH_PASSWORD,
}),
```

Update `ask2watch-mcp-tmdb/.env.example`:
```
TMDB_API_KEY=<your-tmdb-api-key>
MCP_AUTH_EMAIL=<backend-service-account-email>
MCP_AUTH_PASSWORD=<backend-service-account-password>
```

Remove the real API key from the existing `.env` file.

### Step 1.6: Fix default admin password

**File**: `ask2watch-backend/src/main/java/com/ask2watch/service/CsvImportService.java:117`

Read admin password from env var instead of hardcoding `"admin"`:
```java
@Value("${app.default-admin-password:#{T(java.util.UUID).randomUUID().toString()}}")
private String defaultAdminPassword;
```

Log the generated password once at startup if auto-generated, so the developer can use it.

### Step 1.7: Generate strong JWT secret

Replace the predictable string with a cryptographically random key.
Document in `.env.example` how to generate one:
```
# Generate with: openssl rand -base64 32
JWT_SECRET=<your-256-bit-secret>
```

### Step 1.8: Configure GitHub Secrets

Set up the following secrets in the GitHub repository settings:

| Secret Name | Description |
|-------------|-------------|
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `TMDB_API_KEY` | TMDB API v3 key |
| `JWT_SECRET` | 256-bit random signing key |
| `ANTHROPIC_API_KEY` | Claude API key |
| `MCP_AUTH_EMAIL` | MCP service account email |
| `MCP_AUTH_PASSWORD` | MCP service account password |

These get injected as environment variables in GitHub Actions workflows.

### Step 1.9: Rotate exposed secrets

Since the current secrets have been committed to git history:
- Regenerate TMDB API key from themoviedb.org account
- Generate a new JWT secret
- Change database password
- Regenerate Anthropic API key if one was ever committed

---

## Phase 2 — Error Handling & Information Leakage (SEC-07, SEC-08)

Priority: HIGH

### Step 2.1: Create custom exception classes

**Files (new)**:
- `exception/ResourceNotFoundException.java` — for "not found" cases
- `exception/AuthenticationException.java` — for login failures
- `exception/DuplicateResourceException.java` — for "email already in use"

### Step 2.2: Update GlobalExceptionHandler

**File**: `config/GlobalExceptionHandler.java`

- Add handlers for custom exceptions with controlled messages
- Change `RuntimeException` handler to return generic `"An internal error occurred"` instead of `ex.getMessage()`
- Log the real error server-side at ERROR level

### Step 2.3: Fix AgentService error responses

**File**: `service/AgentService.java` (lines 138, 214)

Replace:
```java
return "Erreur: " + e.getMessage();
```
With:
```java
log.error("Claude API call failed", e);
return "Desolé, je ne peux pas repondre pour le moment.";
```

### Step 2.4: Replace RuntimeException throws

**Files**: `AuthService.java`, `MediaService.java`, `PickService.java`

Replace all `throw new RuntimeException(...)` with the appropriate custom exception.

---

## Phase 3 — Rate Limiting & Auth Hardening (SEC-06, SEC-15, SEC-16)

Priority: HIGH

### Step 3.1: Add rate limiting on auth endpoints

Add `bucket4j-spring-boot-starter` dependency.

Configure rate limits:
- `/api/auth/login` — 5 requests per minute per IP
- `/api/auth/register` — 3 requests per hour per IP

### Step 3.2: Strengthen password policy

**File**: `dto/auth/RegisterRequest.java`

Add pattern validation:
```java
@Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
         message = "must contain uppercase, lowercase, and digit")
private String password;
```

### Step 3.3: Add refresh token mechanism

- Reduce access token TTL to 15 minutes
- Add `/api/auth/refresh` endpoint
- Store refresh tokens in database with expiry
- Return refresh token in HttpOnly cookie

---

## Phase 4 — Input Validation & Injection Prevention (SEC-10)

Priority: MEDIUM

### Step 4.1: Validate ChatRequest

**File**: `dto/agent/ChatRequest.java`

Add validation:
```java
@NotBlank
@Size(max = 2000)
private String message;
```

**File**: `controller/AgentController.java`

Add `@Valid`:
```java
public ChatResponse chat(@Valid @RequestBody ChatRequest request, ...)
```

### Step 4.2: Sanitize user input before prompt injection

**File**: `service/AgentService.java`

- Strip or escape control characters from user messages
- Add a clear delimiter between system prompt and user input
- Consider adding a prompt injection detection layer

---

## Phase 5 — Transport & Infrastructure Security (SEC-09, SEC-11, SEC-12, SEC-17, SEC-18)

Priority: MEDIUM

### Step 5.1: Add security response headers

**File**: `config/SecurityConfig.java`

Add headers configuration:
```java
http.headers(headers -> headers
    .contentTypeOptions(Customizer.withDefaults())
    .frameOptions(frame -> frame.deny())
    .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000))
);
```

### Step 5.2: Make CORS origin configurable

**File**: `config/SecurityConfig.java`

Replace hardcoded localhost with env var:
```java
@Value("${app.cors.allowed-origins}")
private List<String> allowedOrigins;
```

### Step 5.3: Configure HTTPS for production

Add to `application-prod.yml`:
```yaml
server:
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
```

Or document TLS termination at reverse proxy level (Nginx/Caddy).

### Step 5.4: Migrate TMDB API to header-based auth

Replace query param `api_key` with Authorization header using TMDB API v4 Bearer token. This prevents key leakage in server logs and Referer headers.

---

## Phase 6 — Operational Security (SEC-13, SEC-14, SEC-19)

Priority: LOW

### Step 6.1: Document media endpoint access model

**File**: `controller/MediaController.java`

Either add ownership check to `GET /api/media/{id}` or document that media data is intentionally public to all authenticated users.

### Step 6.2: Add TTL eviction to conversation history

**File**: `service/AgentService.java`

Replace `ConcurrentHashMap` with Caffeine cache:
```java
Cache<Long, List<ChatMessage>> conversationHistory = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(1, TimeUnit.HOURS)
    .build();
```

### Step 6.3: Add audit logging

Create an audit log for:
- Login success/failure (with IP address)
- Registration
- Data deletion (watched, picks)
- Password changes

Use Spring AOP or a simple `@EventListener` pattern.

---

## Execution Order

| Phase | Scope | Findings Addressed | Status |
|-------|-------|--------------------|--------|
| 1 | Secret management | SEC-01 to SEC-05, SEC-20 | DONE |
| 2 | Error handling | SEC-07, SEC-08 | DONE |
| 3 | Auth hardening | SEC-06, SEC-15, SEC-16 | PARTIAL (SEC-15 done, SEC-06/SEC-16 pending) |
| 4 | Input validation | SEC-10 | DONE |
| 5 | Transport security | SEC-09, SEC-11, SEC-12, SEC-17, SEC-18 | PARTIAL (SEC-11/SEC-18 done, SEC-09/SEC-12/SEC-17 pending) |
| 6 | Operational | SEC-13, SEC-14, SEC-19 | PENDING |

---

## Post-Fix Checklist

- [x] All secrets removed from source code (application.yml, CsvImportService, MCP index.js)
- [x] GitHub Actions workflow created (`.github/workflows/ci.yml`) — injects secrets as env vars
- [ ] GitHub Secrets configured in repo settings (manual — user action required)
- [x] `.env.example` files created and committed (backend + MCP)
- [x] `.env` / `.env.local` added to `.gitignore` (backend + MCP)
- [ ] Exposed secrets rotated (manual — user action required: TMDB key, JWT secret, DB password)
- [x] Error responses return generic messages (GlobalExceptionHandler + AgentService)
- [ ] Rate limiting active on auth endpoints (Phase 3 — pending: requires bucket4j)
- [x] Security headers present in responses (X-Content-Type-Options, X-Frame-Options)
- [x] Chat input validated and length-limited (@Valid + @Size(max=2000))
- [ ] Audit logging operational (Phase 6 — pending)
- [x] Custom exceptions replace RuntimeException (ResourceNotFoundException, AuthenticationException, DuplicateResourceException)
- [x] Password policy strengthened (min 8 chars, uppercase + lowercase + digit)
- [x] CORS origins configurable via environment variable
- [x] spring-dotenv added for local dev environment loading
- [x] MCP .gitignore created (was missing entirely)
- [ ] Refresh token mechanism (Phase 3 — pending)
- [ ] HTTPS configuration (Phase 5 — pending: infrastructure decision)
- [ ] TMDB API v4 migration (Phase 5 — pending)
- [ ] Conversation history TTL eviction (Phase 6 — pending)
- [ ] Audit logging (Phase 6 — pending)
