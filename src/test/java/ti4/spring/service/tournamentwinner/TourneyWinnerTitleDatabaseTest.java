package ti4.spring.service.tournamentwinner;

import static org.assertj.core.api.Assertions.assertThat;

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
import ti4.spring.service.persistence.EarnedTitle;
import ti4.spring.service.persistence.StandaloneTitleEntity;
import ti4.spring.service.persistence.StandaloneTitleEntityRepository;
import ti4.spring.service.persistence.UserEntity;
import ti4.spring.service.persistence.UserEntityRepository;
import ti4.test.PostgresDatabaseTest;

// Runs against a real Postgres; see PlayerTitleServiceDatabaseTest for the local setup.
// Titles are stored as plain text; the emoji column holds the application emoji's name.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = TourneyWinnerTitleDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TourneyWinnerTitleDatabaseTest extends PostgresDatabaseTest {

    @Autowired
    private TourneyWinnerService service;

    @Autowired
    private TournamentWinnerRepository winnerRepository;

    @Autowired
    private StandaloneTitleEntityRepository standaloneTitleRepository;

    @Autowired
    private UserEntityRepository userRepository;

    @BeforeEach
    void clearTables() {
        winnerRepository.deleteAll();
        standaloneTitleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void recordingAWinnerGrantsTheTitleWithTheDefaultEmojiAndCreatesTheUser() {
        service.add("1", "alice", "Fall Cup");

        assertThat(standaloneTitleRepository.findEarnedTitlesByUserId("1"))
                .containsExactly(new EarnedTitle("Fall Cup Winner", "Fall Cup", "LegendaryPlanet"));
        assertThat(userRepository.findById("1")).map(UserEntity::getName).contains("alice");
    }

    @Test
    void aLazaxTournamentGetsTheLazaxEmoji() {
        service.add("1", "alice", "Lazax Season 2");

        assertThat(standaloneTitleRepository.findEarnedTitlesByUserId("1"))
                .containsExactly(new EarnedTitle("Lazax Season 2 Winner", "Lazax Season 2", "Lazax"));
    }

    @Test
    void removingAWinnerRevokesOnlyThatTournamentsTitle() {
        service.add("1", "alice", "Fall Cup");
        service.add("1", "alice", "Spring Cup");

        service.remove("1", "Fall Cup");

        assertThat(standaloneTitleRepository.findEarnedTitlesByUserId("1"))
                .containsExactly(new EarnedTitle("Spring Cup Winner", "Spring Cup", "LegendaryPlanet"));
    }

    @Test
    void recordingTheSameWinTwiceKeepsOneTitle() {
        service.add("1", "alice", "Fall Cup");
        service.add("1", "alice", "Fall Cup");

        assertThat(standaloneTitleRepository.findEarnedTitlesByUserId("1"))
                .containsExactly(new EarnedTitle("Fall Cup Winner", "Fall Cup", "LegendaryPlanet"));
    }

    @Test
    void aTournamentWhoseNameEndsAnotherStillGetsItsOwnTitle() {
        // "Season 1" is a suffix of "Lazax Season 1", so a substring match would wrongly treat it as already held.
        service.add("1", "alice", "Lazax Season 1");
        service.add("1", "alice", "Season 1");

        assertThat(standaloneTitleRepository.findEarnedTitlesByUserId("1"))
                .containsExactlyInAnyOrder(
                        new EarnedTitle("Lazax Season 1 Winner", "Lazax Season 1", "Lazax"),
                        new EarnedTitle("Season 1 Winner", "Season 1", "LegendaryPlanet"));
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = {StandaloneTitleEntity.class, TournamentWinner.class})
    @EnableJpaRepositories(
            basePackageClasses = {StandaloneTitleEntityRepository.class, TournamentWinnerRepository.class})
    @Import({TourneyWinnerService.class, TournamentWinnerTitleService.class})
    static class TestConfig {}
}
