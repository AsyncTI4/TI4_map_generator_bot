package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

class WinAwareObjectiveValueTest extends BaseTi4Test {

    private static final String EMPTY_SYSTEM = "46";

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.game.setVp(10);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private void disableExperiment() {
        AiExperiments.disable(test.nekro, EnumSet.of(AiExperiments.Experiment.WIN_AWARE_POINTS));
    }

    // Four destroyers at home and a cruiser beside it: bringing the cruiser home completes Raise a Fleet.
    private ObjectiveValue raiseFleetValue() {
        reveal("raise_fleet");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 4);
        test.place(EMPTY_SYSTEM, neighbour);
        test.units(test.game.getTileByPosition(neighbour), "space", test.nekro, UnitType.Cruiser, 1);
        return new ObjectiveValue(test.game, test.nekro);
    }

    private Footprint cruiserJoins(ObjectiveValue value) {
        return value.before()
                .after(AiTestGame.HOME, List.of(new Footprint.Move(neighbour, UnitType.Cruiser, 1)), List.of());
    }

    // At 9 of 10 points the completion is the winning point, so it is priced as the win.
    @Test
    void theWinningCompletionIsWorthTheWin() {
        test.points(test.nekro, 9);
        ObjectiveValue value = raiseFleetValue();

        assertThat(value.gain(cruiserJoins(value))).isEqualTo(ScoringStakes.WIN_VALUE);
    }

    // The control arm keeps the flat 8 trade goods per point even when the point wins the game.
    @Test
    void withTheExperimentOffTheWinningCompletionStaysFlat() {
        test.points(test.nekro, 9);
        disableExperiment();
        ObjectiveValue value = raiseFleetValue();

        assertThat(value.gain(cruiserJoins(value))).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Far from the goal the flat 8 stays as the floor, so early completions keep their old price.
    @Test
    void anEarlyCompletionKeepsTheFlatFloor() {
        ObjectiveValue value = raiseFleetValue();

        assertThat(value.gain(cruiserJoins(value))).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Close to the goal a point that does not win yet is worth the rising per-point price (6, +2 per point past
    // 6 VP, +2 with a contender, capped at 12): at 8 of 10 that is the 12 cap.
    @Test
    void aLatePointBelowTheGoalIsWorthTheRisingPrice() {
        test.points(test.nekro, 8);
        ObjectiveValue value = raiseFleetValue();

        assertThat(value.gain(cruiserJoins(value))).isEqualTo(ScoringStakes.vpWorth(test.game, test.nekro));
        assertThat(ScoringStakes.vpWorth(test.game, test.nekro)).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Five dreadnoughts complete both Raise a Fleet and Gather a Mighty Fleet: two points at 9 of 10 still win only
    // once, so the move is worth the win and not two wins. The control arm adds two flat points.
    @Test
    void twoCompletionsThatWinTogetherAreWorthOneWin() {
        test.points(test.nekro, 9);
        reveal("raise_fleet");
        test.nekro.setSecret("gamf");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);
        Footprint built = value.before().withBuilt(AiTestGame.HOME, Map.of(UnitType.Dreadnought, 5));

        assertThat(value.gain(built)).isEqualTo(ScoringStakes.WIN_VALUE);

        disableExperiment();
        ObjectiveValue control = new ObjectiveValue(test.game, test.nekro);
        assertThat(control.gain(control.before().withBuilt(AiTestGame.HOME, Map.of(UnitType.Dreadnought, 5))))
                .isEqualTo(2 * ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // An affordable spend objective (Negotiate Trade Routes) does not fill the scoring slot: completing a board
    // public lets Nekro score it instead and keep the trade goods, so it keeps its full price.
    @Test
    void anAffordableSpendObjectiveDoesNotFillTheSlot() {
        reveal("trade_routes");
        test.nekro.setTg(5);
        ObjectiveValue value = raiseFleetValue();

        assertThat(value.gain(cruiserJoins(value))).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Five destroyers at home already qualify for Raise a Fleet.
    private ObjectiveValue withRaiseFleetReady() {
        reveal("raise_fleet");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 5);
        return new ObjectiveValue(test.game, test.nekro);
    }

    // Raise a Fleet already fills the one public slot this status phase, so a second ready board public waits a round:
    // it counts at the partial share, not a full point.
    @Test
    void aBoardPublicBeyondTheScoringSlotIsDiscounted() {
        reveal("deep_space");
        ObjectiveValue value = withRaiseFleetReady();

        assertThat(value.gainAt("deep_space", 3)).isCloseTo(0.6 * ObjectiveValue.VICTORY_POINT_VALUE, within(1e-9));
    }

    // The unplayed Imperial gives a second public slot, so the second ready public is a full point.
    @Test
    void imperialOpensASecondSlot() {
        reveal("deep_space");
        test.nekro.addSC(8);
        ObjectiveValue value = withRaiseFleetReady();

        assertThat(value.gainAt("deep_space", 3)).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // A completed 2-point stage II takes the slot from the ready 1-point public: one more point now, and Raise a Fleet
    // waits a round at the partial share.
    @Test
    void aStageTwoUpgradesTheReadyPublic() {
        reveal("command_armada");
        ObjectiveValue value = withRaiseFleetReady();

        assertThat(value.gainAt("command_armada", 8))
                .isCloseTo(ObjectiveValue.VICTORY_POINT_VALUE + 0.6 * ObjectiveValue.VICTORY_POINT_VALUE, within(1e-9));
    }

    // At 9 of 10 with Raise a Fleet ready, moving a destroyer out of home breaks it but completes Explore Deep Space
    // (third planetless system). The seat still scores one public for the win, so the swap is worth nothing.
    @Test
    void breakingOneReadyPublicWhileCompletingAnotherKeepsTheWin() {
        test.points(test.nekro, 9);
        reveal("deep_space");
        List<String> around = adjacentEmptyPositions(3);
        for (String position : around) test.place(EMPTY_SYSTEM, position);
        test.units(test.game.getTileByPosition(around.get(1)), "space", test.nekro, UnitType.Destroyer, 1);
        test.units(test.game.getTileByPosition(around.get(2)), "space", test.nekro, UnitType.Destroyer, 1);
        ObjectiveValue value = withRaiseFleetReady();
        Footprint swap = value.before()
                .after(around.get(0), List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.brokenReady(swap)).containsExactly("raise_fleet");
        assertThat(value.completedPoints(swap)).isEqualTo(1);
        assertThat(value.gain(swap)).isZero();
    }

    private List<String> adjacentEmptyPositions(int count) {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && test.game.getTileByPosition(position) == null)
                .limit(count)
                .toList();
    }

    // Sol decides and sees Nekro, a rival at 9 of 10, through a public view. The price follows the deciding seat:
    // a control Sol keeps the flat 8 although Nekro is in the treatment arm, and a treatment Sol sees the win
    // although Nekro is a control seat.
    @Test
    void aRivalViewFollowsTheDecidingSeatsFlag() {
        test.points(test.nekro, 9);
        raiseFleetValue();
        AiExperiments.disable(test.sol, EnumSet.of(AiExperiments.Experiment.WIN_AWARE_POINTS));

        ObjectiveValue seenByControl = ObjectiveValue.publicOnly(test.game, test.nekro, test.sol);
        assertThat(seenByControl.gain(cruiserJoins(seenByControl))).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);

        AiExperiments.disable(test.sol, EnumSet.noneOf(AiExperiments.Experiment.class));
        disableExperiment();
        ObjectiveValue seenByTreatment = ObjectiveValue.publicOnly(test.game, test.nekro, test.sol);
        assertThat(seenByTreatment.gain(cruiserJoins(seenByTreatment))).isEqualTo(ScoringStakes.WIN_VALUE);
    }

    @Test
    void withTheExperimentOffAPublicBeyondTheSlotCountsInFull() {
        reveal("deep_space");
        disableExperiment();
        ObjectiveValue value = withRaiseFleetReady();

        assertThat(value.gainAt("deep_space", 3)).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // A secret ignores the public slot: it is scored on its own.
    @Test
    void aSecretIsNotCappedByThePublicSlot() {
        reveal("trade_routes");
        test.nekro.setTg(5);
        test.nekro.setSecret("gamf");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        assertThat(value.gain(value.before().withBuilt(AiTestGame.HOME, Map.of(UnitType.Dreadnought, 5))))
                .isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Destroyers in six systems (home and five around it) already qualify for Control the Region, and Gather a
    // Mighty Fleet is held too.
    private ObjectiveValue withControlTheRegionReady() {
        test.nekro.setSecret("ctr");
        test.nekro.setSecret("gamf");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        for (String position : adjacentEmptyPositions(5)) {
            test.place(EMPTY_SYSTEM, position);
            test.units(test.game.getTileByPosition(position), "space", test.nekro, UnitType.Destroyer, 1);
        }
        return new ObjectiveValue(test.game, test.nekro);
    }

    private static Footprint fiveDreadnoughts(ObjectiveValue value) {
        return value.before().withBuilt(AiTestGame.HOME, Map.of(UnitType.Dreadnought, 5));
    }

    // LRR 61.6: one secret objective per status phase. Control the Region already takes the secret slot, so
    // completing Gather a Mighty Fleet waits a round and counts at the partial share (0.6 x 8 = 4.8), like a public
    // past the public slot.
    @Test
    void aSecondReadySecretWaitsARound() {
        ObjectiveValue value = withControlTheRegionReady();

        assertThat(value.gain(fiveDreadnoughts(value)))
                .isCloseTo(0.6 * ObjectiveValue.VICTORY_POINT_VALUE, within(1e-9));
    }

    // At 9 of 10 the ready Control the Region is already the winning point, so a second secret is not a second win.
    @Test
    void aSecondReadySecretIsNotTheWinWhenTheFirstAlreadyWins() {
        test.points(test.nekro, 9);
        ObjectiveValue value = withControlTheRegionReady();

        assertThat(value.gain(fiveDreadnoughts(value)))
                .isCloseTo(0.6 * ObjectiveValue.VICTORY_POINT_VALUE, within(1e-9));
    }

    @Test
    void withTheExperimentOffASecondReadySecretCountsInFull() {
        disableExperiment();
        ObjectiveValue value = withControlTheRegionReady();

        assertThat(value.gain(fiveDreadnoughts(value))).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // The move hands back a Support for the Throne: one point back for one point gained nets nothing, so the
    // completion is worth nothing, even at 9 of 10.
    @Test
    void aCompletionThatOnlyCancelsAReturnedThroneIsWorthNothing() {
        test.points(test.nekro, 9);
        ObjectiveValue value = raiseFleetValue();

        assertThat(value.gain(cruiserJoins(value), 1)).isZero();
    }

    // Two points gained for one returned still reaches 10 from 9, so it is still the win.
    @Test
    void twoCompletionsPastAReturnedThroneStillWin() {
        test.points(test.nekro, 9);
        reveal("raise_fleet");
        test.nekro.setSecret("gamf");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);
        Footprint built = value.before().withBuilt(AiTestGame.HOME, Map.of(UnitType.Dreadnought, 5));

        assertThat(value.gain(built, 1)).isEqualTo(ScoringStakes.WIN_VALUE);
    }

    // Breaking up a fleet that already qualifies for the winning public loses the win.
    @Test
    void breakingTheWinningReadyPublicLosesTheWin() {
        test.points(test.nekro, 9);
        reveal("raise_fleet");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 5);
        test.place(EMPTY_SYSTEM, neighbour);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint split = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(split)).isEqualTo(-ScoringStakes.WIN_VALUE);
    }

    // Partial progress stays on the flat base, so it is unchanged by the experiment.
    @Test
    void partialProgressIsUnchanged() {
        test.points(test.nekro, 9);
        reveal("deep_space");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.place(EMPTY_SYSTEM, neighbour);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);
        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());
        double treated = value.gain(after);

        disableExperiment();
        ObjectiveValue control = new ObjectiveValue(test.game, test.nekro);

        assertThat(treated).isPositive().isEqualTo(control.gain(after));
    }
}
