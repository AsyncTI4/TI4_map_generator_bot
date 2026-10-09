package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The fingerprint tells the stall detector whether anything the AI cares about changed since its last look.
class AiProgressTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.aiIsActive("action");
    }

    @Test
    void isStableWhileNothingChanges() {
        assertThat(AiProgress.fingerprint(test.game, test.nekro))
                .isEqualTo(AiProgress.fingerprint(test.game, test.nekro));
    }

    // Picking units during a tactical action only changes the displacement, not the board.
    @Test
    void changesWhenUnitsAreStagedForMovement() {
        int before = AiProgress.fingerprint(test.game, test.nekro);

        test.game
                .getTacticalActionDisplacement()
                .computeIfAbsent("301-space", ignored -> new HashMap<>())
                .put(Units.getUnitKey(UnitType.Carrier, "black"), new ArrayList<>(List.of(1, 0, 0, 0)));

        assertThat(AiProgress.fingerprint(test.game, test.nekro)).isNotEqualTo(before);
    }

    // Landing moves units between holders of the same tile; the unit count stays the same.
    @Test
    void changesWhenUnitsMoveBetweenHolders() {
        int before = AiProgress.fingerprint(test.game, test.nekro);

        home.removeUnit("mordaiii", Units.getUnitKey(UnitType.Infantry, "black"), 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 1);

        assertThat(AiProgress.fingerprint(test.game, test.nekro)).isNotEqualTo(before);
    }

    @Test
    void changesWhenUnitsMoveToAnotherSystem() {
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        int before = AiProgress.fingerprint(test.game, test.nekro);

        home.removeUnit("space", Units.getUnitKey(UnitType.Carrier, "black"), 1);
        test.units(lodor, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(AiProgress.fingerprint(test.game, test.nekro)).isNotEqualTo(before);
    }

    // Each press while building a trade offer only adds an item to the AI's transaction list; that is progress, so the
    // attempt guard does not stop the builder after two presses.
    @Test
    void changesWhenATransactionItemIsAdded() {
        int before = AiProgress.fingerprint(test.game, test.nekro);

        test.nekro.addTransactionItem("sendingsol_receivingnekro_Comms_3");

        assertThat(AiProgress.fingerprint(test.game, test.nekro)).isNotEqualTo(before);
    }

    // Another player's units are not the AI's progress.
    @Test
    void ignoresOtherPlayersUnits() {
        int before = AiProgress.fingerprint(test.game, test.nekro);

        test.units(home, "space", test.sol, UnitType.Fighter, 1);

        assertThat(AiProgress.fingerprint(test.game, test.nekro)).isEqualTo(before);
    }
}
