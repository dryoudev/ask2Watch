# CI, secrets et exploitation

## 1. Workflow GitHub

Le pipeline principal est [ci.yml](/Users/dr.youd/Desktop/Atexo/ask2Watch/.github/workflows/ci.yml).

Il se declenche sur :

- `push` sur `main`
- `pull_request` vers `main`

## 2. Jobs du workflow

### `backend`

Actions :

1. checkout
2. JDK 21
3. PostgreSQL de service
4. `mvn clean verify -q`

Variables injectees au build backend :

- `DB_USERNAME`
- `DB_PASSWORD`
- `TMDB_API_KEY`
- `JWT_SECRET`
- `ANTHROPIC_API_KEY`
- `DEFAULT_ADMIN_PASSWORD`
- `CORS_ALLOWED_ORIGINS`

Source :

- `secrets.*` pour les secrets
- `vars.CORS_ALLOWED_ORIGINS` pour une variable non sensible

### `frontend`

Actions :

1. checkout
2. Node 20
3. `npm ci`
4. `npm run build`

### `mcp-server`

Actions :

1. checkout
2. Node 20
3. `npm ci`

Note :

- il n'y a pas encore de vrai lint/test Node dans ce job
- le nom "Lint" est un peu plus ambitieux que ce qu'il execute reellement

## 3. Secrets applicatifs

### Backend

Decrits par [.env.example](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/.env.example) :

- `DB_USERNAME`
- `DB_PASSWORD`
- `TMDB_API_KEY`
- `JWT_SECRET`
- `ANTHROPIC_API_KEY`
- `DEFAULT_ADMIN_PASSWORD`
- `CORS_ALLOWED_ORIGINS`

Usage :

- datasource PostgreSQL
- signature JWT
- appels TMDB
- appels Anthropic
- mot de passe admin initial

### MCP server

Decrits par [ask2watch-mcp-tmdb/.env.example](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-mcp-tmdb/.env.example) :

- `TMDB_API_KEY`
- `MCP_AUTH_EMAIL`
- `MCP_AUTH_PASSWORD`
- `BACKEND_URL`

Interpretation :

- le serveur MCP agit comme un client applicatif
- il doit s'authentifier contre le backend
- il doit connaitre l'URL du backend

## 4. Separation entre secrets GitHub et secrets locaux

Le projet utilise plusieurs canaux de configuration.

### GitHub Actions

Utilise :

- `secrets.*`
- `vars.*`

Ces valeurs sont censees rester dans GitHub.

### `.env`

Utilise pour les executions locales backend et MCP.

Le package `spring-dotenv` cote backend permet de charger ces variables localement.

### Fichiers locaux MCP versionnes

- `.mcp.json`
- `.claude.json`

Ils contiennent actuellement une configuration sensible en clair.
Ce point doit etre documente comme dette de securite.

## 5. Demarrage local

### Backend

Pre-requis :

- PostgreSQL actif
- `.env` rempli

Commande :

```bash
cd ask2watch-backend
./mvnw spring-boot:run
```

### Frontend

Commande :

```bash
cd ask2watch-frontend
npm install
npm start
```

Le frontend utilise un proxy Angular vers `localhost:8080`.

### MCP server

Commande type :

```bash
cd ask2watch-mcp-tmdb
npm install
node index.js
```

## 6. Observabilite et logs

Aujourd'hui, l'observabilite repose surtout sur :

- logs Spring Boot
- logs d'audit applicatifs
- logs serveur MCP

Il n'y a pas de :

- dashboard centralise
- tracing distribue
- stockage structure des audits en base

## 7. Dette technique / vigilance ops

### Secrets exposes

La plus visible.

### Pipeline MCP minimal

Le job GitHub du MCP installe juste les deps.
Il ne valide pas reellement le comportement.

### Dependances runtime externes

Le backend depend de :

- PostgreSQL
- TMDB
- Anthropic

En cas de panne externe, certaines fonctions produit degradent fortement.

### Cle JWT

Le secret JWT doit etre suffisamment long.
Le `.env.example` rappelle de generer une cle 256 bits.
