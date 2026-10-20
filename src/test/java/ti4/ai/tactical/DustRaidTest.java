package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Turn Their Fleets to Dust: a zero-move activation of a system whose ships our space cannon may wipe. The bar is
// "worth a tactic token" (the token floor of 1.5), not "worth an attack", and the plan is a RAID, not an ATTACK.
class DustRaidTest extends BaseTi4Test {

    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile home;
    private String neighbour;
    private Player neutral;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        test.sol.removeTech("amd");
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        neutral = test.game.getNeutral();
        test.nekro.setSecret("ttfd");
    }

    private Tile quannWithOurPds(int pds) {
        Tile quann = test.place("25", neighbour);
        test.nekro.addPlanet("quann");
        test.units(quann, "quann", test.nekro, UnitType.Pds, pds);
        return quann;
    }

    private void pdsIiAtHome() {
        test.nekro.removeOwnedUnitByID("pds");
        test.nekro.addOwnedUnitByID("pds2");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
    }

    private Optional<TacticalPlan> raid() {
        return TacticalPlanner.forTarget(test.game, test.nekro, neighbour).filter(plan -> plan.kind() == Kind.RAID);
    }

    // Two neutral destroyers under two PDS dice on 6 die together a quarter of the time: 0.25 of a point (2.0), over
    // the token floor.
    @Test
    void raidsNeutralShipsUnderOurPdsAtAQuarterChance() {
        Tile quann = quannWithOurPds(2);
        test.units(quann, "space", neutral, UnitType.Destroyer, 2);

        TacticalPlan plan = raid().orElseThrow();

        assertThat(plan.moves()).isEmpty();
        assertThat(plan.score()).isCloseTo(VP * 0.25, within(EXACT));
    }

    // Three destroyers need three hits from three dice: 12.5% of a point is 1.0, below the token floor.
    @Test
    void doesNotRaidAtAnEighthOfAChance() {
        Tile quann = quannWithOurPds(3);
        test.units(quann, "space", neutral, UnitType.Destroyer, 3);

        assertThat(raid()).isEmpty();
    }

    @Test
    void raidsAnEnemyShipCoveredOnlyByAdjacentPdsIi() {
        pdsIiAtHome();
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);

        TacticalPlan plan = raid().orElseThrow();

        assertThat(plan.score()).isCloseTo(VP * 0.6 + 0.6, within(EXACT));
    }

    // Several ships are not a lone ship, but the wipe chance already counts the hits they need.
    @Test
    void raidsSeveralShipsWhenTheCannonCanWipeThem() {
        test.nekro.removeOwnedUnitByID("pds");
        test.nekro.addOwnedUnitByID("pds2");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 2);
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Destroyer, 2);

        TacticalPlan plan = raid().orElseThrow();

        assertThat(plan.score()).isCloseTo(VP * 0.36 + 0.36 * 2, within(EXACT));
    }

    // A rival's planet in the system makes two other players present, but only one owns ships.
    @Test
    void raidsNeutralShipsInASystemWhereARivalOwnsAPlanet() {
        pdsIiAtHome();
        Tile quann = test.place("25", neighbour);
        test.sol.addPlanet("quann");
        test.units(quann, "space", neutral, UnitType.Destroyer, 1);

        assertThat(raid().orElseThrow().score()).isCloseTo(VP * 0.6, within(EXACT));
    }

    @Test
    void countsANeutralTargetInTheWipeChance() {
        pdsIiAtHome();
        Tile target = test.place("46", neighbour);
        test.units(target, "space", neutral, UnitType.Destroyer, 1);

        assertThat(CannonWipeRules.chanceToWipe(test.game, test.nekro, target, neutral, Map.of()))
                .isCloseTo(0.6, within(EXACT));
        assertThat(CannonWipeRules.shipOwner(test.game, test.nekro, target)).isSameAs(neutral);
    }

    @Test
    void changesNothingWithoutTheSecret() {
        pdsIiAtHome();
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(raid()).isPresent();

        test.nekro.removeSecret(test.nekro.getSecretsUnscored().get("ttfd"));

        assertThat(raid()).isEmpty();
        assertThat(CannonWipeRules.hasCoveredTarget(test.game, test.nekro)).isFalse();
    }

    @Test
    void aLowRaidIsNotUrgentButAHighOneIs() {
        TacticalPlan low = new TacticalPlan(Kind.RAID, neighbour, List.of(), Map.of(), 2.0);
        TacticalPlan high = new TacticalPlan(Kind.RAID, neighbour, List.of(), Map.of(), VP);
        TacticalPlan attack = new TacticalPlan(Kind.ATTACK, neighbour, List.of(), Map.of(), 2.0);

        assertThat(TacticalRules.urgent(low)).isFalse();
        assertThat(TacticalRules.urgent(high)).isTrue();
        assertThat(TacticalRules.urgent(attack)).isTrue();
        assertThat(TacticalPlan.decode(low.encode())).contains(low);
    }
}
