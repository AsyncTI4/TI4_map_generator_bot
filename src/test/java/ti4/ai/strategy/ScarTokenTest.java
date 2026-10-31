package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Adapt New Strategies through an Entropic Scar: buying the faction technology at the status phase costs a strategy
// token, so the AI keeps one back while it has ships in or beside a scar, and it takes a third strategy token at the
// status phase while a reachable research route still needs work.
class ScarTokenTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile scar;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        String position = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.solHomeBeside(position);
        scar = test.place("114", position);
        test.sol.setSecret("ans");
        test.sol.setTacticalCC(3);
    }

    @Test
    void keepsAStrategyTokenForTheScarPurchase() {
        test.units(scar, "space", test.sol, UnitType.Destroyer, 1);
        test.sol.setStrategicCC(1);

        assertThat(ScarToken.kept(test.game, test.sol)).isEqualTo(1);
        assertThat(StrategyTokenCost.canSpare(test.game, test.sol)).isFalse();

        test.sol.setStrategicCC(2);
        assertThat(StrategyTokenCost.canSpare(test.game, test.sol)).isTrue();
    }

    @Test
    void keepsNothingWithoutTheSecret() {
        test.units(scar, "space", test.sol, UnitType.Destroyer, 1);
        test.sol.removeSecret(test.sol.getSecretsUnscored().get("ans"));

        assertThat(ScarToken.kept(test.game, test.sol)).isZero();
    }

    @Test
    void takesAThirdStrategyTokenWhileTheRouteNeedsResearch() {
        test.game.setPhaseOfGame("status");
        Map<CommandTokenPolicy.Pool, Integer> held = CommandTokenPolicy.ideal(test.game, test.sol, 6);
        test.sol.removeSecret(test.sol.getSecretsUnscored().get("ans"));
        Map<CommandTokenPolicy.Pool, Integer> without = CommandTokenPolicy.ideal(test.game, test.sol, 6);

        assertThat(held.get(CommandTokenPolicy.Pool.STRATEGY))
                .isGreaterThan(without.get(CommandTokenPolicy.Pool.STRATEGY));
    }
}
