# ask2Watch - Plan d'implementation

## Vue d'ensemble

Application web de recommandation de films/series pour les soirees cine hebdomadaires.
Un agent Claude analyse la liste des films vus + commentaires et suggere des picks de la semaine.

## Stack technique

| Couche | Technologie |
|---|---|
| Frontend | Angular 19 (standalone components) + Tailwind CSS |
| Backend | Spring Boot 3.x + Java 21+ |
| Base de donnees | PostgreSQL 14 |
| API externe | TMDB API (posters, cast, synopsis) |
| IA | Anthropic Claude SDK + MCP (TMDB tools) |

## Structure du plan

Le plan est decoupe en 8 phases, chacune dans un fichier separe :

| Phase | Fichier | Description |
|---|---|---|
| 1 | [phase-1-backend-setup.md](phase-1-backend-setup.md) | Projet Spring Boot, entites JPA, schema DB |
| 2 | [phase-2-tmdb-service.md](phase-2-tmdb-service.md) | Service TMDB (posters, cast, details) |
| 3 | [phase-3-csv-import.md](phase-3-csv-import.md) | Import CSV + enrichissement TMDB |
| 4 | [phase-4-rest-api-auth.md](phase-4-rest-api-auth.md) | REST API + JWT + securite |
| 5 | [phase-5-angular-setup.md](phase-5-angular-setup.md) | Projet Angular, routing, auth, services |
| 6 | [phase-6-angular-ui.md](phase-6-angular-ui.md) | Composants UI Angular (port du React) |
| 7 | [phase-7-mcp-agent.md](phase-7-mcp-agent.md) | Agent Claude + MCP server TMDB |
| 8 | [phase-8-chat-picks.md](phase-8-chat-picks.md) | Chat UI Angular + picks integration |
| 9 | [phase-9-db-enum-tests-csv-upload-plan.md](phase-9-db-enum-tests-csv-upload-plan.md) | Enum SQL, tests stabiles, import CSV manuel + upload Angular |

## Suivi d'avancement

Voir [PROGRESS.md](PROGRESS.md) pour le suivi tache par tache.

## Architecture globale

```
ask2watch/
  ask2watch-backend/          # Spring Boot 3.x
    src/main/java/com/ask2watch/
      config/                 # SecurityConfig, CorsConfig, TmdbConfig
      controller/             # REST controllers
      dto/                    # Request/Response DTOs
      model/                  # JPA entities
      repository/             # Spring Data JPA repositories
      service/                # Business logic
      agent/                  # Claude AI agent + MCP
    src/main/resources/
      application.yml
      data/                   # CSV files for import
    src/test/java/com/ask2watch/
      controller/
      service/
      repository/

  ask2watch-frontend/         # Angular 19
    src/app/
      core/                   # Guards, interceptors, services singleton
        guards/
        interceptors/
        services/
      features/               # Feature modules (lazy loaded)
        home/
        watched/
        picks/
        login/
        chat/
      shared/                 # Composants reutilisables
        components/
        models/
        pipes/
        utils/
    src/assets/               # Images, fonts
    src/styles/               # Styles globaux Tailwind

  docker-compose.yml          # PostgreSQL
```

## Schema de la base de donnees

```
users
  id BIGSERIAL PK
  username VARCHAR(100)
  email VARCHAR(255) UNIQUE
  password_hash VARCHAR(255)

media
  id BIGSERIAL PK
  imdb_id VARCHAR(20) UNIQUE
  tmdb_id INT UNIQUE
  title VARCHAR(500)
  original_title VARCHAR(500)
  media_type VARCHAR(10)       -- MOVIE | SERIES
  year VARCHAR(20)
  runtime_mins INT
  genres VARCHAR(500)
  imdb_rating DECIMAL(3,1)
  num_votes INT
  release_date DATE
  directors VARCHAR(500)
  stars VARCHAR(1000)
  synopsis TEXT
  rated VARCHAR(20)
  poster_path VARCHAR(255)
  seasons INT                  -- NULL pour les films
  imdb_url VARCHAR(500)

user_watched
  id BIGSERIAL PK
  user_id BIGINT FK -> users
  media_id BIGINT FK -> media
  user_rating INT              -- 1-5
  date_watched DATE
  comment TEXT

picks_of_week
  id BIGSERIAL PK
  media_id BIGINT FK -> media
  user_id BIGINT FK -> users
  week_date DATE
  created_by_agent BOOLEAN
```

## Appels TMDB valides (testes)

```
# Etape 1 : IMDb ID -> TMDB ID + poster + synopsis
GET /find/{imdb_id}?external_source=imdb_id&api_key=XXX

# Etape 2 : Details complets (cast, director, certification, seasons)
GET /movie/{tmdb_id}?append_to_response=credits,release_dates&api_key=XXX
GET /tv/{tmdb_id}?append_to_response=credits,content_ratings&api_key=XXX

# Frontend : construction URL poster
https://image.tmdb.org/t/p/w500{poster_path}
```

## Donnees CSV

- `watchedList/moviesWatched.csv` : 162 films
- `watchedList/tvSeriesWatched.csv` : 43 series
- Colonnes utiles : Const (IMDb ID), Title, Original Title, URL, Title Type, IMDb Rating, Runtime, Year, Genres, Num Votes, Release Date, Directors
- Colonnes vides : Description, Your Rating, Date Rated
- Directors vide pour les series (normal)
