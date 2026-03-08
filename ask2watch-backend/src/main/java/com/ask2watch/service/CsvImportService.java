package com.ask2watch.service;

import com.ask2watch.dto.csv.CsvMediaRow;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.User;
import com.ask2watch.model.UserWatched;
import com.ask2watch.repository.MediaRepository;
import com.ask2watch.repository.UserRepository;
import com.ask2watch.repository.UserWatchedRepository;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@Slf4j
public class CsvImportService {

    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final UserWatchedRepository userWatchedRepository;
    private final TmdbService tmdbService;
    private final PasswordEncoder passwordEncoder;
    private final String defaultAdminPassword;

    public CsvImportService(
            MediaRepository mediaRepository,
            UserRepository userRepository,
            UserWatchedRepository userWatchedRepository,
            TmdbService tmdbService,
            PasswordEncoder passwordEncoder,
            @Value("${app.default-admin-password}") String defaultAdminPassword) {
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.userWatchedRepository = userWatchedRepository;
        this.tmdbService = tmdbService;
        this.passwordEncoder = passwordEncoder;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    public void importAll() {
        if (mediaRepository.count() > 0) {
            log.info("Database already populated, skipping CSV import.");
            return;
        }

        log.info("Starting CSV import...");
        long start = System.currentTimeMillis();

        int movies = importFile("data/moviesWatched.csv", MediaType.MOVIE);
        int series = importFile("data/tvSeriesWatched.csv", MediaType.SERIES);

        User defaultUser = createDefaultUser();
        linkAllMediaToUser(defaultUser);

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("CSV import complete: {} movies + {} series in {}s", movies, series, elapsed);
    }

    private int importFile(String resourcePath, MediaType type) {
        List<CsvMediaRow> rows;
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            Reader reader = new InputStreamReader(resource.getInputStream());
            rows = new CsvToBeanBuilder<CsvMediaRow>(reader)
                    .withType(CsvMediaRow.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
        } catch (Exception e) {
            log.error("Failed to read CSV file {}: {}", resourcePath, e.getMessage());
            return 0;
        }

        int imported = 0;
        for (CsvMediaRow row : rows) {
            try {
                if ("Video".equalsIgnoreCase(row.getTitleType())) {
                    continue;
                }
                if (mediaRepository.findByImdbId(row.getImdbId()).isPresent()) {
                    continue;
                }

                Media media = mapToMedia(row, type);
                tmdbService.enrichMedia(media);
                mediaRepository.save(media);
                imported++;

                Thread.sleep(250);
            } catch (Exception e) {
                log.warn("Failed to import '{}': {}", row.getTitle(), e.getMessage());
            }
        }

        log.info("Imported {} {} from {}", imported, type, resourcePath);
        return imported;
    }

    private Media mapToMedia(CsvMediaRow row, MediaType type) {
        return Media.builder()
                .imdbId(row.getImdbId())
                .title(row.getTitle())
                .originalTitle(row.getOriginalTitle())
                .mediaType(type)
                .year(row.getYear())
                .runtimeMins(parseInteger(row.getRuntimeMins()))
                .genres(row.getGenres())
                .imdbRating(parseBigDecimal(row.getImdbRating()))
                .numVotes(parseInteger(row.getNumVotes()))
                .releaseDate(parseDate(row.getReleaseDate()))
                .directors(blankToNull(row.getDirectors()))
                .imdbUrl(row.getUrl())
                .build();
    }

    private User createDefaultUser() {
        return userRepository.findByEmail("admin@ask2watch.com")
                .orElseGet(() -> {
                    log.info("Creating default admin user with configured password");
                    return userRepository.save(User.builder()
                            .username("admin")
                            .email("admin@ask2watch.com")
                            .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                            .build());
                });
    }

    private void linkAllMediaToUser(User user) {
        List<Media> allMedia = mediaRepository.findAll();
        for (Media media : allMedia) {
            if (userWatchedRepository.findByUserIdAndMediaId(user.getId(), media.getId()).isEmpty()) {
                userWatchedRepository.save(UserWatched.builder()
                        .user(user)
                        .media(media)
                        .build());
            }
        }
        log.info("Linked {} media to user '{}'", allMedia.size(), user.getUsername());
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
