# Phase 4 : REST API + JWT + Securite

## 4.1 DTOs de l'API

### Fichier : `src/main/java/com/ask2watch/dto/auth/LoginRequest.java`

- `String email` (@NotBlank, @Email)
- `String password` (@NotBlank)

### Fichier : `src/main/java/com/ask2watch/dto/auth/RegisterRequest.java`

- `String username` (@NotBlank)
- `String email` (@NotBlank, @Email)
- `String password` (@NotBlank, @Size(min=6))

### Fichier : `src/main/java/com/ask2watch/dto/auth/AuthResponse.java`

- `String token`
- `String username`

### Fichier : `src/main/java/com/ask2watch/dto/media/MediaResponse.java`

DTO de reponse pour un media (film ou serie). Mapping depuis l'entite Media :

- `Long id`
- `String imdbId`
- `Integer tmdbId`
- `String title`
- `String mediaType` (MOVIE / SERIES)
- `String year`
- `Integer runtimeMins`
- `String genres`
- `Double imdbRating`
- `String directors`
- `String stars`
- `String synopsis`
- `String rated`
- `String posterPath`
- `Integer seasons`

### Fichier : `src/main/java/com/ask2watch/dto/media/WatchedMediaResponse.java`

Etend les infos media avec les donnees utilisateur :

- `Long watchedId`
- `MediaResponse media`
- `Integer userRating`
- `String dateWatched`
- `String comment`

### Fichier : `src/main/java/com/ask2watch/dto/media/UpdateWatchedRequest.java`

- `Integer userRating` (@Min(1), @Max(5))
- `String comment`

### Fichier : `src/main/java/com/ask2watch/dto/media/PickResponse.java`

- `Long pickId`
- `MediaResponse media`
- `String weekDate`
- `boolean createdByAgent`

---

## 4.2 Mappers DTO

### Fichier : `src/main/java/com/ask2watch/dto/mapper/MediaMapper.java`

Classe utilitaire avec methodes statiques :

- `toMediaResponse(Media entity)` -> `MediaResponse`
- `toWatchedMediaResponse(UserWatched entity)` -> `WatchedMediaResponse`
- `toPickResponse(PickOfWeek entity)` -> `PickResponse`

---

## 4.3 Service JWT

### Fichier : `src/main/java/com/ask2watch/service/JwtService.java`

- `String generateToken(User user)` : genere un JWT avec subject=email, claim username, expiration
- `String extractEmail(String token)` : extrait l'email du JWT
- `boolean isTokenValid(String token, UserDetails userDetails)` : verifie signature + expiration
- Cle secrete lue depuis `jwt.secret` dans application.yml
- Librairie : `io.jsonwebtoken` (jjwt)

---

## 4.4 Service Auth

### Fichier : `src/main/java/com/ask2watch/service/AuthService.java`

- `AuthResponse login(LoginRequest request)` : verifie credentials, retourne JWT
- `AuthResponse register(RegisterRequest request)` : cree l'utilisateur, retourne JWT
- Injecte `UserRepository`, `PasswordEncoder`, `JwtService`
- Hash le mot de passe avec BCrypt

---

## 4.5 Filtre JWT

### Fichier : `src/main/java/com/ask2watch/config/JwtAuthFilter.java`

- Etend `OncePerRequestFilter`
- Extrait le token du header `Authorization: Bearer {token}`
- Valide le token via `JwtService`
- Charge le `UserDetails` depuis la DB
- Set le `SecurityContextHolder`

---

## 4.6 Configuration Securite

### Fichier : `src/main/java/com/ask2watch/config/SecurityConfig.java`

- `@Configuration` + `@EnableWebSecurity`
- Bean `SecurityFilterChain` :
  - Desactive CSRF (API stateless)
  - Session policy : STATELESS
  - Endpoints publics : `/api/auth/**`
  - Tous les autres endpoints : authentifies
  - Ajoute `JwtAuthFilter` avant `UsernamePasswordAuthenticationFilter`
- Bean `PasswordEncoder` : BCryptPasswordEncoder
- Bean `AuthenticationManager`

### Fichier : `src/main/java/com/ask2watch/config/CorsConfig.java`

- Autorise `http://localhost:4200` (Angular dev server)
- Methodes : GET, POST, PUT, DELETE, OPTIONS
- Headers : Authorization, Content-Type

---

## 4.7 Controllers

### Fichier : `src/main/java/com/ask2watch/controller/AuthController.java`

- `@RestController` + `@RequestMapping("/api/auth")`
- `POST /api/auth/login` -> `AuthResponse`
- `POST /api/auth/register` -> `AuthResponse`

### Fichier : `src/main/java/com/ask2watch/controller/MediaController.java`

- `@RestController` + `@RequestMapping("/api/media")`
- `GET /api/media/watched?type=MOVIE` -> `List<WatchedMediaResponse>`
- `GET /api/media/watched?type=SERIES` -> `List<WatchedMediaResponse>`
- `POST /api/media/watched` -> ajouter un media a la liste (body : mediaId)
- `PUT /api/media/watched/{id}` -> mettre a jour rating/comment
- `GET /api/media/{id}` -> `MediaResponse` (details d'un media)

### Fichier : `src/main/java/com/ask2watch/controller/PickController.java`

- `@RestController` + `@RequestMapping("/api/picks")`
- `GET /api/picks/current` -> `List<PickResponse>` (picks de la semaine courante)
- `GET /api/picks` -> `List<PickResponse>` (tous les picks)

### Fichier : `src/main/java/com/ask2watch/controller/AdminController.java`

- `@RestController` + `@RequestMapping("/api/admin")`
- `POST /api/admin/refresh-posters` -> rafraichir tous les poster_path via TMDB
- `POST /api/admin/import-csv` -> relancer l'import CSV

---

## 4.8 Services Metier

### Fichier : `src/main/java/com/ask2watch/service/MediaService.java`

- `List<WatchedMediaResponse> getWatchedByType(Long userId, MediaType type)`
- `WatchedMediaResponse updateWatched(Long userId, Long watchedId, UpdateWatchedRequest req)`
- `MediaResponse getMediaById(Long id)`

### Fichier : `src/main/java/com/ask2watch/service/PickService.java`

- `List<PickResponse> getCurrentPicks(Long userId)`
- `List<PickResponse> getAllPicks(Long userId)`

---

## 4.9 Tests Phase 4

### Fichier : `src/test/java/com/ask2watch/controller/AuthControllerTest.java`

- `@WebMvcTest(AuthController.class)`
- **Test 1 :** POST /api/auth/register avec body valide -> 200 + token
- **Test 2 :** POST /api/auth/login avec bon credentials -> 200 + token
- **Test 3 :** POST /api/auth/login avec mauvais password -> 401
- **Test 4 :** POST /api/auth/register email deja pris -> 409

### Fichier : `src/test/java/com/ask2watch/controller/MediaControllerTest.java`

- `@WebMvcTest(MediaController.class)`
- **Test 1 :** GET /api/media/watched?type=MOVIE avec JWT valide -> 200 + liste
- **Test 2 :** GET /api/media/watched sans JWT -> 401
- **Test 3 :** PUT /api/media/watched/{id} met a jour le rating + comment

### Fichier : `src/test/java/com/ask2watch/service/JwtServiceTest.java`

- **Test 1 :** Generer un token et l'extraire -> email correct
- **Test 2 :** Token expire -> isTokenValid retourne false

### Execution :

```bash
mvn test -Dtest="*ControllerTest,JwtServiceTest"
```

---

## 4.10 Validation Phase 4

Tester avec curl ou Postman :

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@test.com","password":"123456"}'

# Login -> recuperer le token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456"}'

# Watched movies (avec token)
curl http://localhost:8080/api/media/watched?type=MOVIE \
  -H "Authorization: Bearer {TOKEN}"

# Update rating
curl -X PUT http://localhost:8080/api/media/watched/1 \
  -H "Authorization: Bearer {TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"userRating":5,"comment":"Chef d oeuvre!"}'
```

- [ ] Register retourne un token JWT valide
- [ ] Login retourne un token JWT valide
- [ ] Endpoint protege sans token -> 401
- [ ] GET watched retourne la liste des films avec poster_path
- [ ] PUT watched met a jour le rating/comment
- [ ] CORS fonctionne depuis localhost:4200

---

## 4.11 Nettoyage

- Supprimer les `System.out.println` de debug -> utiliser `@Slf4j` partout
- S'assurer que `jwt.secret` n'est PAS en dur (utiliser `${JWT_SECRET}`)
- Verifier qu'aucun endpoint ne retourne l'entite JPA directement (toujours passer par les DTOs)
