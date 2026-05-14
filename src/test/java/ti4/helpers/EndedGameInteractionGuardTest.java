package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.objectives.ScorePublicObjectiveService;

class EndedGameInteractionGuardTest {

    @Test
    void scorePublicObjectiveIsBlockedAfterGameEnds() {
        Game game = new Game();
        game.setHasEnded(true);
        game.setRevealedPublicObjectives(new HashMap<>());
        game.getRevealedPublicObjectives().put("push_boundaries", 1);
        Player player = new Player("user", "user", game);

        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            ScorePublicObjectiveService.scorePO(mock(GenericInteractionCreateEvent.class), game, player, 1);
        }

        assertThat(game.getScoredPublicObjectives()).isEmpty();
    }

    @Test
    void scoreSecretObjectiveIsBlockedAfterGameEnds() {
        Game game = new Game();
        game.setHasEnded(true);
        Player player = new Player("user", "user", game);
        player.setSecret("accept_bribes_pbd100", 1);

        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            boolean scored = SecretObjectiveHelper.scoreSO(
                    mock(GenericInteractionCreateEvent.class), game, player, 1, mock(MessageChannel.class));

            assertThat(scored).isFalse();
        }

        assertThat(player.getSecrets()).containsEntry("accept_bribes_pbd100", 1);
        assertThat(player.getSecretsScored()).isEmpty();
    }

    @Test
    void actionCardPlayIsBlockedAfterGameEnds() {
        Game game = new Game();
        game.setHasEnded(true);
        Player player = new Player("user", "user", game);
        player.setActionCard("dh1", 1);

        String error = ActionCardHelper.playAC(
                mock(GenericInteractionCreateEvent.class), game, player, "1", mock(MessageChannel.class));

        assertThat(error).isEqualTo("This game has ended. You cannot play action cards.");
        assertThat(player.getActionCards()).containsEntry("dh1", 1);
        assertThat(game.getDiscardActionCards()).isEmpty();
    }
}
