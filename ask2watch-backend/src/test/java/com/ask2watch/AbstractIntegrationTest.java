package com.ask2watch;

import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.PickOfWeek;
import com.ask2watch.model.User;
import com.ask2watch.model.UserWatched;
import com.ask2watch.repository.MediaRepository;
import com.ask2watch.repository.PickOfWeekRepository;
import com.ask2watch.repository.UserRepository;
import com.ask2watch.repository.UserWatchedRepository;
import com.ask2watch.service.AgentService;
import com.ask2watch.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected MediaRepository mediaRepository;

    @Autowired
    protected UserWatchedRepository userWatchedRepository;

    @Autowired
    protected PickOfWeekRepository pickOfWeekRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    protected AgentService agentService;

    @BeforeEach
    void clearDatabase() {
        try {
            // Disable foreign key constraints temporarily
            jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED");

            // Delete all data in reverse dependency order
            try {
                jdbcTemplate.execute("TRUNCATE TABLE user_watched CASCADE");
            } catch (Exception e) {
                // Table might not exist yet
            }
            try {
                jdbcTemplate.execute("TRUNCATE TABLE picks_of_week CASCADE");
            } catch (Exception e) {
                // Table might not exist yet
            }
            try {
                jdbcTemplate.execute("TRUNCATE TABLE media CASCADE");
            } catch (Exception e) {
                // Table might not exist yet
            }
            try {
                jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
            } catch (Exception e) {
                // Table might not exist yet
            }

            // Re-enable constraints
            jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
        } catch (Exception e) {
            // Database not yet initialized, ignore
        }
    }

    // --- Helpers ---

    protected String generateToken(User user) {
        return jwtService.generateToken(user);
    }

    protected String authHeader(User user) {
        return "Bearer " + generateToken(user);
    }

    protected User createUser(String username, String email, String rawPassword) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(new BCryptPasswordEncoder().encode(rawPassword))
                .build();
        return userRepository.save(user);
    }

    protected Media createMedia(String title, MediaType type, Integer tmdbId) {
        Media media = Media.builder()
                .title(title)
                .mediaType(type)
                .tmdbId(tmdbId)
                .imdbId("tt" + tmdbId)
                .build();
        return mediaRepository.save(media);
    }

    protected UserWatched createWatched(User user, Media media, Integer rating, String comment) {
        UserWatched uw = UserWatched.builder()
                .user(user)
                .media(media)
                .userRating(rating)
                .comment(comment)
                .build();
        return userWatchedRepository.save(uw);
    }

    protected PickOfWeek createPick(User user, Media media, LocalDate weekDate) {
        PickOfWeek pick = PickOfWeek.builder()
                .user(user)
                .media(media)
                .weekDate(weekDate)
                .createdByAgent(false)
                .build();
        return pickOfWeekRepository.save(pick);
    }
}
