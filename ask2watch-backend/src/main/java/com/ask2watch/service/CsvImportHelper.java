package com.ask2watch.service;

import com.ask2watch.dto.csv.CsvMediaRow;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class CsvImportHelper {

    private CsvImportHelper() {}

    public static boolean isRowSupported(String titleType, MediaType selectedType) {
        MediaType normalizedType = normalizeType(titleType);
        return normalizedType != null && normalizedType == selectedType;
    }

    public static Media mapToMedia(CsvMediaRow row, MediaType type) {
        return Media.builder()
                .imdbId(trimToNull(row.getImdbId()))
                .title(trimToNull(row.getTitle()) != null ? row.getTitle().trim() : "Unknown title")
                .originalTitle(trimToNull(row.getOriginalTitle()))
                .mediaType(type)
                .year(trimToNull(row.getYear()))
                .runtimeMins(parseInteger(row.getRuntimeMins()))
                .genres(trimToNull(row.getGenres()))
                .imdbRating(parseBigDecimal(row.getImdbRating()))
                .numVotes(parseInteger(row.getNumVotes()))
                .releaseDate(parseDate(row.getReleaseDate()))
                .directors(trimToNull(row.getDirectors()))
                .imdbUrl(trimToNull(row.getUrl()))
                .build();
    }

    public static MediaType normalizeType(String titleType) {
        if (titleType == null || titleType.isBlank()) {
            return null;
        }

        return switch (titleType.trim().toLowerCase()) {
            case "movie" -> MediaType.MOVIE;
            case "tv series", "tv mini series" -> MediaType.SERIES;
            case "video" -> null;
            default -> null;
        };
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
