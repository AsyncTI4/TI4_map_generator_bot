package ti4.ai.promissory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class PlayAreaNotesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        tile = test.place("19", AiTestGame.HOME);
    }

    private TacticalPlan plan(boolean moves, double score) {
        List<UnitMove> unitMoves = moves
                ? List.of(new UnitMove(AiTestGame.neighbourOf(AiTestGame.HOME), "space", UnitType.Carrier, 1))
                : List.of();
        return new TacticalPlan(moves ? Kind.POSITION : Kind.PRODUCE, tile.getPosition(), unitMoves, Map.of(), score);
    }

    private void aiHoldsInPlayArea(String note) {
        test.sol.addOwnedPromissoryNoteByID(note);
        test.nekro.setPromissoryNote(note);
        test.nekro.addPromissoryNoteToPlayArea(note);
    }

    // Activating a system with Sol's units would hand Sol's Support for the Throne back: a victory point lost.
    @Test
    void chargesAVictoryPointForReturningSupportForTheThrone() {
        aiHoldsInPlayArea("blue_sftt");
        test.units(tile, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(PlayAreaNotes.adjustedScore(test.game, test.nekro, tile, plan(false, 10.0)))
                .isEqualTo(10.0 - ObjectiveValue.VICTORY_POINT_VALUE);
    }

    @Test
    void chargesNothingWhereTheOwnerHasNoUnits() {
        aiHoldsInPlayArea("blue_sftt");

        assertThat(PlayAreaNotes.adjustedScore(test.game, test.nekro, tile, plan(false, 10.0)))
                .isEqualTo(10.0);
    }

    // Its Ceasefire is out of its hand and Sol, the only other player, has units in the target: moving in would
    // likely be stopped, producing would not.
    @Test
    void discountsMovingWhereItsCeasefireCanBePlayed() {
        test.nekro.addOwnedPromissoryNoteByID("black_cf");
        test.sol.setPromissoryNote("black_cf");
        test.units(tile, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(PlayAreaNotes.adjustedScore(test.game, test.nekro, tile, plan(true, 8.0)))
                .isEqualTo(2.0);
        assertThat(PlayAreaNotes.adjustedScore(test.game, test.nekro, tile, plan(false, 8.0)))
                .isEqualTo(8.0);
    }

    // It cannot see who holds its Ceasefire: with two other players and only one of them in the target, the risk is
    // halved (8 - 8 x 0.75 x 1/2).
    @Test
    void weighsTheCeasefireRiskByWhoCouldHoldIt() {
        Player hacan = test.addSeat("200000000000000002", "hacan", "yellow");
        test.nekro.addOwnedPromissoryNoteByID("black_cf");
        hacan.setPromissoryNote("black_cf");
        test.units(tile, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(PlayAreaNotes.adjustedScore(test.game, test.nekro, tile, plan(true, 8.0)))
                .isEqualTo(5.0);
    }

    // Betray a Friend counts the notes in its play area at the start of its tactical action, before activating a
    // system with their owner's units sends them back.
    @Test
    void remembersWhoseNotesItHeldWhenItsActionStarted() {
        aiHoldsInPlayArea("blue_an");
        test.aiIsActive("action");
        PromissoryRules.observe(test.context());

        test.nekro.removePromissoryNote("blue_an");
        test.sol.setPromissoryNote("blue_an");

        assertThat(PlayAreaNotes.heldAtActionStart(test.context(), test.sol)).isTrue();
    }
}
