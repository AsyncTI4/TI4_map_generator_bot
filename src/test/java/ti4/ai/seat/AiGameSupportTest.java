package ti4.ai.seat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// /ai add asks AiGameSupport whether a seat may join. The global AI switch is a persisted bot setting, so it is
// stubbed here rather than written to disk.
class AiGameSupportTest extends BaseTi4Test {

    private MockedStatic<AiSettings> settings;
    private AiTestGame test;

    @BeforeEach
    void setUp() {
        settings = Mockito.mockStatic(AiSettings.class, Mockito.CALLS_REAL_METHODS);
        settings.when(AiSettings::isEnabled).thenReturn(true);
        test = new AiTestGame();
        test.game.setRound(1);
    }

    @AfterEach
    void tearDown() {
        settings.close();
    }

    @Test
    void allowsASecondAiSeatWithADifferentSupportedFaction() {
        assertThat(AiGameSupport.refusalReason(test.game, "hacan")).isNull();
    }

    @Test
    void refusesAFactionAlreadyAtTheTable() {
        assertThat(AiGameSupport.refusalReason(test.game, "sol")).contains("already taken");
        assertThat(AiGameSupport.refusalReason(test.game, "nekro")).contains("already taken");
    }

    @Test
    void refusesAFactionTheAiCannotPlay() {
        assertThat(AiGameSupport.refusalReason(test.game, "arborec")).contains("AI seats can play");
    }

    @Test
    void refusesEverythingWhileAiPlayersAreDisabled() {
        settings.when(AiSettings::isEnabled).thenReturn(false);

        assertThat(AiGameSupport.refusalReason(test.game, "hacan")).contains("disabled");
    }

    @Test
    void refusesOnceTheGameHasStarted() {
        test.game.setRound(2);

        assertThat(AiGameSupport.refusalReason(test.game, "hacan")).contains("before the secret objectives are dealt");
    }

    // Round 1 status cleanup resets the per-round markers, so a game in its first status phase is still started.
    @Test
    void refusesInTheFirstRoundsStatusPhase() {
        test.game.setPhaseOfGame("statusHomework");

        assertThat(AiGameSupport.refusalReason(test.game, "hacan")).isNotNull();
    }

    @Test
    void refusesWhileADraftIsRunning() {
        test.game.setPhaseOfGame("miltydraft");

        assertThat(AiGameSupport.refusalReason(test.game, "hacan")).contains("draft");
    }

    // Adding the first AI seat switches the game to homebrew itself, so that flag must not block a second seat.
    @Test
    void ignoresHomebrewOnceTheGameAlreadyHasAnAiSeat() {
        test.game.setHomebrew(true);

        assertThat(AiGameSupport.unsupportedModes(test.game)).doesNotContain("Homebrew");
        assertThat(AiGameSupport.refusalReason(test.game, "hacan")).isNull();
    }

    @Test
    void stillRefusesAHomebrewGameWithoutAiSeats() {
        Game humans = new Game();
        humans.setName("humans-only");
        humans.setRound(1);
        humans.setHomebrew(true);
        Player human = humans.addPlayer(AiTestGame.HUMAN_ID, "Human");
        human.setFaction("sol");
        human.setColor("blue");

        assertThat(AiGameSupport.unsupportedModes(humans)).contains("Homebrew");
        assertThat(AiGameSupport.refusalReason(humans, "nekro")).contains("Homebrew");
    }

    @Test
    void firstFreeFactionSkipsTakenFactions() {
        assertThat(AiGameSupport.firstFreeFaction(test.game)).isEqualTo("sardakk");

        test.addSeat("7100000555555555", "sardakk", "red");

        assertThat(AiGameSupport.firstFreeFaction(test.game)).isEqualTo("hacan");
    }

    @Test
    void noFreeFactionOnceEverySupportedFactionIsTaken() {
        String[] colors = {"red", "yellow", "green", "purple", "orange", "pink", "gray", "brown"};
        int seat = 0;
        for (String faction : AiSettings.SUPPORTED_FACTIONS) {
            if (AiGameSupport.isTaken(test.game, faction)) continue;
            test.addSeat("71000005555555" + (10 + seat), faction, colors[seat]);
            seat++;
        }

        assertThat(AiGameSupport.firstFreeFaction(test.game)).isNull();
    }
}
