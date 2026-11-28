package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.service.planet.FlipTileService;
import ti4.testUtils.BaseTi4Test;

// A ship moving into the inactive Wormhole Nexus flips it only at the end of the Movement step (LRR 100.2a), which is
// after Space Cannon Offense (LRR 58.7). So for that action only cannons reaching the Nexus through its gamma wormhole
// can fire, and the engine offers the shot to nobody else. By the time the AI looks, the tile on the board is already
// the active side with alpha and beta wormholes. In game aiab9700b Sol waited on Xxcha cannons beside an alpha
// wormhole that never had a shot, and fell back.
class OffenceCoverTest extends BaseTi4Test {

    private static final String NEXUS = "tl";
    private static final String LODOR = "313";

    private AiTestGame test;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        // The fixture deals no decks; the locked-side check copies the game, and saving needs them, as a real game has.
        test.game.newGameSetup();
        xxcha = test.addSeat("7100000555555555", "xxcha", "green");
        Tile lodor = test.place("26", LODOR);
        test.units(lodor, "space", xxcha, UnitType.Flagship, 1);
        Tile nexus = test.place("82a", NEXUS);
        test.units(nexus, "space", test.sol, UnitType.Carrier, 1);
        test.isActive(test.sol, "action");
        test.game.setActiveSystem(NEXUS);
    }

    private void activatedAs(String tileName) {
        test.game.setStoredValue("currentActionSummarysol", " Activated " + NEXUS + " (" + tileName + ").");
    }

    private void flip() {
        FlipTileService.flipTileIfNeeded(test.game.getTileByPosition(NEXUS), test.game);
    }

    @Test
    void cannonsBesideAnAlphaWormholeHaveNoShotAtANexusThatWasLockedWhenActivated() {
        activatedAs("Mallice - Locked Nexus");
        flip();

        assertThat(OffenceCover.of(test.game, test.sol, NEXUS)).doesNotContain(xxcha);
    }

    @Test
    void theSameCannonsCoverANexusThatWasAlreadyActive() {
        flip();
        activatedAs("Mallice - Nexus");

        assertThat(OffenceCover.of(test.game, test.sol, NEXUS)).contains(xxcha);
    }

    // Before movement the tile is still the locked side, and the engine check already gives the right answer.
    @Test
    void aLockedNexusOnTheBoardHasNoAlphaCover() {
        activatedAs("Mallice - Locked Nexus");

        assertThat(OffenceCover.of(test.game, test.sol, NEXUS)).doesNotContain(xxcha);
    }

    // The engine still posts a window when someone else covers the Nexus; Xxcha must not take a shot it never had.
    @Test
    void anAiDefenderDoesNotFireAtANexusThatWasLockedWhenActivated() {
        activatedAs("Mallice - Locked Nexus");
        flip();
        AiPrompt window = prompt(
                "window",
                PromptSource.PUBLIC,
                NOW,
                "combatRoll_" + NEXUS + "_space_spacecannonoffence",
                "declinePDS_" + test.game.getTileByPosition(NEXUS).getTileID());

        assertThat(CombatRules.next(test.contextFor(xxcha, Set.of(), NOW, window)))
                .isEmpty();
    }
}
