import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import dotenv from "dotenv";

dotenv.config();

const TMDB_API_KEY = process.env.TMDB_API_KEY;
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const MCP_AUTH_EMAIL = process.env.MCP_AUTH_EMAIL;
const MCP_AUTH_PASSWORD = process.env.MCP_AUTH_PASSWORD;
const BASE_URL = "https://api.themoviedb.org/3";

if (!TMDB_API_KEY) {
  console.error("TMDB_API_KEY environment variable is required");
  process.exit(1);
}

if (!MCP_AUTH_EMAIL || !MCP_AUTH_PASSWORD) {
  console.error("MCP_AUTH_EMAIL and MCP_AUTH_PASSWORD environment variables are required");
  process.exit(1);
}

// --- Auth: login once and cache token ---
let authToken = null;

async function getToken() {
  if (authToken) return authToken;
  const res = await fetch(`${BACKEND_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: MCP_AUTH_EMAIL, password: MCP_AUTH_PASSWORD }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  const data = await res.json();
  authToken = data.token;
  return authToken;
}

async function backendFetch(path) {
  const token = await getToken();
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Backend error: ${res.status} on ${path}`);
  return res.json();
}

async function backendPost(path, body) {
  const token = await getToken();
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Backend error: ${res.status} on ${path}`);
  return res.json();
}

async function backendPut(path, body) {
  const token = await getToken();
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Backend error: ${res.status} on ${path}`);
  return res.json();
}

async function backendDelete(path) {
  const token = await getToken();
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Backend error: ${res.status} on ${path}`);
  return res.status === 204 ? { success: true } : res.json();
}

// --- TMDB helpers ---
async function tmdbFetch(path, params = {}) {
  const url = new URL(`${BASE_URL}${path}`);
  url.searchParams.set("api_key", TMDB_API_KEY);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  }
  const res = await fetch(url);
  if (!res.ok) throw new Error(`TMDB API error: ${res.status}`);
  return res.json();
}

function formatMovie(m) {
  return {
    tmdb_id: m.id,
    title: m.title || m.name,
    year: (m.release_date || m.first_air_date || "").substring(0, 4),
    poster_path: m.poster_path,
    overview: m.overview?.substring(0, 200),
    vote_average: m.vote_average,
    media_type: m.title ? "movie" : "tv",
  };
}

function formatWatched(items) {
  return items.map((w) => ({
    title: w.media.title,
    genres: w.media.genres,
    imdbRating: w.media.imdbRating,
    userRating: w.userRating,
    comment: w.comment,
    dateWatched: w.dateWatched,
    mediaType: w.media.mediaType,
    year: w.media.year,
  }));
}

// --- MCP Server ---
const server = new McpServer({
  name: "ask2watch",
  version: "1.0.0",
});

// ========== BACKEND TOOLS ==========

server.tool(
  "get_watched_movies",
  "Get all movies the user has watched, with ratings and comments",
  {},
  async () => {
    const data = await backendFetch("/api/media/watched?type=MOVIE");
    const formatted = formatWatched(data);
    return { content: [{ type: "text", text: JSON.stringify(formatted, null, 2) }] };
  }
);

server.tool(
  "get_watched_series",
  "Get all TV series the user has watched, with ratings and comments",
  {},
  async () => {
    const data = await backendFetch("/api/media/watched?type=SERIES");
    const formatted = formatWatched(data);
    return { content: [{ type: "text", text: JSON.stringify(formatted, null, 2) }] };
  }
);

server.tool(
  "get_current_picks",
  "Get the current picks of the week",
  {},
  async () => {
    const data = await backendFetch("/api/picks/current");
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  }
);

// ========== TMDB TOOLS ==========

server.tool(
  "search_movie",
  "Search for movies on TMDB by title",
  {
    query: z.string().describe("Movie title to search"),
    year: z.number().optional().describe("Release year filter"),
  },
  async ({ query, year }) => {
    const data = await tmdbFetch("/search/movie", { query, year });
    const results = data.results.slice(0, 5).map(formatMovie);
    return { content: [{ type: "text", text: JSON.stringify(results, null, 2) }] };
  }
);

server.tool(
  "search_tv",
  "Search for TV series on TMDB by title",
  {
    query: z.string().describe("TV series title to search"),
    year: z.number().optional().describe("First air date year filter"),
  },
  async ({ query, year }) => {
    const data = await tmdbFetch("/search/tv", { query, first_air_date_year: year });
    const results = data.results.slice(0, 5).map(formatMovie);
    return { content: [{ type: "text", text: JSON.stringify(results, null, 2) }] };
  }
);

server.tool(
  "get_trending",
  "Get trending movies or TV shows this week",
  {
    media_type: z.enum(["movie", "tv", "all"]).describe("Type: movie, tv, or all"),
    time_window: z.enum(["day", "week"]).describe("Time window: day or week"),
  },
  async ({ media_type, time_window }) => {
    const data = await tmdbFetch(`/trending/${media_type}/${time_window}`);
    const results = data.results.slice(0, 10).map(formatMovie);
    return { content: [{ type: "text", text: JSON.stringify(results, null, 2) }] };
  }
);

server.tool(
  "get_recommendations",
  "Get movie/TV recommendations similar to a given title (by TMDB ID)",
  {
    tmdb_id: z.number().describe("TMDB ID of the movie or TV show"),
    media_type: z.enum(["movie", "tv"]).describe("Type: movie or tv"),
  },
  async ({ tmdb_id, media_type }) => {
    const data = await tmdbFetch(`/${media_type}/${tmdb_id}/recommendations`);
    const results = data.results.slice(0, 10).map(formatMovie);
    return { content: [{ type: "text", text: JSON.stringify(results, null, 2) }] };
  }
);

server.tool(
  "get_movie_details",
  "Get complete movie/TV details from TMDB by ID (poster, genres, rating, directors, cast, etc.)",
  {
    tmdb_id: z.number().describe("TMDB ID of the movie or TV show"),
    media_type: z.enum(["movie", "tv"]).describe("Type: movie or tv"),
  },
  async ({ tmdb_id, media_type }) => {
    const data = await tmdbFetch(`/${media_type}/${tmdb_id}`, {
      append_to_response: "credits"
    });
    const details = {
      tmdb_id: data.id,
      title: data.title || data.name,
      year: (data.release_date || data.first_air_date || "").substring(0, 4),
      poster_path: data.poster_path,
      overview: data.overview,
      vote_average: data.vote_average,
      genres: data.genres?.map(g => g.name).join(", ") || null,
      directors: data.credits?.crew?.filter(c => c.job === "Director").map(d => d.name).join(", ") || null,
      stars: data.credits?.cast?.slice(0, 5).map(a => a.name).join(", ") || null,
      runtime_mins: data.runtime || data.episode_run_time?.[0] || null,
      rated: data.rated || null,
    };
    return { content: [{ type: "text", text: JSON.stringify(details, null, 2) }] };
  }
);

server.tool(
  "discover",
  "Discover movies or TV shows by genre, year range, and minimum rating. Genre IDs: 28=Action, 12=Adventure, 16=Animation, 35=Comedy, 80=Crime, 99=Documentary, 18=Drama, 14=Fantasy, 27=Horror, 10402=Music, 9648=Mystery, 10749=Romance, 878=SciFi, 53=Thriller, 10752=War, 37=Western",
  {
    media_type: z.enum(["movie", "tv"]).describe("Type: movie or tv"),
    genres: z.string().optional().describe("Comma-separated TMDB genre IDs"),
    year_min: z.number().optional().describe("Minimum release year"),
    year_max: z.number().optional().describe("Maximum release year"),
    rating_min: z.number().optional().describe("Minimum vote average (0-10)"),
  },
  async ({ media_type, genres, year_min, year_max, rating_min }) => {
    const params = { sort_by: "vote_average.desc", "vote_count.gte": 100 };
    if (genres) params.with_genres = genres;
    if (rating_min) params["vote_average.gte"] = rating_min;
    if (media_type === "movie") {
      if (year_min) params["primary_release_date.gte"] = `${year_min}-01-01`;
      if (year_max) params["primary_release_date.lte"] = `${year_max}-12-31`;
    } else {
      if (year_min) params["first_air_date.gte"] = `${year_min}-01-01`;
      if (year_max) params["first_air_date.lte"] = `${year_max}-12-31`;
    }
    const data = await tmdbFetch(`/discover/${media_type}`, params);
    const results = data.results.slice(0, 10).map(formatMovie);
    return { content: [{ type: "text", text: JSON.stringify(results, null, 2) }] };
  }
);

// ========== WATCHED LIST CRUD TOOLS ==========

server.tool(
  "search_watched",
  "Search for a movie or series in the user's watched list by title",
  {
    query: z.string().describe("Title to search in watched list"),
  },
  async ({ query }) => {
    const movies = await backendFetch("/api/media/watched?type=MOVIE");
    const series = await backendFetch("/api/media/watched?type=SERIES");
    const all = [...movies, ...series];
    const results = all.filter((w) =>
      w.media.title.toLowerCase().includes(query.toLowerCase())
    );
    const formatted = formatWatched(results);
    return { content: [{ type: "text", text: JSON.stringify(formatted, null, 2) }] };
  }
);

server.tool(
  "add_to_watched",
  "Add a movie or TV series to the user's watched list",
  {
    tmdb_id: z.number().describe("TMDB ID of the movie or TV show"),
    media_type: z.enum(["MOVIE", "SERIES"]).describe("Type: MOVIE or SERIES"),
    title: z.string().describe("Title of the movie or series"),
  },
  async ({ tmdb_id, media_type, title }) => {
    const result = await backendPost("/api/media/watched", {
      tmdbId: tmdb_id,
      mediaType: media_type,
      title: title,
    });
    return { content: [{ type: "text", text: `✅ "${title}" ajouté à la liste du Maître.` }] };
  }
);

server.tool(
  "remove_from_watched",
  "Remove a movie or series from the user's watched list",
  {
    watched_id: z.number().describe("ID of the watched entry to remove"),
  },
  async ({ watched_id }) => {
    await backendDelete(`/api/media/watched/${watched_id}`);
    return { content: [{ type: "text", text: `✅ Retiré de la liste du Maître.` }] };
  }
);

server.tool(
  "rate_watched",
  "Set or update the rating for a watched movie/series (1-10)",
  {
    watched_id: z.number().describe("ID of the watched entry"),
    rating: z.number().min(1).max(10).describe("Rating from 1 to 10"),
  },
  async ({ watched_id, rating }) => {
    const result = await backendPut(`/api/media/watched/${watched_id}`, {
      userRating: rating,
    });
    return { content: [{ type: "text", text: `✅ Note ${rating}/10 enregistrée pour le Maître.` }] };
  }
);

server.tool(
  "comment_watched",
  "Add or update a comment for a watched movie/series",
  {
    watched_id: z.number().describe("ID of the watched entry"),
    comment: z.string().describe("Comment text"),
  },
  async ({ watched_id, comment }) => {
    const result = await backendPut(`/api/media/watched/${watched_id}`, {
      comment: comment,
    });
    return { content: [{ type: "text", text: `✅ Commentaire enregistré pour le Maître.` }] };
  }
);

server.tool(
  "update_watched",
  "Update rating, comment, and/or watch date for a watched entry",
  {
    watched_id: z.number().describe("ID of the watched entry"),
    rating: z.number().min(1).max(10).optional().describe("Rating from 1 to 10"),
    comment: z.string().optional().describe("Comment text"),
    date_watched: z.string().optional().describe("Date watched (YYYY-MM-DD)"),
  },
  async ({ watched_id, rating, comment, date_watched }) => {
    const body = {};
    if (rating) body.userRating = rating;
    if (comment) body.comment = comment;
    if (date_watched) body.dateWatched = date_watched;
    const result = await backendPut(`/api/media/watched/${watched_id}`, body);
    return { content: [{ type: "text", text: `✅ Données mises à jour pour le Maître.` }] };
  }
);

// ========== PICKS CRUD TOOLS ==========

server.tool(
  "add_pick",
  "Add a movie or TV series as a pick of the week",
  {
    tmdb_id: z.number().describe("TMDB ID of the movie or TV show"),
    media_type: z.enum(["MOVIE", "SERIES"]).describe("Type: MOVIE or SERIES"),
    title: z.string().describe("Title of the pick"),
    reason: z.string().describe("Why this pick (short reason)"),
  },
  async ({ tmdb_id, media_type, title, reason }) => {
    const result = await backendPost("/api/picks", {
      tmdbId: tmdb_id,
      mediaType: media_type,
      title: title,
      reason: reason,
    });
    return { content: [{ type: "text", text: `✅ "${title}" ajouté aux picks du Maître.` }] };
  }
);

server.tool(
  "remove_pick",
  "Remove a pick from the current week",
  {
    pick_id: z.number().describe("ID of the pick to remove"),
  },
  async ({ pick_id }) => {
    await backendDelete(`/api/picks/${pick_id}`);
    return { content: [{ type: "text", text: `✅ Pick retiré de la liste du Maître.` }] };
  }
);

server.tool(
  "list_picks_history",
  "Get picks from previous weeks",
  {
    limit: z.number().optional().describe("Number of weeks to go back"),
  },
  async ({ limit }) => {
    const data = await backendFetch("/api/picks/history?limit=" + (limit || 10));
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  }
);

server.tool(
  "generate_picks",
  "Generate intelligent picks based on user taste and current trends",
  {},
  async () => {
    const [watched_movies, watched_series, trending, trending_tv] = await Promise.all([
      backendFetch("/api/media/watched?type=MOVIE"),
      backendFetch("/api/media/watched?type=SERIES"),
      tmdbFetch("/trending/movie/week"),
      tmdbFetch("/trending/tv/week"),
    ]);

    // Extraire genres des films regardés
    const genreFreq = {};
    [...watched_movies, ...watched_series].forEach((w) => {
      (w.media.genres || "").split(",").forEach((g) => {
        g = g.trim();
        if (g) genreFreq[g] = (genreFreq[g] || 0) + 1;
      });
    });

    const suggestion = {
      user_taste: {
        movies_watched: watched_movies.length,
        series_watched: watched_series.length,
        top_genres: Object.entries(genreFreq)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 3)
          .map((e) => e[0]),
      },
      trending_this_week: {
        movies: trending.results.slice(0, 5).map(formatMovie),
        tv_shows: trending_tv.results.slice(0, 5).map(formatMovie),
      },
      instruction: "Dobby a analysé les goûts du Maître et les tendances. Proposez 5 picks basés sur ces données.",
    };

    return { content: [{ type: "text", text: JSON.stringify(suggestion, null, 2) }] };
  }
);

// --- Start ---
const transport = new StdioServerTransport();
await server.connect(transport);
