# Checklist de réalisation — Tests d'intégration Controller

## Statut global : 0 / 65 tests implémentés

---

## Etape 1 — Infrastructure (prérequis obligatoires)

- [ ] **1.1** Ajouter dans `ask2watch-backend/pom.xml` :
  - `org.testcontainers:postgresql` (scope test)
  - `org.testcontainers:junit-jupiter` (scope test)

- [ ] **1.2** Créer `src/test/resources/application-test.yml`
  - URL Testcontainers `jdbc:tc:postgresql:15:///ask2watch_test`
  - `ddl-auto: create-drop`
  - JWT secret >= 32 chars
  - Clés TMDB + Anthropic fictives

- [ ] **1.3** Créer `src/test/java/com/ask2watch/AbstractIntegrationTest.java`
  - `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@ActiveProfiles("test")` + `@Testcontainers` + `@Transactional`
  - Container PostgreSQL statique (`@Container static`)
  - `@DynamicPropertySource` pour injecter l'URL Testcontainers
  - `@Autowired` : MockMvc, ObjectMapper, JwtService, tous les Repositories
  - `@MockBean AgentService`
  - Helpers : `createUser()`, `createMedia()`, `createWatched()`, `createPick()`, `authHeader()`

---

## Etape 2 — AuthControllerIT (11 tests)

**Fichier :** `src/test/java/com/ask2watch/controller/AuthControllerIT.java`

### POST /api/auth/register
- [ ] **2.1** `register_success` — 200, token JWT, username, user en DB, password haché
- [ ] **2.2** `register_fail_emailAlreadyUsed` — 500, pas de doublon en DB
- [ ] **2.3** `register_fail_usernameBlank` — 400, aucun user créé
- [ ] **2.4** `register_fail_emailInvalid` — 400
- [ ] **2.5** `register_fail_passwordTooShort` — 400 (< 6 chars)
- [ ] **2.6** `register_fail_missingBody` — 400

### POST /api/auth/login
- [ ] **2.7** `login_success` — 200, token valide, username, `jwtService.isTokenValid == true`
- [ ] **2.8** `login_fail_wrongPassword` — 500
- [ ] **2.9** `login_fail_emailNotFound` — 500
- [ ] **2.10** `login_fail_emailBlank` — 400
- [ ] **2.11** `securedEndpoint_returns401_whenNoAuth` — 401 sur GET /api/media/watched

**Progression :** 0 / 11

---

## Etape 3 — MediaControllerIT (27 tests)

**Fichier :** `src/test/java/com/ask2watch/controller/MediaControllerIT.java`

**Setup `@BeforeEach` :** user1, user2, movieMedia(Inception,tmdbId=27205), seriesMedia(Breaking Bad,tmdbId=1396), watched1(user1+movie,rating=4,"Excellent film"), watched2(user1+series)

### GET /api/media/watched
- [ ] **3.1** `getWatched_movies_success` — 200, 1 film, tous les champs corrects
- [ ] **3.2** `getWatched_series_success` — 200, 1 série
- [ ] **3.3** `getWatched_empty_whenUserHasNone` — 200, []
- [ ] **3.4** `getWatched_fail_noAuth` — 401
- [ ] **3.5** `getWatched_fail_invalidToken` — 401
- [ ] **3.6** `getWatched_fail_invalidType` — 400

### POST /api/media/watched
- [ ] **3.7** `addToWatched_newMedia_success` — 200, media créé en DB, watched créé
- [ ] **3.8** `addToWatched_existingMedia_success` — 200, pas de doublon Media en DB
- [ ] **3.9** `addToWatched_fail_tmdbIdNull` — 400, aucune entrée DB
- [ ] **3.10** `addToWatched_fail_mediaTypeNull` — 400
- [ ] **3.11** `addToWatched_fail_titleBlank` — 400
- [ ] **3.12** `addToWatched_fail_noAuth` — 401

### PUT /api/media/watched/{id}
- [ ] **3.13** `updateWatched_rating_success` — 200, rating=5 en DB, comment inchangé
- [ ] **3.14** `updateWatched_comment_success` — 200, comment mis à jour, rating inchangé
- [ ] **3.15** `updateWatched_ratingAndComment_success` — 200, les deux champs mis à jour
- [ ] **3.16** `updateWatched_fail_ratingAboveMax` — 400 **(BUG : @Max(5) devrait être @Max(10))**
- [ ] **3.17** `updateWatched_fail_ratingBelowMin` — 400 (rating=0)
- [ ] **3.18** `updateWatched_fail_wrongUser` — 500, DB inchangée
- [ ] **3.19** `updateWatched_fail_notFound` — 500
- [ ] **3.20** `updateWatched_fail_noAuth` — 401

### DELETE /api/media/watched/{id}
- [ ] **3.21** `removeFromWatched_success` — 204, entry supprimée, Media toujours en DB
- [ ] **3.22** `removeFromWatched_fail_wrongUser` — 500, entry toujours en DB
- [ ] **3.23** `removeFromWatched_fail_notFound` — 500
- [ ] **3.24** `removeFromWatched_fail_noAuth` — 401

### GET /api/media/{id}
- [ ] **3.25** `getMedia_success` — 200, tous les champs corrects
- [ ] **3.26** `getMedia_fail_notFound` — 500
- [ ] **3.27** `getMedia_fail_noAuth` — 401

**Progression :** 0 / 27

---

## Etape 4 — PickControllerIT (19 tests)

**Fichier :** `src/test/java/com/ask2watch/controller/PickControllerIT.java`

**Setup `@BeforeEach` :** user1, user2, media1(Inception), media2(Breaking Bad), media3(Matrix), pick1+pick2(semaine courante), pick3(semaine-1)

### GET /api/picks/current
- [ ] **4.1** `getCurrentPicks_success` — 200, 2 picks, weekDate=lundi, createdByAgent=false
- [ ] **4.2** `getCurrentPicks_isolation_otherUserSeesEmpty` — 200, []
- [ ] **4.3** `getCurrentPicks_fail_noAuth` — 401

### GET /api/picks
- [ ] **4.4** `getAllPicks_success` — 200, 3 picks
- [ ] **4.5** `getAllPicks_fail_noAuth` — 401

### GET /api/picks/history
- [ ] **4.6** `getPicksHistory_defaultLimit` — 200, 3 picks, triés desc par weekDate
- [ ] **4.7** `getPicksHistory_customLimit` — 200, 2 picks (limit=2)
- [ ] **4.8** `getPicksHistory_fail_noAuth` — 401

### POST /api/picks
- [ ] **4.9** `addPick_newMedia_success` — 200, media créé, pick créé, weekDate=lundi, reason absent de réponse **(BUG)**
- [ ] **4.10** `addPick_existingMedia_success` — 200, pas de doublon Media
- [ ] **4.11** `addPick_fail_tmdbIdNull` — 400
- [ ] **4.12** `addPick_fail_titleBlank` — 400
- [ ] **4.13** `addPick_fail_reasonBlank` — 400
- [ ] **4.14** `addPick_fail_mediaTypeNull` — 400
- [ ] **4.15** `addPick_fail_noAuth` — 401

### DELETE /api/picks/{id}
- [ ] **4.16** `removePick_success` — 204, entry supprimée, Media toujours en DB
- [ ] **4.17** `removePick_fail_wrongUser` — 500, entry toujours en DB
- [ ] **4.18** `removePick_fail_notFound` — 500
- [ ] **4.19** `removePick_fail_noAuth` — 401

**Progression :** 0 / 19

---

## Etape 5 — AgentControllerIT (8 tests)

**Fichier :** `src/test/java/com/ask2watch/controller/AgentControllerIT.java`
`AgentService` = `@MockBean`

### POST /api/agent/chat
- [ ] **5.1** `chat_success` — 200, réponse mockée, `verify(agentService).chat(userId, message)`
- [ ] **5.2** `chat_emptyMessage_success` — 200 (pas de @NotBlank sur ChatRequest.message)
- [ ] **5.3** `chat_nullMessage_success` — 200 (body `{}`)
- [ ] **5.4** `chat_isolation_userId` — verify appelé avec bon userId
- [ ] **5.5** `chat_fail_noAuth` — 401

### DELETE /api/agent/history
- [ ] **5.6** `clearHistory_success` — 204, `verify(agentService).clearHistory(userId)`
- [ ] **5.7** `clearHistory_isolation_userId` — verify avec userId correct (user2 != user1)
- [ ] **5.8** `clearHistory_fail_noAuth` — 401

**Progression :** 0 / 8

---

## Tableau de progression global

| Etape | Description | Tests | Fait |
|-------|-------------|-------|------|
| 1 | Infrastructure | — | [ ] |
| 2 | AuthControllerIT | 11 | 0 |
| 3 | MediaControllerIT | 27 | 0 |
| 4 | PickControllerIT | 19 | 0 |
| 5 | AgentControllerIT | 8 | 0 |
| **Total** | | **65** | **0** |

---

## Bugs à corriger (identifiés pendant la planification)

| # | Fichier | Bug | Correction recommandée |
|---|---------|-----|----------------------|
| B1 | `UpdateWatchedRequest.java` | `@Max(5)` sur `userRating` — MCP accepte 1-10 | Changer en `@Max(10)` |
| B2 | `PickResponse.java` + `PickOfWeek.java` | `reason` de `PickRequest` non persisté ni retourné | Ajouter `reason` à `PickOfWeek` + `PickResponse` |

---

## Commandes

```bash
# Tous les tests IT
./mvnw test -Dspring.profiles.active=test -Dtest="*IT"

# Un seul fichier
./mvnw test -Dspring.profiles.active=test -Dtest="AuthControllerIT"
./mvnw test -Dspring.profiles.active=test -Dtest="MediaControllerIT"
./mvnw test -Dspring.profiles.active=test -Dtest="PickControllerIT"
./mvnw test -Dspring.profiles.active=test -Dtest="AgentControllerIT"

# Rapport surefire
./mvnw test -Dspring.profiles.active=test surefire-report:report
```
