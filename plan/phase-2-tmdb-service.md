# Phase 2 : Service TMDB (posters, cast, details)

## Rappel des appels API valides (testes)

```
# Etape 1 : IMDb ID -> TMDB ID + poster + synopsis
GET https://api.themoviedb.org/3/find/{imdb_id}?external_source=imdb_id&api_key=XXX
Reponse : movie_results[] ou tv_results[] avec { id, poster_path, overview, vote_average }

# Etape 2 : Details complets
GET https://api.themoviedb.org/3/movie/{tmdb_id}?append_to_response=credits,release_dates&api_key=XXX
GET https://api.themoviedb.org/3/tv/{tmdb_id}?append_to_response=credits,content_ratings&api_key=XXX
```

Rate limit TMDB : ~40 requetes / 10 secondes.

---

## 2.1 Configuration TMDB

### Fichier : `src/main/java/com/ask2watch/config/TmdbConfig.java`

- Annoter `@Configuration`
- Lire `tmdb.api-key` et `tmdb.base-url` depuis application.yml via `@Value`
- Exposer un bean `WebClient` preconfigure avec la base URL TMDB
- Le WebClient ajoute automatiquement `api_key` en query param sur chaque requete

---

## 2.2 DTOs de reponse TMDB

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbFindResponse.java`

- Champs : `List<TmdbMovieResult> movieResults`, `List<TmdbTvResult> tvResults`
- Annotation `@JsonProperty("movie_results")` etc.

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbMovieResult.java`

- Champs : `int id`, `String title`, `String posterPath`, `String overview`, `double voteAverage`, `String releaseDate`
- Annotations `@JsonProperty("poster_path")` etc.

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbTvResult.java`

- Champs : `int id`, `String name`, `String posterPath`, `String overview`, `double voteAverage`, `String firstAirDate`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbMovieDetails.java`

- Champs : `int id`, `int runtime`, `TmdbCredits credits`, `TmdbReleaseDates releaseDates`
- Annotations `@JsonProperty("release_dates")` etc.

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbTvDetails.java`

- Champs : `int id`, `int numberOfSeasons`, `List<TmdbCreator> createdBy`, `TmdbCredits credits`, `TmdbContentRatings contentRatings`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbCredits.java`

- Champs : `List<TmdbCastMember> cast`, `List<TmdbCrewMember> crew`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbCastMember.java`

- Champs : `String name`, `int order`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbCrewMember.java`

- Champs : `String name`, `String job`
- Methode utile : filtrer par `job.equals("Director")`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbCreator.java`

- Champs : `String name`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbReleaseDates.java`

- Champs : `List<TmdbCountryRelease> results`
- Chaque `TmdbCountryRelease` : `String iso31661`, `List<TmdbReleaseInfo> releaseDates`
- Chaque `TmdbReleaseInfo` : `String certification`

### Fichier : `src/main/java/com/ask2watch/dto/tmdb/TmdbContentRatings.java`

- Champs : `List<TmdbCountryRating> results`
- Chaque `TmdbCountryRating` : `String iso31661`, `String rating`

---

## 2.3 Service TMDB

### Fichier : `src/main/java/com/ask2watch/service/TmdbService.java`

**Methode 1 : `findByImdbId(String imdbId)`**
- Appel : `GET /find/{imdbId}?external_source=imdb_id`
- Retourne `TmdbFindResponse`
- Le type CSV determine si on lit `movie_results` ou `tv_results`

**Methode 2 : `getMovieDetails(int tmdbId)`**
- Appel : `GET /movie/{tmdbId}?append_to_response=credits,release_dates`
- Retourne `TmdbMovieDetails`

**Methode 3 : `getTvDetails(int tmdbId)`**
- Appel : `GET /tv/{tmdbId}?append_to_response=credits,content_ratings`
- Retourne `TmdbTvDetails`

**Methode 4 : `enrichMedia(Media media)`**
- Orchestre les appels : findByImdbId -> getDetails
- Remplit les champs manquants sur l'entite Media :
  - `tmdbId` <- find response id
  - `posterPath` <- find response poster_path
  - `synopsis` <- find response overview
  - `stars` <- credits.cast (top 5, separes par virgule)
  - `directors` <- credits.crew filtre job=Director (films) ou createdBy (series)
  - `rated` <- release_dates US certification (films) ou content_ratings US (series)
  - `seasons` <- numberOfSeasons (series uniquement)
- Retourne le Media enrichi

**Methode 5 : `refreshPosterPath(Media media)`**
- Appel : `GET /movie/{tmdbId}` ou `GET /tv/{tmdbId}`
- Met a jour uniquement `posterPath`
- Utilise le `tmdbId` stocke (pas besoin de repasser par `/find`)

**Gestion du rate limit :**
- Ajouter un delai de 250ms entre chaque appel (`Thread.sleep` ou `delayElements` en reactive)
- Logger les erreurs 429 (Too Many Requests) et retenter apres 1 seconde

---

## 2.4 Tests Phase 2

### Fichier : `src/test/java/com/ask2watch/service/TmdbServiceTest.java`

- Annoter avec `@SpringBootTest` ou utiliser des mocks WebClient
- **Test 1 :** `findByImdbId("tt0111161")` retourne un resultat avec title "The Shawshank Redemption"
- **Test 2 :** `findByImdbId("tt0903747")` retourne un tv_result pour "Breaking Bad"
- **Test 3 :** `getMovieDetails(278)` retourne credits avec "Tim Robbins" dans le cast
- **Test 4 :** `enrichMedia(shawshankMedia)` remplit posterPath, synopsis, stars, directors, rated
- **Test 5 :** `findByImdbId("tt_invalid")` retourne des listes vides sans exception

### Fichier : `src/test/java/com/ask2watch/service/TmdbServiceIntegrationTest.java`

- Test d'integration reel (appel TMDB) marque `@Tag("integration")`
- Verifie qu'un appel reel retourne un poster_path valide
- Verifie que l'URL construite `https://image.tmdb.org/t/p/w500{posterPath}` retourne HTTP 200

### Execution :

```bash
# Tests unitaires seuls
mvn test -Dtest="TmdbServiceTest"

# Tests integration (appel reel TMDB)
mvn test -Dtest="TmdbServiceIntegrationTest" -Dgroups="integration"
```

---

## 2.5 Validation Phase 2

- [ ] `findByImdbId` retourne le bon tmdb_id pour un film connu
- [ ] `findByImdbId` retourne le bon tmdb_id pour une serie connue
- [ ] `getMovieDetails` retourne cast + director + certification
- [ ] `getTvDetails` retourne cast + creator + seasons + content_rating
- [ ] `enrichMedia` remplit tous les champs manquants sur une entite Media
- [ ] Le poster URL construit fonctionne (HTTP 200)
- [ ] Tous les tests passent

---

## 2.6 Nettoyage

- Aucun fichier a supprimer dans cette phase
- S'assurer que la cle API TMDB n'est PAS en dur dans le code (utiliser `${TMDB_API_KEY}`)
- Ajouter `TMDB_API_KEY` dans `.env.example` (sans la vraie valeur)
