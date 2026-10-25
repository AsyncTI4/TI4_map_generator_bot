package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Occupy the Seat of the Empire needs the Mecatol Rex holder to have 3 ships in its system. Fighters are ships, so a
// carrier that brings fighters is worth the objective they complete, and an enemy space cannon on Mecatol is a price
// to pay for the last ship rather than a reason to stay away.
class SeatOfTheEmpireTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private AiTestGame test;
    private Tile rex;
    private List<String> ring;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.nekroHome();
        test.nekro.setTacticalCC(3);
        rex = test.place("18", MECATOL);
        test.nekro.addPlanet("mr");
        ring = MapPositionMapper.getAdjacentTilePositions(MECATOL).stream()
                .filter(position -> !"x".equals(position))
                .toList();
    }

    private Optional<TacticalPlan> positioning() {
        return TacticalPlanner.forTarget(test.game, test.nekro, MECATOL).filter(plan -> plan.kind() == Kind.POSITION);
    }

    private void dreadnoughtHoldsMecatol() {
        test.units(rex, "space", test.nekro, UnitType.Dreadnought, 1);
    }

    // The fighters sit in a system with a space dock, which supports them, so they are not carried along just because
    // the carrier leaves: only the escort step brings them.
    private Tile carrierWithFightersNextDoor(int fighters) {
        Tile launch = test.place("26", ring.get(0));
        String planet = launch.getPlanetUnitHolders().getFirst().getName();
        test.nekro.addPlanet(planet);
        test.units(launch, planet, test.nekro, UnitType.Spacedock, 1);
        test.units(launch, "space", test.nekro, UnitType.Carrier, 1);
        if (fighters > 0) test.units(launch, "space", test.nekro, UnitType.Fighter, fighters);
        return launch;
    }

    private void solPdsTwoCoversMecatol() {
        test.place("01", ring.get(3));
        test.sol.addPlanet("jord");
        test.sol.removeOwnedUnitByID("pds");
        test.sol.addOwnedUnitByID("pds2");
        test.units(test.game.getTileByPosition(ring.get(3)), "jord", test.sol, UnitType.Pds, 1);
    }

    private long fightersMoved(TacticalPlan plan) {
        return plan.moves().stream()
                .filter(move -> move.type() == UnitType.Fighter)
                .mapToInt(TacticalPlan.UnitMove::count)
                .sum();
    }

    // One dreadnought is on Mecatol; the carrier alone makes 2 ships and the two fighters it carries make the third.
    @Test
    void bringsTheFightersThatCompleteTheSeat() {
        test.nekro.setSecret("ose");
        dreadnoughtHoldsMecatol();
        carrierWithFightersNextDoor(2);

        TacticalPlan plan = positioning().orElseThrow();

        assertThat(plan.moves()).anyMatch(move -> move.type() == UnitType.Carrier);
        assertThat(fightersMoved(plan)).isEqualTo(2);
    }

    @Test
    void scoresTheFightersAboveTheSameCarrierWithoutThem() {
        test.nekro.setSecret("ose");
        dreadnoughtHoldsMecatol();
        carrierWithFightersNextDoor(2);
        double withFighters = positioning().orElseThrow().score();

        carrierWithFightersNextDoor(0);
        double without = positioning().orElseThrow().score();

        assertThat(withFighters).isGreaterThan(without + VP / 2);
    }

    @Test
    void pullsNoFightersToMecatolWithoutTheSecret() {
        dreadnoughtHoldsMecatol();
        carrierWithFightersNextDoor(2);

        assertThat(positioning().map(this::fightersMoved).orElse(0L)).isZero();
    }

    // One PDS II die hits on a 6, so one ship is expected to be lost: four ships make it to the three the secret needs.
    @Test
    void goesIntoSpaceCannonCoverWhenTheSeatSurvivesTheExpectedHit() {
        test.nekro.setSecret("ose");
        dreadnoughtHoldsMecatol();
        test.units(rex, "space", test.nekro, UnitType.Cruiser, 1);
        carrierWithFightersNextDoor(1);
        solPdsTwoCoversMecatol();

        TacticalPlan plan = positioning().orElseThrow();

        assertThat(plan.moves()).anyMatch(move -> move.type() == UnitType.Carrier);
    }

    @Test
    void staysOutOfSpaceCannonCoverWhenTheExpectedHitBreaksTheSeat() {
        test.nekro.setSecret("ose");
        dreadnoughtHoldsMecatol();
        carrierWithFightersNextDoor(1);
        solPdsTwoCoversMecatol();

        assertThat(positioning()).isEmpty();
    }

    @Test
    void chargesTheShipsTheCannonIsExpectedToKill() {
        test.nekro.setSecret("ose");
        dreadnoughtHoldsMecatol();
        test.units(rex, "space", test.nekro, UnitType.Cruiser, 1);
        carrierWithFightersNextDoor(1);
        double open = positioning().orElseThrow().score();
        solPdsTwoCoversMecatol();
        double covered = positioning().orElseThrow().score();

        assertThat(covered).isLessThan(open);
    }
}
