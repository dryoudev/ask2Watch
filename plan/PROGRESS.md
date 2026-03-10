# ask2Watch - Suivi de realisation

## Legende

- [ ] A faire
- [~] En cours
- [x] Termine

---

## Phase 1 : Backend Setup + Entites + DB

- [x] 1.1 Generer le projet Spring Boot (Spring Boot 3.5.0, Java 21+)
- [x] 1.2 Configuration application.yml
- [x] 1.3 docker-compose.yml (+ PostgreSQL local via Homebrew comme alternative)
- [x] 1.4 Schema SQL (schema.sql) - 4 tables creees
- [x] 1.5 Entites JPA (User, Media, MediaType, UserWatched, PickOfWeek)
- [x] 1.6 Repositories (UserRepository, MediaRepository, UserWatchedRepository, PickOfWeekRepository)
- [ ] 1.7 Tests repositories
- [x] 1.8 Validation : app demarre en 2s + 4 tables creees dans PostgreSQL
- [x] 1.9 Nettoyage (application.properties supprime)

---

## Phase 2 : Service TMDB

- [x] 2.1 TmdbConfig (WebClient bean)
- [x] 2.2 DTOs TMDB (TmdbFindResponse, TmdbMovieResult, TmdbTvResult, TmdbMovieDetails, TmdbTvDetails, TmdbCredits, TmdbCastMember, TmdbCrewMember)
- [x] 2.3 TmdbService (findByImdbId, getMovieDetails, getTvDetails, enrichMedia)
- [ ] 2.4 Tests TmdbService
- [x] 2.5 Validation : enrichMedia fonctionne, poster URL valide (verifie via import)
- [x] 2.6 Nettoyage

---

## Phase 3 : Import CSV

- [x] 3.1 Copier les CSV dans resources/data/
- [x] 3.2 CsvMediaRow DTO (opencsv binding)
- [x] 3.3 CsvImportService (parse + enrich + save + dedup + skip Video)
- [x] 3.4 DataInitializer (CommandLineRunner)
- [x] 3.5 Utilisateur par defaut (admin@ask2watch.com) + user_watched lie
- [ ] 3.6 Tests import
- [x] 3.7 Validation : 157 films + 42 series = 199 medias, 100% avec poster_path et synopsis, import en 96s
- [ ] 3.8 Nettoyage

---

## Phase 4 : REST API + JWT

- [x] 4.1 DTOs API (LoginRequest, RegisterRequest, AuthResponse, MediaResponse, WatchedMediaResponse, UpdateWatchedRequest, PickResponse)
- [x] 4.2 MediaMapper
- [x] 4.3 JwtService (generate, extract, validate)
- [x] 4.4 AuthService (login, register)
- [x] 4.5 JwtAuthFilter
- [x] 4.6 SecurityConfig + CorsConfig (CORS localhost:4200)
- [x] 4.7 Controllers (AuthController, MediaController, PickController)
- [x] 4.8 Services metier (MediaService, PickService)
- [ ] 4.9 Tests controllers + JWT
- [x] 4.10 Validation : login OK, JWT OK, watched?type=MOVIE retourne 157 films avec poster/cast/rated, 403 sans token
- [ ] 4.11 Nettoyage

---

## Phase 5 : Angular Setup

- [~] 5.1 Generer le projet Angular
- [ ] 5.2 Configuration Tailwind CSS + styles globaux
- [ ] 5.3 Modeles TypeScript
- [ ] 5.4 Utilitaires (tmdb.util, duration.util)
- [ ] 5.5 Services core (auth, media, pick)
- [ ] 5.6 Intercepteur + Guard
- [ ] 5.7 Routing + app.config
- [ ] 5.8 Proxy config
- [ ] 5.9 Tests services + guard
- [ ] 5.10 Validation : ng serve OK + proxy OK
- [ ] 5.11 Nettoyage

## Phase 6 : Angular UI

- [ ] 6.1 AppHeader
- [ ] 6.2 StarRating
- [ ] 6.3 MovieCard
- [ ] 6.4 PickCard
- [ ] 6.5 MovieRow
- [ ] 6.6 MediaDetailDialog
- [ ] 6.7 LoginComponent
- [ ] 6.8 HomeComponent
- [ ] 6.9 WatchedComponent
- [ ] 6.10 PicksComponent
- [ ] 6.11 DurationPipe
- [ ] 6.12 Tests composants
- [ ] 6.13 Validation : UI complete + responsive
- [ ] 6.14 Nettoyage

## Phase 7 : Agent Claude + MCP

- [ ] 7.1 MCP server TMDB (Node.js)
- [ ] 7.2 Dependances backend (Anthropic SDK)
- [ ] 7.3 AgentConfig
- [ ] 7.4 DTOs agent
- [ ] 7.5 AgentService
- [ ] 7.6 AgentController
- [ ] 7.7 Tests agent + MCP
- [ ] 7.8 Validation : agent repond + utilise tools
- [ ] 7.9 Nettoyage

## Phase 8 : Chat UI + Picks + Finalisation

- [ ] 8.1 AgentService Angular
- [ ] 8.2 ChatComponent
- [ ] 8.3 Lien Chat dans navigation
- [ ] 8.4 Connecter Picks au chat agent
- [ ] 8.5 Tests chat
- [ ] 8.6 Test E2E scenario complet
- [ ] 8.7 Validation finale
- [ ] 8.8 Nettoyage final (supprimer cine-picks-main, watchedList, etc.)

## Phase 9 : Enum SQL, tests, CSV import manuel + upload frontend

- [x] 9.1 media.media_type -> enum PostgreSQL + JPA mapping & persistence tests (MediaTypePersistenceIT)
- [~] 9.2 Stabiliser les tests backend (`MediaControllerIT` still expects different error codes/ratings)
- [x] 9.3 `UserCsvImportService`, endpoint `/api/media/import/csv`, CSV fixtures + integration tests (MediaCsvImportIT & UserCsvImportServiceIT)
- [x] 9.4 Frontend button + service + specs + cleanup (watched upload UI + tests)
- [x] 9.5 Documentation mise à jour (`plan/README.md`, `plan/PROGRESS.md`, phase 9 realisation)
