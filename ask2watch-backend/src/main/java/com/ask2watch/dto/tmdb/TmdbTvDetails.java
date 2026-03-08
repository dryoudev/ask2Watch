package com.ask2watch.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbTvDetails {

    private int id;
    private String overview;
    private List<Genre> genres;

    @JsonProperty("number_of_seasons")
    private int numberOfSeasons;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("created_by")
    private List<Creator> createdBy;

    private TmdbCredits credits;

    @JsonProperty("content_ratings")
    private ContentRatingsWrapper contentRatings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Genre {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Creator {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentRatingsWrapper {
        private List<CountryRating> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CountryRating {
        @JsonProperty("iso_3166_1")
        private String iso31661;
        private String rating;
    }
}
