DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'media_type_enum') THEN
        EXECUTE 'CREATE TYPE media_type_enum AS ENUM (''MOVIE'', ''SERIES'')';
    END IF;
END;
$$
//

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_cast c
        JOIN pg_type src ON c.castsource = src.oid
        JOIN pg_type tgt ON c.casttarget = tgt.oid
        WHERE src.typname = 'varchar' AND tgt.typname = 'media_type_enum'
    ) THEN
        EXECUTE 'CREATE CAST (varchar AS media_type_enum) WITH INOUT AS IMPLICIT';
    END IF;
END;
$$
//

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_cast c
        JOIN pg_type src ON c.castsource = src.oid
        JOIN pg_type tgt ON c.casttarget = tgt.oid
        WHERE src.typname = 'text' AND tgt.typname = 'media_type_enum'
    ) THEN
        EXECUTE 'CREATE CAST (text AS media_type_enum) WITH INOUT AS IMPLICIT';
    END IF;
END;
$$
//

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);
//

CREATE TABLE IF NOT EXISTS media (
    id BIGSERIAL PRIMARY KEY,
    imdb_id VARCHAR(20) UNIQUE,
    tmdb_id INT UNIQUE,
    title VARCHAR(500) NOT NULL,
    original_title VARCHAR(500),
    media_type media_type_enum NOT NULL,
    year VARCHAR(20),
    runtime_mins INT,
    genres VARCHAR(500),
    imdb_rating DECIMAL(3,1),
    num_votes INT,
    release_date DATE,
    directors VARCHAR(500),
    stars VARCHAR(1000),
    synopsis TEXT,
    rated VARCHAR(20),
    poster_path VARCHAR(255),
    seasons INT,
    imdb_url VARCHAR(500)
);
//

CREATE TABLE IF NOT EXISTS user_watched (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id BIGINT NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    user_rating INT CHECK (user_rating BETWEEN 1 AND 5),
    date_watched DATE,
    comment TEXT,
    UNIQUE(user_id, media_id)
);
//

CREATE TABLE IF NOT EXISTS picks_of_week (
    id BIGSERIAL PRIMARY KEY,
    media_id BIGINT NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_date DATE NOT NULL,
    created_by_agent BOOLEAN DEFAULT false
);
//
