package com.ask2watch.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieResult {

    private int id;
    private String title;

    @JsonProperty("poster_path")
    private String posterPath;

    private String overview;

    @JsonProperty("vote_average")
    private double voteAverage;

    @JsonProperty("release_date")
    private String releaseDate;
}
