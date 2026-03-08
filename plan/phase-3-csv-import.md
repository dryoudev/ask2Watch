# Phase 3 : Import CSV + Enrichissement TMDB

## Donnees source

| Fichier | Contenu | Lignes |
|---|---|---|
| `watchedList/moviesWatched.csv` | 162 films | Colonnes utiles : Const, Title, Original Title, URL, IMDb Rating, Runtime, Year, Genres, Num Votes, Release Date, Directors |
| `watchedList/tvSeriesWatched.csv` | 43 series | Memes colonnes, Directors vide |

**Problemes identifies :**
- Colonne `Your Rating` vide partout (pas de note utilisateur)
- Colonne `Date Rated` vide partout
- Colonne `Description` vide partout
- 1 doublon : "Ready Player One" (lignes 148 et 153 dans movies)
- 1 entree type "Video" au lieu de "Movie" (ligne 31 - Untouchable 2011)
- Runtime manquant pour 2 entrees (Untouchable video, Family Business)

---

## 3.1 Copier les CSV dans le backend

### Action : Copier les fichiers CSV

**Source :** `watchedList/moviesWatched.csv` et `watchedList/tvSeriesWatched.csv`
**Destination :** `ask2watch-backend/src/main/resources/data/`

Ces fichiers sont lus au demarrage pour peupler la DB.

---

## 3.2 DTO de mapping CSV

### Fichier : `src/main/java/com/ask2watch/dto/csv/CsvMediaRow.java`

Classe POJO avec les champs mappes depuis le CSV :

- `String position`
- `String imdbId` (colonne "Const")
- `String title` (colonne "Title")
- `String originalTitle` (colonne "Original Title")
- `String url` (colonne "URL")
- `String titleType` (colonne "Title Type")
- `String imdbRating` (colonne "IMDb Rating")
- `String runtimeMins` (colonne "Runtime (mins)")
- `String year` (colonne "Year")
- `String genres` (colonne "Genres")
- `String numVotes` (colonne "Num Votes")
- `String releaseDate` (colonne "Release Date")
- `String directors` (colonne "Directors")

Annotations OpenCSV : `@CsvBindByName(column = "Const")` etc.

---

## 3.3 Service d'import CSV

### Fichier : `src/main/java/com/ask2watch/service/CsvImportService.java`

**Methode principale : `importAll()`**

1. Verifier si la table `media` est deja peuplee -> skip si oui (idempotent)
2. Appeler `importFile("data/moviesWatched.csv", MediaType.MOVIE)`
3. Appeler `importFile("data/tvSeriesWatched.csv", MediaType.SERIES)`
4. Logger le nombre total d'entrees importees

**Methode : `importFile(String resourcePath, MediaType type)`**

1. Lire le CSV avec OpenCSV -> `List<CsvMediaRow>`
2. Pour chaque ligne :
   a. Verifier que `imdbId` n'est pas deja en DB (skip les doublons)
   b. Ignorer les entrees avec `titleType = "Video"` (ne pas importer)
   c. Creer une entite `Media` avec les champs CSV :
      - `imdbId` <- Const
      - `title` <- Title
      - `originalTitle` <- Original Title
      - `mediaType` <- type passe en parametre (pas le CSV, car on separe les fichiers)
      - `year` <- Year
      - `runtimeMins` <- Runtime (mins) parse en Integer (null si vide)
      - `genres` <- Genres
      - `imdbRating` <- IMDb Rating parse en BigDecimal (null si vide)
      - `numVotes` <- Num Votes parse en Integer
      - `releaseDate` <- Release Date parse en LocalDate
      - `directors` <- Directors (sera vide pour les series, enrichi par TMDB)
      - `imdbUrl` <- URL
   d. Appeler `tmdbService.enrichMedia(media)` pour remplir :
      - tmdbId, posterPath, synopsis, stars, rated, seasons (series)
      - directors (ecrase si vide, sinon garde la valeur CSV)
   e. Sauvegarder en DB via `mediaRepository.save(media)`
   f. Pause 250ms entre chaque titre (rate limit TMDB)
3. Logger les erreurs sans bloquer l'import (log + continue)

---

## 3.4 Runner au demarrage

### Fichier : `src/main/java/com/ask2watch/config/DataInitializer.java`

- Implementer `CommandLineRunner`
- Injecter `CsvImportService`
- Appeler `csvImportService.importAll()` au demarrage
- Logger le temps total d'import
- Activer uniquement en profil `dev` avec `@Profile("dev")`

**Temps estime :** ~205 titres x 2 appels TMDB x 250ms = ~102 secondes au premier demarrage.

---

## 3.5 Creer un utilisateur par defaut

### Dans `DataInitializer.java` (meme fichier)

Apres l'import CSV, creer un utilisateur par defaut :
- email : `admin@ask2watch.com`
- username : `admin`
- password : hash BCrypt de "admin"

Puis associer tous les medias importes a cet utilisateur dans `user_watched` :
- `user_rating` : null (pas de note dans le CSV)
- `date_watched` : null (pas de date dans le CSV)
- `comment` : null

---

## 3.6 Tests Phase 3

### Fichier : `src/test/java/com/ask2watch/service/CsvImportServiceTest.java`

- **Test 1 :** Parsing d'une ligne CSV valide -> CsvMediaRow correctement rempli
- **Test 2 :** Doublon ignore (2e appel avec meme imdbId ne cree pas de doublon)
- **Test 3 :** Entree "Video" ignoree
- **Test 4 :** Runtime vide -> runtimeMins = null (pas d'exception)
- **Test 5 :** Import complet d'un petit CSV de test (3 lignes) -> 3 medias en DB

### Fichier : `src/test/resources/data/test-movies.csv`

Creer un CSV de test avec 3 lignes (header + 3 films connus) pour les tests unitaires.

### Execution :

```bash
mvn test -Dtest="CsvImportServiceTest"
```

---

## 3.7 Validation Phase 3

- [ ] Au premier demarrage avec profil `dev`, l'import se lance automatiquement
- [ ] 162 films + 43 series = ~204 medias en DB (moins les doublons et "Video")
- [ ] Chaque media a un `poster_path` non null
- [ ] Chaque media a un `synopsis` non null
- [ ] Les series ont un `seasons` renseigne
- [ ] L'utilisateur par defaut existe
- [ ] Les entrees `user_watched` lient l'utilisateur aux medias
- [ ] Au 2e demarrage, l'import est skipe (pas de doublons)
- [ ] Verifier dans psql : `SELECT count(*) FROM media WHERE poster_path IS NOT NULL;`

---

## 3.8 Nettoyage

- Supprimer les CSV de `watchedList/` une fois copies dans `src/main/resources/data/`
  (ou les garder comme archive et ajouter dans `.gitignore` les copies backend)
- Supprimer les entrees de log verbose de l'import (garder seulement le resume)
