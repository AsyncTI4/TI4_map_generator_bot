package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CombatModifiers;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Sardakk's standing combat bonuses (Unrelenting, the flagship aura) come from the engine's combat modifiers; the
// Valkyrie Particle Weave and the Valkyrie Exoskeleton mech are modelled in the ground combat force.
class SardakkCombatForcesTest extends BaseTi4Test {

    private static final String SPACE = "space";
    private static final String PLANET = "mordaiii";

    private AiTestGame test;
    private Player sardakk;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sardakk = test.addSeat(AiTestGame.SECOND_AI_ID, "sardakk", "orange");
        tile = test.nekroHome();
        test.isActive(sardakk, "action");
        test.game.setActiveSystem(AiTestGame.HOME);
    }

    private Map<UnitType, Integer> hitModifiers(
            Player side, Player opponent, String holder, Map<UnitType, Integer> own) {
        return CombatModifiers.hitModifiers(
                test.game,
                tile,
                tile.getUnitHolders().get(holder),
                new Side(side, own),
                new Side(opponent, Map.of(UnitType.Infantry, 1)));
    }

    @Test
    void unrelentingAddsOneToEveryUnitsRollsInSpaceAndOnTheGround() {
        Map<UnitType, Integer> ships = Map.of(UnitType.Cruiser, 2, UnitType.Destroyer, 1);
        Map<UnitType, Integer> ground = Map.of(UnitType.Infantry, 3, UnitType.Mech, 1);

        assertThat(hitModifiers(sardakk, test.sol, SPACE, ships))
                .containsEntry(UnitType.Cruiser, 1)
                .containsEntry(UnitType.Destroyer, 1);
        assertThat(hitModifiers(sardakk, test.sol, PLANET, ground))
                .containsEntry(UnitType.Infantry, 1)
                .containsEntry(UnitType.Mech, 1);
    }

    @Test
    void otherFactionsGetNoUnrelentingBonus() {
        assertThat(hitModifiers(test.sol, sardakk, SPACE, Map.of(UnitType.Cruiser, 2)))
                .containsEntry(UnitType.Cruiser, 0);
    }

    // The flagship adds one to its other ships on top of Unrelenting, and not to itself.
    @Test
    void theFlagshipAuraAddsOneToTheOtherShipsInItsSystem() {
        Map<UnitType, Integer> fleet = Map.of(UnitType.Flagship, 1, UnitType.Cruiser, 2);

        assertThat(hitModifiers(sardakk, test.sol, SPACE, fleet))
                .containsEntry(UnitType.Cruiser, 2)
                .containsEntry(UnitType.Flagship, 1);
    }

    @Test
    void theFlagshipAuraDoesNotReachTheGround() {
        Map<UnitType, Integer> ground = Map.of(UnitType.Infantry, 2);

        assertThat(hitModifiers(sardakk, test.sol, PLANET, ground)).containsEntry(UnitType.Infantry, 1);
    }

    @Test
    void theModifiersReachTheCombatantsTheOddsAreBuiltFrom() {
        test.units(tile, SPACE, sardakk, UnitType.Flagship, 1);
        test.units(tile, SPACE, sardakk, UnitType.Cruiser, 2);
        test.units(tile, SPACE, test.sol, UnitType.Cruiser, 2);

        Force sardakkFleet =
                CombatForces.current(test.game, tile, tile.getUnitHolders().get(SPACE), sardakk, test.sol, false);
        Force solFleet =
                CombatForces.current(test.game, tile, tile.getUnitHolders().get(SPACE), test.sol, sardakk, false);

        assertThat(sardakkFleet.units().stream().filter(unit -> unit.type() == UnitType.Cruiser))
                .allSatisfy(cruiser -> assertThat(cruiser.hitsOn()).isEqualTo(7 - 2));
        assertThat(solFleet.units())
                .allSatisfy(cruiser -> assertThat(cruiser.hitsOn()).isEqualTo(7));
    }

    private Force groundForce(Player player, Player opponent) {
        return CombatForces.current(test.game, tile, tile.getUnitHolders().get(PLANET), player, opponent, true);
    }

    @Test
    void theParticleWeaveShapesTheGroundForceOfItsOwnerOnly() {
        test.units(tile, PLANET, sardakk, UnitType.Infantry, 2);
        test.units(tile, PLANET, test.sol, UnitType.Infantry, 2);

        assertThat(groundForce(sardakk, test.sol).weaves()).isFalse();

        sardakk.addTech("vpw");

        assertThat(groundForce(sardakk, test.sol).weaves()).isTrue();
        assertThat(groundForce(test.sol, sardakk).weaves()).isFalse();
    }

    @Test
    void theParticleWeaveDoesNotApplyInSpace() {
        test.units(tile, SPACE, sardakk, UnitType.Cruiser, 2);
        test.units(tile, SPACE, test.sol, UnitType.Cruiser, 2);
        sardakk.addTech("vpw");

        Force fleet =
                CombatForces.current(test.game, tile, tile.getUnitHolders().get(SPACE), sardakk, test.sol, false);

        assertThat(fleet.weaves()).isFalse();
    }

    @Test
    void theParticleWeaveRaisesTheOddsOfAnInvasion() {
        test.units(tile, PLANET, sardakk, UnitType.Infantry, 3);
        test.units(tile, PLANET, test.sol, UnitType.Infantry, 3);
        Force defence = groundForce(test.sol, sardakk);
        double without =
                CombatOdds.resolve(groundForce(sardakk, test.sol), defence).attackerWins();

        sardakk.addTech("vpw");
        double with =
                CombatOdds.resolve(groundForce(sardakk, test.sol), defence).attackerWins();

        assertThat(with).isGreaterThan(without + 0.05);
    }

    @Test
    void theValkyrieMechRetaliatesOnlyWhileItCanStillSustain() {
        test.units(tile, PLANET, sardakk, UnitType.Mech, 2);
        tile.addUnitDamage(PLANET, Units.getUnitKey(UnitType.Mech, sardakk.getColor()), 1);

        Force force = groundForce(sardakk, test.sol);

        assertThat(force.units()).hasSize(2);
        assertThat(force.units().stream().filter(Combatant::sustain)).hasSize(1);
        assertThat(force.units().stream().filter(Combatant::retaliates)).hasSize(1);
    }

    @Test
    void otherMechsDoNotRetaliate() {
        test.units(tile, PLANET, test.sol, UnitType.Mech, 1);

        Force force = groundForce(test.sol, sardakk);

        assertThat(force.units()).hasSize(1);
        assertThat(force.units().getFirst().sustain()).isTrue();
        assertThat(force.units().getFirst().retaliates()).isFalse();
    }

    @Test
    void theValkyrieMechRaisesTheOddsOfHoldingAPlanet() {
        test.units(tile, PLANET, sardakk, UnitType.Mech, 1);
        test.units(tile, PLANET, test.sol, UnitType.Infantry, 3);
        Force invaders = groundForce(test.sol, sardakk);
        Force defenders = groundForce(sardakk, test.sol);
        Force plainDefenders = defenders.withUnits(defenders.units().stream()
                .map(unit -> unit.withoutSustain().rolling(unit.hitsOn(), unit.dice()))
                .toList());
        Force sustainingOnly = defenders.withUnits(defenders.units().stream()
                .map(unit -> new Combatant(unit.hitsOn(), unit.dice(), true, unit.cost(), unit.type()))
                .toList());

        double holding = CombatOdds.resolve(invaders, defenders).defenderWins();
        double sustainOnly = CombatOdds.resolve(invaders, sustainingOnly).defenderWins();
        double noSustain = CombatOdds.resolve(invaders, plainDefenders).defenderWins();

        assertThat(holding).isGreaterThan(sustainOnly);
        assertThat(sustainOnly).isGreaterThan(noSustain);
    }
}
