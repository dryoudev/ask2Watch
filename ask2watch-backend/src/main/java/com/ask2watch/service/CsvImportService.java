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
                if (!CsvImportHelper.isRowSupported(row.getTitleType(), type)) {
                    log.debug("Skipping '{}' because title type '{}' is not handled for {}", row.getTitle(), row.getTitleType(), type);
                    continue;
                }
                if (mediaRepository.findByImdbId(row.getImdbId()).isPresent()) {
                    continue;
                }

                Media media = CsvImportHelper.mapToMedia(row, type);
                tmdbService.enrichMedia(media);
                mediaRepository.save(media);
                imported++;
            } catch (Exception e) {
                log.warn("Failed to import '{}': {}", row.getTitle(), e.getMessage());
            }
        }

        log.info("Imported {} {} from {}", imported, type, resourcePath);
        return imported;
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

}
