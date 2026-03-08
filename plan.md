# Plan ask2Watch - Résolution des problèmes

## ✅ BLOC 1-10 : Correctifs Frontend & Backend (COMPLÉTÉ)
Voir détails dans realisation.md

---

## 🔄 TÂCHE EN COURS : Importer infos TMDB complètes dans les picks

### Problème
Quand on ajoute un pick, seulement `tmdb_id`, `media_type`, `title`, `reason` sont stockés.
Les infos TMDB (poster, genres, rating, synopsis, etc.) ne sont **pas importées** dans la BD.

### Solution
1. ✅ **Ajouter nouvel outil MCP** : `get_movie_details(tmdb_id, media_type)`
   - Récupère détails complets depuis TMDB API
   - Retourne: title, year, poster_path, overview, vote_average, genres, directors, stars

2. ⏳ **Modifier endpoint POST /api/picks**
   - Quand on crée un pick, appeler le nouvel outil MCP
   - Importer les infos complètes dans la table `media`
   - Associer le pick au film avec toutes les infos

### Étapes d'exécution
- [ ] Étape 1 : Ajouter `get_movie_details` dans ask2watch-mcp-tmdb/index.js
- [ ] Étape 2 : Modifier le backend POST /api/picks pour importer les infos
- [ ] Étape 3 : Tester l'ajout d'un pick et vérifier les infos
- [ ] Étape 4 : Documenter le changement

