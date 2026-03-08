package com.ask2watch.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbFindResponse {

    @JsonProperty("movie_results")
    private List<TmdbMovieResult> movieResults;

    @JsonProperty("tv_results")
    private List<TmdbTvResult> tvResults;
}
