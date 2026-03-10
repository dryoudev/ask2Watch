package com.ask2watch.service;

import com.ask2watch.dto.csv.CsvMediaRow;
import com.ask2watch.dto.media.CsvImportError;
import com.ask2watch.dto.media.CsvImportResponse;
import com.ask2watch.dto.media.CsvImportSummary;
import com.ask2watch.exception.ResourceNotFoundException;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.User;
import com.ask2watch.model.UserWatched;
import com.ask2watch.repository.MediaRepository;
import com.ask2watch.repository.UserRepository;
import com.ask2watch.repository.UserWatchedRepository;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCsvImportService {

    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final UserWatchedRepository userWatchedRepository;

    public CsvImportResponse importCsv(Long userId, MultipartFile file, MediaType selectedType) {
        return doImport(userId, file, selectedType);
    }

    public CsvImportResponse importCsvAuto(Long userId, MultipartFile file) {
        return doImport(userId, file, null);
    }

    private CsvImportResponse doImport(Long userId, MultipartFile file, MediaType selectedType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<CsvMediaRow> rows = parseRows(file);
        List<CsvImportError> errors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int duplicates = 0;

        for (int index = 0; index < rows.size(); index++) {
            CsvMediaRow row = rows.get(index);
            int lineNumber = index + 2;

            if (isBlank(row.getImdbId())) {
                errors.add(buildError(lineNumber, row, "Missing IMDb ID"));
                continue;
            }

            MediaType rowType = normalizeType(row.getTitleType());
            if (rowType == null) {
                skipped++;
                continue;
            }

            if (selectedType != null && rowType != selectedType) {
                skipped++;
                continue;
            }

            try {
                Media media = mediaRepository.findByImdbId(row.getImdbId().trim())
                        .orElseGet(() -> mediaRepository.save(mapToMedia(row, rowType)));

                if (userWatchedRepository.findByUserIdAndMediaId(user.getId(), media.getId()).isPresent()) {
                    duplicates++;
                    continue;
                }

                userWatchedRepository.save(UserWatched.builder()
                        .user(user)
                        .media(media)
                        .build());
                imported++;
            } catch (RuntimeException ex) {
                errors.add(buildError(lineNumber, row, ex.getMessage() == null ? "Import failed" : ex.getMessage()));
            }
        }

        CsvImportSummary summary = CsvImportSummary.builder()
                .totalLines(rows.size())
                .imported(imported)
                .skipped(skipped)
                .duplicates(duplicates)
                .errors(errors.size())
                .build();

        return CsvImportResponse.builder()
                .message(buildMessage(summary))
                .summary(summary)
                .errors(errors)
                .build();
    }

    private List<CsvMediaRow> parseRows(MultipartFile file) {
        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            return new CsvToBeanBuilder<CsvMediaRow>(reader)
                    .withType(CsvMediaRow.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read CSV file");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid CSV file");
        }
    }

    private Media mapToMedia(CsvMediaRow row, MediaType type) {
        return Media.builder()
                .imdbId(row.getImdbId().trim())
                .title(defaultTitle(row))
                .originalTitle(blankToNull(row.getOriginalTitle()))
                .mediaType(type)
                .year(blankToNull(row.getYear()))
                .runtimeMins(parseInteger(row.getRuntimeMins()))
                .genres(blankToNull(row.getGenres()))
                .imdbRating(parseBigDecimal(row.getImdbRating()))
                .numVotes(parseInteger(row.getNumVotes()))
                .releaseDate(parseDate(row.getReleaseDate()))
                .directors(blankToNull(row.getDirectors()))
                .imdbUrl(blankToNull(row.getUrl()))
                .build();
    }

    private MediaType normalizeType(String titleType) {
        if (isBlank(titleType)) {
            return null;
        }

        String normalized = titleType.trim().toLowerCase();
        return switch (normalized) {
            case "movie" -> MediaType.MOVIE;
            case "tv series", "tv mini series" -> MediaType.SERIES;
            case "video" -> null;
            default -> null;
        };
    }

    private CsvImportError buildError(int lineNumber, CsvMediaRow row, String reason) {
        return CsvImportError.builder()
                .lineNumber(lineNumber)
                .title(defaultTitle(row))
                .reason(reason)
                .build();
    }

    private String buildMessage(CsvImportSummary summary) {
        return "CSV import completed: %d imported, %d skipped, %d duplicates, %d errors"
                .formatted(summary.getImported(), summary.getSkipped(), summary.getDuplicates(), summary.getErrors());
    }

    private String defaultTitle(CsvMediaRow row) {
        String title = blankToNull(row.getTitle());
        return title != null ? title : "Unknown title";
    }

    private Integer parseInteger(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
