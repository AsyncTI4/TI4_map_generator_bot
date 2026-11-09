package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// WIN_BREAK_DENIAL in strike valuation: Sardakk sits on 9 of 10 with Mecatol Rex and the unplayed Imperial, whose
// primary scores a point for controlling Mecatol Rex (LRR 45.2). Taking Mecatol Rex breaks that win, so the strike is
// worth the stake of losing the game; the old threat share of the Mecatol denial weight is dropped so it is not
// counted twice.
class WinBreakStrikeTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.test.game.setVp(10);
        board.test.aiIsActive("action");
        for (int card = 1; card <= IMPERIAL; card++) board.test.game.setScTradeGood(card, 0);
        Tile rex = board.test.place("18", "000");
        sardakk().addPlanet("mr");
        board.test.units(rex, "mr", sardakk(), UnitType.Infantry, 1);
        String spot = MapPositionMapper.getAdjacentTilePositions("000").stream()
                .filter(candidate -> !"x".equals(candidate) && board.test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
        Tile launch = board.test.place("47", spot);
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(launch, "space", nekro(), UnitType.Cruiser, 2);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 2);
        board.test.points(sardakk(), 9);
        sardakk().addSC(IMPERIAL);
    }

    private Player nekro() {
        return board.test.nekro;
    }

    private Player sardakk() {
        return board.sardakk;
    }

    private StrikeValue breakdown() {
        return TacticalPlanner.explainAttack(board.test.game, nekro(), "000").orElseThrow();
    }

    @Test
    void takingMecatolFromARivalAboutToWinIsWorthTheGame() {
        StrikeValue value = breakdown();

        assertThat(value.winBreak()).isGreaterThan(ScoringStakes.WIN_VALUE / 2);
        assertThat(value.winBreak()).isLessThanOrEqualTo(ScoringStakes.WIN_VALUE);
        // Sardakk wins for certain before Nekro can score, so no share of the ordinary point denial is left.
        assertThat(value.mecatolDenial()).isZero();
    }

    @Test
    void withTheExperimentOffNoWinBreakIsAdded() {
        StrikeValue on = breakdown();
        AiExperiments.disable(nekro(), Set.of(Experiment.WIN_BREAK_DENIAL));
        StrikeValue off = breakdown();

        assertThat(off.winBreak()).isZero();
        assertThat(on.value()).isGreaterThan(off.value());
    }
}
