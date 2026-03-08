package com.ask2watch.dto.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class CsvMediaRow {

    @CsvBindByName(column = "Position")
    private String position;

    @CsvBindByName(column = "Const")
    private String imdbId;

    @CsvBindByName(column = "Title")
    private String title;

    @CsvBindByName(column = "Original Title")
    private String originalTitle;

    @CsvBindByName(column = "URL")
    private String url;

    @CsvBindByName(column = "Title Type")
    private String titleType;

    @CsvBindByName(column = "IMDb Rating")
    private String imdbRating;

    @CsvBindByName(column = "Runtime (mins)")
    private String runtimeMins;

    @CsvBindByName(column = "Year")
    private String year;

    @CsvBindByName(column = "Genres")
    private String genres;

    @CsvBindByName(column = "Num Votes")
    private String numVotes;

    @CsvBindByName(column = "Release Date")
    private String releaseDate;

    @CsvBindByName(column = "Directors")
    private String directors;
}
