package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.IconStake;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Seize an Icon (control a legendary planet at the status phase) is a full point the AI should chase: it expands onto a
// free legendary planet, marches towards one it cannot reach yet, attacks through the structures on one a rival holds,
// pays the custodians for a legendary Mecatol Rex and guards the planet until the status phase.
class SeizeAnIconTest extends BaseTi4Test {

    private static final String PRIMOR_TILE = "65";
    private static final String EMPTY_SYSTEM = "46";
    private static final String CARRIER_SPOT = "202";
    private static final String NEXT_TO_SPOT = "302";
    private static final String TWO_STEPS = "402";

    private AiTestGame test;

    private void board(boolean holdsTheSecret) {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(5);
        if (holdsTheSecret) test.nekro.setSecret(IconStake.SEIZE_AN_ICON);
    }

    private Tile carrierWithInfantry(int infantry) {
        Tile origin = test.place(EMPTY_SYSTEM, CARRIER_SPOT);
        test.units(origin, "space", test.nekro, UnitType.Carrier, 1);
        test.units(origin, "space", test.nekro, UnitType.Infantry, infantry);
        return origin;
    }

    private Optional<TacticalPlan> planOfKind(Kind kind) {
        return TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.kind() == kind)
                .findFirst();
    }

    private void rivalHoldsPrimorBehindAPds() {
        Tile origin = carrierWithInfantry(3);
        test.units(origin, "space", test.nekro, UnitType.Cruiser, 2);
        Tile primor = test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        test.sol.addPlanet("primor");
        test.units(primor, "primor", test.sol, UnitType.Infantry, 1);
        test.units(primor, "primor", test.sol, UnitType.Pds, 1);
    }

    @Test
    void seeksAnIconOnlyWhileTheSecretIsUnscoredAndNoLegendaryPlanetIsHeld() {
        board(false);
        assertThat(IconStake.seeks(test.game, test.nekro)).isFalse();

        board(true);
        assertThat(IconStake.seeks(test.game, test.nekro)).isTrue();

        test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        test.nekro.addPlanet("primor");
        assertThat(IconStake.seeks(test.game, test.nekro)).isFalse();
    }

    @Test
    void aFreeLegendaryPlanetIsWorthTheSecretsPointOnTopOfItsOwnValue() {
        board(false);
        carrierWithInfantry(2);
        test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        double without = TacticalPlanner.forTarget(test.game, test.nekro, NEXT_TO_SPOT)
                .orElseThrow()
                .score();

        board(true);
        carrierWithInfantry(2);
        test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        double with = TacticalPlanner.forTarget(test.game, test.nekro, NEXT_TO_SPOT)
                .orElseThrow()
                .score();

        assertThat(with - without).isGreaterThanOrEqualTo(ObjectiveValue.VICTORY_POINT_VALUE - 0.01);
    }

    // With one infantry to land, a system's legendary planet is taken before a planet that is worth more on its own.
    @Test
    void claimsTheLegendaryPlanetFirstWhileSeekingAnIcon() {
        board(true);
        test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        Planet primor = test.game.getPlanetsInfo().get("primor");
        Planet jord = test.game.getPlanetsInfo().get("jord");

        List<Planet> seeking = Stream.of(jord, primor)
                .sorted(IconStake.claimOrder(test.game, test.nekro))
                .toList();
        List<Planet> idle = Stream.of(primor, jord)
                .sorted(IconStake.claimOrder(test.game, test.sol))
                .toList();

        assertThat(seeking).containsExactly(primor, jord);
        assertThat(idle)
                .isSortedAccordingTo(
                        Comparator.comparingDouble(BoardView::planetValue).reversed());
    }

    // Primor is two systems from the carrier, which moves one: nothing can land this turn, so the carrier steps closer.
    @Test
    void marchesTowardsALegendaryPlanetItCannotReachYet() {
        board(true);
        carrierWithInfantry(2);
        test.place(EMPTY_SYSTEM, NEXT_TO_SPOT);
        test.place(PRIMOR_TILE, TWO_STEPS);

        TacticalPlan plan = planOfKind(Kind.STAGE).orElseThrow();

        assertThat(plan.target()).isEqualTo(NEXT_TO_SPOT);
        assertThat(plan.moves())
                .anyMatch(move -> move.type() == UnitType.Carrier)
                .anyMatch(move -> move.type() == UnitType.Infantry);
    }

    @Test
    void doesNotMarchWithoutTheSecret() {
        board(false);
        carrierWithInfantry(2);
        test.place(EMPTY_SYSTEM, NEXT_TO_SPOT);
        test.place(PRIMOR_TILE, TWO_STEPS);

        assertThat(planOfKind(Kind.STAGE)).isEmpty();
    }

    @Test
    void doesNotMarchOnceALegendaryPlanetIsHeld() {
        board(true);
        carrierWithInfantry(2);
        test.place(EMPTY_SYSTEM, NEXT_TO_SPOT);
        test.place(PRIMOR_TILE, TWO_STEPS);
        test.nekro.addPlanet("primor");

        assertThat(planOfKind(Kind.STAGE)).isEmpty();
    }

    // A rival's planet with a PDS is left alone outside its home system, unless the planet is the icon.
    @Test
    void attacksALegendaryPlanetThroughTheRivalsStructures() {
        board(true);
        rivalHoldsPrimorBehindAPds();

        TacticalPlan plan = planOfKind(Kind.ATTACK).orElseThrow();

        assertThat(plan.target()).isEqualTo(NEXT_TO_SPOT);
        assertThat(plan.landings()).containsKey("primor");
    }

    @Test
    void leavesTheRivalsStructuresAloneWithoutTheSecret() {
        board(false);
        rivalHoldsPrimorBehindAPds();

        assertThat(planOfKind(Kind.ATTACK)).isEmpty();
    }

    @Test
    void guardsTheSystemOfItsLegendaryPlanetUntilTheStatusPhase() {
        board(true);
        Tile primor = test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        test.nekro.addPlanet("primor");
        test.units(primor, "primor", test.nekro, UnitType.Infantry, 1);

        assertThat(new DefenseOutlook(test.game, test.nekro).isGuarded(primor)).isTrue();

        board(false);
        Tile idle = test.place(PRIMOR_TILE, NEXT_TO_SPOT);
        test.nekro.addPlanet("primor");
        test.units(idle, "primor", test.nekro, UnitType.Infantry, 1);

        assertThat(new DefenseOutlook(test.game, test.nekro).isGuarded(idle)).isFalse();
    }

    private void legendaryMecatolWithCustodiansAndAnOutpost() {
        Tile rex = test.place("112", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mrte");
        Tile origin = test.place(EMPTY_SYSTEM, "101");
        test.units(origin, "space", test.nekro, UnitType.Carrier, 1);
        test.units(origin, "space", test.nekro, UnitType.Infantry, 2);
    }

    private Optional<TacticalPlan> expansionToMecatol() {
        return TacticalPlanner.forTarget(test.game, test.nekro, "000").filter(plan -> plan.kind() == Kind.EXPAND);
    }

    // Trade Routes is revealed and the seat holds 10 trade goods: its 5 are reserved, so the custodians' 6 cannot also
    // be paid. For the icon on a legendary Mecatol Rex the custodians point and the icon outweigh the reserve.
    @Test
    void paysTheCustodiansForALegendaryMecatolRexOutOfTheReserve() {
        board(true);
        test.game.setPhaseOfGame("action");
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);
        test.nekro.setTg(10);
        legendaryMecatolWithCustodiansAndAnOutpost();

        assertThat(IconStake.behindCustodians(test.game, test.nekro)).isTrue();
        assertThat(expansionToMecatol()).isPresent();
    }

    @Test
    void takesTheCustodiansBeforeAnObjectiveReserveWithoutTheSecret() {
        board(false);
        test.game.setPhaseOfGame("action");
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);
        test.nekro.setTg(10);
        legendaryMecatolWithCustodiansAndAnOutpost();

        assertThat(expansionToMecatol()).isPresent();
    }

    @Test
    void aLegendaryMecatolRexIsWorthMoreInTheCustodiansRaceWhileSeekingAnIcon() {
        board(false);
        legendaryMecatolWithCustodiansAndAnOutpost();
        double without = CustodiansRace.of(test.game, test.nekro)
                .raceValue(test.nekro, OptionalInt.of(3), CustodiansRace.Order.UNKNOWN);

        board(true);
        legendaryMecatolWithCustodiansAndAnOutpost();
        double with = CustodiansRace.of(test.game, test.nekro)
                .raceValue(test.nekro, OptionalInt.of(3), CustodiansRace.Order.UNKNOWN);

        assertThat(with).isGreaterThan(without);
    }
}
