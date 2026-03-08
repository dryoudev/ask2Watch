# Plan — Tests d'intégration Controller (API → Base de données)

## Contexte

Tests d'intégration **full stack** : chaque test démarre une vraie instance Spring Boot,
frappe les endpoints HTTP réels, traverse les services et repositories, et valide
l'état en base de données PostgreSQL (via Testcontainers).

Aucun mock de service ou repository. Seul `AgentService` est mocké (appel Anthropic externe).

---

## Architecture des tests

```
Test HTTP (MockMvc)
    → Spring Security (JWT réel)
        → Controller
            → Service
                → Repository
                    → PostgreSQL (Testcontainers)
```

**Annotation clé :** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers`

---

## Infrastructure à mettre en place

### A. pom.xml — dépendances à ajouter

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

La BOM Testcontainers est déjà gérée par Spring Boot 3.5.

### B. application-test.yml
`src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:15:///ask2watch_test
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  sql:
    init:
      mode: always

jwt:
  secret: test-secret-key-minimum-32-characters-long!!
  expiration: 3600000

anthropic:
  api-key: test-key-not-used
  model: claude-test

tmdb:
  api-key: test-tmdb-key-not-used
```

### C. AbstractIntegrationTest — classe de base
`src/test/java/com/ask2watch/AbstractIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Transactional  // rollback après chaque test
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected MediaRepository mediaRepository;
    @Autowired protected UserWatchedRepository userWatchedRepository;
    @Autowired protected PickOfWeekRepository pickOfWeekRepository;

    @MockBean
    protected AgentService agentService; // seul mock — appel externe Anthropic

    protected String authHeader(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    protected User createUser(String username, String email, String rawPassword) {
        return userRepository.save(User.builder()
            .username(username).email(email)
            .passwordHash(new BCryptPasswordEncoder().encode(rawPassword))
            .build());
    }

    protected Media createMedia(String title, MediaType type, Integer tmdbId) {
        return mediaRepository.save(Media.builder()
            .title(title).mediaType(type)
            .tmdbId(tmdbId).imdbId("tt" + tmdbId)
            .build());
    }

    protected UserWatched createWatched(User user, Media media, Integer rating, String comment) {
        return userWatchedRepository.save(UserWatched.builder()
            .user(user).media(media).userRating(rating).comment(comment)
            .build());
    }

    protected PickOfWeek createPick(User user, Media media, LocalDate weekDate) {
        return pickOfWeekRepository.save(PickOfWeek.builder()
            .user(user).media(media).weekDate(weekDate).createdByAgent(false)
            .build());
    }
}
```

---

## Couche 1 — AuthControllerIT

**Fichier :** `src/test/java/com/ask2watch/controller/AuthControllerIT.java`
Endpoints publics — aucun JWT requis.

---

### POST /api/auth/register

#### TEST 1.1 — register_success
**Préconditions :** Aucun utilisateur en base.
**Request :**
```json
POST /api/auth/register
{"username": "testuser", "email": "test@example.com", "password": "password123"}
```
**Assertions :**
- Status `200`
- `$.token` non-null, contient 2 points (format JWT)
- `$.username == "testuser"`
- DB : `userRepository.findByEmail("test@example.com")` présent
- DB : `passwordHash != "password123"` (BCrypt)

#### TEST 1.2 — register_fail_emailAlreadyUsed
**Préconditions :** Utilisateur `test@example.com` déjà en base.
**Request :** même email
**Assertions :** Status `500`, DB : 1 seul utilisateur avec cet email

#### TEST 1.3 — register_fail_usernameBlank
**Request :** `{"username": "", "email": "test@example.com", "password": "password123"}`
**Assertions :** Status `400`, aucun user créé

#### TEST 1.4 — register_fail_emailInvalid
**Request :** `{"username": "u", "email": "notanemail", "password": "password123"}`
**Assertions :** Status `400`

#### TEST 1.5 — register_fail_passwordTooShort
**Request :** `{"username": "u", "email": "test@example.com", "password": "abc"}`
**Assertions :** Status `400` (`@Size(min=6)`)

#### TEST 1.6 — register_fail_missingBody
**Request :** POST sans body
**Assertions :** Status `400`

---

### POST /api/auth/login

#### TEST 1.7 — login_success
**Préconditions :** User `test@example.com` / `password123` en base.
**Request :** `{"email": "test@example.com", "password": "password123"}`
**Assertions :**
- Status `200`
- `$.token` non-null, JWT valide
- `$.username == "testuser"`
- `jwtService.extractEmail(token) == "test@example.com"`
- `jwtService.isTokenValid(token) == true`

#### TEST 1.8 — login_fail_wrongPassword
**Request :** bon email, mauvais mot de passe
**Assertions :** Status `500`

#### TEST 1.9 — login_fail_emailNotFound
**Request :** `{"email": "unknown@example.com", "password": "pass123"}`
**Assertions :** Status `500`

#### TEST 1.10 — login_fail_emailBlank
**Request :** `{"email": "", "password": "pass123"}`
**Assertions :** Status `400`

#### TEST 1.11 — securedEndpoint_returns401_whenNoAuth
**Request :** `GET /api/media/watched?type=MOVIE` sans token
**Assertions :** Status `401`

---

## Couche 2 — MediaControllerIT

**Fichier :** `src/test/java/com/ask2watch/controller/MediaControllerIT.java`

**Setup `@BeforeEach` :**
```
user1 = createUser("alice", "alice@test.com", "pass123")
user2 = createUser("bob", "bob@test.com", "pass123")
movieMedia = createMedia("Inception", MOVIE, 27205)
seriesMedia = createMedia("Breaking Bad", SERIES, 1396)
watched1 = createWatched(user1, movieMedia, 4, "Excellent film")
watched2 = createWatched(user1, seriesMedia, null, null)
```

---

### GET /api/media/watched?type=

#### TEST 2.1 — getWatched_movies_success
**Request :** `GET /api/media/watched?type=MOVIE` + `Authorization: Bearer {token_user1}`
**Assertions :**
- Status `200`, array de taille 1
- `$[0].watchedId == watched1.id`
- `$[0].media.title == "Inception"`, `$[0].media.mediaType == "MOVIE"`
- `$[0].userRating == 4`, `$[0].comment == "Excellent film"`

#### TEST 2.2 — getWatched_series_success
**Request :** `GET /api/media/watched?type=SERIES` + token_user1
**Assertions :** Status `200`, array taille 1, `$[0].media.title == "Breaking Bad"`

#### TEST 2.3 — getWatched_empty_whenUserHasNone
**Request :** `GET /api/media/watched?type=MOVIE` + token_user2
**Assertions :** Status `200`, body `[]`

#### TEST 2.4 — getWatched_fail_noAuth
**Assertions :** Status `401`

#### TEST 2.5 — getWatched_fail_invalidToken
**Request :** `Authorization: Bearer invalid.token.here`
**Assertions :** Status `401`

#### TEST 2.6 — getWatched_fail_invalidType
**Request :** `?type=INVALID` + token_user1
**Assertions :** Status `400`

---

### POST /api/media/watched

#### TEST 2.7 — addToWatched_newMedia_success
**Préconditions :** Aucun media avec `tmdbId=550` en base.
**Request :**
```json
{"tmdbId": 550, "mediaType": "MOVIE", "title": "Fight Club"}
```
**Assertions :**
- Status `200`
- `$.watchedId` non-null
- `$.media.title == "Fight Club"`, `$.media.tmdbId == 550`, `$.media.mediaType == "MOVIE"`
- DB : `mediaRepository.findByTmdbId(550)` présent
- DB : entrée `userWatched` pour user1 + newMedia présente

#### TEST 2.8 — addToWatched_existingMedia_success
**Préconditions :** `movieMedia` (tmdbId=27205) en base, user2 ne l'a pas regardé.
**Request :** `{"tmdbId": 27205, "mediaType": "MOVIE", "title": "Inception"}` + token_user2
**Assertions :**
- Status `200`
- DB : `mediaRepository.count()` inchangé (pas de doublon)
- `$.media.id == movieMedia.id`

#### TEST 2.9 — addToWatched_fail_tmdbIdNull
**Request :** `{"mediaType": "MOVIE", "title": "Fight Club"}`
**Assertions :** Status `400`, aucune entrée DB créée

#### TEST 2.10 — addToWatched_fail_mediaTypeNull
**Request :** `{"tmdbId": 550, "title": "Fight Club"}`
**Assertions :** Status `400`

#### TEST 2.11 — addToWatched_fail_titleBlank
**Request :** `{"tmdbId": 550, "mediaType": "MOVIE", "title": ""}`
**Assertions :** Status `400`

#### TEST 2.12 — addToWatched_fail_noAuth
**Assertions :** Status `401`

---

### PUT /api/media/watched/{id}

#### TEST 2.13 — updateWatched_rating_success
**Request :** `{"userRating": 5}` sur watched1
**Assertions :**
- Status `200`
- `$.userRating == 5`, `$.comment == "Excellent film"` (inchangé)
- DB : `userWatchedRepository.findById(watched1.id).userRating == 5`

#### TEST 2.14 — updateWatched_comment_success
**Request :** `{"comment": "Chef-d'oeuvre absolu"}` sur watched1
**Assertions :**
- Status `200`
- `$.comment == "Chef-d'oeuvre absolu"`, `$.userRating == 4` (inchangé)
- DB : commentaire mis à jour

#### TEST 2.15 — updateWatched_ratingAndComment_success
**Request :** `{"userRating": 3, "comment": "Revu, moins bien"}`
**Assertions :** Status `200`, les deux champs mis à jour en DB

#### TEST 2.16 — updateWatched_fail_ratingAboveMax
**Request :** `{"userRating": 10}`
**Assertions :** Status `400`
> **BUG DOCUMENTÉ :** `@Max(5)` sur `UpdateWatchedRequest.userRating` devrait être `@Max(10)` pour correspondre au MCP

#### TEST 2.17 — updateWatched_fail_ratingBelowMin
**Request :** `{"userRating": 0}`
**Assertions :** Status `400` (`@Min(1)`)

#### TEST 2.18 — updateWatched_fail_wrongUser
**Request :** `{"userRating": 1}` sur watched1 avec token_user2
**Assertions :** Status `500`, DB : `watched1.userRating` toujours `4`

#### TEST 2.19 — updateWatched_fail_notFound
**Request :** PUT sur id `99999`
**Assertions :** Status `500`

#### TEST 2.20 — updateWatched_fail_noAuth
**Assertions :** Status `401`

---

### DELETE /api/media/watched/{id}

#### TEST 2.21 — removeFromWatched_success
**Request :** `DELETE /api/media/watched/{watched1.id}` + token_user1
**Assertions :**
- Status `204`, body vide
- DB : `userWatchedRepository.findById(watched1.id)` → `Optional.empty()`
- DB : `movieMedia` toujours présent (le Media n'est pas supprimé)

#### TEST 2.22 — removeFromWatched_fail_wrongUser
**Request :** DELETE watched1 avec token_user2
**Assertions :** Status `500`, `watched1` toujours en DB

#### TEST 2.23 — removeFromWatched_fail_notFound
**Request :** DELETE id `99999`
**Assertions :** Status `500`

#### TEST 2.24 — removeFromWatched_fail_noAuth
**Assertions :** Status `401`

---

### GET /api/media/{id}

#### TEST 2.25 — getMedia_success
**Request :** `GET /api/media/{movieMedia.id}` + token_user1
**Assertions :**
- Status `200`
- `$.id == movieMedia.id`, `$.title == "Inception"`, `$.tmdbId == 27205`, `$.mediaType == "MOVIE"`

#### TEST 2.26 — getMedia_fail_notFound
**Request :** `GET /api/media/99999`
**Assertions :** Status `500`

#### TEST 2.27 — getMedia_fail_noAuth
**Request :** sans token
**Assertions :** Status `401`

---

## Couche 3 — PickControllerIT

**Fichier :** `src/test/java/com/ask2watch/controller/PickControllerIT.java`

**Setup `@BeforeEach` :**
```
user1 = createUser("alice", "alice@test.com", "pass123")
user2 = createUser("bob", "bob@test.com", "pass123")
media1 = createMedia("Inception", MOVIE, 27205)
media2 = createMedia("Breaking Bad", SERIES, 1396)
media3 = createMedia("The Matrix", MOVIE, 603)
currentMonday = LocalDate.now().with(DayOfWeek.MONDAY)
pick1 = createPick(user1, media1, currentMonday)
pick2 = createPick(user1, media2, currentMonday)
pick3 = createPick(user1, media3, currentMonday.minusWeeks(1))
```

---

### GET /api/picks/current

#### TEST 3.1 — getCurrentPicks_success
**Request :** + token_user1
**Assertions :**
- Status `200`, array taille 2
- Titres : "Inception" et "Breaking Bad" présents
- `$[*].weekDate` tous == `currentMonday.toString()`
- `$[*].createdByAgent` tous `false`

#### TEST 3.2 — getCurrentPicks_isolation_otherUserSeesEmpty
**Request :** + token_user2
**Assertions :** Status `200`, body `[]`

#### TEST 3.3 — getCurrentPicks_fail_noAuth
**Assertions :** Status `401`

---

### GET /api/picks

#### TEST 3.4 — getAllPicks_success
**Request :** + token_user1
**Assertions :** Status `200`, array taille 3

#### TEST 3.5 — getAllPicks_fail_noAuth
**Assertions :** Status `401`

---

### GET /api/picks/history

#### TEST 3.6 — getPicksHistory_defaultLimit
**Request :** `GET /api/picks/history` + token_user1
**Assertions :**
- Status `200`, array taille 3
- Trié par weekDate décroissant : `$[0].weekDate == currentMonday.toString()`
- `$[2].weekDate == currentMonday.minusWeeks(1).toString()`

#### TEST 3.7 — getPicksHistory_customLimit
**Request :** `GET /api/picks/history?limit=2`
**Assertions :** Status `200`, array taille 2

#### TEST 3.8 — getPicksHistory_fail_noAuth
**Assertions :** Status `401`

---

### POST /api/picks

#### TEST 3.9 — addPick_newMedia_success
**Préconditions :** Aucun media avec `tmdbId=680` en base.
**Request :**
```json
{"tmdbId": 680, "mediaType": "MOVIE", "title": "Pulp Fiction", "reason": "Chef-d'oeuvre de Tarantino"}
```
**Assertions :**
- Status `200`
- `$.pickId` non-null
- `$.media.title == "Pulp Fiction"`, `$.media.tmdbId == 680`
- `$.weekDate == currentMonday.toString()`, `$.createdByAgent == false`
- `reason` **absent** de la réponse (absent de `PickResponse`)
- DB : nouveau Media tmdbId=680 créé
- DB : nouveau PickOfWeek pour user1 créé

> **BUG DOCUMENTÉ :** `reason` de `PickRequest` non persisté ni retourné dans `PickResponse`

#### TEST 3.10 — addPick_existingMedia_success
**Request :** `{"tmdbId": 27205, "mediaType": "MOVIE", "title": "Inception", "reason": "Mon préféré"}`
**Assertions :** Status `200`, `mediaRepository.count()` inchangé (pas de doublon)

#### TEST 3.11 — addPick_fail_tmdbIdNull
**Request :** sans tmdbId
**Assertions :** Status `400`

#### TEST 3.12 — addPick_fail_titleBlank
**Request :** `"title": ""`
**Assertions :** Status `400`

#### TEST 3.13 — addPick_fail_reasonBlank
**Request :** `"reason": ""`
**Assertions :** Status `400`

#### TEST 3.14 — addPick_fail_mediaTypeNull
**Request :** sans mediaType
**Assertions :** Status `400`

#### TEST 3.15 — addPick_fail_noAuth
**Assertions :** Status `401`

---

### DELETE /api/picks/{id}

#### TEST 3.16 — removePick_success
**Request :** `DELETE /api/picks/{pick1.id}` + token_user1
**Assertions :**
- Status `204`, body vide
- DB : `pickOfWeekRepository.findById(pick1.id)` → `Optional.empty()`
- DB : `media1` toujours présent

#### TEST 3.17 — removePick_fail_wrongUser
**Request :** DELETE pick1 avec token_user2
**Assertions :** Status `500`, `pick1` toujours en DB

#### TEST 3.18 — removePick_fail_notFound
**Request :** DELETE id `99999`
**Assertions :** Status `500`

#### TEST 3.19 — removePick_fail_noAuth
**Assertions :** Status `401`

---

## Couche 4 — AgentControllerIT

**Fichier :** `src/test/java/com/ask2watch/controller/AgentControllerIT.java`
`AgentService` est `@MockBean` — appels Anthropic non réels.

**Setup `@BeforeEach` :**
```
user1 = createUser("alice", "alice@test.com", "pass123")
user2 = createUser("bob", "bob@test.com", "pass123")
```

---

### POST /api/agent/chat

#### TEST 4.1 — chat_success
**Préconditions mock :**
```java
when(agentService.chat(user1.getId(), "Recommande moi un film"))
    .thenReturn(ChatResponse.builder().message("Je recommande Inception").suggestedMedia(List.of()).build());
```
**Request :** `{"message": "Recommande moi un film"}` + token_user1
**Assertions :**
- Status `200`
- `$.message == "Je recommande Inception"`
- `verify(agentService).chat(user1.getId(), "Recommande moi un film")`

#### TEST 4.2 — chat_emptyMessage_success
**Préconditions mock :** `when(agentService.chat(any(), eq(""))).thenReturn(...)`
**Request :** `{"message": ""}` + token_user1
**Assertions :** Status `200` (pas de `@NotBlank` sur `ChatRequest.message`)

#### TEST 4.3 — chat_nullMessage_success
**Request :** `{}` + token_user1
**Assertions :** Status `200`

#### TEST 4.4 — chat_isolation_userId
**Request :** chat avec token_user2
**Assertions :** `verify(agentService).chat(user2.getId(), any())` — PAS `user1.getId()`

#### TEST 4.5 — chat_fail_noAuth
**Assertions :** Status `401`

---

### DELETE /api/agent/history

#### TEST 4.6 — clearHistory_success
**Request :** `DELETE /api/agent/history` + token_user1
**Assertions :**
- Status `204`
- `verify(agentService, times(1)).clearHistory(user1.getId())`

#### TEST 4.7 — clearHistory_isolation_userId
**Request :** DELETE avec token_user2
**Assertions :** `verify(agentService).clearHistory(user2.getId())` — PAS `user1.getId()`

#### TEST 4.8 — clearHistory_fail_noAuth
**Assertions :** Status `401`

---

## Résumé

| Contrôleur | Fichier IT | Tests |
|---|---|---|
| AuthController | `AuthControllerIT.java` | 11 |
| MediaController | `MediaControllerIT.java` | 27 |
| PickController | `PickControllerIT.java` | 19 |
| AgentController | `AgentControllerIT.java` | 8 |
| **Total** | **4 fichiers** | **65** |

---

## Commande d'exécution

```bash
cd ask2watch-backend
./mvnw test -Dspring.profiles.active=test -Dtest="*IT"
```

---

## Bugs identifiés

| # | Fichier | Bug | Correction |
|---|---------|-----|------------|
| B1 | `UpdateWatchedRequest.java` | `@Max(5)` sur `userRating` — MCP accepte 1-10 | Changer en `@Max(10)` |
| B2 | `PickResponse.java` | `reason` de `PickRequest` absent de la réponse | Ajouter `reason` à `PickOfWeek` + `PickResponse` |
