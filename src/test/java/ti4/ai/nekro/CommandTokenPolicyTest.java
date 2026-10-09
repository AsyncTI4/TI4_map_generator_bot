package ti4.ai.nekro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The status phase lets every player gain two command tokens; some abilities and technologies add one.
class CommandTokenPolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    @Test
    void gainsTwoTokensByDefault() {
        assertThat(CommandTokenPolicy.statusPhaseGain(test.nekro)).isEqualTo(2);
    }

    @Test
    void solsVersatileGainsAThirdToken() {
        assertThat(test.sol.hasAbility("versatile")).isTrue();

        assertThat(CommandTokenPolicy.statusPhaseGain(test.sol)).isEqualTo(3);
    }

    @Test
    void hyperMetabolismGainsAThirdToken() {
        test.nekro.addTech("hm");

        assertThat(CommandTokenPolicy.statusPhaseGain(test.nekro)).isEqualTo(3);
    }

    @Test
    void versatileAndHyperMetabolismStack() {
        test.sol.addTech("hm");

        assertThat(CommandTokenPolicy.statusPhaseGain(test.sol)).isEqualTo(4);
    }

    // With few tokens, tactics come first: two of them, then the starting fleet pool of three, then two strategy
    // tokens for follows; past that, tactics again.
    @Test
    void growsTwoTacticsThenTwoStrategyTokensThenTactics() {
        test.nekro.setTacticalCC(0);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(0);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");

        test.nekro.setTacticalCC(2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.nekro.setStrategicCC(1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.nekro.setStrategicCC(2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Raise a Fleet needs five non-fighter ships in one system, which fleet supply only allows with five fleet tokens.
    // Those tokens are worth more than strategy tokens once a stack can grow into them, but not before.
    @Test
    void growsTheFleetPoolForRaiseAFleetOnceAStackCanReachIt() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.nekro.setTacticalCC(2);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(1);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.units(home, "space", test.nekro, UnitType.Destroyer, 2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_fleet_cc");
    }

    @Test
    void stillTakesTwoTacticTokensBeforeGrowingTheFleet() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.nekro.setTacticalCC(1);
        test.nekro.setFleetCC(3);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 3);

        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Fleet beyond three only pays off when there are ships to use it: a stack of three with more ships elsewhere
    // that could join it. Spare tokens then go there before piling up as tactics.
    @Test
    void growsTheFleetForABiggerStackOnlyWhenThereAreShipsToFillIt() {
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 3);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");

        Tile away = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(away, "space", test.nekro, UnitType.Destroyer, 1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_fleet_cc");
    }

    // Fleet Regulations caps the fleet pool at four, even with a five-ship stack left over from before the law, and
    // makes Raise a Fleet (five ships in one system) impossible, so no token goes toward it.
    @Test
    void keepsTheFleetPoolWithinFleetRegulations() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.game.addLaw("regulations", null);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(4);
        test.nekro.setStrategicCC(2);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 5);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 9)).containsEntry(CommandTokenPolicy.Pool.FLEET, 4);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isNotEqualTo("increase_fleet_cc");
    }

    // Late in the game, with tactics piling up unused, a third strategy token is kept for follows, never a fourth.
    @Test
    void keepsAtMostThreeStrategyTokens() {
        test.nekro.setTacticalCC(5);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.nekro.setStrategicCC(3);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Redistribution moves a token out of whichever pool holds more than the priorities give it, least important pool
    // first. Here the fifth fleet token is no longer needed once Raise a Fleet is not on the table.
    @Test
    void redistributesSurplusTokensTowardTheIdealPools() {
        test.nekro.setTacticalCC(1);
        test.nekro.setFleetCC(5);
        test.nekro.setStrategicCC(2);
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 8))
                .containsEntry(CommandTokenPolicy.Pool.TACTIC, 3)
                .containsEntry(CommandTokenPolicy.Pool.FLEET, 3)
                .containsEntry(CommandTokenPolicy.Pool.STRATEGY, 2);
        assertThat(CommandTokenPolicy.poolToRedistributeFrom(test.game, test.nekro))
                .contains("decrease_fleet_cc");

        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        assertThat(CommandTokenPolicy.poolToRedistributeFrom(test.game, test.nekro))
                .isEmpty();
    }

    // Each move takes a token from a pool above its ideal and gives it back to one below, so redistribution always
    // ends: replaying the moves from any starting pools reaches the ideal within a few presses.
    @Test
    void redistributionAlwaysSettles() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        for (int tactic = 0; tactic <= 6; tactic++) {
            for (int fleet = 3; fleet <= 6; fleet++) {
                for (int strategy = 0; strategy <= 4; strategy++) {
                    test.nekro.setTacticalCC(tactic);
                    test.nekro.setFleetCC(fleet);
                    test.nekro.setStrategicCC(strategy);
                    int moves = 0;
                    java.util.Optional<String> from;
                    while ((from = CommandTokenPolicy.poolToRedistributeFrom(test.game, test.nekro)).isPresent()) {
                        assertThat(moves++).isLessThan(20);
                        adjust(from.get(), -1);
                        adjust(CommandTokenPolicy.poolToGrow(test.game, test.nekro), 1);
                    }
                    assertThat(test.nekro.getFleetCC()).isGreaterThanOrEqualTo(3);
                }
            }
        }
    }

    private void adjust(String handler, int delta) {
        if (handler.contains("tactic")) test.nekro.setTacticalCC(test.nekro.getTacticalCC() + delta);
        if (handler.contains("fleet")) test.nekro.setFleetCC(test.nekro.getFleetCC() + delta);
        if (handler.contains("strategy")) test.nekro.setStrategicCC(test.nekro.getStrategicCC() + delta);
    }
}
