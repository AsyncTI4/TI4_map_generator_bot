package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Sol holds Jord (4 resources) and a strategy token while Nekro holds an unplayed Technology: the seat that wants to
// follow keeps the 4 resources of the secondary out of its builds until the card is played.
class TechnologyFollowReserveTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(2);
        test.solHomeBeside("305");
        sol.setStrategicCC(1);
        sol.setTg(0);
        test.nekro.addSC(TECHNOLOGY);
        test.isActive(sol, "action");
    }

    @Test
    void keepsTheSecondaryCostWhileARivalHoldsTechnologyUnplayed() {
        assertThat(TechnologyFollowReserve.of(test.game, sol)).isEqualTo(SpendCost.resources(4));
    }

    @Test
    void reservesNothingOnceTechnologyIsPlayed() {
        test.game.setSCPlayed(TECHNOLOGY, true);

        assertThat(TechnologyFollowReserve.of(test.game, sol)).isEqualTo(SpendCost.NONE);
    }

    @Test
    void reservesNothingWithoutAStrategyToken() {
        sol.setStrategicCC(0);

        assertThat(TechnologyFollowReserve.of(test.game, sol)).isEqualTo(SpendCost.NONE);
    }

    @Test
    void reservesNothingWhenTheSeatHoldsTechnologyItself() {
        test.nekro.removeSC(TECHNOLOGY);
        sol.addSC(TECHNOLOGY);

        assertThat(TechnologyFollowReserve.of(test.game, sol)).isEqualTo(SpendCost.NONE);
    }

    @Test
    void reservesNothingForASeatThatCannotResearch() {
        test.nekro.removeSC(TECHNOLOGY);
        test.sol.addSC(TECHNOLOGY);
        assertThat(StrategyCardRules.cannotResearch(test.nekro)).isTrue();
        test.nekro.setStrategicCC(1);

        assertThat(TechnologyFollowReserve.of(test.game, test.nekro)).isEqualTo(SpendCost.NONE);
    }

    @Test
    void reservesNothingOutsideTheActionPhase() {
        test.game.setPhaseOfGame("agenda");

        assertThat(TechnologyFollowReserve.of(test.game, sol)).isEqualTo(SpendCost.NONE);
    }

    // The Leadership purchase keeps the 4 resources of the follow unspent; with TECH_FOLLOW_KEEP off (the other two
    // split flags on) it keeps nothing, as before 4b2988f8f6.
    @Test
    void leadershipKeepsTheFollowOnlyWithTheExperimentOn() {
        Wallet wallet = Wallet.of(test.game, sol);
        assertThat(TokenPurchase.technologyFollowToKeep(test.game, sol, wallet)).isEqualTo(SpendCost.resources(4));

        AiExperiments.disable(sol, Set.of(AiExperiments.Experiment.TECH_FOLLOW_KEEP));

        assertThat(AiExperiments.enabled(sol, AiExperiments.Experiment.DIPLOMACY_READIED_PLANNING))
                .isTrue();
        assertThat(AiExperiments.enabled(sol, AiExperiments.Experiment.XXCHA_LEADER_READIES))
                .isTrue();
        assertThat(TokenPurchase.technologyFollowToKeep(test.game, sol, wallet)).isEqualTo(SpendCost.NONE);
    }

    @Test
    void reservesNothingWhenTheSeatCouldNotPayAnyway() {
        sol.exhaustPlanet("jord");

        assertThat(TechnologyFollowReserve.of(test.game, sol)).isEqualTo(SpendCost.NONE);
    }
}
