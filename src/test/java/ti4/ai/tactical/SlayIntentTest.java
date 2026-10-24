package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// A seat that sees, on public information, another seat winning before it can score announces "We must slay X." once,
// and keeps the targets in a per-game register so other AI seats can read its intent. The register lives in memory
// because the runtime reloads the game from disk every tick: a stored value set while deciding was lost, and the seat
// re-announced on every tick in self-play until the game stalled.
class SlayIntentTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int TRADE = 5;
    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private Player hacan;
    private int awards;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(5);
        test.aiIsActive("action");
        test.nekroHome();
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        hacan = test.addSeat("300000000000000003", "hacan", "yellow");
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        test.nekro.addSC(TRADE);
        test.sol.addSC(LEADERSHIP);
        test.sol.setTacticalCC(2);
        test.sol.setStrategicCC(1);
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Test points " + (++awards), 8));
    }

    @Test
    void announcesTheTargetsOnceAndStoresThem() {
        AiDecision first = SlayIntent.announce(test.context()).orElseThrow();

        assertThat(first)
                .isInstanceOfSatisfying(
                        AiDecision.Announce.class,
                        announce -> assertThat(announce.text())
                                .endsWith("We must slay " + test.sol.getRepresentationNoPing() + "."));
        assertThat(SlayIntent.announce(test.context())).isEmpty();
        assertThat(SlayIntent.targetsOf(test.game, test.nekro)).containsExactly("sol");
        assertThat(SlayIntent.hunts(test.game, test.nekro, test.sol)).isTrue();
        assertThat(SlayIntent.huntsOthers(test.game, test.nekro, hacan)).isTrue();
        assertThat(SlayIntent.huntsOthers(test.game, test.nekro, test.sol)).isFalse();
    }

    // Intent is per round: last round's declaration no longer counts.
    @Test
    void aDeclarationLapsesWithTheRound() {
        SlayIntent.announce(test.context());
        test.game.setRound(6);

        assertThat(SlayIntent.targetsOf(test.game, test.nekro)).isEmpty();
    }

    // In Fog of War the seat neither announces nor stores anything.
    @Test
    void staysSilentInFogOfWar() {
        test.game.setFowMode(true);

        assertThat(SlayIntent.announce(test.context())).isEmpty();
        assertThat(SlayIntent.targetsOf(test.game, test.nekro)).isEmpty();
    }
}
