package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Signal Jamming puts a rival's command token in a system, and ships in a system holding their own token cannot move
// (LRR 58.4c). Sol sits on 9 of 10 with the only carrier that can land on the custodians this round (LRR 55), so
// jamming that staging system is worth the game under WIN_BREAK_DENIAL.
class SignalJammingWinBreakTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String STAGING = "101";
    private static final String WATCH = "102";
    private static final int IMPERIAL = 8;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(4);
        test.aiIsActive("action");
        test.nekroHome();
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        Tile staging = test.place("46", STAGING);
        test.units(staging, "space", test.sol, UnitType.Carrier, 1);
        test.units(staging, "space", test.sol, UnitType.Infantry, 1);
        Tile watch = test.place("47", WATCH);
        test.units(watch, "space", test.nekro, UnitType.Destroyer, 1);
        test.sol.setTg(6);
        test.sol.setTacticalCC(3);
        test.nekro.setTacticalCC(3);
        test.points(test.sol, 9);
    }

    @Test
    void jamsTheStagingSystemOfARivalAboutToWin() {
        Plan plan = TokenCards.signalJammingPlan(test.game, test.nekro).orElseThrow();

        assertThat(plan.target()).endsWith("sol_" + STAGING);
        assertThat(plan.value()).isGreaterThanOrEqualTo(ScoringStakes.WIN_VALUE);
    }

    @Test
    void withTheExperimentOffTheStagingSystemIsWorthNothingExtra() {
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_BREAK_DENIAL));

        Plan plan = TokenCards.signalJammingPlan(test.game, test.nekro).orElseThrow();

        assertThat(plan.value()).isLessThan(ScoringStakes.WIN_VALUE);
    }
}
