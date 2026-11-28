package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalPlanner.Candidate;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Fighter II moves 2 and may move without being transported (units/baseUnits.json fighter2); fighters beyond capacity
// count against the fleet pool (ships ruling I). The planner flies spare fighters, plans fighter-only strikes and lets
// a flying fighter set the Gravleash pace (letnev ruling II).
class FighterIIPlanTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private List<String> corridor;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        test.nekro.removeOwnedUnitByID("fighter");
        test.nekro.addOwnedUnitByID("fighter2");
        corridor = corridor(3);
    }

    // Carrier, cruiser and six Fighter IIs: four ride the carrier and two fly, one fleet slot each. Four fleet tokens
    // send all six; three send five.
    @Test
    void aCarrierStackSendsSpareFighterIIsWithinFleetSupply() {
        Tile lodor = solDestroyerAt(corridor.getFirst());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(home, "space", test.nekro, UnitType.Fighter, 6);

        test.nekro.setFleetCC(4);
        assertThat(mostFighters(lodor)).isEqualTo(6);

        test.nekro.setFleetCC(3);
        assertThat(mostFighters(lodor)).isEqualTo(5);
    }

    // Carrier II, four infantry and two Fighter IIs three systems out with Gravity Drive: the infantry fill the
    // carrier,
    // so both fighters fly and fall one short each. Only Flank Speed reaches, so the plan needs the card.
    @Test
    void flankSpeedIsNeededWhenGroundForcesPushTheFightersOutOfTheCarrier() {
        test.nekro.removeOwnedUnitByID("carrier");
        test.nekro.addOwnedUnitByID("carrier2");
        test.nekro.addTech("gd");
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 6);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
        TacticalPlan plan = new TacticalPlan(
                Kind.POSITION,
                corridor.get(2),
                List.of(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Infantry, 6),
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 2)),
                Map.of(),
                1.0);

        assertThat(TacticalPlanner.needsCard(test.game, test.nekro, plan, ti4.ai.eval.MovementCard.FLANK_SPEED))
                .isTrue();
    }

    // Fleet pool 3: carrier, destroyer, four infantry and two Fighter IIs need four slots. Dropping the destroyer
    // keeps all four infantry in the carrier and lets both fighters fly, which fits exactly (ships ruling I).
    @Test
    void reSplittingKeepsTheInfantryAndLetsFighterIIsFly() {
        test.nekro.setFleetCC(3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 4);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
        TacticalPlanner.Part part = new TacticalPlanner.Part(
                home,
                new TacticalPlanner.Departure(
                        List.of(
                                new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                                new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1),
                                new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 2)),
                        List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Infantry, 4)),
                        false));

        List<TacticalPlanner.Part> fitted = TacticalPlanner.fitToSupply(
                new TacticalPlanner.Context(test.game, test.nekro),
                test.game.getTileByPosition(corridor.getFirst()),
                List.of(part),
                List.of());

        assertThat(fitted).hasSize(1);
        assertThat(fitted.getFirst().departure().cargo())
                .containsExactly(new UnitMove(AiTestGame.HOME, "space", UnitType.Infantry, 4));
        assertThat(fitted.getFirst().departure().ships())
                .contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 2))
                .noneMatch(move -> move.type() == UnitType.Destroyer);
    }

    @Test
    void aFighterIIOnlyStrikeIsACandidate() {
        Tile lodor = solDestroyerAt(corridor.getFirst());
        test.nekro.setFleetCC(4);
        test.units(home, "space", test.nekro, UnitType.Fighter, 4);

        List<Candidate> pool = pool(lodor);

        assertThat(pool)
                .anySatisfy(found -> assertThat(found.moves())
                        .containsExactly(new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 4)));
    }

    // The dreadnought (move 1) reaches two systems out only behind a flying Fighter II, so the fighter is in the moves.
    @Test
    void theGravleashFighterPacerIsInThePlannedMoves() {
        test.nekro.addBreakthrough("letnevbt");
        test.nekro.setBreakthroughUnlocked("letnevbt", true);
        test.nekro.setFleetCC(4);
        Tile lodor = solDestroyerAt(corridor.get(1));
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);

        assertThat(pool(lodor))
                .anySatisfy(found -> assertThat(found.moves())
                        .contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 1))
                        .anyMatch(move -> move.type() == UnitType.Fighter && move.count() >= 1));
    }

    // Three Fighter IIs reach two systems out but not three, so a plan sending them three out is out of range.
    @Test
    void aFighterIIGroupOutOfRangeIsTrimmed() {
        test.units(home, "space", test.nekro, UnitType.Fighter, 3);
        List<UnitMove> wing = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 3));

        assertThat(TacticalRules.inRange(
                        test.game, test.nekro, new TacticalPlan(Kind.POSITION, corridor.get(1), wing, Map.of(), 1)))
                .isTrue();
        assertThat(TacticalRules.inRange(
                        test.game, test.nekro, new TacticalPlan(Kind.POSITION, corridor.get(2), wing, Map.of(), 1)))
                .isFalse();
    }

    private Tile solDestroyerAt(String position) {
        Tile lodor = test.place("26", position);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        return lodor;
    }

    private List<Candidate> pool(Tile target) {
        return TacticalPlanner.candidatePool(new TacticalPlanner.Context(test.game, test.nekro), target, test.sol);
    }

    private int mostFighters(Tile target) {
        return pool(target).stream()
                .flatMap(found -> found.moves().stream())
                .filter(move -> move.type() == UnitType.Fighter)
                .mapToInt(UnitMove::count)
                .max()
                .orElse(0);
    }

    /** A straight line of plain systems leading away from home; only placed systems exist, so it is the only path. */
    private List<String> corridor(int length) {
        List<String> positions = new ArrayList<>(List.of(AiTestGame.HOME));
        List<String> tiles = List.of("19", "20", "21");
        for (int step = 0; step < length; step++) {
            String last = positions.getLast();
            String next = MapPositionMapper.getAdjacentTilePositions(last).stream()
                    .filter(candidate -> !"x".equals(candidate) && !positions.contains(candidate))
                    .filter(candidate -> positions.stream()
                            .limit(positions.size() - 1L)
                            .noneMatch(earlier -> MapPositionMapper.getAdjacentTilePositions(earlier)
                                    .contains(candidate)))
                    .findFirst()
                    .orElseThrow();
            positions.add(next);
            test.place(tiles.get(step), next);
        }
        return positions.subList(1, positions.size());
    }
}
