# Frontend - Structure et fonctionnement

## 1. Stack

Le frontend est une SPA Angular 21 avec :

- Angular standalone components
- Angular router
- HttpClient + interceptor
- signals pour l'etat local
- Tailwind CSS 4

## 2. Organisation

- `app.routes.ts`
  routing principal
- `core/`
  services, guard, interceptor
- `features/`
  pages applicatives
- `shared/`
  composants reutilisables, models, utils, pipes

## 3. Routing

Routes principales :

- `/login`
- `/`
- `/watched`
- `/picks`
- `/chat`

Le guard `authGuard` protege toutes les pages sauf login.

## 4. Gestion de session

### `AuthService`

Responsabilites :

- login/register
- persistance session
- logout
- restauration session depuis `localStorage`

Etat expose via signals :

- `isLoggedIn`
- `username`
- `token`

### `authInterceptor`

Fonction :

- ajoute automatiquement le JWT aux requetes HTTP
- en cas de `401` ou `403`, force le logout et redirige vers `/login`

Cela signifie que toute la logique de securite frontend est simple :

- si token present, l'app essaye
- si backend refuse, l'utilisateur est deconnecte

## 5. Pages principales

### Login

Page d'entree :

- formulaire login/register
- a la reponse positive, stockage session et navigation

### Home

Page de synthese personnalisee.

Charge :

- films vus recents
- top ratings
- tendances
- widget chat Dobby

Elle sert de dashboard.

### Watched

Page la plus CRUD du frontend.

Fonctions :

- affichage films et series par onglet
- recherche par titre
- tri
- filtre genre
- import CSV
- ouverture d'un dialog detail
- edition note/commentaire

L'etat de la page repose surtout sur des signals et des `computed`.

### Picks

Page des picks de la semaine.

Fonctions :

- lister les picks
- supprimer un pick
- deplacer un pick vers les vus
- lancer `Generate Picks`

Flux notable :

- au hover d'un poster, des actions rapides apparaissent
- le bouton "Vu" ouvre un modal note/commentaire
- le frontend enchaine :
  1. `addToWatched`
  2. `updateWatched` si note/commentaire
  3. `removePick`

### Chat

Page de conversation dediee avec Dobby.

Flux :

1. l'utilisateur envoie un message
2. le frontend appelle `agentService.chat`
3. la reponse texte est ajoutee a l'historique local

Le frontend ne gere pas le tool-calling.
Il se contente de rendre la reponse du backend.

## 6. Services frontend

### `AuthService`

Dialogue avec :

- `POST /api/auth/login`
- `POST /api/auth/register`

### `MediaService`

Dialogue avec :

- `GET /api/media/watched`
- `POST /api/media/watched`
- `PUT /api/media/watched/{id}`
- `GET /api/media/recommendations`
- `GET /api/media/trending`
- import CSV

### `PickService`

Dialogue avec :

- `GET /api/picks/current`
- `GET /api/picks`
- `POST /api/picks`
- `DELETE /api/picks/{id}`

### `AgentService`

Dialogue avec :

- `POST /api/agent/chat`
- `POST /api/agent/generate-picks`
- `DELETE /api/agent/history`

## 7. Composants shared

### Cartes

- `movie-card`
- `media-card`
- `pick-card`

Ces composants affichent les posters et deleguent les actions a la page parent.

### `media-detail-dialog`

Dialog transverse pour afficher :

- details d'un media vu
- details d'un pick
- details d'une recommandation

Il peut aussi :

- editer le commentaire d'un titre vu
- ajouter une recommandation aux picks

### `star-rating`

Composant reutilisable de notation.

Important :

- l'UI est en 5 etoiles
- le backend borne aussi `user_rating` entre 1 et 5

## 8. Couplage frontend/backend

Le frontend est relativement fin.

Il ne contient pas la logique metier lourde.
La plupart des decisions importantes vivent dans le backend :

- securite
- validation
- generation picks
- agent Dobby
- enrichissement TMDB

Le frontend orchestre surtout l'experience utilisateur et les enchainements d'appels API.
