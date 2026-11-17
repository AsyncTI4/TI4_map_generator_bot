package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The user's tactical strength rating (2026-11-17), replacing a fear rating: each AI rates itself from what it knows
// (its own hand counts 0.5 per mean or combat card) and every rival from public information only (0.2 per card in
// hand). Both count the cost of the units on the board, +1 Assault Cannon, +2 X-89, +1 PDS II and a per-faction
// bonus to come. Angering a rival weighs more when the rival is strong or the seat is weak, and less the other way.
class TacticalStrengthTest extends BaseTi4Test {

    private static final double CLOSE = 1e-9;

    private AiTestGame test;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        nekro = test.nekro;
        sol = test.sol;
    }

    @Test
    void technologiesAddStrength() {
        double base = TacticalStrength.seenBy(test.game, sol);
        sol.addTech("asc");
        sol.addTech("x89");
        sol.addTech("pds2");

        assertThat(TacticalStrength.seenBy(test.game, sol) - base)
                .isCloseTo(
                        TacticalStrength.ASSAULT_CANNON + TacticalStrength.X89 + TacticalStrength.PDS_TWO,
                        within(CLOSE));
    }

    // A rival's hand counts by size, since its cards are hidden; the seat's own hand counts its mean and combat cards.
    @Test
    void handsCountByWhatIsKnown() {
        sol.setActionCard("sabo1");
        sol.setActionCard("sabo2");
        assertThat(TacticalStrength.rate(test.game, sol, false) - TacticalStrength.rate(test.game, sol, true))
                .isCloseTo(2 * TacticalStrength.UNSEEN_CARD, within(CLOSE));

        double before = TacticalStrength.rate(test.game, nekro, true);
        nekro.setActionCard("plague");
        nekro.setActionCard("dh1");
        assertThat(TacticalStrength.rate(test.game, nekro, true) - before)
                .isCloseTo(2 * TacticalStrength.HARSH_CARD, within(CLOSE));
    }

    @Test
    void unitsOnTheBoardAddStrength() {
        double base = TacticalStrength.seenBy(test.game, sol);
        Tile tile = test.place("19", "311");
        test.units(tile, "space", sol, UnitType.Dreadnought, 1);
        test.units(tile, "wellon", sol, UnitType.Infantry, 2);

        assertThat(TacticalStrength.seenBy(test.game, sol) - base).isCloseTo(4 + 2 * 0.5, within(CLOSE));
    }

    // A strong rival: Nekro avoids angering it. A weak one: Nekro cares less.
    @Test
    void aStrongRivalWeighsMore() {
        Tile tile = test.place("19", "311");
        test.units(tile, "space", sol, UnitType.Dreadnought, 2);
        assertThat(TacticalStrength.angerWeight(test.game, nekro, sol)).isGreaterThan(1);

        test.units(tile, "space", nekro, UnitType.Dreadnought, 4);
        assertThat(TacticalStrength.angerWeight(test.game, nekro, sol)).isLessThan(1);
    }

    @Test
    void equalStrengthLeavesAngerAsIs() {
        assertThat(TacticalStrength.angerWeight(test.game, nekro, sol)).isEqualTo(1);
    }

    @Test
    void withoutTheExperimentAngerIsUnweighted() {
        sol.addTech("x89");
        AiExperiments.disable(nekro, Set.of(Experiment.STRENGTH_WEIGHTED_ANGER));
        assertThat(TacticalStrength.angerWeight(test.game, nekro, sol)).isEqualTo(1);
    }
}
