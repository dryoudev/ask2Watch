# Phase 9 - Plan detaille : enum SQL media_type, stabilisation tests, import CSV manuel, upload front

## Objectif

Cette phase a 4 objectifs techniques lies entre eux :

1. Verrouiller `media.media_type` au niveau PostgreSQL avec un vrai enum SQL.
2. Stabiliser les tests backend pour que `./mvnw test` soit fiable et independant de la configuration locale.
3. Decoupler l'import CSV du demarrage applicatif et introduire un import manuel HTTP.
4. Ajouter dans le frontend un bouton permettant d'uploader un CSV de films ou de series regardes.

Contrainte metier supplementaire :

- Lors de l'import CSV des series, les valeurs source `TV Series` et `TV Mini Series` doivent etre mappees vers la valeur interne unique `SERIES`.

Ce document ne met rien en place. Il decrit precisement quoi modifier, creer, tester, valider et nettoyer.

---

## Regles de mise en oeuvre

- Ne pas changer plusieurs choses a la fois sans validation intermediaire.
- Chaque etape doit produire un etat compilable ou testable.
- Toute suppression de fichier ou de code mort doit etre faite explicitement, pas laissee "pour plus tard".
- Les changements de schema doivent etre testes avec PostgreSQL reel via Testcontainers.
- L'import manuel CSV doit etre pense pour un utilisateur authentifie, pas pour le seed global historique.

---

## Vue d'ensemble des fichiers concernes

### Backend - fichiers a modifier

- `ask2watch-backend/src/main/resources/schema.sql`
- `ask2watch-backend/src/main/resources/application.yml`
- `ask2watch-backend/src/main/java/com/ask2watch/model/Media.java`
- `ask2watch-backend/src/main/java/com/ask2watch/model/MediaType.java`
- `ask2watch-backend/src/main/java/com/ask2watch/config/DataInitializer.java`
- `ask2watch-backend/src/main/java/com/ask2watch/service/CsvImportService.java`
- `ask2watch-backend/src/main/java/com/ask2watch/service/MediaService.java`
- `ask2watch-backend/src/main/java/com/ask2watch/controller/MediaController.java`
- `ask2watch-backend/src/main/java/com/ask2watch/dto/csv/CsvMediaRow.java`
- `ask2watch-backend/src/main/java/com/ask2watch/repository/MediaRepository.java`
- `ask2watch-backend/src/test/resources/application-test.yml`
- `ask2watch-backend/src/test/java/com/ask2watch/Ask2watchBackendApplicationTests.java`
- `ask2watch-backend/src/test/java/com/ask2watch/AbstractIntegrationTest.java`
- `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaControllerIT.java`

### Backend - fichiers a creer

- `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportResponse.java`
- `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportError.java`
- `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportSummary.java`
- `ask2watch-backend/src/main/java/com/ask2watch/service/UserCsvImportService.java`
- `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaCsvImportIT.java`
- `ask2watch-backend/src/test/java/com/ask2watch/repository/MediaTypePersistenceIT.java`

### Frontend - fichiers a modifier

- `ask2watch-frontend/src/app/core/services/media.service.ts`
- `ask2watch-frontend/src/app/features/watched/watched.component.ts`
- `ask2watch-frontend/src/app/features/watched/watched.component.html`
- `ask2watch-frontend/src/app/features/watched/watched.component.css`
- `ask2watch-frontend/src/app/shared/models/media.model.ts`

### Frontend - fichiers a creer

- `ask2watch-frontend/src/app/shared/models/csv-import.model.ts`
- `ask2watch-frontend/src/app/features/watched/watched.component.spec.ts`
- `ask2watch-frontend/src/app/core/services/media.service.spec.ts`

### Fichiers a nettoyer

- `.DS_Store` a la racine et dans `ask2watch-backend/src/**`
- eventuels anciens tests ou helpers devenus obsoletes apres refacto import
- code mort dans `CsvImportService` une fois les responsabilites separees
- sections de documentation devenues fausses dans `plan/README.md` et `plan/PROGRESS.md`

---

## Etape 1 - Verrouiller `media_type` avec un enum SQL PostgreSQL

### 1.1 Modifier `ask2watch-backend/src/main/resources/schema.sql`

#### Ce qu'il faut faire

1. Ajouter la creation du type PostgreSQL avant la creation de la table `media`.
2. Utiliser une creation idempotente si possible, compatible avec un rerun de `schema.sql`.
3. Remplacer la definition actuelle de colonne :
   - supprimer `VARCHAR(10) NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES'))`
   - ajouter `media_type media_type_enum NOT NULL`

#### Forme cible

- Le fichier doit contenir une section dediee a l'enum SQL.
- La table `media` doit consommer cet enum.
- Le schema doit rester lisible et ordonne : types, puis tables.

#### Nettoyage precis a faire dans ce fichier

- Supprimer uniquement la contrainte `CHECK` sur `media_type`.
- Ne pas toucher aux autres contraintes si elles sont correctes.

### 1.2 Modifier `ask2watch-backend/src/main/java/com/ask2watch/model/Media.java`

#### Ce qu'il faut faire

1. Verifier que le champ `mediaType` est bien annote avec `@Enumerated(EnumType.STRING)`.
2. Si la colonne JPA n'est pas explicite, ajouter une annotation `@Column(name = "media_type", nullable = false, columnDefinition = "media_type_enum")`.
3. Ne pas convertir l'entite vers un enum ordinal.

#### But

- Faire comprendre a Hibernate que la colonne est stockee sous forme texte cote Java, mais dans un type PostgreSQL strict cote base.

#### Nettoyage precis a faire dans ce fichier

- Supprimer toute annotation ou commentaire qui ferait croire que `media_type` est un `VARCHAR`.

### 1.3 Verifier `ask2watch-backend/src/main/java/com/ask2watch/model/MediaType.java`

#### Ce qu'il faut faire

1. Confirmer que seules deux valeurs existent :
   - `MOVIE`
   - `SERIES`
2. Ne pas ajouter `TV_SERIES`, `TV_MINI_SERIES` ou une autre variante.

#### But

- Toute la variabilite source CSV doit etre normalisee avant persistence.

#### Nettoyage precis a faire dans ce fichier

- Supprimer toute valeur legacy ou inutile si elle existe.

### 1.4 Creer `ask2watch-backend/src/test/java/com/ask2watch/repository/MediaTypePersistenceIT.java`

#### Ce qu'il faut tester

1. Persister un `Media` avec `MediaType.MOVIE` puis le relire.
2. Persister un `Media` avec `MediaType.SERIES` puis le relire.
3. Verifier que la lecture restitue bien le meme enum Java.

#### Ce qu'il ne faut pas faire

- Ne pas tester une insertion SQL invalide via JPA. Si tu veux tester une valeur invalide brute, fais-le via `JdbcTemplate` avec une assertion d'erreur SQL explicite.

#### Validation attendue

- Le test prouve que le mapping JPA <-> enum SQL PostgreSQL est fonctionnel.

---

## Etape 2 - Stabiliser les tests backend

### 2.1 Modifier `ask2watch-backend/src/test/java/com/ask2watch/Ask2watchBackendApplicationTests.java`

#### Problematique actuelle

Le test charge le contexte avec `@SpringBootTest` sans profil `test`, donc il tente d'utiliser la config de `application.yml` au lieu de la config de test.

#### Ce qu'il faut faire

Option recommandee :

1. Supprimer ce fichier si sa valeur est nulle par rapport aux tests d'integration existants.

Option acceptable :

1. Le faire heriter du meme mode de test que les autres tests.
2. Ajouter `@ActiveProfiles("test")`.
3. S'assurer qu'il ne contourne pas `AbstractIntegrationTest`.

#### Nettoyage precis

- Si suppression :
  - supprimer entierement le fichier
  - ne pas le remplacer par un duplicat du setup d'integration

### 2.2 Modifier `ask2watch-backend/src/test/java/com/ask2watch/AbstractIntegrationTest.java`

#### Ce qu'il faut faire

1. Conserver Testcontainers PostgreSQL.
2. Verifier que les proprietes injectees couvrent bien :
   - URL
   - username
   - password
3. Corriger le nettoyage SQL dans `clearDatabase()`.

#### Correction obligatoire

La table du schema est `picks_of_week`, mais le helper tente `TRUNCATE TABLE pick_of_week`.

#### Actions detaillees

1. Remplacer `TRUNCATE TABLE pick_of_week CASCADE` par `TRUNCATE TABLE picks_of_week CASCADE`.
2. Garder l'ordre de purge inverse des dependances.
3. Si le helper devient fragile, remplacer le bloc `try/catch` repetitif par une methode utilitaire privee du type `truncateIfExists(String tableName)`.

#### Nettoyage precis

- Supprimer l'ancien nom de table fautif.
- Supprimer les commentaires faux ou ambigus si la methode evolue.

### 2.3 Modifier `ask2watch-backend/src/test/resources/application-test.yml`

#### Ce qu'il faut faire

1. Verifier que le profil test n'active pas l'import CSV global.
2. Verifier que `spring.sql.init.mode` est coherent avec le schema enum SQL.
3. Garder des secrets de test factices.

#### Point de vigilance

- Si `ddl-auto: create-drop` entre en conflit avec `schema.sql`, il faudra choisir une seule source de verite pour les tests.
- Recommandation pour cette phase :
  - garder `spring.sql.init.mode: always`
  - passer `ddl-auto` a `validate` si le schema SQL doit rester la source de verite

#### Nettoyage precis

- Supprimer toute configuration test redondante ou contradictoire avec le schema reel.

### 2.4 Modifier `ask2watch-backend/src/main/resources/application.yml`

#### Ce qu'il faut faire

1. Verifier que la config par defaut de l'app reste exploitable.
2. Ne pas laisser les tests dependre implicitement de ce fichier.

#### Nettoyage precis

- Ne pas ajouter de fallback "test" dans ce fichier.
- Garder ce fichier reserve au runtime applicatif normal.

### 2.5 Executer et valider les tests backend existants

#### Commandes de validation

```bash
cd ask2watch-backend
./mvnw test
./mvnw test -Dtest=MediaTypePersistenceIT
./mvnw test -Dtest=MediaControllerIT
```

#### Critere de validation

- `./mvnw test` doit passer sans PostgreSQL local externe.
- Aucun test ne doit tenter de parler a `localhost:5432` hors container de test.

---

## Etape 3 - Decoupler l'import CSV du demarrage

### 3.1 Modifier `ask2watch-backend/src/main/java/com/ask2watch/config/DataInitializer.java`

#### Ce qu'il faut faire

1. Rendre l'import CSV opt-in uniquement.
2. Enlever `matchIfMissing = true`.
3. Garder la possibilite d'activer le seed explicitement par propriete.

#### Forme cible

- L'import de seed ne doit plus tourner si la propriete n'est pas definie explicitement.

#### Nettoyage precis

- Supprimer le comportement implicite "si rien n'est configure, on importe tout".

### 3.2 Modifier `ask2watch-backend/src/main/java/com/ask2watch/service/CsvImportService.java`

#### Problematique actuelle

Le service melange :

- lecture CSV seed
- filtrage de type source
- enrichissement TMDB
- creation user admin
- liaison complete des medias au user admin
- throttling artificiel avec `Thread.sleep(250)`

#### Refacto recommandee

Ce fichier doit etre reduit a un role explicite de seed applicatif initial, ou etre remplace par deux services separes.

#### Option retenue pour ce plan

1. Garder `CsvImportService` pour le seed historique au besoin.
2. Lui enlever la responsabilite du futur import utilisateur manuel.
3. Extraire le nouvel import utilisateur dans un service dedie.

#### Actions detaillees dans ce fichier

1. Introduire une methode privee de normalisation du type source CSV.
2. Pour le seed series :
   - `TV Series` => `SERIES`
   - `TV Mini Series` => `SERIES`
3. Continuer a ignorer `Video`.
4. Documenter explicitement quoi faire pour les autres types non supportes :
   - soit les ignorer
   - soit logguer et skipper
5. Supprimer `Thread.sleep(250)`.
6. Isoler si possible la creation du user admin dans une methode bien delimitee.

#### Nettoyage precis

- Supprimer le `Thread.sleep(250)`.
- Supprimer toute logique qui sera dupliquee par le futur `UserCsvImportService`.
- Supprimer les commentaires devenus faux si le service n'est plus le point d'entree principal d'import.

### 3.3 Creer `ask2watch-backend/src/main/java/com/ask2watch/service/UserCsvImportService.java`

#### Role du fichier

Service metier dedie a l'import d'un CSV envoye par un utilisateur authentifie.

#### Responsabilites a implementer plus tard

1. Recevoir :
   - `userId`
   - `MediaType` cible (`MOVIE` ou `SERIES`)
   - contenu du fichier CSV
2. Parser les lignes CSV.
3. Appliquer les regles de mapping source -> type interne.
4. Creer ou reutiliser `Media`.
5. Creer les `UserWatched`.
6. Retourner un rapport d'import detaille.

#### Regles metier precises a documenter dans ce fichier

Pour import `SERIES` :

- accepter `TV Series`
- accepter `TV Mini Series`
- mapper les deux vers `MediaType.SERIES`
- refuser ou skipper tout type non compatible avec l'import series

Pour import `MOVIE` :

- accepter uniquement les types source correspondant aux films
- refuser `TV Series` et `TV Mini Series`

#### Nettoyage precis

- Ne pas reutiliser des blocs de code copies-colles de `CsvImportService` si une methode commune propre peut etre extraite.
- Ne pas mettre la creation de l'admin dans ce service.

---

## Etape 4 - Exposer un endpoint backend d'import CSV manuel

### 4.1 Modifier `ask2watch-backend/src/main/java/com/ask2watch/controller/MediaController.java`

#### Ce qu'il faut faire

Ajouter un endpoint authentifie, par exemple :

- `POST /api/media/import/csv`

#### Contrat HTTP recommande

- `multipart/form-data`
- champs :
  - `file` : fichier CSV
  - `type` : `MOVIE` ou `SERIES`

#### Actions detaillees

1. Recuperer `userId` depuis `Authentication`.
2. Recuperer le fichier via `@RequestPart` ou `@RequestParam MultipartFile`.
3. Recuperer le type cible via `@RequestParam MediaType type`.
4. Appeler `UserCsvImportService`.
5. Retourner un DTO de resultat.

#### Nettoyage precis

- Ne pas mettre la logique CSV directement dans le controller.
- Ne pas reutiliser l'ancien endpoint `/watched` pour ce besoin.

### 4.2 Creer `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportError.java`

#### Role

Representer une erreur par ligne ou une erreur de parsing.

#### Champs recommandes

- `lineNumber`
- `title`
- `reason`

### 4.3 Creer `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportSummary.java`

#### Role

Representer les compteurs agreges.

#### Champs recommandes

- `totalLines`
- `imported`
- `skipped`
- `duplicates`
- `errors`

### 4.4 Creer `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportResponse.java`

#### Role

Payload de reponse du endpoint.

#### Champs recommandes

- `message`
- `summary`
- `errors`

### 4.5 Modifier `ask2watch-backend/src/main/java/com/ask2watch/service/MediaService.java`

#### Ce qu'il faut faire

Deux options :

1. Laisser `MediaService` concentre sur watched/recommendations, et appeler directement `UserCsvImportService` depuis le controller.
2. Ou exposer une methode facade de `MediaService` qui delegue a `UserCsvImportService`.

#### Recommandation

Conserver `MediaService` mince et coherent :

- ajouter une methode facade si vous voulez une API service unique cote controller
- ne pas y coller le parsing CSV

#### Nettoyage precis

- Si une methode d'import y est ajoutee, elle doit etre pure delegation.
- Ne pas melanger le code d'import ligne par ligne avec les operations CRUD watched.

#### Remarque d'implémentation

- `MediaService` expose désormais `importCsv(userId, type, bytes)` et délègue à `UserCsvImportService`.
- `MediaController` ne dépend plus directement de `UserCsvImportService`, favorisant un point d'entrée métier unique.

---

## Etape 5 - Regles de mapping CSV tres precises

### 5.1 Modifier `ask2watch-backend/src/main/java/com/ask2watch/dto/csv/CsvMediaRow.java`

#### Ce qu'il faut faire

1. Verifier que le champ `titleType` existe et est correctement mappe depuis le CSV.
2. Si le nom du champ Java ou la colonne OpenCSV n'est pas clair, le rendre explicite.

#### Validation

- Ajouter `UserCsvImportServiceIT` qui charge un fichier `series-mixed.csv` pour prouver que :
  * `TV Series` et `TV Mini Series` sont acceptes et persistants en `MediaType.SERIES`
  * `Video` est ignore
  * Les lignes re-importees ne créent pas de doublons `user_watched`

#### Nettoyage precis

- Supprimer toute propriete CSV non utilisee seulement si tu confirmes qu'elle n'est utile ni au seed ni au futur import utilisateur.
- Ne pas supprimer `titleType`.

### 5.2 Regle de normalisation a coder plus tard dans le service d'import

#### Pour un import series

- `TV Series` => accepte, persiste `SERIES`
- `TV Mini Series` => accepte, persiste `SERIES`
- `Video` => skip
- autres types => skip + enregistrer une erreur ou un skip motive

#### Pour un import movies

- types source film compatibles => accepte, persiste `MOVIE`
- `TV Series` => skip
- `TV Mini Series` => skip
- `Video` => skip

#### Critere de validation

- Aucun `Media` de type `SERIES` ne doit etre persiste avec un type source heterogene hors normalisation.

---

## Etape 6 - Ajouter les tests backend de l'import CSV manuel

### 6.1 Creer `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaCsvImportIT.java`

#### Cas de test a couvrir

1. Upload CSV films valide
2. Upload CSV series valide avec lignes `TV Series`
3. Upload CSV series valide avec lignes `TV Mini Series`
4. Upload CSV series mixte :
   - `TV Series` importe
   - `TV Mini Series` importe
   - `Video` skip
5. Upload CSV avec doublon media deja existant
6. Upload CSV avec doublon deja dans `user_watched`
7. Upload sans auth => `401`
8. Upload sans fichier => `400`
9. Upload avec `type` invalide => `400`
10. Upload fichier non CSV ou mal parse => `400` ou `422` selon convention retenue

#### Donnees de test

Creer des mini fixtures CSV inline dans les tests ou dans `src/test/resources/fixtures/`.

#### Recommandation

Si des fixtures sont creees :

- creer `ask2watch-backend/src/test/resources/fixtures/import/series-valid.csv` (deja en place)
- creer `ask2watch-backend/src/test/resources/fixtures/import/series-mixed.csv` (ajoute pour ce plan avec `TV Series`/`TV Mini Series` et `Video`)
- creer `ask2watch-backend/src/test/resources/fixtures/import/movies-valid.csv` (ajoute pour couvrir les films)

#### Nettoyage precis

- Si des fixtures temporaires sont creees puis non utilisees, les supprimer.
- Ne pas laisser des CSV de debug dans `src/test/resources/`.

### 6.2 Modifier `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaControllerIT.java`

#### Ce qu'il faut faire

Verifier si certains tests existants doivent etre ajustes apres ajout du nouvel endpoint ou d'un nouveau DTO de reponse.

#### Nettoyage precis

- Supprimer les imports inutiles.
- Ne pas dupliquer dans `MediaControllerIT` ce qui est deja couvert par `MediaCsvImportIT`.

---

## Etape 7 - Ajouter l'upload CSV dans le frontend

### 7.1 Creer `ask2watch-frontend/src/app/shared/models/csv-import.model.ts`

#### Role

Modeliser la reponse backend d'import CSV.

#### Contenu attendu

- interfaces TypeScript pour :
  - `CsvImportError`
  - `CsvImportSummary`
  - `CsvImportResponse`

### 7.2 Modifier `ask2watch-frontend/src/app/shared/models/media.model.ts`

#### Ce qu'il faut faire

1. Verifier si `MediaType` TypeScript existe deja.
2. Si non, ajouter un type explicite :
   - `'MOVIE' | 'SERIES'`

#### Supplement

- Exposer ce type pour qu'il soit réutilisé par le service et le composant `watched`, afin d'éviter plusieurs déclarations littérales.

#### Nettoyage precis

- Supprimer toute duplication de type `MOVIE/SERIES` si le meme type est deja defini ailleurs.

### 7.3 Modifier `ask2watch-frontend/src/app/core/services/media.service.ts`

#### Ce qu'il faut faire

Ajouter une methode dediee, par exemple :

- `importCsv(file: File, type: 'MOVIE' | 'SERIES'): Observable<CsvImportResponse>`

#### Actions detaillees

1. Construire un `FormData`.
2. Ajouter `file`.
3. Ajouter `type`.
4. Poster vers `/api/media/import/csv`.
5. Typer correctement la reponse.

#### Nettoyage precis

- Ne pas melanger cette methode avec `addToWatched`.
- Supprimer tout helper temporaire d'upload si un vrai service type est introduit.

### 7.4 Modifier `ask2watch-frontend/src/app/features/watched/watched.component.ts`

#### Ce qu'il faut faire

Ajouter l'etat UI d'upload :

- type choisi
- fichier selectionne
- chargement
- succes
- erreurs
- resume d'import

#### Actions detaillees

1. Ajouter une methode de selection du type.
2. Ajouter une methode `onFileSelected`.
3. Ajouter une methode `onImportCsv`.
 4. Recharger la liste watched apres import reussi.
 5. Afficher les erreurs d'import de facon lisible.

#### Tests associes

- `WatchedComponent` spec devrait :
  * Appeler `MediaService.importCsv` avec le `File` et le `MediaType` correspondant.
  * Mettre a jour `uploadSummary` puis relancer `getWatchedSeries`/`getWatchedMovies` selon le type pour rafraichir l'affichage.

#### Details techniques

- Introduire des helpers `loadMovies()`/`loadSeries()` et une methode `refreshWatchedAfterImport(type)` pour recharger proprement les listes `movies/series`.
- Utiliser le type `MediaType` pour `uploadType` afin d'aligner l'API Angular avec le backend.
- Le handler retenu pour le clic d'import est `triggerUpload()`, qui met a jour `uploadStatus`, `uploadMessage`, `uploadSummary` et `uploadErrors`.

#### Nettoyage precis

- Supprimer les variables temporaires inutiles une fois le flux stabilise.
- Ne pas laisser de `console.log` de debug.

### 7.5 Modifier `ask2watch-frontend/src/app/features/watched/watched.component.html`

#### Ce qu'il faut faire

Ajouter les elements UI suivants :

1. Un bouton visible `Importer un CSV`.
2. Un `input type="file"` cache ou stylise.
3. Un selecteur ou groupe de boutons :
   - `Films`
   - `Series`
4. Un bouton de confirmation d'import.
5. Une zone d'affichage du resultat :
   - nombre importe
   - lignes skippees
   - doublons
   - erreurs detaillees

#### Nettoyage precis

- Supprimer tout markup de debug ou placeholder qui ne sert plus.
- Eviter les textes generiques non relies au vrai comportement backend.

### 7.6 Modifier `ask2watch-frontend/src/app/features/watched/watched.component.css`

#### Ce qu'il faut faire

1. Styliser la zone d'upload.
2. Prevoir les etats :
   - idle
   - loading
   - success
   - error

#### Nettoyage precis

- Supprimer les styles morts si l'interface remplace un ancien bloc.

---

## Etape 8 - Ajouter les tests frontend

### 8.1 Creer `ask2watch-frontend/src/app/core/services/media.service.spec.ts`

#### Ce qu'il faut tester

1. La methode `importCsv()` envoie bien un `POST` vers `/api/media/import/csv`.
2. Le `FormData` contient bien :
   - `file`
   - `type`
3. La reponse est correctement typee.

### 8.2 Creer `ask2watch-frontend/src/app/features/watched/watched.component.spec.ts`

#### Ce qu'il faut tester

1. Le bouton d'import est rendu.
2. Le type peut etre selectionne.
3. L'appel au service se fait au clic de confirmation.
4. Apres succes, la liste watched est rechargee.
5. En cas d'erreur, un message utilisateur est visible.

### 8.3 Commandes de validation frontend

```bash
cd ask2watch-frontend
npm test
npm run build
```

#### Remarques

- La target Angular `test` doit exister dans `angular.json` avec le builder `@angular/build:unit-test`.
- Le runner retenu est `vitest`, avec `jsdom` comme environnement DOM pour permettre l'execution des specs de composant/service.
- Validation actuelle :
  - `npm test` passe.
  - `npm run build` passe.

#### Critere de validation

- Le composant compile.
- Le build Angular passe.
- Les tests de service/composant couvrent le flux nominal et le flux erreur.

---

## Etape 9 - Nettoyage obligatoire apres implementation

### 9.1 Nettoyage des fichiers parasites du repo

#### Fichiers a supprimer

- `/Users/dr.youd/Desktop/Atexo/ask2Watch/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/test/java/.DS_Store` (supprime)
- `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/test/java/com/.DS_Store` (supprime)

#### Fichiers IDE a surveiller

Les fichiers `.idea/**` sont deja non suivis ou non utiles au projet applicatif. Ne pas les committer.

### 9.2 Nettoyage du code backend

#### Dans `CsvImportService.java`

- supprimer le `Thread.sleep(250)`
- supprimer les blocs qui ne servent plus au nouvel import utilisateur
- supprimer tout doublon extrait vers `UserCsvImportService`

#### Dans `AbstractIntegrationTest.java`

- supprimer l'ancien nom `pick_of_week`
- supprimer tout helper devenu inutile apres factorisation

#### Dans `MediaController.java`

- supprimer tout code CSV temporaire si une implementation finale propre a ete mise en place

### 9.3 Nettoyage du code frontend

#### Dans `watched.component.ts`

- supprimer `console.log`
- supprimer drapeaux d'etat temporaires non utilises
- supprimer essais UI abandonnes (ceux-ci n'existent plus)

#### Dans `media.service.ts`

- supprimer imports inutiles
- supprimer typages dupliques apres creation de `csv-import.model.ts`

### 9.4 Nettoyage documentation

#### Fichiers a mettre a jour apres implementation

- `plan/README.md`
- `plan/PROGRESS.md`
- le futur document de realisation de cette phase

#### Ce qu'il faudra y faire

- ajouter la phase 9 dans le plan global
- marquer les etapes terminees
- documenter les validations finales effectuees

---

## Strategie d'execution recommandee

1. Modifier le schema SQL et le mapping JPA de `media_type`.
2. Ajouter les tests de persistence enum SQL.
3. Corriger le socle de tests backend.
4. Decoupler l'import seed du demarrage.
5. Creer `UserCsvImportService`.
6. Exposer l'endpoint CSV backend.
7. Ecrire les tests d'integration backend du nouvel endpoint.
8. Ajouter les modeles et le service Angular.
9. Ajouter l'UI d'upload dans `watched`.
10. Ajouter les tests frontend.
11. Nettoyer les fichiers parasites, le code mort et la documentation.

---

## Validation finale complete

### Backend

```bash
cd ask2watch-backend
./mvnw test
./mvnw test -Dtest=MediaTypePersistenceIT
./mvnw test -Dtest=MediaCsvImportIT
./mvnw test -Dtest=MediaControllerIT
```

### Frontend

```bash
cd ask2watch-frontend
npm test
npm run build
```

### Validation fonctionnelle manuelle

1. Demarrer backend et frontend.
2. Se connecter avec un utilisateur valide.
3. Aller sur l'ecran watched.
4. Choisir `Series`.
5. Uploader un CSV contenant :
   - une ligne `TV Series`
   - une ligne `TV Mini Series`
   - une ligne `Video`
6. Verifier que :
   - les deux premieres lignes sont importees comme `SERIES`
   - la ligne `Video` est ignoree
   - la reponse d'import affiche les bons compteurs
   - la liste watched est rechargee

### Criteres d'acceptation de la phase

- `media.media_type` est un enum SQL PostgreSQL.
- Aucun test backend ne depend de PostgreSQL local machine.
- L'import seed ne tourne plus implicitement au demarrage.
- L'utilisateur peut importer un CSV depuis le frontend.
- `TV Series` et `TV Mini Series` sont persistés comme `SERIES`.
- Les fichiers parasites et le code mort ont ete nettoyes.
