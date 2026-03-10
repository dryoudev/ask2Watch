# ask2Watch

Application web de suivi de films et series, recommandations personnalisees et assistant IA (Dobby).

## Stack technique

| Couche | Technologie |
|--------|------------|
| Frontend | Angular 21, Tailwind CSS 4, TypeScript 5.9 |
| Backend | Spring Boot 3.5, Java 21, Spring Security, JPA |
| Base de donnees | PostgreSQL 14 |
| IA | Anthropic Claude API (Sonnet 4.6) |
| API externe | TMDB (The Movie Database) |
| MCP Server | Node.js, Model Context Protocol SDK |

## Fonctionnalites

### Watched List
- Ajouter des films et series a sa liste personnelle
- Noter (1-10) et commenter chaque titre
- Filtrer par type (Movies / TV Series), genre, note, annee
- Recherche par titre
- Import CSV depuis un export IMDb (detection automatique du type)

### Picks de la semaine
- Creer des picks avec une raison
- Historique des picks par semaine
- Ajout depuis les recommandations ou via Dobby

### Chat avec Dobby (Assistant IA)
- Conversation en langage naturel depuis la page `/chat` ou le widget sur la home
- System prompt statique et leger: la watchlist n'est plus injectee dans chaque requete
- Dobby consulte la watchlist et les picks a la demande via les tools `get_watched_movies`, `get_watched_series`, `get_current_picks`
- Tool `search_watched` pour verifier rapidement si un titre existe deja avant une recommandation
- Recherche TMDB en temps reel via 5 outils (search, trending, discover, recommendations)
- 10 outils : recherche TMDB, consultation de la watchlist, verification ciblee, picks et actions CRUD
- Historique de conversation avec TTL (1h)

### Recommandations
- Recommandations TMDB basees sur les genres regardes
- Architecture extensible (Strategy Pattern) pour ajouter d'autres providers

### Home Page
- Accueil personnalise ("Bonjour, {username}")
- 3 lignes : Recemment vus, Meilleures Notes, Tendances de la semaine
- Widget chat Dobby flottant

### Securite
- Authentification JWT (24h expiration)
- Rate limiting sur les endpoints d'authentification
- Audit logging des operations sensibles
- CORS configure

## Architecture

```
ask2watch/
├── ask2watch-backend/       # API REST Spring Boot
│   ├── controller/          # Auth, Media, Pick, Agent endpoints
│   ├── service/             # Logique metier + AgentService (Claude API)
│   ├── model/               # Entites JPA (User, Media, UserWatched, PickOfWeek)
│   ├── dto/                 # Request/Response DTOs
│   └── config/              # Security, CORS, Rate Limiting
├── ask2watch-frontend/      # SPA Angular
│   ├── features/            # Login, Home, Watched, Picks, Chat
│   ├── shared/              # Composants reutilisables (MovieCard, MediaCard, Dialog)
│   └── core/                # Services, Guards, Interceptors
├── ask2watch-mcp-tmdb/      # Serveur MCP pour Claude Code (CLI)
└── docker-compose.yml       # PostgreSQL
```

## Installation

### Pre-requis

- Java 21+
- Node.js 18+
- PostgreSQL 14+ (ou Docker)
- Cles API : [TMDB](https://www.themoviedb.org/settings/api) et [Anthropic](https://console.anthropic.com/)

### 1. Base de donnees

```bash
docker-compose up -d
```

Cree une instance PostgreSQL sur `localhost:5432` (user: `ask2watch`, db: `ask2watch`).

### 2. Backend

```bash
cd ask2watch-backend

# Configurer les variables d'environnement
cp .env.example .env
# Editer .env avec vos cles API (TMDB_API_KEY, ANTHROPIC_API_KEY, JWT_SECRET)

# Lancer
./mvnw spring-boot:run
```

Le backend demarre sur `http://localhost:8080`. Au premier lancement, le schema SQL est cree et un compte admin est initialise.

### 3. Frontend

```bash
cd ask2watch-frontend

npm install
npm start
```

Le frontend demarre sur `http://localhost:4200` avec un proxy vers le backend.

### 4. MCP Server (optionnel, pour Claude Code CLI)

```bash
cd ask2watch-mcp-tmdb

npm install
cp .env.example .env
# Editer .env avec TMDB_API_KEY, MCP_AUTH_EMAIL, MCP_AUTH_PASSWORD, BACKEND_URL
```

Le serveur MCP est utilise par Claude Code pour interagir avec l'application depuis le terminal.

## Variables d'environnement

### Backend (.env)

| Variable | Description |
|----------|------------|
| `DB_USERNAME` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | Mot de passe PostgreSQL |
| `TMDB_API_KEY` | Cle API TMDB |
| `JWT_SECRET` | Secret JWT (256 bits, generer avec `openssl rand -base64 32`) |
| `ANTHROPIC_API_KEY` | Cle API Anthropic pour le chat Dobby |
| `DEFAULT_ADMIN_PASSWORD` | Mot de passe du compte admin initial |
| `CORS_ALLOWED_ORIGINS` | Origines autorisees (default: `http://localhost:4200`) |

## API Endpoints

### Authentification
| Methode | Endpoint | Description |
|---------|----------|------------|
| POST | `/api/auth/register` | Creer un compte |
| POST | `/api/auth/login` | Se connecter (retourne JWT) |

### Media (Watched)
| Methode | Endpoint | Description |
|---------|----------|------------|
| GET | `/api/media/watched?type=MOVIE\|SERIES` | Liste des films/series vus |
| POST | `/api/media/watched` | Ajouter a la liste |
| PUT | `/api/media/watched/{id}` | Modifier note/commentaire |
| DELETE | `/api/media/watched/{id}` | Retirer de la liste |
| GET | `/api/media/{id}` | Details d'un media |
| GET | `/api/media/trending?limit=10` | Tendances TMDB |
| GET | `/api/media/recommendations?limit=5` | Recommandations personnalisees |

### CSV Import
| Methode | Endpoint | Description |
|---------|----------|------------|
| POST | `/api/media/import/csv/auto` | Import auto (detecte movies + series) |
| POST | `/api/media/import/csv` | Import avec type specifie |

### Picks
| Methode | Endpoint | Description |
|---------|----------|------------|
| GET | `/api/picks/current` | Picks de la semaine |
| GET | `/api/picks/history?limit=10` | Historique des picks |
| POST | `/api/picks` | Creer un pick |
| DELETE | `/api/picks/{id}` | Supprimer un pick |

### Agent (Chat Dobby)
| Methode | Endpoint | Description |
|---------|----------|------------|
| POST | `/api/agent/chat` | Envoyer un message a Dobby |
| DELETE | `/api/agent/history` | Effacer l'historique |

## Fonctionnement du prompt agent

Avant, le backend injectait toute la liste des films et series vus dans le `system prompt` a chaque message. Avec une watchlist volumineuse, cela augmentait fortement le cout en tokens.

Maintenant, le prompt reste statique et court. Dobby charge les donnees du Maitre uniquement quand il en a besoin pendant la conversation :

- `get_watched_movies` pour la liste des films vus
- `get_watched_series` pour la liste des series vues
- `search_watched` pour verifier rapidement un titre
- `get_current_picks` pour controler les picks deja actifs

Les resultats de tools restent dans l'historique de conversation en memoire pendant 1 heure, ce qui evite de recharger les memes donnees a chaque message de la meme session.

## Tests

### Backend
```bash
cd ask2watch-backend
./mvnw test
```

65 tests d'integration (Testcontainers + PostgreSQL) couvrant Auth, Media, Pick et Agent.

### Frontend
```bash
cd ask2watch-frontend
npm test
```

Tests unitaires avec Vitest + JSDOM.

## CSV Import (Format IMDb)

L'application accepte les exports CSV d'IMDb. Le format attendu contient les colonnes :

```
Position,Const,Created,Modified,Description,Title,Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors,Your Rating,Date Rated
```

La colonne `Title Type` determine le classement :
- `Movie` → Films
- `TV Series`, `TV Mini Series` → Series
- `Video` → Ignore

## Licence

Projet prive - Atexo
