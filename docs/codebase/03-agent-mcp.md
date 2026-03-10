# Agent Dobby et orchestration MCP

## 1. Il y a deux mondes a distinguer

Le projet contient deux mecanismes proches mais differents.

### A. Agent Dobby integre au backend

Code principal :

- [AgentService.java](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/service/AgentService.java)

But :

- alimenter le chat applicatif et la generation de picks depuis le backend
- utiliser Claude via Anthropic API
- exposer des tools executes localement par le backend lui-meme

### B. Serveur MCP Node.js

Code principal :

- [index.js](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-mcp-tmdb/index.js)

But :

- permettre a Claude Code d'utiliser ask2Watch comme serveur MCP externe
- exposer des tools a un client compatible MCP

Conclusion :

- dans l'app web, on n'utilise pas un "vrai serveur MCP" en runtime
- on reproduit la meme idee de tool-use directement dans le backend Java
- pour Claude Code CLI, on passe par le serveur MCP Node.js

## 2. Comment fonctionne l'agent backend

### Prompt systeme

Le prompt systeme est statique et impose :

- persona Dobby
- style de reponse
- limites sur spoilers et volume de recommandations
- obligation d'utiliser les tools pour consulter watched/picks

Important :

- la watchlist complete n'est plus injectee dans le prompt
- l'agent doit charger les donnees a la demande via tools

Gain :

- moins de tokens
- logique plus scalable

### Historique de conversation

Le backend garde un cache Caffeine :

- cle : `userId`
- valeur : `List<ChatMessage>`
- TTL : 1 heure
- taille max : 1000 conversations

Effet :

- les resultats de tools restent dans l'historique
- Claude peut reutiliser le contexte sans recharger certaines infos a chaque tour

### Boucle tool calling

Le flux dans `AgentService` est :

1. construire la requete Anthropic
2. envoyer `system + messages + tools`
3. lire la reponse
4. si `stop_reason == tool_use`
   - extraire les appels tools
   - executer chaque tool cote backend
   - reinjecter les `tool_result`
   - rappeler Claude
5. sinon concatener le texte final

L'orchestration est donc recursive.

## 3. Tools backend disponibles

### Tools TMDB

- `search_movie`
- `search_tv`
- `get_trending`
- `get_recommendations`
- `discover`

Ils s'appuient sur des appels HTTP directs vers TMDB.

### Tools CRUD / consultation app

- `add_pick`
- `add_to_watched`
- `rate_watched`
- `comment_watched`
- `remove_from_watched`
- `remove_pick`
- `get_watched_movies`
- `get_watched_series`
- `search_watched`
- `get_current_picks`

Ces tools ne passent pas par MCP.
Ils appellent directement les services Java internes.

## 4. Generation de picks via l'agent

Le bouton "Generate Picks" du frontend appelle :

- `POST /api/agent/generate-picks`

Le backend envoie alors a Claude une instruction specialisee :

- analyser les films/séries vus
- utiliser notes et commentaires
- verifier les picks actuels
- eviter deja vu + deja picke
- ajouter 3 a 5 picks max

Claude doit ensuite utiliser les tools, notamment :

- `get_watched_movies`
- `get_watched_series`
- `get_current_picks`
- tools TMDB
- `add_pick`

Le backend retourne enfin les picks courants apres execution.

## 5. Comment fonctionne le serveur MCP Node.js

Le serveur [ask2watch-mcp-tmdb/index.js](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-mcp-tmdb/index.js) :

- expose un serveur MCP en stdio
- declare des tools
- appelle :
  - le backend REST ask2Watch
  - TMDB directement

Exemples de tools exposes par le serveur MCP :

- `get_watched_movies`
- `get_watched_series`
- `get_current_picks`
- `search_movie`
- `discover`
- `add_pick`
- `remove_pick`
- `generate_picks`

Le tool `generate_picks` du serveur MCP est different de l'endpoint backend :

- il ne genere pas lui-meme les picks en base
- il retourne surtout une synthese structuree des gouts + tendances

## 6. Config locale MCP

Fichiers repérés :

- [.mcp.json](/Users/dr.youd/Desktop/Atexo/ask2Watch/.mcp.json)
- [.claude.json](/Users/dr.youd/Desktop/Atexo/ask2Watch/.claude.json)
- [test-mcp.js](/Users/dr.youd/Desktop/Atexo/ask2Watch/test-mcp.js)

Role :

- enregistrer le serveur MCP pour Claude/clients locaux
- pointer vers `ask2watch-mcp-tmdb/index.js`
- fournir certaines variables d'environnement

## 7. Point de vigilance securite

Dans l'etat actuel du repo, `.mcp.json` et `.claude.json` contiennent une valeur TMDB API key en clair.

C'est important a comprendre :

- ce n'est pas la meme chose que les secrets GitHub Actions
- ce sont des configs locales versionnees dans le repo
- c'est un risque de fuite de secret si le repo est partage

Recommendation :

- sortir ces valeurs des fichiers commits
- utiliser `.env` non versionne ou variables shell locales

## 8. Resume conceptuel

Le projet melange donc trois couches d'intelligence :

1. recommandations deterministes simples
   `TmdbRecommendationProvider`
2. agent backend outille
   `AgentService`
3. serveur MCP externe pour Claude Code
   `ask2watch-mcp-tmdb/index.js`

Ces couches sont proches dans l'intention, mais pas identiques dans leur execution.
