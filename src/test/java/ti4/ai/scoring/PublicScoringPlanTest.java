package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.eval.DecidingSeat;
import ti4.testUtils.BaseTi4Test;

class PublicScoringPlanTest extends BaseTi4Test {
    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.aiIsActive("action");
        test.nekroHome();
        for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
    }

    private void reveal(String... ids) {
        for (String id : ids)
            test.game
                    .getRevealedPublicObjectives()
                    .put(id, test.game.getRevealedPublicObjectives().size() + 1);
    }

    @Test
    void tokensAreSpentOnceAcrossScoringWindows() {
        reveal("lead", "galvanize");
        test.nekro.setTacticalCC(6);
        test.nekro.setStrategicCC(0);
        assertThat(PublicScoringPlan.points(test.game, test.nekro, 2)).isEqualTo(2);
        test.nekro.setTacticalCC(9);
        assertThat(PublicScoringPlan.points(test.game, test.nekro, 2)).isEqualTo(3);
        assertThat(test.nekro.getTacticalCC()).isEqualTo(9);
    }

    @Test
    void resourcesAndInfluenceCompeteForTradeGoods() {
        reveal("monument", "sway_council");
        test.nekro.setTg(8);
        assertThat(PublicScoringPlan.points(test.game, test.nekro, 2)).isEqualTo(1);
        test.nekro.setTg(12);
        assertThat(PublicScoringPlan.points(test.game, test.nekro, 2)).isEqualTo(2);
        assertThat(test.nekro.getTg()).isEqualTo(12);
        assertThat(test.nekro.getReadiedPlanets()).contains("mordaiii");
    }

    @Test
    void freeObjectiveFillsAWindowWithoutConsumingTheSpendWallet() {
        reveal("develop", "centralize_trade", "trade_routes");
        test.nekro.addTech("cv2");
        test.nekro.addTech("dd2");
        test.nekro.setTg(10);
        assertThat(PublicScoringPlan.points(test.game, test.nekro, 2)).isEqualTo(3);
        assertThat(PublicScoringPlan.points(test.game, test.nekro, 0)).isZero();
    }

    @Test
    void preservesTheUnlockedWinnuHeroScoringWindow() {
        reveal("develop", "centralize_trade", "trade_routes");
        test.nekro.addTech("cv2");
        test.nekro.addTech("dd2");
        test.nekro.setTg(15);
        test.nekro.addSC(8);
        test.nekro.addLeader("winnuhero");
        test.nekro.getLeader("winnuhero").orElseThrow().setLocked(false);
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(4);
    }

    @Test
    void experimentFollowsTheDecidingPlayerWhenEvaluatingARival() {
        reveal("centralize_trade", "trade_routes");
        test.nekro.setTg(5);
        AiExperiments.disable(test.nekro, Set.of(AiExperiments.Experiment.READY_PUBLIC_POINTS));
        assertThat(DecidingSeat.of(test.nekro, () -> ScoringPaths.readyPoints(test.game, test.nekro)))
                .isZero();
        assertThat(DecidingSeat.of(test.sol, () -> ScoringPaths.readyPoints(test.game, test.nekro)))
                .isEqualTo(1);
    }
}
