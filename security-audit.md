# Security Audit - ask2Watch Backend

**Date**: 2026-03-08
**Scope**: ask2watch-backend, ask2watch-mcp-tmdb
**Severity levels**: CRITICAL / HIGH / MEDIUM / LOW / INFO

---

## CRITICAL

### SEC-01: Hardcoded secrets in application.yml (committed to git)

**File**: `ask2watch-backend/src/main/resources/application.yml`

| Line | Secret | Value |
|------|--------|-------|
| 5 | Database password | `ask2watch` |
| 21 | TMDB API key (default fallback) | `2d6be03989ed15b3acafc07caa6b5260` |
| 26 | JWT signing secret (default fallback) | `ask2watch-dev-secret-key-minimum-32-characters-long` |

**Risk**: Anyone with access to the repository can extract all credentials. The JWT secret allows forging valid tokens for any user. The TMDB API key can be abused. Even though env vars are supported via `${VAR:default}`, the hardcoded defaults contain real production values.

**Recommendation**:
- Remove all hardcoded default values from application.yml
- Use `${VAR}` without defaults (fail-fast if not set)
- Create a `.env.example` with placeholder values for documentation
- Use Spring profiles: `application-dev.yml` (gitignored) for local dev, `application-prod.yml` for production
- Consider using a secrets manager (Vault, AWS Secrets Manager) for production

---

### SEC-02: Hardcoded credentials in MCP server

**File**: `ask2watch-mcp-tmdb/index.js:25`

```javascript
body: JSON.stringify({ email: "admin@ask2watch.com", password: "admin" }),
```

**Risk**: Admin credentials in plain text in source code. This is the same default admin user created by `CsvImportService`. Anyone reading the code can authenticate as admin.

**Recommendation**:
- Move credentials to environment variables (`MCP_AUTH_EMAIL`, `MCP_AUTH_PASSWORD`)
- Consider using a service account with a dedicated API key instead of user/password auth for machine-to-machine communication

---

### SEC-03: Hardcoded default admin password

**File**: `ask2watch-backend/src/main/java/com/ask2watch/service/CsvImportService.java:117`

```java
.passwordHash(passwordEncoder.encode("admin"))
```

**Risk**: The default admin user is created with the password `"admin"`. Combined with SEC-02, this is a well-known credential pair that can be exploited immediately.

**Recommendation**:
- Generate a random password at startup and log it once (like Spring Boot does)
- Or read the default admin password from an environment variable
- Force password change on first login

---

### SEC-04: TMDB API key in .env file committed to git

**File**: `ask2watch-mcp-tmdb/.env:1`

```
TMDB_API_KEY=2d6be03989ed15b3acafc07caa6b5260
```

**Risk**: The `.env` file is NOT in `.gitignore` for either the backend or the MCP server. If this repository is pushed to a remote, the API key is exposed.

**Recommendation**:
- Add `.env` to all `.gitignore` files
- Rotate the exposed TMDB API key immediately
- Audit git history for other leaked secrets (`git log -p -S "api_key"`)

---

## HIGH

### SEC-05: JWT secret is weak and predictable

**File**: `ask2watch-backend/src/main/resources/application.yml:26`

```yaml
secret: ${JWT_SECRET:ask2watch-dev-secret-key-minimum-32-characters-long}
```

**Risk**: The fallback JWT secret is a human-readable string. If the `JWT_SECRET` env var is not set (which is likely in dev/staging), tokens can be forged by anyone who reads this code.

**Recommendation**:
- Generate a cryptographically random 256-bit key (e.g., `openssl rand -base64 32`)
- Never use a default fallback for JWT secrets - fail at startup if not configured
- Consider using asymmetric keys (RS256) instead of symmetric (HS256)

---

### SEC-06: No rate limiting on authentication endpoints

**Files**: `AuthController.java`, `SecurityConfig.java`

The `/api/auth/login` and `/api/auth/register` endpoints are publicly accessible (`permitAll()`) with no rate limiting.

**Risk**: Brute-force attacks on login. Mass account creation via register endpoint.

**Recommendation**:
- Add rate limiting (e.g., Spring Boot + Bucket4j, or a reverse proxy like Nginx)
- Limit login attempts per IP/email (e.g., 5 attempts per minute)
- Limit registration per IP (e.g., 3 accounts per hour)
- Add account lockout after N failed attempts

---

### SEC-07: Error messages leak internal state

**File**: `ask2watch-backend/src/main/java/com/ask2watch/config/GlobalExceptionHandler.java:47`

```java
error.put("error", ex.getMessage());
```

The `RuntimeException` handler returns the raw exception message to the client. Multiple services throw `RuntimeException` with internal details:

| File | Message |
|------|---------|
| `PickService.java:52` | `"User not found"` |
| `PickService.java:89` | `"Pick not found"` |
| `MediaService.java:37` | `"Watched entry not found"` |
| `MediaService.java:53` | `"Media not found"` |
| `MediaService.java:58` | `"User not found"` |
| `AuthService.java:22,25` | `"Invalid credentials"` |

**Risk**: While some messages are benign, the catch-all `RuntimeException` handler will also expose unexpected internal errors (stack traces, SQL errors, class names) to the client.

**Recommendation**:
- Use custom exception classes (e.g., `ResourceNotFoundException`, `AuthenticationException`)
- Return generic messages to client, log detailed messages server-side
- Never expose `ex.getMessage()` from uncontrolled RuntimeExceptions

---

### SEC-08: Agent chat exposes internal error details to users

**File**: `ask2watch-backend/src/main/java/com/ask2watch/service/AgentService.java:138`

```java
return "Desolé, je ne peux pas repondre pour le moment. Erreur: " + e.getMessage();
```

Also line 214:
```java
return "Error executing tool: " + e.getMessage();
```

**Risk**: Raw exception messages from the Anthropic API or TMDB API are returned directly to the user. This could leak API keys, internal URLs, or stack trace details.

**Recommendation**:
- Return a generic error message to the user
- Log the full error server-side only

---

## MEDIUM

### SEC-09: TMDB API key passed as URL query parameter

**Files**: `TmdbService.java` (lines 28, 40, 52), `AgentService.java` (lines 220, 230, 237, 246)

```java
.queryParam("api_key", tmdbConfig.getApiKey())
```

**Risk**: API keys in query parameters are logged in web server access logs, proxy logs, browser history, and can leak via Referer headers. While this is TMDB's v3 API design, it's still a concern.

**Recommendation**:
- Migrate to TMDB API v4 which uses Bearer token authentication via headers
- If staying on v3, ensure HTTP access logs don't capture full query strings

---

### SEC-10: No input sanitization on agent chat messages

**File**: `ask2watch-backend/src/main/java/com/ask2watch/controller/AgentController.java:19`

```java
public ChatResponse chat(@RequestBody ChatRequest request, Authentication auth) {
    Long userId = (Long) auth.getPrincipal();
    return agentService.chat(userId, request.getMessage());
}
```

The user message is passed directly to the Claude API system prompt without sanitization. The `ChatRequest` is not validated with `@Valid`.

**Risk**: Prompt injection - a user could craft a message to manipulate the LLM's behavior, extract the system prompt, or bypass content restrictions.

**Recommendation**:
- Add `@Valid` annotation and input length validation on `ChatRequest`
- Sanitize or escape user input before including in the prompt
- Implement prompt injection detection
- Limit message length (prevent abuse of Claude API tokens)

---

### SEC-11: CORS allows only localhost but is overly permissive in methods

**File**: `ask2watch-backend/src/main/java/com/ask2watch/config/SecurityConfig.java:43-44`

```java
config.setAllowedOrigins(List.of("http://localhost:4200"));
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
```

**Risk**: While the origin is restricted to localhost (good for dev), this config will need to change for production. `DELETE` and `PUT` are allowed globally when they may not be needed on all endpoints.

**Recommendation**:
- Make CORS origin configurable via environment variable
- Restrict methods per endpoint if possible
- Add the production frontend URL when deploying

---

### SEC-12: No HTTPS enforcement

**File**: `application.yml:18`

```yaml
server:
  port: 8080
```

No TLS/SSL configuration. All traffic including JWT tokens and credentials is transmitted in plain text.

**Recommendation**:
- Configure HTTPS with TLS certificates for production
- Add HSTS headers
- Redirect HTTP to HTTPS
- Use `server.ssl.*` properties or terminate TLS at reverse proxy

---

### SEC-13: GET /api/media/{id} has no authorization check

**File**: `ask2watch-backend/src/main/java/com/ask2watch/controller/MediaController.java:59-62`

```java
@GetMapping("/{id}")
public ResponseEntity<MediaResponse> getMedia(@PathVariable Long id) {
    return ResponseEntity.ok(mediaService.getMediaById(id));
}
```

**Risk**: Any authenticated user can access any media entry by ID. While media itself is not sensitive (movie data), this is an IDOR pattern that could be problematic if the data model evolves.

**Recommendation**:
- Add ownership validation or clarify that media is shared/public data
- Document the intended access model

---

## LOW

### SEC-14: Conversation history stored in-memory without limits

**File**: `ask2watch-backend/src/main/java/com/ask2watch/service/AgentService.java:30,58`

```java
private final Map<Long, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();
```

History is trimmed to 20 messages but is never fully purged. New user IDs accumulate indefinitely.

**Risk**: Memory exhaustion (DoS) if many users create conversations. History is lost on restart.

**Recommendation**:
- Add a max number of active conversations
- Implement TTL-based eviction (e.g., Caffeine cache)
- Consider persisting to database for durability

---

### SEC-15: No password complexity requirements beyond minimum length

**File**: `ask2watch-backend/src/main/java/com/ask2watch/dto/auth/RegisterRequest.java:21`

```java
@Size(min = 6)
private String password;
```

**Risk**: Users can register with weak passwords like `"aaaaaa"` or `"123456"`.

**Recommendation**:
- Add password complexity rules (uppercase, lowercase, digit, special char)
- Check against common password lists
- Consider using a library like Passay

---

### SEC-16: JWT token has no refresh mechanism

**File**: `ask2watch-backend/src/main/java/com/ask2watch/service/JwtService.java:27-36`

JWT expiration is set to 24 hours (`86400000ms`). There is no refresh token mechanism.

**Risk**: Users must re-login every 24 hours. If a token is stolen, it's valid for a full day with no way to revoke it.

**Recommendation**:
- Implement refresh token rotation
- Reduce access token lifetime (15-30 minutes)
- Add token revocation (blacklist) capability
- Store refresh tokens in HttpOnly cookies

---

### SEC-17: CSRF protection disabled

**File**: `ask2watch-backend/src/main/java/com/ask2watch/config/SecurityConfig.java:30`

```java
.csrf(csrf -> csrf.disable())
```

**Risk**: Acceptable for a stateless JWT API, but only if the JWT is never stored in cookies. If the frontend ever moves to cookie-based auth, CSRF attacks become possible.

**Recommendation**:
- Document that CSRF is intentionally disabled for JWT-based stateless auth
- If cookies are ever used for tokens, re-enable CSRF protection

---

## INFO

### SEC-18: Spring Security debug headers not configured

No security headers are configured (X-Content-Type-Options, X-Frame-Options, Content-Security-Policy, X-XSS-Protection).

**Recommendation**: Add security headers via `SecurityConfig` or a reverse proxy.

### SEC-19: No request/response logging or audit trail

No audit logging for sensitive operations (login, register, delete watched, delete pick).

**Recommendation**: Add audit logging for authentication events and data mutations.

### SEC-20: Database credentials are the same as the database name

`username: ask2watch`, `password: ask2watch` for database `ask2watch` - trivially guessable.

**Recommendation**: Use strong, unique database credentials.

---

## Summary

| Severity | Count | Key Issues |
|----------|-------|------------|
| CRITICAL | 4 | Hardcoded secrets, default admin password, .env in git |
| HIGH | 4 | Weak JWT secret, no rate limiting, error message leaks |
| MEDIUM | 5 | API key in URL params, no input sanitization, no HTTPS, IDOR |
| LOW | 4 | In-memory storage, weak password policy, no refresh tokens |
| INFO | 3 | Missing headers, no audit trail, weak DB credentials |

**Priority remediation order**:
1. Remove all hardcoded secrets and rotate exposed keys (SEC-01, SEC-02, SEC-03, SEC-04)
2. Fix JWT secret generation (SEC-05)
3. Add rate limiting on auth endpoints (SEC-06)
4. Sanitize error responses (SEC-07, SEC-08)
5. Add input validation on chat endpoint (SEC-10)
6. Configure HTTPS and security headers (SEC-12, SEC-18)
