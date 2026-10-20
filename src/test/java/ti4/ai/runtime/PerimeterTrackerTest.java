package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class PerimeterTrackerTest extends BaseTi4Test {

    private static final int CONSTRUCTION = 4;

    private AiTestGame test;
    private PerimeterTracker tracker;
    private Tile forward;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        forward = test.place("26", "101");
        test.nekro.addPlanet("lodor");
        test.nekro.setStrategicCC(2);
        test.sol.addSC(CONSTRUCTION);
        test.game.setSCPlayed(CONSTRUCTION, true);
        tracker = new PerimeterTracker();
        tracker.observe(test.game);
    }

    private boolean reportsFollows() {
        return tracker.lines(test.game).stream().anyMatch(line -> line.contains("real follows"));
    }

    // Marking Construction as followed is what a declined follow does too, so a structure that appears from another
    // source while no strategy token was spent is not a follow.
    @Test
    void doesNotCountADeclinedFollowThatSawAStructureAppear() {
        test.nekro.addFollowedSC(CONSTRUCTION);
        test.units(forward, "lodor", test.nekro, UnitType.Pds, 1);

        tracker.observe(test.game);

        assertThat(reportsFollows()).isFalse();
    }

    @Test
    void doesNotCountAFollowThatPlacedNothing() {
        test.nekro.addFollowedSC(CONSTRUCTION);
        test.nekro.setStrategicCC(1);

        tracker.observe(test.game);

        assertThat(reportsFollows()).isFalse();
    }

    @Test
    void countsAForwardPdsPlacedAfterSpendingAStrategyToken() {
        test.nekro.addFollowedSC(CONSTRUCTION);
        test.nekro.setStrategicCC(1);
        tracker.observe(test.game);
        test.units(forward, "lodor", test.nekro, UnitType.Pds, 1);

        tracker.observe(test.game);

        assertThat(tracker.lines(test.game))
                .anySatisfy(line -> assertThat(line).contains("real follows", "pds/forward=1", "pds/home=0"));
    }

    @Test
    void countsAHomePdsSeparatelyFromAForwardOne() {
        test.nekro.addFollowedSC(CONSTRUCTION);
        test.nekro.setStrategicCC(1);
        tracker.observe(test.game);
        test.units(test.game.getTileFromPlanet("mordaiii"), "mordaiii", test.nekro, UnitType.Pds, 1);

        tracker.observe(test.game);

        assertThat(tracker.lines(test.game))
                .anySatisfy(line -> assertThat(line).contains("pds/home=1", "pds/forward=0"));
    }
}
