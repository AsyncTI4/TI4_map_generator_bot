package ti4.helpers.thundersedge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Set;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

/**
 * Crisis is played at the END of the previous player's turn, so it must resolve before any start-of-turn
 * preset (Extreme Duress, Stasis) aimed at the player whose turn is being skipped. Reported in a live game
 * where both were preset on the same player and the bot fired Extreme Duress first.
 */
class TeHelperActionCardsPresetTurnStartTest extends BaseTi4Test {

    private Game game;
    private Player target;
    private Player crisisHolder;
    private Player duressHolder;

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);

        game = new Game();
        game.setName("presetTurnStartTest");
        target = addPlayer("p1", "nekro", "red", 1);
        crisisHolder = addPlayer("p2", "sol", "green", 2);
        duressHolder = addPlayer("p3", "hacan", "yellow", 3);

        crisisHolder.setActionCard("crisis", 11);
        duressHolder.setActionCard("extremeduress", 12);
    }

    private Player addPlayer(String id, String faction, String color, int sc) {
        Player player = game.addPlayer(id, id);
        player.setFaction(faction);
        player.setColor(color);
        player.setSCs(Set.of(sc));
        return player;
    }

    @Test
    void crisisPresetResolvesAndHoldsExtremeDuressOnTheSkippedPlayer() {
        game.setStoredValue("Crisis Target", target.getColor());
        game.setStoredValue("ExtremeDuress", target.getColor());

        try (MockedStatic<ActionCardHelper> acHelper = mockStatic(ActionCardHelper.class);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            TeHelperActionCards.resolvePresetTurnStartCards(null, game, target);

            acHelper.verify(() -> ActionCardHelper.playAC(any(), eq(game), eq(crisisHolder), eq("crisis"), any()));
            acHelper.verify(() -> ActionCardHelper.playAC(any(), any(), any(), eq("extremeduress"), any()), never());
        }

        assertThat(game.getStoredValue("Crisis Target")).isEmpty();
        // Still armed: fires if the Crisis is sabotaged, or at the start of the target's next turn.
        assertThat(game.getStoredValue("ExtremeDuress")).isEqualTo(target.getColor());
    }

    @Test
    void extremeDuressPresetResolvesWhenNoCrisisIsPreset() {
        game.setStoredValue("ExtremeDuress", target.getColor());

        try (MockedStatic<ActionCardHelper> acHelper = mockStatic(ActionCardHelper.class);
                MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            TeHelperActionCards.resolvePresetTurnStartCards(null, game, target);

            acHelper.verify(
                    () -> ActionCardHelper.playAC(any(), eq(game), eq(duressHolder), eq("extremeduress"), any()));
        }

        assertThat(game.getStoredValue("ExtremeDuress")).isEmpty();
    }
}
