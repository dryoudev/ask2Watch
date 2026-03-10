# Risques, limites actuelles et pistes de lecture

## 1. Risques identifies dans la base actuelle

### Secrets en clair dans des fichiers versionnes

Fichiers concernes :

- [.mcp.json](/Users/dr.youd/Desktop/Atexo/ask2Watch/.mcp.json)
- [.claude.json](/Users/dr.youd/Desktop/Atexo/ask2Watch/.claude.json)

Impact :

- fuite potentielle de cle TMDB
- confusion entre config locale et secret manager

### Audit logs seulement en logs texte

Conséquence :

- peu pratique pour analyse historique
- pas de requetage structure

### Agent backend et MCP paralleles

Il y a une duplication conceptuelle :

- outils backend Java
- outils MCP Node.js

Impact :

- maintenance double
- risque de divergence fonctionnelle

### Recommendation classique limitee

`TmdbRecommendationProvider` repose surtout sur les genres des films les mieux notes.

Il ne prend pas fortement en compte :

- commentaires
- picks deja faits historiquement
- melange fin film/serie

### Cohabitation de plusieurs modes de "generation"

Il existe :

- recommandations deterministes
- picks generes par agent
- outils MCP `generate_picks`

Pour un nouveau lecteur, cela peut sembler redondant.

## 2. Comment lire le projet efficacement

Ordre conseille si tu veux vraiment comprendre :

1. [00-overview.md](/Users/dr.youd/Desktop/Atexo/ask2Watch/docs/codebase/00-overview.md)
2. [01-backend-metier.md](/Users/dr.youd/Desktop/Atexo/ask2Watch/docs/codebase/01-backend-metier.md)
3. [02-frontend.md](/Users/dr.youd/Desktop/Atexo/ask2Watch/docs/codebase/02-frontend.md)
4. [03-agent-mcp.md](/Users/dr.youd/Desktop/Atexo/ask2Watch/docs/codebase/03-agent-mcp.md)
5. [04-ci-secrets-ops.md](/Users/dr.youd/Desktop/Atexo/ask2Watch/docs/codebase/04-ci-secrets-ops.md)

Puis dans le code :

1. [SecurityConfig.java](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/config/SecurityConfig.java)
2. [MediaService.java](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/service/MediaService.java)
3. [PickService.java](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/service/PickService.java)
4. [AgentService.java](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/service/AgentService.java)
5. [index.js](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-mcp-tmdb/index.js)
6. [app.routes.ts](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-frontend/src/app/app.routes.ts)
7. [watched.component.ts](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-frontend/src/app/features/watched/watched.component.ts)
8. [picks.component.ts](/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-frontend/src/app/features/picks/picks.component.ts)

## 3. Pistes d'amelioration si tu veux faire evoluer le projet

### Rationaliser agent backend et serveur MCP

Deux directions possibles :

- tout basculer vers un backend unique et garder MCP uniquement comme adaptateur
- ou factoriser une couche commune de definitions de tools

### Renforcer la couche picks

Par exemple :

- persister la raison du pick
- garder une trace des generations successives
- distinguer plus clairement picks humains vs picks agent

### Renforcer la couche securite

- retirer toutes les cles versionnees
- stocker les audits en base
- mieux separer variables locales et CI

### Documenter l'architecture cible

Le repo contient deja des fichiers `plan/`.
Ils sont utiles pour reconstruire l'historique de conception.

## 4. Conclusion

Le projet est deja assez riche.

Sa complexite ne vient pas tant du volume de code que du croisement entre :

- CRUD classique
- enrichissement externe TMDB
- personnalisation utilisateur
- orchestration agentique
- integration MCP
- considerations de securite et CI

La documentation locale ajoutee dans ce dossier vise justement a rendre ce croisement lisible.
