# Phase 6 : Composants UI Angular (port du React)

## Convention : chaque composant = 3 fichiers separes

```
nom-composant/
  nom-composant.component.ts     # Logique
  nom-composant.component.html   # Template
  nom-composant.component.css    # Styles specifiques
```

Aucun fichier ne doit depasser 200 lignes.

---

## 6.1 Composant : AppHeader

### Fichier : `src/app/shared/components/app-header/app-header.component.ts`

- Standalone component
- Injecte `AuthService` et `Router`
- Propriete `username` lue depuis `authService.username`
- Methode `logout()` : appelle `authService.logout()` puis `router.navigate(['/login'])`

### Fichier : `src/app/shared/components/app-header/app-header.component.html`

- `<header>` fixe en haut avec backdrop-blur
- Logo : icone Film (lucide) + texte "ask2Watch" en `text-gradient-red`
- Navigation : 2 liens `routerLink` (Watched avec icone Eye, Picks avec icone Sparkles)
- Lien actif : classe `text-primary` via `routerLinkActive`
- Droite : username + bouton Sign Out (icone LogOut)

### Fichier : `src/app/shared/components/app-header/app-header.component.css`

- Styles specifiques au header (si necessaire, sinon vide — Tailwind suffit)

---

## 6.2 Composant : StarRating

### Fichier : `src/app/shared/components/star-rating/star-rating.component.ts`

- Standalone component
- Input : `rating: number` (1-5)
- Input : `max: number` (default 5)
- Input : `size: number` (default 16, en px)
- Computed : tableau de `max` elements, chaque element `filled: boolean`

### Fichier : `src/app/shared/components/star-rating/star-rating.component.html`

```html
<div class="flex items-center gap-0.5">
  @for (star of stars; track $index) {
    <svg><!-- icone Star remplie ou vide selon star.filled --></svg>
  }
</div>
```

- Etoile remplie : classe `fill-gold text-gold`
- Etoile vide : classe `text-muted-foreground/30`

### Fichier : `src/app/shared/components/star-rating/star-rating.component.css`

Vide (Tailwind suffit).

---

## 6.3 Composant : MovieCard (WatchedMovieCard)

### Fichier : `src/app/shared/components/movie-card/movie-card.component.ts`

- Standalone component
- Input : `watched: WatchedMediaResponse`
- Input : `index: number` (pour l'animation decalee)
- Output : `cardClick: EventEmitter<WatchedMediaResponse>`
- Methode `getPosterUrl()` : appelle `posterUrl(this.watched.media.posterPath)`
- Methode `getAnimationDelay()` : retourne `${this.index * 0.1}s`

### Fichier : `src/app/shared/components/movie-card/movie-card.component.html`

```html
<div class="card-hover cursor-pointer animate-fade-in rounded-xl overflow-hidden"
     [style.animation-delay]="getAnimationDelay()"
     (click)="cardClick.emit(watched)">
  <div class="relative aspect-[2/3]">
    <img [src]="getPosterUrl()" [alt]="watched.media.title"
         class="w-full h-full object-cover" loading="lazy" />
    <div class="absolute bottom-0 left-0 right-0 p-3 bg-gradient-to-t from-black/90 to-transparent">
      <h3 class="text-sm font-semibold truncate">{{ watched.media.title }}</h3>
      <app-star-rating [rating]="watched.userRating ?? 0" [size]="12" />
      <p class="text-xs text-muted-foreground mt-1">{{ watched.dateWatched }}</p>
    </div>
  </div>
</div>
```

### Fichier : `src/app/shared/components/movie-card/movie-card.component.css`

Vide.

---

## 6.4 Composant : PickCard (PickMovieCard)

### Fichier : `src/app/shared/components/pick-card/pick-card.component.ts`

- Standalone component
- Input : `pick: PickResponse`
- Input : `index: number`
- Output : `cardClick: EventEmitter<PickResponse>`
- Methode `getPosterUrl()` : appelle `posterUrl(this.pick.media.posterPath)`

### Fichier : `src/app/shared/components/pick-card/pick-card.component.html`

```html
<div class="card-hover cursor-pointer animate-fade-in rounded-xl overflow-hidden relative"
     [style.animation-delay]="index * 0.1 + 's'"
     (click)="cardClick.emit(pick)">
  <div class="absolute top-2 right-2 z-10">
    <span class="bg-primary text-white text-xs px-2 py-1 rounded-full">Pick</span>
  </div>
  <div class="relative aspect-[2/3]">
    <img [src]="getPosterUrl()" [alt]="pick.media.title"
         class="w-full h-full object-cover" loading="lazy" />
    <div class="absolute bottom-0 left-0 right-0 p-3 bg-gradient-to-t from-black/90 to-transparent">
      <h3 class="text-sm font-semibold truncate">{{ pick.media.title }}</h3>
      <p class="text-xs text-muted-foreground">{{ pick.media.year }} - {{ pick.media.genres }}</p>
    </div>
  </div>
</div>
```

### Fichier : `src/app/shared/components/pick-card/pick-card.component.css`

Vide.

---

## 6.5 Composant : MovieRow

### Fichier : `src/app/shared/components/movie-row/movie-row.component.ts`

- Standalone component
- Input : `title: string`
- `@ViewChild('scrollContainer')` : reference au container scrollable
- Methode `scroll(direction: 'left' | 'right')` : scroll de 400px smooth

### Fichier : `src/app/shared/components/movie-row/movie-row.component.html`

```html
<div class="relative group/row">
  <h2 class="text-xl font-display mb-4">{{ title }}</h2>
  <button (click)="scroll('left')"
          class="absolute left-0 top-1/2 z-10 opacity-0 group-hover/row:opacity-100 transition-opacity">
    <!-- chevron left SVG -->
  </button>
  <div #scrollContainer class="flex gap-4 overflow-x-auto scrollbar-hide scroll-smooth">
    <ng-content />
  </div>
  <button (click)="scroll('right')"
          class="absolute right-0 top-1/2 z-10 opacity-0 group-hover/row:opacity-100 transition-opacity">
    <!-- chevron right SVG -->
  </button>
</div>
```

### Fichier : `src/app/shared/components/movie-row/movie-row.component.css`

```css
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
```

---

## 6.6 Composant : MediaDetailDialog

### Fichier : `src/app/shared/components/media-detail-dialog/media-detail-dialog.component.ts`

- Standalone component
- Input : `item: WatchedMediaResponse | PickResponse | null`
- Input : `open: boolean`
- Output : `openChange: EventEmitter<boolean>`
- Signal `showCommentInput` (boolean)
- Signal `commentText` (string)
- Computed `isWatched()` : verifie si item est WatchedMediaResponse
- Computed `mediaData()` : extrait le MediaResponse depuis item
- Methode `close()` : emet `openChange(false)`
- Methode `saveComment()` : appelle `mediaService.updateWatched()`
- Methode `getPosterUrl()` : construit l'URL poster
- Methode `formatDuration()` : utilise l'utilitaire

### Fichier : `src/app/shared/components/media-detail-dialog/media-detail-dialog.component.html`

Structure en 2 colonnes (poster gauche, details droite) :

- **Overlay** : fond semi-transparent, click ferme le dialog
- **Contenu** : `glass-card` avec padding
- **Colonne gauche** : image poster `aspect-[2/3]`
- **Colonne droite** :
  - Titre + icone (Film ou TV selon mediaType)
  - Badges : year, duration/seasons, rated, genres
  - Note IMDb avec icone etoile
  - Note utilisateur (si watched) avec `<app-star-rating>`
  - Synopsis
  - Director/Creator + Cast (stars)
  - Date de visionnage (si watched)
  - Commentaire existant
  - Zone input commentaire (textarea + boutons save/cancel)
- **Bouton fermer** : croix en haut a droite

### Fichier : `src/app/shared/components/media-detail-dialog/media-detail-dialog.component.css`

```css
:host {
  display: contents;
}

.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  z-index: 50;
}

.dialog-content {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 51;
  max-height: 90vh;
  overflow-y: auto;
  width: 95%;
  max-width: 42rem;
}
```

---

## 6.7 Feature : LoginComponent

### Fichier : `src/app/features/login/login.component.ts`

- Standalone component
- Injecte `AuthService`, `Router`
- Signals : `email`, `password`, `error`
- Methode `onSubmit()` : appelle authService.login, redirige vers '/' si OK

### Fichier : `src/app/features/login/login.component.html`

- Background : hero-bg.png en full screen
- Overlay semi-transparent
- Card centree `glass-card` avec :
  - Logo + titre "ask2Watch"
  - Input email
  - Input password
  - Message d'erreur (si error)
  - Bouton "Sign In"
  - Lien "Create account" (optionnel)

### Fichier : `src/app/features/login/login.component.css`

Vide (Tailwind suffit).

### Asset : copier `cine-picks-main/src/assets/hero-bg.png` vers `src/assets/hero-bg.png`

---

## 6.8 Feature : HomeComponent

### Fichier : `src/app/features/home/home.component.ts`

- Standalone component
- Injecte `MediaService`, `PickService`
- Signal `watchedMovies` : liste des films vus (5 premiers)
- Signal `picks` : picks de la semaine
- Au `ngOnInit` : charge les donnees via les services

### Fichier : `src/app/features/home/home.component.html`

```html
<app-header />

<!-- Hero section -->
<section class="relative h-[60vh] flex items-center justify-center"
         style="background-image: url('assets/hero-bg.png')">
  <div class="absolute inset-0 bg-background/75"></div>
  <div class="relative z-10 text-center">
    <h1 class="font-display text-6xl text-gradient-red">Your Movies</h1>
  </div>
</section>

<!-- Watched Row -->
<section class="px-8 py-6">
  <app-movie-row title="Recently Watched">
    @for (item of watchedMovies(); track item.watchedId) {
      <app-movie-card [watched]="item" [index]="$index" (cardClick)="onWatchedClick($event)" />
    }
  </app-movie-row>
</section>

<!-- Picks Row -->
<section class="px-8 py-6">
  <app-movie-row title="Picks of the Week">
    @for (item of picks(); track item.pickId) {
      <app-pick-card [pick]="item" [index]="$index" (cardClick)="onPickClick($event)" />
    }
  </app-movie-row>
</section>

<app-media-detail-dialog [item]="selectedItem()" [open]="dialogOpen()" (openChange)="dialogOpen.set($event)" />
```

### Fichier : `src/app/features/home/home.component.css`

Vide.

---

## 6.9 Feature : WatchedComponent

### Fichier : `src/app/features/watched/watched.component.ts`

- Standalone component
- Injecte `MediaService`
- Signal `activeTab` : 'movies' | 'series'
- Signal `movieSearch` / `seriesSearch` : string
- Signal `movies` / `series` : listes chargees depuis l'API
- Computed `filteredMovies` / `filteredSeries` : filtrage par titre (case-insensitive)
- Signal `selectedItem` + `dialogOpen` : pour le detail dialog

### Fichier : `src/app/features/watched/watched.component.html`

- `<app-header />`
- Titre "Watched" avec icone Eye
- 2 onglets : "Movies" / "TV Series" (boutons toggle)
- Barre de recherche (input avec icone Search)
- Grille responsive : `grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4`
- Iteration `@for` sur `filteredMovies()` ou `filteredSeries()` selon l'onglet actif
- `<app-media-detail-dialog>`

### Fichier : `src/app/features/watched/watched.component.css`

Vide.

---

## 6.10 Feature : PicksComponent

### Fichier : `src/app/features/picks/picks.component.ts`

- Standalone component
- Injecte `PickService`
- Signal `picks` : liste chargee depuis l'API
- Signal `search` : string
- Computed `filteredPicks` : filtrage par titre
- Signal `selectedItem` + `dialogOpen`

### Fichier : `src/app/features/picks/picks.component.html`

- `<app-header />`
- Titre "Picks of the Week" avec icone Sparkles
- Barre de recherche
- Grille responsive (meme que Watched)
- `<app-media-detail-dialog>`

### Fichier : `src/app/features/picks/picks.component.css`

Vide.

---

## 6.11 Pipe : DurationPipe

### Fichier : `src/app/shared/pipes/duration.pipe.ts`

- Standalone pipe `duration`
- Transforme un nombre de minutes en "Xh Ymin"
- Utilise `formatDuration()` de `duration.util.ts`

---

## 6.12 Tests Phase 6

### Fichier : `src/app/shared/components/star-rating/star-rating.component.spec.ts`

- **Test 1 :** Rating 3/5 affiche 3 etoiles remplies et 2 vides
- **Test 2 :** Rating 0 affiche 5 etoiles vides

### Fichier : `src/app/shared/components/movie-card/movie-card.component.spec.ts`

- **Test 1 :** Affiche le titre du film
- **Test 2 :** Emet l'event cardClick au clic

### Fichier : `src/app/shared/components/media-detail-dialog/media-detail-dialog.component.spec.ts`

- **Test 1 :** Dialog visible quand open=true
- **Test 2 :** Dialog masque quand open=false
- **Test 3 :** Affiche les details du media passe en input

### Fichier : `src/app/features/watched/watched.component.spec.ts`

- **Test 1 :** Les onglets Movies/Series switchent correctement
- **Test 2 :** La recherche filtre les resultats

### Fichier : `src/app/features/login/login.component.spec.ts`

- **Test 1 :** Le formulaire appelle authService.login() au submit
- **Test 2 :** Un message d'erreur s'affiche si login echoue

### Execution :

```bash
ng test --watch=false
```

---

## 6.13 Validation Phase 6

- [ ] La page Login affiche le formulaire avec hero-bg
- [ ] Apres login, la Home affiche les rows horizontales
- [ ] La page Watched affiche la grille avec onglets Movies/Series
- [ ] La recherche filtre en temps reel
- [ ] Clic sur une card ouvre le MediaDetailDialog
- [ ] Le dialog affiche poster + tous les details
- [ ] La page Picks affiche la grille des picks
- [ ] Les posters chargent correctement depuis TMDB CDN
- [ ] Les animations (fade-in, card-hover) fonctionnent
- [ ] Le layout est responsive (mobile, tablet, desktop)
- [ ] Tous les tests passent

---

## 6.14 Nettoyage

- Supprimer le dossier `cine-picks-main/` (l'app React source, plus necessaire)
- Verifier qu'aucun composant n'importe de CSS non utilise
- Verifier qu'aucun `console.log` de debug ne reste dans le code
- S'assurer que les SVG des icones (Star, Film, Tv, etc.) sont extraits en composants ou importes depuis lucide-angular et non dupliques
