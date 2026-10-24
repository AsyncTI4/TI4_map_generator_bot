package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.MecatolStake;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// Holding Mecatol Rex is worth the Imperial points of the rounds ahead (MecatolStake.holdValue): to the seat that
// takes it, to the one it is taken from, in what a Mecatol Rex the seat holds is worth defending, and in what a
// counterstrike on it would cost. Taking it is contested ground, not an insult.
class MecatolHoldTest extends BaseTi4Test {

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
    }

    private Player nekro() {
        return board.test.nekro;
    }

    private Player sardakk() {
        return board.sardakk;
    }

    private String nextTo(String position, String... excluded) {
        List<String> skip = List.of(excluded);
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !skip.contains(candidate)
                        && board.test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }

    private void sardakkHoldsMecatolWithOneInfantry() {
        Tile rex = board.test.place("18", "000");
        sardakk().addPlanet("mr");
        board.test.units(rex, "mr", sardakk(), UnitType.Infantry, 1);
    }

    private String raidersOneMoveFromMecatol() {
        String spot = nextTo("000");
        Tile launch = board.test.place("47", spot);
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(launch, "space", nekro(), UnitType.Cruiser, 2);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 2);
        return spot;
    }

    private StrikeValue breakdown() {
        return TacticalPlanner.explainAttack(board.test.game, nekro(), "000").orElseThrow();
    }

    // A neighbour holds Mecatol Rex with a single infantry and nobody holds the Imperial card, so the planet itself and
    // what holding it is worth in the rounds ahead (to the seat, and denied to the neighbour) make the strike.
    @Test
    void takingMecatolIsWorthWhatHoldingItIsWorth() {
        sardakkHoldsMecatolWithOneInfantry();
        raidersOneMoveFromMecatol();

        StrikeValue value = breakdown();

        assertThat(value.mecatolHold()).isPositive();
        assertThat(value.mecatolDenial()).isPositive();
        assertThat(TacticalPlanner.ranked(board.test.game, nekro()))
                .anyMatch(plan -> plan.kind() == Kind.ATTACK && "000".equals(plan.target()));
    }

    // A strong fleet beside Mecatol Rex could take it back. The hold value is the same, and the risk of losing it again
    // is priced where it belongs, in the counterstrike on the planet.
    @Test
    void aFleetThatCouldRetakeItRaisesTheCounterstrikeNotTheHold() {
        sardakkHoldsMecatolWithOneInfantry();
        String launch = raidersOneMoveFromMecatol();
        double quietHold = breakdown().mecatolHold();
        double quietCounter = breakdown().counter();

        Tile hunters = board.test.place("49", nextTo("000", launch));
        board.test.units(hunters, "space", sardakk(), UnitType.Dreadnought, 3);
        board.test.units(hunters, "space", sardakk(), UnitType.Carrier, 1);
        board.test.units(hunters, "space", sardakk(), UnitType.Infantry, 4);
        sardakk().setFleetCC(6);
        StrikeValue watched = breakdown();

        assertThat(watched.mecatolHold()).isEqualTo(quietHold);
        assertThat(watched.counter()).isGreaterThan(quietCounter);
    }

    @Test
    void aMecatolRexTheSeatHoldsIsWorthDefendingForItsHoldValue() {
        Tile rex = board.test.place("18", "000");
        nekro().addPlanet("mr");
        board.test.units(rex, "mr", nekro(), UnitType.Infantry, 1);
        DefenseOutlook defense = new DefenseOutlook(board.test.game, nekro());
        double stake = defense.stake(rex);

        board.test.game.scorePublicObjective(
                board.test.sol.getUserID(), board.test.game.addCustomPO("Nearly there", 9));
        double lateStake = new DefenseOutlook(board.test.game, nekro()).stake(rex);

        assertThat(MecatolStake.holdValue(board.test.game, nekro())).isZero();
        assertThat(stake - lateStake).isCloseTo(4.0, within(1e-9));
    }

    // Mecatol Rex is contested ground: taking it costs its holder no more goodwill than any other loss of plastic and
    // planets, while raiding their home system makes them really angry.
    @Test
    void takingMecatolIsContestedGroundNotAnInsult() {
        Tile rex = board.test.place("18", "000");
        Tile home = board.test.game.getTileByPosition(DefenseScenario.SARDAKK_HOME_POSITION);

        assertThat(Grievance.location(board.test.game, rex, sardakk())).isEqualTo(Grievance.CONTESTED_FACTOR);
        assertThat(Grievance.location(board.test.game, home, sardakk())).isEqualTo(Grievance.HOME_FACTOR);
    }
}
