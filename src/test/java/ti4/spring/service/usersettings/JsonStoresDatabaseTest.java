package ti4.spring.service.usersettings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import ti4.json.JsonMapperManager;
import ti4.model.metadata.AutoPingMetadataManager.AutoPing;
import ti4.model.metadata.TechSummariesMetadataManager.FactionTechSummary;
import ti4.model.metadata.TechSummariesMetadataManager.RoundTechSummaries;
import ti4.settings.users.UserSettings;
import ti4.spring.service.autoping.AutoPingService;
import ti4.spring.service.techsummary.TechSummaryService;
import ti4.test.PostgresDatabaseTest;

// User settings, auto pings and tech summaries used to live in JSON files that were rewritten in place, so a kill
// mid-write could truncate them. These tests run the database-backed replacements against a real Postgres:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = JsonStoresDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JsonStoresDatabaseTest extends PostgresDatabaseTest {

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserSettingsEntityRepository userSettingsRepository;

    @Autowired
    private AutoPingService autoPingService;

    @Autowired
    private TechSummaryService techSummaryService;

    @BeforeEach
    void clearTables() {
        userSettingsRepository.deleteAll();
        autoPingService.delete(autoPingService.gameNames());
        techSummaryService.replaceAll(techSummaryService.findAll().keySet(), Map.of());
    }

    @Test
    void userSettingsRoundTripEveryField() {
        UserSettings settings = userSettings("100");
        settings.setPreferredColors(List.of("red", "blue"));
        settings.setGameLimit(7);
        settings.setPrefersSarweenMsg(false);
        settings.setMatchmakingPlayerCounts(List.of("6"));

        userSettingsService.save(settings);

        assertThat(userSettingsService.find("100"))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(settings);
    }

    @Test
    void savingAgainReplacesTheStoredSettings() {
        UserSettings settings = userSettings("100");
        userSettingsService.save(settings);
        settings.setGameLimit(3);

        userSettingsService.save(settings);

        assertThat(userSettingsService.find("100"))
                .get()
                .extracting(UserSettings::getGameLimit)
                .isEqualTo(3);
        assertThat(userSettingsRepository.count()).isEqualTo(1);
    }

    @Test
    void updateReadsModifiesAndWritesInOneStep() {
        userSettingsService.update("300", () -> userSettings("300"), settings -> settings.setGameLimit(4));
        userSettingsService.update(
                "300", () -> userSettings("300"), settings -> settings.setGameLimit(settings.getGameLimit() + 1));

        assertThat(userSettingsService.find("300"))
                .get()
                .extracting(UserSettings::getGameLimit)
                .isEqualTo(5);
    }

    @Test
    void updateAllChangesExistingAndCreatesMissingSettingsInOneCall() {
        userSettingsService.update("400", () -> userSettings("400"), settings -> settings.setGameLimit(2));

        userSettingsService.updateAll(
                Map.of(
                        "400", settings -> settings.setGameLimit(settings.getGameLimit() + 1),
                        "401", settings -> settings.setGameLimit(7)),
                JsonStoresDatabaseTest::userSettings);

        assertThat(userSettingsService.find("400"))
                .get()
                .extracting(UserSettings::getGameLimit)
                .isEqualTo(3);
        assertThat(userSettingsService.find("401"))
                .get()
                .extracting(UserSettings::getGameLimit)
                .isEqualTo(7);
    }

    @Test
    void autoPingComputeUpdateAndDelete() {
        autoPingService.compute("pbd1", _ -> new AutoPing(10, 0, false));
        autoPingService.compute("pbd1", ping -> new AutoPing(20, ping.pingCount() + 1, ping.quickPing()));
        autoPingService.updateIfPresent("pbd2", ping -> new AutoPing(99, 99, true));

        assertThat(autoPingService.find("pbd1")).contains(new AutoPing(20, 1, false));
        assertThat(autoPingService.find("pbd2")).isEmpty();

        autoPingService.delete(List.of("pbd1", "pbd-never-stored"));
        assertThat(autoPingService.gameNames()).isEmpty();
    }

    // AutoPingCron passes every ended game (tens of thousands of names) on each run; only stored ones are deleted,
    // so the delete never builds a statement with more bind parameters than Postgres allows.
    @Test
    void autoPingDeleteToleratesEveryEndedGameName() {
        autoPingService.compute("pbd5", _ -> new AutoPing(1, 0, false));
        List<String> endedGames = new ArrayList<>(
                IntStream.range(0, 40_000).mapToObj(i -> "ended" + i).toList());
        endedGames.add("pbd5");

        autoPingService.delete(endedGames);

        assertThat(autoPingService.gameNames()).isEmpty();
    }

    @Test
    void techSummariesRoundTripAndReplaceAllDropsRemovedGames() {
        techSummaryService.save("pbd1", summaries(3, "sol", "fl"));
        techSummaryService.save("pbd2", summaries(2, "hacan", "sdn"));

        Map<String, RoundTechSummaries> all = techSummaryService.findAll();
        assertThat(all).containsOnlyKeys("pbd1", "pbd2");
        assertThat(all.get("pbd1").round()).isEqualTo(3);
        assertThat(all.get("pbd1").techSummaries())
                .singleElement()
                .satisfies(summary -> assertThat(summary.getTech()).containsExactly("fl"));

        Set<String> previous = Set.copyOf(all.keySet());
        all.remove("pbd2");
        techSummaryService.replaceAll(previous, all);

        assertThat(techSummaryService.findAll()).containsOnlyKeys("pbd1");
    }

    @Test
    void techSummaryListsLoadMutable() {
        techSummaryService.save("pbd1", summaries(1, "sol", "fl"));

        RoundTechSummaries loaded = techSummaryService.find("pbd1").orElseThrow();

        loaded.techSummaries().add(loaded.techSummaries().getFirst());
        assertThat(loaded.techSummaries()).hasSize(2);
    }

    private static UserSettings userSettings(String userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        return settings;
    }

    private static RoundTechSummaries summaries(int round, String faction, String tech) {
        String json = "{\"faction\":\"" + faction + "\",\"tech\":[\"" + tech + "\"]}";
        FactionTechSummary summary = JsonMapperManager.basic().readValue(json, FactionTechSummary.class);
        return new RoundTechSummaries(round, new ArrayList<>(List.of(summary)));
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = {UserSettingsEntity.class, AutoPingService.class, TechSummaryService.class})
    @EnableJpaRepositories(
            basePackageClasses = {UserSettingsEntity.class, AutoPingService.class, TechSummaryService.class})
    @Import({UserSettingsService.class, AutoPingService.class, TechSummaryService.class})
    static class TestConfig {}
}
