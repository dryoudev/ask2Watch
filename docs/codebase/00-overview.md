# ask2Watch - Vue d'ensemble

## 1. Ce que fait le projet

ask2Watch est une application de suivi cinema/series avec 4 briques principales :

1. `ask2watch-backend`
   API Spring Boot qui gere l'auth, la watchlist, les picks, les imports CSV, les recommandations et l'agent Dobby.
2. `ask2watch-frontend`
   SPA Angular qui consomme le backend et expose les pages Login, Home, Watched, Picks et Chat.
3. `ask2watch-mcp-tmdb`
   Serveur MCP Node.js utilise pour exposer des tools a Claude Code en mode terminal.
4. `.github/workflows/ci.yml`
   Pipeline GitHub Actions pour builder et tester backend/frontend.

## 2. Intention produit

Le coeur du produit est simple :

- un utilisateur s'authentifie
- il importe ou ajoute des films/series vus
- il note et commente ces titres
- le systeme s'appuie sur cet historique pour proposer :
  - des recommandations automatiques
  - des picks de la semaine
  - une conversation outillee avec Dobby

Le projet contient donc a la fois :

- de la logique CRUD classique
- de la logique de personnalisation
- de l'orchestration LLM/tool-use

## 3. Architecture logique

```text
Angular SPA
  -> appelle /api/*
Spring Boot API
  -> PostgreSQL pour persister users/media/watched/picks
  -> TMDB pour enrichissement/recherche/tendances
  -> Anthropic Claude pour le chat Dobby et la generation de picks

Claude Code / clients MCP
  -> MCP server Node.js
  -> ce serveur appelle le backend REST + TMDB
```

## 4. Domaines metier

Le projet tourne autour de 4 concepts.

### Utilisateur

- stocke identite, email, mot de passe hash
- porte l'ensemble des donnees metier

### Media

- represente un film ou une serie
- sert de reference commune pour watched et picks
- peut etre enrichi depuis TMDB

### Watched

- lien `User <-> Media`
- porte les metadonnees personnelles :
  - note utilisateur
  - date de visionnage
  - commentaire

### Pick of the Week

- lien `User <-> Media` pour une semaine donnee
- permet de materialiser une selection courante

## 5. Flux metiers principaux

### Auth

1. le frontend envoie login/register
2. le backend valide et genere un JWT
3. le frontend stocke le token en `localStorage`
4. l'interceptor Angular le renvoie sur les requetes suivantes

### Ajouter aux vus

1. le frontend envoie `POST /api/media/watched`
2. le backend rattache un `Media` existant ou le cree
3. le backend cree une ligne `user_watched`
4. un `PUT /api/media/watched/{id}` peut ensuite enrichir note/commentaire

### Picks

1. un pick peut etre ajoute manuellement via `POST /api/picks`
2. il peut etre supprime via `DELETE /api/picks/{id}`
3. il peut aussi etre genere par l'agent via l'orchestration Claude + tools

### Chat Dobby

1. le frontend appelle `POST /api/agent/chat`
2. le backend construit un prompt systeme statique
3. Claude choisit des tools
4. le backend execute ces tools localement
5. le resultat est reinjecte dans la conversation
6. Claude repond au format texte final

## 6. Ce qu'il faut retenir

L'application n'est pas "juste un CRUD films".

Elle combine :

- une base de donnees utilisateur
- un enrichissement externe via TMDB
- une personnalisation simple via notes/commentaires
- une couche agentique avec tool calling
- un mode MCP distinct pour Claude Code

La suite de la documentation detaille chaque partie.
