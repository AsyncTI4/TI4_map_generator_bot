package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

/**
 * Regression guard for the HIDE_AC_DISCARD leak via Mirrored Memories: a forced/unplayed discard must not
 * make {@link OblivionTechHandler#canUseMirroredMemories} eligible, or the Oblivion player could learn about
 * and play a component action card they aren't supposed to be able to see.
 */
class OblivionTechHandlerTest extends BaseTi4Test {

    private static final String MM = "thobliviong";
    private static final String COMPONENT_ACTION_CARD = "cripple"; // window: "Action"

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);

        game = new Game();
        game.setName("test-game");
        player = game.addPlayer("test-user-id", "arborec");
        player.setFaction("arborec");
        player.setColor("red");
        player.addTech(MM);

        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_AC_DISCARD, true);
    }

    @Test
    void canUseMirroredMemories_forcedDiscardOnly_isNotEligible() {
        seedDiscard(COMPONENT_ACTION_CARD, false);

        assertThat(OblivionTechHandler.canUseMirroredMemories(game, player)).isFalse();
    }

    @Test
    void canUseMirroredMemories_playedDiscard_isEligible() {
        seedDiscard(COMPONENT_ACTION_CARD, true);

        assertThat(OblivionTechHandler.canUseMirroredMemories(game, player)).isTrue();
    }

    @Test
    void canUseMirroredMemories_forcedDiscard_withHideAcDiscardOff_isEligible() {
        game.setFowOption(FOWOption.HIDE_AC_DISCARD, false);
        seedDiscard(COMPONENT_ACTION_CARD, false);

        assertThat(OblivionTechHandler.canUseMirroredMemories(game, player)).isTrue();
    }

    private void seedDiscard(String acID, boolean played) {
        player.setActionCard(acID);
        game.discardActionCard(player.getUserID(), player.getActionCards().get(acID), played);
    }
}
