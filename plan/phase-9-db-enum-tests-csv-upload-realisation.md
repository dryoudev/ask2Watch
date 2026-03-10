# Realisation - Phase 9 : enum SQL media_type, tests backend, import CSV manuel, upload front

## Statut global

- [ ] Non commence
- [ ] En cours
- [ ] Termine

Statut retenu : [~] En cours

---

## Legende

- [ ] A faire
- [~] En cours
- [x] Termine

---

## Bloc 1 - Enum SQL `media_type`

- [x] 1.1 Modifier `ask2watch-backend/src/main/resources/schema.sql`
  - [ ] Ajouter le type PostgreSQL `media_type_enum`
  - [ ] Remplacer la colonne `media.media_type` de type `VARCHAR` par `media_type_enum`
  - [ ] Supprimer la contrainte `CHECK` precedente

- [x] 1.2 Modifier `ask2watch-backend/src/main/java/com/ask2watch/model/Media.java`
  - [ ] Verifier `@Enumerated(EnumType.STRING)`
  - [ ] Ajouter si necessaire `columnDefinition = "media_type_enum"`

- [x] 1.3 Verifier `ask2watch-backend/src/main/java/com/ask2watch/model/MediaType.java`
  - [ ] Conserver uniquement `MOVIE` et `SERIES`
  - [ ] Supprimer toute variante inutile si elle apparait

- [x] 1.4 Creer `ask2watch-backend/src/test/java/com/ask2watch/repository/MediaTypePersistenceIT.java`
  - [ ] Tester la persistence de `MOVIE`
  - [ ] Tester la persistence de `SERIES`
  - [ ] Verifier la relecture depuis PostgreSQL

---

## Bloc 2 - Stabilisation des tests backend

- [ ] 2.1 Traiter `ask2watch-backend/src/test/java/com/ask2watch/Ask2watchBackendApplicationTests.java`
  - [ ] Supprimer le fichier car le `@SpringBootTest` sans profil n'apporte rien

- [x] 2.2 Modifier `ask2watch-backend/src/test/java/com/ask2watch/AbstractIntegrationTest.java`
  - [x] Corriger `pick_of_week` en `picks_of_week`
  - [ ] Verifier l'injection des proprietes Testcontainers
  - [ ] Factoriser le cleanup si necessaire

- [x] 2.3 Modifier `ask2watch-backend/src/test/resources/application-test.yml`
  - [x] Desactiver l'import CSV global
  - [x] Verifier la compatibilite schema SQL / Hibernate
  - [x] Garder des secrets de test factices

- [x] 2.4 Valider les tests backend (échecs documentés ci-dessous)
  - [x] `./mvnw test` (Surefire n'a détecté aucun test par défaut)
  - [x] `./mvnw test -Dtest=MediaTypePersistenceIT`
  - [x] `./mvnw test -Dtest=MediaControllerIT` (reste en échec : plusieurs assertions attendent des 500/400 alors que les endpoints retournent 404 ou la contrainte `user_rating` est violée à 10)

---

## Bloc 3 - Refacto import CSV backend

- [x] 3.1 Modifier `ask2watch-backend/src/main/java/com/ask2watch/config/DataInitializer.java`
  - [x] Rendre l'import opt-in
  - [x] Supprimer `matchIfMissing = true`

- [x] 3.2 Modifier `ask2watch-backend/src/main/java/com/ask2watch/service/CsvImportService.java`
  - [x] Supprimer `Thread.sleep(250)`
  - [x] Introduire la normalisation des types source
  - [x] Mapper `TV Series` vers `SERIES`
  - [x] Mapper `TV Mini Series` vers `SERIES`
  - [x] Continuer a ignorer `Video`
  - [ ] Isoler la creation admin si necessaire

- [x] 3.3 Creer `ask2watch-backend/src/main/java/com/ask2watch/service/UserCsvImportService.java`
  - [x] Parser le CSV utilisateur
  - [x] Creer/reutiliser `Media`
  - [x] Creer `UserWatched`
  - [x] Retourner un rapport d'import

---

## Bloc 4 - Endpoint backend d'import CSV

- [x] 4.1 Modifier `ask2watch-backend/src/main/java/com/ask2watch/controller/MediaController.java`
  - [x] Ajouter `POST /api/media/import/csv`
  - [x] Recuperer `Authentication`
  - [x] Recuperer `MultipartFile`
  - [x] Recuperer `MediaType`

- [x] 4.2 Creer `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportError.java`

- [x] 4.3 Creer `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportSummary.java`

- [x] 4.4 Creer `ask2watch-backend/src/main/java/com/ask2watch/dto/media/CsvImportResponse.java`

- [x] 4.5 Modifier `ask2watch-backend/src/main/java/com/ask2watch/service/MediaService.java`
  - [x] Ajouter une facade pure delegation à `UserCsvImportService`
  - [x] Eviter d'y mettre le parsing CSV direct

---

## Bloc 5 - Tests backend de l'import CSV

- [ ] 5.1 Creer `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaCsvImportIT.java`
  - [ ] Cas nominal films
  - [ ] Cas nominal series `TV Series`
  - [ ] Cas nominal series `TV Mini Series`
  - [ ] Cas mixte avec `Video` skippe
  - [ ] Cas doublon media
  - [ ] Cas doublon user_watched
  - [ ] Cas sans auth
  - [ ] Cas sans fichier
  - [ ] Cas type invalide
  - [ ] Cas CSV invalide
  - [ ] Ajouter `UserCsvImportServiceIT` pour couvrir l'import réel

- [ ] 5.2 Creer des fixtures si necessaire
  - [ ] `src/test/resources/fixtures/import/movies-valid.csv`
  - [ ] `src/test/resources/fixtures/import/series-valid.csv`
  - [ ] `src/test/resources/fixtures/import/series-mixed.csv`

- [ ] 5.3 Modifier `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaControllerIT.java`
  - [ ] Ajuster si le contrat controller evolue
  - [ ] Eviter la duplication des cas CSV

- [ ] 5.2 Creer des fixtures si necessaire
  - [ ] `src/test/resources/fixtures/import/movies-valid.csv`
  - [ ] `src/test/resources/fixtures/import/series-valid.csv`
  - [ ] `src/test/resources/fixtures/import/series-mixed.csv`

- [ ] 5.3 Modifier `ask2watch-backend/src/test/java/com/ask2watch/controller/MediaControllerIT.java`
  - [ ] Ajuster si le contrat controller evolue
  - [ ] Eviter la duplication des cas CSV

---

## Bloc 6 - Frontend upload CSV

- [x] 6.1 Creer `ask2watch-frontend/src/app/shared/models/csv-import.model.ts`
  - [x] `CsvImportError`
  - [x] `CsvImportSummary`
  - [x] `CsvImportResponse`

- [x] 6.2 Modifier `ask2watch-frontend/src/app/shared/models/media.model.ts`
  - [x] Ajouter ou reutiliser le type `MOVIE | SERIES`

- [x] 6.3 Modifier `ask2watch-frontend/src/app/core/services/media.service.ts`
  - [x] Ajouter `importCsv(file, type)`
  - [x] Construire `FormData`
  - [x] Poster sur `/api/media/import/csv`

- [x] 6.4 Modifier `ask2watch-frontend/src/app/features/watched/watched.component.ts`
  - [x] Ajouter l'etat UI de l'import
  - [x] Ajouter `onFileSelected`
  - [x] Ajouter `triggerUpload`
  - [x] Recharger watched apres succes

- [x] 6.5 Modifier `ask2watch-frontend/src/app/features/watched/watched.component.html`
  - [x] Ajouter le bouton `Importer un CSV`
  - [x] Ajouter l'input fichier
  - [x] Ajouter le choix `Films / Series`
  - [x] Ajouter le rendu du rapport d'import

- [x] 6.6 Modifier `ask2watch-frontend/src/app/features/watched/watched.component.css`
  - [x] Styliser les etats `idle/loading/success/error`

---

- ## Bloc 7 - Tests frontend

- [x] 7.1 Creer `ask2watch-frontend/src/app/core/services/media.service.spec.ts`
  - [x] Verifier le `POST`
  - [x] Verifier `FormData`
  - [x] Verifier le typage de la reponse

- [x] 7.2 Creer `ask2watch-frontend/src/app/features/watched/watched.component.spec.ts`
  - [ ] Verifier le rendu du bouton (non couvert par ce spec)
  - [x] Verifier la selection du type
  - [x] Verifier l'appel au service
  - [x] Verifier le rechargement apres succes
  - [x] Verifier l'affichage d'erreur

- [x] 7.3 Validation frontend
  - [x] `npm test` (réussi après ajout de la target Angular `test`, de `vitest`, de `jsdom` et alignement des specs)
  - [x] `npm run build` (réussi : bundles générés dans `dist/ask2watch-frontend`)

---

## Bloc 8 - Nettoyage obligatoire

- [ ] 8.1 Supprimer les fichiers `.DS_Store`
  - [ ] `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/main/java/com/ask2watch/dto/.DS_Store`
  - [ ] `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/test/.DS_Store`
  - [ ] `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/test/java/.DS_Store`
  - [ ] `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-backend/src/test/java/com/.DS_Store`
  - [ ] `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-frontend/.DS_Store`
  - [ ] `/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-frontend/dist/.DS_Store`

- [ ] 8.2 Nettoyer le backend
  - [ ] Supprimer `Thread.sleep(250)` dans `CsvImportService.java`
  - [ ] Supprimer les doublons de logique apres extraction vers `UserCsvImportService`
  - [ ] Supprimer l'ancien nom de table fautif dans `AbstractIntegrationTest.java`
  - [x] Supprimer code CSV temporaire eventuel dans `MediaController.java`

- [ ] 8.3 Nettoyer le frontend
  - [x] Supprimer `console.log`
  - [ ] Supprimer etats temporaires inutiles
  - [ ] Supprimer imports inutiles
  - [x] Supprimer typages dupliques

- [x] 8.4 Mettre a jour la documentation
  - [x] `plan/README.md` (phase 9 référencée et liée)
  - [x] `plan/PROGRESS.md` (section phase 9 ajoutée)
  - [x] ce fichier de realisation mis à jour

---

## Commandes de validation finale

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

---

## Criteres de validation finale

- [ ] `media.media_type` est un enum SQL PostgreSQL.
- [ ] Les tests backend ne dependent pas d'un PostgreSQL local machine.
- [ ] L'import automatique n'est plus implicite au demarrage.
- [ ] Le backend expose un endpoint d'import CSV authentifie.
- [ ] Le frontend permet l'upload CSV depuis l'ecran watched.
- [ ] `TV Series` et `TV Mini Series` sont mappees vers `SERIES`.
- [ ] `Video` est ignore.
- [ ] Les fichiers parasites et le code mort ont ete nettoyes.
