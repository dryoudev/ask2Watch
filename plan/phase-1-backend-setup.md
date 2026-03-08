# Phase 1 : Setup Spring Boot + Entites + Base de donnees

## 1.1 Generer le projet Spring Boot

**Action :** Creer le projet via Spring Initializr ou `mvn archetype:generate`

**Dossier cible :** `ask2watch-backend/`

**Configuration initiale :**
- Group: `com.ask2watch`
- Artifact: `ask2watch-backend`
- Java: 21+
- Spring Boot: 3.4.x
- Packaging: JAR

**Dependencies Maven (pom.xml) :**
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `postgresql` (runtime)
- `lombok` (optional/compile)
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JWT)
- `spring-boot-starter-test` (test)
- `opencsv` (CSV parsing)
- `spring-boot-starter-webflux` (WebClient pour TMDB API)

---

## 1.2 Configuration application

### Fichier : `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ask2watch
    username: ask2watch
    password: ask2watch
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  sql:
    init:
      mode: always

server:
  port: 8080

tmdb:
  api-key: ${TMDB_API_KEY}
  base-url: https://api.themoviedb.org/3
  image-base-url: https://image.tmdb.org/t/p

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400000
```

### Fichier : `src/main/resources/application-dev.yml`

Surcharge pour le dev local avec valeurs en dur (non commitees en prod).

### Fichier : `src/main/resources/schema.sql`

Script DDL pour creer les tables (voir section 1.4).

---

## 1.3 docker-compose.yml (racine du projet)

**Fichier :** `docker-compose.yml` (a la racine `ask2watch/`)

```yaml
services:
  postgres:
    image: postgres:14
    environment:
      POSTGRES_DB: ask2watch
      POSTGRES_USER: ask2watch
      POSTGRES_PASSWORD: ask2watch
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

**Attention :** Avant de lancer, arreter le PostgreSQL local (brew services stop postgresql)
ou changer le port dans le compose.

---

## 1.4 Schema SQL

### Fichier : `src/main/resources/schema.sql`

Creer 4 tables dans cet ordre (respecter les FK) :

**Table `users` :**
- `id` BIGSERIAL PRIMARY KEY
- `username` VARCHAR(100) NOT NULL
- `email` VARCHAR(255) NOT NULL UNIQUE
- `password_hash` VARCHAR(255) NOT NULL

**Table `media` :**
- `id` BIGSERIAL PRIMARY KEY
- `imdb_id` VARCHAR(20) UNIQUE
- `tmdb_id` INT UNIQUE
- `title` VARCHAR(500) NOT NULL
- `original_title` VARCHAR(500)
- `media_type` VARCHAR(10) NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES'))
- `year` VARCHAR(20)
- `runtime_mins` INT
- `genres` VARCHAR(500)
- `imdb_rating` DECIMAL(3,1)
- `num_votes` INT
- `release_date` DATE
- `directors` VARCHAR(500)
- `stars` VARCHAR(1000)
- `synopsis` TEXT
- `rated` VARCHAR(20)
- `poster_path` VARCHAR(255)
- `seasons` INT
- `imdb_url` VARCHAR(500)

**Table `user_watched` :**
- `id` BIGSERIAL PRIMARY KEY
- `user_id` BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
- `media_id` BIGINT NOT NULL REFERENCES media(id) ON DELETE CASCADE
- `user_rating` INT CHECK (user_rating BETWEEN 1 AND 5)
- `date_watched` DATE
- `comment` TEXT
- UNIQUE(user_id, media_id)

**Table `picks_of_week` :**
- `id` BIGSERIAL PRIMARY KEY
- `media_id` BIGINT NOT NULL REFERENCES media(id) ON DELETE CASCADE
- `user_id` BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
- `week_date` DATE NOT NULL
- `created_by_agent` BOOLEAN DEFAULT false

---

## 1.5 Entites JPA

### Fichier : `src/main/java/com/ask2watch/model/User.java`

- Annotations : `@Entity`, `@Table(name = "users")`
- Champs : id (Long, @GeneratedValue IDENTITY), username, email, passwordHash
- Pas de relation bidirectionnelle (eviter les boucles)

### Fichier : `src/main/java/com/ask2watch/model/MediaType.java`

- Enum avec 2 valeurs : `MOVIE`, `SERIES`

### Fichier : `src/main/java/com/ask2watch/model/Media.java`

- Annotations : `@Entity`, `@Table(name = "media")`
- Champs : id, imdbId, tmdbId, title, originalTitle, mediaType (@Enumerated STRING), year, runtimeMins, genres, imdbRating (BigDecimal), numVotes, releaseDate (LocalDate), directors, stars, synopsis, rated, posterPath, seasons, imdbUrl

### Fichier : `src/main/java/com/ask2watch/model/UserWatched.java`

- Annotations : `@Entity`, `@Table(name = "user_watched")`
- Champs : id, user (@ManyToOne LAZY), media (@ManyToOne LAZY), userRating, dateWatched (LocalDate), comment

### Fichier : `src/main/java/com/ask2watch/model/PickOfWeek.java`

- Annotations : `@Entity`, `@Table(name = "picks_of_week")`
- Champs : id, media (@ManyToOne LAZY), user (@ManyToOne LAZY), weekDate (LocalDate), createdByAgent

---

## 1.6 Repositories

### Fichier : `src/main/java/com/ask2watch/repository/UserRepository.java`

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

### Fichier : `src/main/java/com/ask2watch/repository/MediaRepository.java`

```java
public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByImdbId(String imdbId);
    Optional<Media> findByTmdbId(Integer tmdbId);
    List<Media> findByMediaType(MediaType mediaType);
}
```

### Fichier : `src/main/java/com/ask2watch/repository/UserWatchedRepository.java`

```java
public interface UserWatchedRepository extends JpaRepository<UserWatched, Long> {
    List<UserWatched> findByUserIdAndMedia_MediaType(Long userId, MediaType type);
    Optional<UserWatched> findByUserIdAndMediaId(Long userId, Long mediaId);
    List<UserWatched> findByUserId(Long userId);
}
```

### Fichier : `src/main/java/com/ask2watch/repository/PickOfWeekRepository.java`

```java
public interface PickOfWeekRepository extends JpaRepository<PickOfWeek, Long> {
    List<PickOfWeek> findByUserIdAndWeekDate(Long userId, LocalDate weekDate);
    List<PickOfWeek> findByUserId(Long userId);
}
```

---

## 1.7 Tests Phase 1

### Fichier : `src/test/java/com/ask2watch/repository/MediaRepositoryTest.java`

- Annoter avec `@DataJpaTest`
- Tester `findByImdbId` avec un media insere
- Tester `findByMediaType` filtre correctement MOVIE vs SERIES

### Fichier : `src/test/java/com/ask2watch/repository/UserWatchedRepositoryTest.java`

- Tester `findByUserIdAndMedia_MediaType`
- Tester la contrainte UNIQUE (user_id, media_id)

### Execution :

```bash
cd ask2watch-backend
mvn test -Dtest="*RepositoryTest"
```

---

## 1.8 Validation Phase 1

- [ ] `docker compose up -d` demarre PostgreSQL sans erreur
- [ ] `mvn spring-boot:run` demarre l'app sans erreur
- [ ] Les 4 tables sont creees dans la DB (verifier avec `psql`)
- [ ] Les tests repository passent au vert
- [ ] Aucune erreur Hibernate au demarrage

---

## 1.9 Nettoyage

- Supprimer les fichiers generes inutiles : `src/main/java/.../DemoApplication.java` (renommer en `Ask2WatchApplication.java`)
- Supprimer `src/test/java/.../DemoApplicationTests.java` (remplacer par nos tests)
- Supprimer `src/main/resources/application.properties` (on utilise `.yml`)
