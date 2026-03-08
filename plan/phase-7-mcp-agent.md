# Phase 7 : Agent Claude + MCP Server TMDB

## Architecture

```
Angular Chat UI
      |
      v
Spring Boot API  (POST /api/agent/chat)
      |
      v
AgentService  (Anthropic Java SDK)
      |
      v (MCP tool calls)
TMDB MCP Server  (Node.js, expose des tools TMDB)
```

L'agent Claude recoit le contexte de l'utilisateur (films vus, notes, commentaires)
et utilise les tools MCP pour chercher des films/series sur TMDB.

---

## 7.1 TMDB MCP Server (Node.js)

### Dossier : `ask2watch-mcp-tmdb/`

Petit serveur MCP qui expose des outils TMDB a Claude.

### Fichier : `ask2watch-mcp-tmdb/package.json`

```json
{
  "name": "ask2watch-mcp-tmdb",
  "version": "1.0.0",
  "type": "module",
  "dependencies": {
    "@modelcontextprotocol/sdk": "latest"
  }
}
```

### Fichier : `ask2watch-mcp-tmdb/index.js`

Serveur MCP stdio qui expose 5 outils :

**Tool 1 : `search_movie`**
- Params : `query` (string), `year` (number, optionnel)
- Appel : `GET /search/movie?query={query}&year={year}&api_key=XXX`
- Retourne : top 5 resultats avec id, title, poster_path, overview, release_date, vote_average

**Tool 2 : `search_tv`**
- Params : `query` (string), `year` (number, optionnel)
- Appel : `GET /search/tv?query={query}&first_air_date_year={year}&api_key=XXX`
- Retourne : top 5 resultats avec id, name, poster_path, overview, first_air_date, vote_average

**Tool 3 : `get_trending`**
- Params : `media_type` (movie | tv | all), `time_window` (day | week)
- Appel : `GET /trending/{media_type}/{time_window}?api_key=XXX`
- Retourne : top 10 trending avec id, title/name, poster_path, overview, vote_average

**Tool 4 : `get_recommendations`**
- Params : `tmdb_id` (number), `media_type` (movie | tv)
- Appel : `GET /{media_type}/{tmdb_id}/recommendations?api_key=XXX`
- Retourne : top 10 recommandations similaires

**Tool 5 : `discover`**
- Params : `media_type` (movie | tv), `genres` (string, ids separes par virgule), `year_min` (number), `year_max` (number), `rating_min` (number)
- Appel : `GET /discover/{media_type}?with_genres={genres}&primary_release_date.gte={year_min}&vote_average.gte={rating_min}&api_key=XXX`
- Retourne : top 10 resultats filtres

Chaque outil retourne un JSON formatee lisible par Claude.

### Fichier : `ask2watch-mcp-tmdb/.env`

```
TMDB_API_KEY=2d6be03989ed15b3acafc07caa6b5260
```

**Ne pas committer ce fichier.** Ajouter `.env` au `.gitignore`.

### Fichier : `ask2watch-mcp-tmdb/.env.example`

```
TMDB_API_KEY=your_tmdb_api_key_here
```

---

## 7.2 Dependances Backend pour l'agent

### Modifier : `ask2watch-backend/pom.xml`

Ajouter la dependance Anthropic Java SDK :

```xml
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java</artifactId>
  <version>LATEST</version>
</dependency>
```

---

## 7.3 Configuration Agent

### Fichier : `src/main/java/com/ask2watch/config/AgentConfig.java`

- `@Configuration`
- Lire `anthropic.api-key` depuis application.yml via `@Value`
- Exposer un bean `AnthropicClient` preconfigure
- Configurer le model : `claude-sonnet-4-6` (bon ratio performance/cout)

### Modifier : `src/main/resources/application.yml`

Ajouter :

```yaml
anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

mcp:
  tmdb-server:
    command: node
    args: ["../ask2watch-mcp-tmdb/index.js"]
```

---

## 7.4 DTO Agent

### Fichier : `src/main/java/com/ask2watch/dto/agent/ChatMessage.java`

- `String role` (user | assistant)
- `String content`

### Fichier : `src/main/java/com/ask2watch/dto/agent/ChatRequest.java`

- `String message`

### Fichier : `src/main/java/com/ask2watch/dto/agent/ChatResponse.java`

- `String message`
- `List<MediaResponse> suggestedMedia` (nullable, present quand l'agent suggere des picks)

### Fichier : `src/main/java/com/ask2watch/dto/agent/GeneratePicksRequest.java`

- `Long userId`

---

## 7.5 Service Agent

### Fichier : `src/main/java/com/ask2watch/service/AgentService.java`

**Responsabilite :** Orchestre la conversation avec Claude pour generer des recommandations.

**Champs :**
- `AnthropicClient client`
- `MediaService mediaService`
- `PickService pickService`
- `Map<Long, List<ChatMessage>> conversationHistory` (par userId, en memoire)

**Methode : `buildSystemPrompt(Long userId)`**

Construit le system prompt contenant :
- Role : "Tu es un expert en cinema et series TV. Tu aides l'utilisateur a choisir quoi regarder."
- Contexte : liste des films/series vus par l'utilisateur avec titres, genres, notes, commentaires
- Instructions : poser 2-3 questions pour comprendre l'humeur, puis suggerer 5-6 titres avec raisons
- Format de sortie : quand tu fais des suggestions finales, les formater en JSON parseable

**Methode : `chat(Long userId, String userMessage)`**

1. Recuperer l'historique de conversation pour cet utilisateur
2. Ajouter le message utilisateur
3. Construire le system prompt avec le contexte utilisateur
4. Appeler Claude avec les messages + MCP tools disponibles
5. Si Claude utilise un outil MCP -> executer l'outil, renvoyer le resultat a Claude
6. Recuperer la reponse finale
7. Parser les suggestions si presentes (JSON dans la reponse)
8. Ajouter la reponse a l'historique
9. Retourner `ChatResponse`

**Methode : `generatePicks(Long userId)`**

1. Construire un prompt direct : "Analyse mes films vus et genere les 6 picks de la semaine"
2. Appeler Claude avec les tools MCP
3. Parser les suggestions retournees
4. Pour chaque suggestion : chercher/creer le media en DB via TMDB
5. Creer les entrees `picks_of_week`
6. Retourner la liste des picks

**Methode : `clearHistory(Long userId)`**

Vider l'historique de conversation pour repartir de zero.

---

## 7.6 Controller Agent

### Fichier : `src/main/java/com/ask2watch/controller/AgentController.java`

- `@RestController` + `@RequestMapping("/api/agent")`

**Endpoints :**

- `POST /api/agent/chat` : envoie un message, recoit la reponse de Claude
  - Body : `ChatRequest`
  - Retour : `ChatResponse`
  - Recupere le userId depuis le JWT

- `POST /api/agent/generate-picks` : genere les picks de la semaine
  - Retour : `List<PickResponse>`

- `DELETE /api/agent/history` : vide l'historique de conversation
  - Retour : 204 No Content

---

## 7.7 Tests Phase 7

### Fichier : `ask2watch-mcp-tmdb/test.js`

Script de test du MCP server :
- **Test 1 :** `search_movie("Inception")` retourne des resultats avec "Inception" dans le titre
- **Test 2 :** `get_trending("movie", "week")` retourne au moins 5 resultats
- **Test 3 :** `discover("movie", "28", null, null, 7.0)` retourne des films d'action notes > 7

### Fichier : `src/test/java/com/ask2watch/service/AgentServiceTest.java`

- **Test 1 :** `buildSystemPrompt` inclut les films vus de l'utilisateur
- **Test 2 :** `chat` retourne une reponse non vide
- **Test 3 :** `generatePicks` cree des entrees dans picks_of_week (mock Claude)

### Execution :

```bash
# Test MCP server
cd ask2watch-mcp-tmdb
node test.js

# Test backend
cd ask2watch-backend
mvn test -Dtest="AgentServiceTest"
```

---

## 7.8 Validation Phase 7

- [ ] Le MCP server demarre sans erreur
- [ ] Les 5 tools retournent des resultats valides
- [ ] `POST /api/agent/chat` retourne une reponse conversationnelle
- [ ] Claude utilise les tools MCP pour chercher des films
- [ ] `POST /api/agent/generate-picks` cree des picks en DB
- [ ] Le system prompt contient bien les films vus de l'utilisateur
- [ ] Les picks generes ne sont pas des films deja vus

---

## 7.9 Nettoyage

- S'assurer que `ANTHROPIC_API_KEY` n'est pas en dur
- Ajouter `ask2watch-mcp-tmdb/node_modules/` au `.gitignore`
- Ajouter `ask2watch-mcp-tmdb/.env` au `.gitignore`
- Supprimer les logs verbeux des appels Claude (garder seulement les erreurs)
- Limiter la taille de l'historique de conversation (max 20 messages par user)
