# Phase 5 : Projet Angular, Routing, Auth, Services

## 5.1 Generer le projet Angular

**Commande :**

```bash
cd ask2watch/
ng new ask2watch-frontend --style=css --routing --ssr=false --standalone
```

**Puis installer les dependances :**

```bash
cd ask2watch-frontend
npm install tailwindcss @tailwindcss/postcss postcss
npm install lucide-angular
npm install @angular/cdk
```

---

## 5.2 Configuration Tailwind CSS

### Fichier : `postcss.config.js`

```js
module.exports = {
  plugins: {
    "@tailwindcss/postcss": {}
  }
};
```

### Fichier : `src/styles.css`

Importer Tailwind + definir les variables CSS custom + les classes utilitaires.

**Variables CSS (reprises du React) :**

```css
@import "tailwindcss";

@theme {
  --color-background: hsl(0, 0%, 8%);
  --color-foreground: hsl(0, 0%, 95%);
  --color-primary: hsl(0, 72%, 51%);
  --color-accent: hsl(210, 70%, 50%);
  --color-gold: hsl(45, 90%, 55%);
  --color-card: hsl(0, 0%, 11%);
  --color-muted: hsl(0, 0%, 14%);
  --color-border: hsl(0, 0%, 18%);
  --color-muted-foreground: hsl(0, 0%, 64%);

  --font-display: "Bebas Neue", sans-serif;
  --font-body: "Source Sans 3", sans-serif;
}
```

**Classes utilitaires custom :**

```css
.text-gradient-red {
  background-image: linear-gradient(135deg, hsl(0, 72%, 51%), hsl(15, 80%, 55%));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.glass-card {
  @apply bg-card/60 backdrop-blur-md border border-border/50 rounded-xl;
}

.card-hover {
  @apply transition-all duration-300 hover:scale-105 hover:shadow-2xl hover:shadow-primary/20;
}
```

**Animations custom :**

```css
@keyframes fade-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slide-in {
  from { transform: translateX(-20px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.animate-fade-in {
  animation: fade-in 0.5s ease-out forwards;
}

.animate-slide-in {
  animation: slide-in 0.4s ease-out forwards;
}
```

### Fichier : `src/index.html`

Ajouter les imports Google Fonts dans le `<head>` :
- Bebas Neue (headings)
- Source Sans 3 (body)

---

## 5.3 Modeles TypeScript

### Fichier : `src/app/shared/models/media.model.ts`

```typescript
export interface MediaResponse {
  id: number;
  imdbId: string;
  tmdbId: number;
  title: string;
  mediaType: 'MOVIE' | 'SERIES';
  year: string;
  runtimeMins: number | null;
  genres: string;
  imdbRating: number | null;
  directors: string;
  stars: string;
  synopsis: string;
  rated: string;
  posterPath: string;
  seasons: number | null;
}
```

### Fichier : `src/app/shared/models/watched.model.ts`

```typescript
export interface WatchedMediaResponse {
  watchedId: number;
  media: MediaResponse;
  userRating: number | null;
  dateWatched: string | null;
  comment: string | null;
}

export interface UpdateWatchedRequest {
  userRating: number;
  comment: string;
}
```

### Fichier : `src/app/shared/models/pick.model.ts`

```typescript
export interface PickResponse {
  pickId: number;
  media: MediaResponse;
  weekDate: string;
  createdByAgent: boolean;
}
```

### Fichier : `src/app/shared/models/auth.model.ts`

```typescript
export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}
```

---

## 5.4 Utilitaires

### Fichier : `src/app/shared/utils/tmdb.util.ts`

```typescript
const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

export function posterUrl(posterPath: string | null, size = 'w500'): string {
  if (!posterPath) {
    return 'assets/no-poster.png';
  }
  return `${TMDB_IMAGE_BASE}/${size}${posterPath}`;
}
```

### Fichier : `src/app/shared/utils/duration.util.ts`

```typescript
export function formatDuration(mins: number | null): string {
  if (!mins) return '';
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return h > 0 ? `${h}h ${m}min` : `${m}min`;
}
```

### Fichier : `src/assets/no-poster.png`

Image placeholder pour les medias sans poster. Creer une image simple grise avec un icone film.

---

## 5.5 Services Core

### Fichier : `src/app/core/services/auth.service.ts`

- Injectable `providedIn: 'root'`
- Signal `isLoggedIn` (boolean)
- Signal `username` (string)
- Signal `token` (string | null)
- Methode `login(request: LoginRequest): Observable<AuthResponse>`
  - POST `/api/auth/login`
  - Stocke token dans localStorage
  - Met a jour les signals
- Methode `register(request: RegisterRequest): Observable<AuthResponse>`
  - POST `/api/auth/register`
  - Meme logique que login
- Methode `logout(): void`
  - Supprime le token de localStorage
  - Reset les signals
- Methode `isAuthenticated(): boolean`
  - Verifie si un token valide existe dans localStorage
- Au constructeur : verifier localStorage pour restaurer la session

### Fichier : `src/app/core/services/media.service.ts`

- Injectable `providedIn: 'root'`
- Methode `getWatchedMovies(): Observable<WatchedMediaResponse[]>`
  - GET `/api/media/watched?type=MOVIE`
- Methode `getWatchedSeries(): Observable<WatchedMediaResponse[]>`
  - GET `/api/media/watched?type=SERIES`
- Methode `updateWatched(id: number, req: UpdateWatchedRequest): Observable<WatchedMediaResponse>`
  - PUT `/api/media/watched/{id}`
- Methode `getMediaById(id: number): Observable<MediaResponse>`
  - GET `/api/media/{id}`

### Fichier : `src/app/core/services/pick.service.ts`

- Injectable `providedIn: 'root'`
- Methode `getCurrentPicks(): Observable<PickResponse[]>`
  - GET `/api/picks/current`
- Methode `getAllPicks(): Observable<PickResponse[]>`
  - GET `/api/picks`

---

## 5.6 Intercepteur et Guard

### Fichier : `src/app/core/interceptors/auth.interceptor.ts`

- Functional interceptor (Angular 19 style)
- Lit le token depuis `AuthService`
- Ajoute `Authorization: Bearer {token}` a chaque requete sortante
- Si la reponse est 401, deconnecter l'utilisateur et rediriger vers /login

### Fichier : `src/app/core/guards/auth.guard.ts`

- Functional guard (Angular 19 style)
- Verifie `authService.isAuthenticated()`
- Si non authentifie, redirige vers `/login`

---

## 5.7 Routing

### Fichier : `src/app/app.routes.ts`

```typescript
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component')
      .then(m => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home.component')
          .then(m => m.HomeComponent)
      },
      {
        path: 'watched',
        loadComponent: () => import('./features/watched/watched.component')
          .then(m => m.WatchedComponent)
      },
      {
        path: 'picks',
        loadComponent: () => import('./features/picks/picks.component')
          .then(m => m.PicksComponent)
      },
      {
        path: 'chat',
        loadComponent: () => import('./features/chat/chat.component')
          .then(m => m.ChatComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
```

### Fichier : `src/app/app.config.ts`

- Fournir `provideRouter(routes)`
- Fournir `provideHttpClient(withInterceptors([authInterceptor]))`

### Fichier : `src/app/app.component.ts`

- Template minimal : `<router-outlet />`
- Pas de logique metier ici

### Fichier : `src/app/app.component.html`

```html
<router-outlet />
```

### Fichier : `src/app/app.component.css`

Vide (les styles globaux sont dans `styles.css`).

---

## 5.8 Configuration proxy (dev)

### Fichier : `proxy.conf.json`

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

### Modifier : `angular.json`

Ajouter `"proxyConfig": "proxy.conf.json"` dans `serve.options`.

---

## 5.9 Tests Phase 5

### Fichier : `src/app/core/services/auth.service.spec.ts`

- **Test 1 :** login() appelle POST /api/auth/login et stocke le token
- **Test 2 :** logout() supprime le token et met isLoggedIn a false
- **Test 3 :** isAuthenticated() retourne true si token dans localStorage

### Fichier : `src/app/core/services/media.service.spec.ts`

- **Test 1 :** getWatchedMovies() appelle GET /api/media/watched?type=MOVIE
- **Test 2 :** updateWatched() appelle PUT avec le bon body

### Fichier : `src/app/core/guards/auth.guard.spec.ts`

- **Test 1 :** Utilisateur authentifie -> retourne true
- **Test 2 :** Utilisateur non authentifie -> redirige vers /login

### Execution :

```bash
cd ask2watch-frontend
ng test --watch=false
```

---

## 5.10 Validation Phase 5

- [ ] `ng serve` demarre sans erreur sur http://localhost:4200
- [ ] La page login s'affiche (meme vide pour l'instant)
- [ ] Le proxy redirige `/api/*` vers le backend sur :8080
- [ ] Le guard redirige vers /login si pas authentifie
- [ ] Les services compilent sans erreur TypeScript
- [ ] `ng test` passe au vert

---

## 5.11 Nettoyage

- Supprimer le contenu par defaut de `app.component.html` (le template Angular "Welcome")
- Supprimer `app.component.spec.ts` par defaut (remplacer par nos tests)
- Supprimer les styles par defaut dans `styles.css` (remplacer par Tailwind)
- Supprimer le favicon Angular par defaut -> remplacer par un icone film
