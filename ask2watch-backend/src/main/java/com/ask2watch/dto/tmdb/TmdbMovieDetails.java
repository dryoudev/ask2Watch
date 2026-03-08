package com.ask2watch.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieDetails {

    private int id;
    private int runtime;
    private String overview;
    private List<Genre> genres;

    @JsonProperty("poster_path")
    private String posterPath;

    private TmdbCredits credits;

    @JsonProperty("release_dates")
    private TmdbReleaseDatesWrapper releaseDates;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Genre {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbReleaseDatesWrapper {
        private List<CountryRelease> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CountryRelease {
        @JsonProperty("iso_3166_1")
        private String iso31661;

        @JsonProperty("release_dates")
        private List<ReleaseInfo> releaseDates;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReleaseInfo {
        private String certification;
    }
}
