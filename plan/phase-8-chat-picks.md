# Phase 8 : Chat UI Angular + Picks Integration + Finalisation

## 8.1 Service Agent (Angular)

### Fichier : `src/app/core/services/agent.service.ts`

- Injectable `providedIn: 'root'`
- Methode `chat(message: string): Observable<ChatResponse>`
  - POST `/api/agent/chat` avec body `{ message }`
- Methode `generatePicks(): Observable<PickResponse[]>`
  - POST `/api/agent/generate-picks`
- Methode `clearHistory(): Observable<void>`
  - DELETE `/api/agent/history`

### Fichier : `src/app/shared/models/agent.model.ts`

```typescript
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatResponse {
  message: string;
  suggestedMedia: MediaResponse[] | null;
}
```

---

## 8.2 Feature : ChatComponent

### Fichier : `src/app/features/chat/chat.component.ts`

- Standalone component
- Injecte `AgentService`
- Signal `messages` : `ChatMessage[]` (historique local de la conversation)
- Signal `inputMessage` : string (contenu du champ input)
- Signal `loading` : boolean (attente de reponse Claude)
- Signal `suggestedMedia` : `MediaResponse[] | null`
- `@ViewChild('messagesEnd')` : pour auto-scroll en bas

**Methode `sendMessage()` :**
1. Ajouter le message user a `messages`
2. Vider `inputMessage`
3. Set `loading = true`
4. Appeler `agentService.chat(message)`
5. Ajouter la reponse assistant a `messages`
6. Si `suggestedMedia` non null, les afficher
7. Set `loading = false`
8. Auto-scroll vers le bas

**Methode `generatePicks()` :**
1. Set `loading = true`
2. Appeler `agentService.generatePicks()`
3. Afficher un message de confirmation
4. Set `loading = false`

**Methode `clearHistory()` :**
1. Appeler `agentService.clearHistory()`
2. Vider `messages` localement

### Fichier : `src/app/features/chat/chat.component.html`

```html
<app-header />

<div class="min-h-screen bg-background pt-20 pb-24 px-4 max-w-3xl mx-auto">
  <h1 class="font-display text-3xl text-gradient-red mb-6">Ask the Agent</h1>

  <!-- Messages -->
  <div class="space-y-4 mb-4">
    @for (msg of messages(); track $index) {
      <div [class]="msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
        <div [class]="msg.role === 'user'
          ? 'bg-primary/20 rounded-2xl rounded-br-sm px-4 py-3 max-w-[80%]'
          : 'glass-card rounded-2xl rounded-bl-sm px-4 py-3 max-w-[80%]'">
          <p class="text-sm whitespace-pre-wrap">{{ msg.content }}</p>
        </div>
      </div>
    }

    <!-- Loading indicator -->
    @if (loading()) {
      <div class="flex justify-start">
        <div class="glass-card rounded-2xl px-4 py-3">
          <div class="flex gap-1">
            <span class="w-2 h-2 bg-muted-foreground rounded-full animate-bounce"></span>
            <span class="w-2 h-2 bg-muted-foreground rounded-full animate-bounce" style="animation-delay: 0.1s"></span>
            <span class="w-2 h-2 bg-muted-foreground rounded-full animate-bounce" style="animation-delay: 0.2s"></span>
          </div>
        </div>
      </div>
    }

    <div #messagesEnd></div>
  </div>

  <!-- Suggested media cards -->
  @if (suggestedMedia()) {
    <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3 mb-4">
      @for (media of suggestedMedia(); track media.id) {
        <div class="card-hover rounded-lg overflow-hidden">
          <img [src]="posterUrl(media.posterPath)" [alt]="media.title"
               class="w-full aspect-[2/3] object-cover" />
          <p class="text-xs p-1 truncate">{{ media.title }}</p>
        </div>
      }
    </div>
  }

  <!-- Input bar (fixe en bas) -->
  <div class="fixed bottom-0 left-0 right-0 bg-background/80 backdrop-blur-lg border-t border-border p-4">
    <div class="max-w-3xl mx-auto flex gap-2">
      <input [(ngModel)]="inputMessage"
             (keydown.enter)="sendMessage()"
             placeholder="What are you in the mood for?"
             class="flex-1 bg-card border border-border rounded-xl px-4 py-3 text-sm
                    focus:outline-none focus:ring-2 focus:ring-primary" />
      <button (click)="sendMessage()" [disabled]="loading() || !inputMessage()"
              class="bg-primary text-white rounded-xl px-6 py-3 text-sm font-semibold
                     hover:bg-primary/90 disabled:opacity-50">
        Send
      </button>
      <button (click)="generatePicks()" [disabled]="loading()"
              class="bg-accent text-white rounded-xl px-4 py-3 text-sm font-semibold
                     hover:bg-accent/90 disabled:opacity-50"
              title="Generate Picks">
        <!-- Sparkles icon SVG -->
      </button>
    </div>
  </div>
</div>
```

### Fichier : `src/app/features/chat/chat.component.css`

Vide (Tailwind suffit).

---

## 8.3 Ajouter le lien Chat dans la navigation

### Modifier : `src/app/shared/components/app-header/app-header.component.html`

Ajouter un 3e lien de navigation :
- Route : `/chat`
- Icone : MessageSquare (ou equivalent)
- Label : "Ask"

---

## 8.4 Connecter les Picks au chat agent

### Modifier : `src/app/features/picks/picks.component.ts`

Ajouter un bouton "Generate New Picks" qui appelle `agentService.generatePicks()`
puis recharge la liste des picks.

### Modifier : `src/app/features/picks/picks.component.html`

Ajouter le bouton dans le header de la page :

```html
<button (click)="generateNewPicks()"
        class="bg-primary text-white rounded-lg px-4 py-2 text-sm">
  Generate New Picks
</button>
```

---

## 8.5 Tests Phase 8

### Fichier : `src/app/features/chat/chat.component.spec.ts`

- **Test 1 :** Envoyer un message ajoute un message user dans la liste
- **Test 2 :** Recevoir une reponse ajoute un message assistant
- **Test 3 :** Le bouton Send est disabled quand loading=true
- **Test 4 :** Le bouton Send est disabled quand inputMessage est vide
- **Test 5 :** generatePicks() appelle le service agent

### Fichier : `src/app/core/services/agent.service.spec.ts`

- **Test 1 :** chat() appelle POST /api/agent/chat
- **Test 2 :** generatePicks() appelle POST /api/agent/generate-picks
- **Test 3 :** clearHistory() appelle DELETE /api/agent/history

### Execution :

```bash
ng test --watch=false
```

---

## 8.6 Tests end-to-end (scenario complet)

### Scenario de test manuel :

1. Demarrer PostgreSQL (`docker compose up -d`)
2. Demarrer le MCP server (`cd ask2watch-mcp-tmdb && node index.js`)
3. Demarrer le backend (`cd ask2watch-backend && mvn spring-boot:run`)
4. Demarrer le frontend (`cd ask2watch-frontend && ng serve`)
5. Ouvrir http://localhost:4200
6. Se connecter avec admin@ask2watch.com / admin
7. Verifier la Home : hero + rows avec posters
8. Aller sur Watched : onglets Movies/Series, recherche, clic detail
9. Aller sur Chat : envoyer "Je veux un thriller psychologique"
10. Verifier que Claude repond et utilise les tools TMDB
11. Demander "Genere mes picks de la semaine"
12. Aller sur Picks : verifier que les picks generes apparaissent

---

## 8.7 Validation Phase 8

- [ ] La page Chat affiche l'interface de conversation
- [ ] Envoyer un message retourne une reponse de Claude
- [ ] Claude utilise les tools MCP pour chercher des films pertinents
- [ ] Les suggestions de l'agent affichent les posters
- [ ] "Generate Picks" cree des picks en DB et les affiche sur /picks
- [ ] Le lien "Ask" apparait dans le header
- [ ] Le scenario E2E complet fonctionne du login aux picks
- [ ] Tous les tests Angular passent

---

## 8.8 Nettoyage final du projet

### Fichiers/Dossiers a SUPPRIMER :

| A supprimer | Raison |
|---|---|
| `cine-picks-main/` | App React source, remplacee par Angular |
| `watchedList/` | CSV d'origine, copies dans backend resources |

### Fichiers a AJOUTER :

| Fichier | Contenu |
|---|---|
| `.gitignore` (racine) | node_modules/, .env, target/, dist/, .angular/, *.class, pgdata/ |
| `.env.example` (racine) | Template des variables d'environnement requises |
| `README.md` (racine) | Instructions de lancement du projet |

### Contenu du `.env.example` :

```
TMDB_API_KEY=your_tmdb_api_key
ANTHROPIC_API_KEY=your_anthropic_api_key
JWT_SECRET=your_jwt_secret_at_least_32_chars
POSTGRES_DB=ask2watch
POSTGRES_USER=ask2watch
POSTGRES_PASSWORD=ask2watch
```

### Contenu du `README.md` racine :

Instructions de lancement :

```bash
# 1. Base de donnees
docker compose up -d

# 2. Backend (premier lancement : import CSV ~2 min)
cd ask2watch-backend
TMDB_API_KEY=xxx ANTHROPIC_API_KEY=xxx JWT_SECRET=xxx mvn spring-boot:run

# 3. MCP Server
cd ask2watch-mcp-tmdb
npm install
TMDB_API_KEY=xxx node index.js

# 4. Frontend
cd ask2watch-frontend
npm install
ng serve
```

### Verifications de proprete :

- [ ] Aucun `console.log` ou `System.out.println` de debug
- [ ] Aucune cle API en dur dans le code source
- [ ] Aucun fichier `node_modules/` committe
- [ ] Aucun fichier `.env` committe
- [ ] Aucune entite JPA retournee directement par un controller (toujours DTOs)
- [ ] Aucun composant Angular avec plus de 200 lignes
- [ ] Chaque composant a ses 3 fichiers separes (.ts, .html, .css)
- [ ] Aucune dependance inutilisee dans package.json ou pom.xml
- [ ] Les imports sont nets (pas d'imports non utilises)
