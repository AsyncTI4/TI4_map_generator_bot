package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Imperial scores a public objective and the Mecatol Rex point, but the status phase already scores one public
// objective for free, so the card is only worth taking when it adds a point the seat would not get anyway.
class StrategyCardRankingTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
    }

    private void revealed(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private int bestPick(Player seat) {
        String[] ids = IntStream.rangeClosed(1, 8)
                .mapToObj(card -> "FFCC_" + seat.getFaction() + "_scPick_" + card)
                .toArray(String[]::new);
        List<PromptButton> picks =
                prompt("picks", PromptSource.PUBLIC, NOW, ids).enabledButtons();
        return StrategyCardRanking.initiative(
                StrategyCardRanking.best(test.game, seat, picks).orElseThrow());
    }

    // Lead From the Front is the only objective Nekro can score. The status phase scores it for free anyway, so
    // Imperial would add nothing: the seat takes another card and leaves Imperial to a seat that can score twice.
    @Test
    void leavesImperialWhenTheStatusPhaseCanScoreTheOnlyObjective() {
        revealed("lead");

        assertThat(bestPick(test.nekro)).isNotEqualTo(IMPERIAL);
    }

    // With Build Defenses met as well (the home dock plus three PDS, at most two per planet), Imperial scores one
    // objective and the status phase the other: a point the seat would otherwise wait a round for.
    @Test
    void takesImperialWhenTwoObjectivesAreScorable() {
        revealed("lead");
        revealed("build_defenses");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 2);
        Tile mehar = test.place("24", "202");
        test.nekro.addPlanet("meharxull");
        test.units(mehar, "meharxull", test.nekro, UnitType.Pds, 1);

        assertThat(bestPick(test.nekro)).isEqualTo(IMPERIAL);
    }

    // Holding Mecatol Rex makes Imperial a sure point even with nothing else to score.
    @Test
    void takesImperialWhileHoldingMecatolRex() {
        test.place("18", "000");
        test.nekro.addPlanet("mr");

        assertThat(bestPick(test.nekro)).isEqualTo(IMPERIAL);
    }
}
