# Backend - Logique metier et architecture

## 1. Structure du backend

Le backend suit une structure Spring Boot classique :

- `controller/` : expose les endpoints REST
- `service/` : contient la logique metier
- `repository/` : acces base via Spring Data JPA
- `model/` : entites persistantes
- `dto/` : payloads API et mapping
- `config/` : securite, init, filtres, exceptions

## 2. Schema et persistence

Le schema est defini dans [schema.sql](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/resources/schema.sql).

Tables principales :

- `users`
- `media`
- `user_watched`
- `picks_of_week`

### Points importants

- `media_type_enum` vaut `MOVIE` ou `SERIES`
- `user_watched` a une contrainte `UNIQUE(user_id, media_id)`
  un meme utilisateur ne peut pas marquer deux fois le meme titre comme vu
- `user_rating` est borne entre `1` et `5`
- `picks_of_week` n'a pas de contrainte unique native
  la prevention des doublons est geree en service

## 3. Entites

### `User`

- compte applicatif
- email unique
- mot de passe hash

### `Media`

- referentiel des films/series
- peut etre cree depuis :
  - import CSV IMDb
  - ajout aux picks
  - ajout aux vus
  - enrichissement TMDB

### `UserWatched`

- association utilisateur <-> media
- stocke :
  - note perso
  - commentaire
  - date

### `PickOfWeek`

- selection d'un media pour un utilisateur et une semaine
- attribut `createdByAgent`

## 4. Controllers

### `AuthController`

- `POST /api/auth/register`
- `POST /api/auth/login`

But :

- creer un compte
- authentifier
- retourner un JWT + username

### `MediaController`

Expose :

- lecture watched par type
- update note/commentaire
- ajout/suppression watched
- lecture d'un media
- tendances TMDB
- recommandations
- import CSV manuel ou auto

### `PickController`

Expose :

- picks courants
- tous les picks
- historique
- ajout
- suppression

### `AgentController`

Expose :

- `POST /api/agent/chat`
- `POST /api/agent/generate-picks`
- `DELETE /api/agent/history`

## 5. Services metier

### `AuthService`

Responsabilites :

- login
- register
- hash password
- emission JWT
- audit logs

Flux login :

1. recherche user par email
2. verification du mot de passe bcrypt
3. generation token
4. audit success/failure

### `MediaService`

Service central de la watchlist.

Responsabilites :

- lister les vus par type
- ajouter un titre a la watchlist
- mettre a jour note/commentaire
- supprimer un titre vu
- lancer l'import CSV
- deleguer les recommandations
- deleguer les tendances TMDB

Point important :

- `addToWatched` cree un `Media` si necessaire, mais ne fait pas tout l'enrichissement TMDB automatiquement dans ce flux
- l'unicite utilisateur/media est surtout garantie par le schema SQL

### `PickService`

Responsabilites :

- lire picks courants, tous les picks, historique
- ajouter un pick
- supprimer un pick

Logique utile :

- un `Media` peut etre enrichi par TMDB si ses donnees sont incompletes
- les doublons de picks sur la semaine courante sont evites au niveau service
- `createdByAgent` permet de savoir si le pick vient du bouton/agent ou d'une action humaine

### `TmdbService`

Service d'integration avec TMDB.

Responsabilites :

- retrouver un media depuis un IMDb ID
- charger details film/serie
- charger tendances
- discover par genres
- enrichir un `Media` local avec poster, synopsis, cast, genres, certification

Il joue deux roles distincts :

1. enrichissement du referentiel local
2. source de recherche/recommandation externe

### `TmdbRecommendationProvider`

Mecanisme de recommandation "classique" non-LLM.

Logique :

1. recuperer les titres vus
2. extraire les genres des films les mieux notes
3. normaliser ces genres
4. faire un `discover` TMDB
5. exclure les titres deja vus
6. retourner des `RecommendationDto`

Limite :

- cette logique est simple et orientee "genres favoris"
- elle n'exploite pas vraiment les commentaires
- c'est moins puissant que la logique agentique de Dobby

### `UserCsvImportService`

Responsabilites :

- parser un CSV IMDb
- detecter type film/serie
- ignorer les lignes non importables
- compter imported/skipped/duplicates/errors

Le service ne remplace pas l'existant :

- si un `Media` existe deja, il est reutilise
- si l'utilisateur a deja ce media dans `user_watched`, la ligne est comptee en duplicate

## 6. Securite backend

### `SecurityConfig`

- toutes les routes sauf `/api/auth/**` sont protegees
- backend stateless
- CORS configurable
- deux filtres en amont :
  - `RateLimitFilter`
  - `JwtAuthFilter`

### `JwtAuthFilter`

- lit `Authorization: Bearer ...`
- valide le token
- injecte `userId` dans le `SecurityContext`

Le choix technique important est le suivant :

- le principal Spring transporte directement `userId`
- les controllers recuperent donc `Long userId = (Long) auth.getPrincipal()`

### `RateLimitFilter`

Protection minimale contre bruteforce :

- login : 5 tentatives / minute / IP
- register : 3 tentatives / heure / IP

### `AuditLogService`

Pas de table d'audit dediee.

Les evenements sensibles sont traces dans les logs :

- succes/echec login
- registration
- suppression de donnees
- changement de note

## 7. Configuration backend

Le backend lit ses secrets dans `application.yml` via variables d'environnement :

- `DB_USERNAME`
- `DB_PASSWORD`
- `TMDB_API_KEY`
- `JWT_SECRET`
- `ANTHROPIC_API_KEY`
- `DEFAULT_ADMIN_PASSWORD`
- `CORS_ALLOWED_ORIGINS`

Le fichier `.env.example` decrit ces variables.

## 8. Tests backend

Le backend contient des tests d'integration :

- `AuthControllerIT`
- `MediaControllerIT`
- `PickControllerIT`
- `AgentControllerIT`

Ils s'appuient sur :

- Spring Boot Test
- Testcontainers PostgreSQL

Donc l'approche de validation privilegie les flux reels REST + DB, pas juste des unit tests isoles.
