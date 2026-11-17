package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// resolveQueue must only change game state; the announcements are sent by the caller
// once the game is saved, so a deploy cut-off can't reveal a draw the save lacks.
class HelperResolveQueueTest extends BaseTi4Test {

    private static final String QUEUE = "queueToDrawSOs";
    private static final String BLOCKERS = "potentialBlockers";

    private Game game;
    private Player sol;
    private Player hacan;
    private Player jolnar;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        sol = addPlayer("sol", "blue");
        hacan = addPlayer("hacan", "green");
        jolnar = addPlayer("jolnar", "red");
        // In the agenda phase the speaker stands in for the Imperial holder.
        game.setPhaseOfGame("agenda");
        game.setSpeaker(sol);
    }

    private Player addPlayer(String faction, String color) {
        Player player = game.addPlayer(faction + "-id", faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    @Test
    void queuedPlayersDrawAndLeaveTheQueueWithoutAnyMessage() {
        game.setStoredValue(QUEUE, "sol*jolnar*");

        QueuedSecretDraws draws;
        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            draws = Helper.resolveQueue(game);
            messages.verifyNoInteractions();
        }

        assertThat(sol.getSecrets()).hasSize(1);
        assertThat(jolnar.getSecrets()).hasSize(1);
        assertThat(hacan.getSecrets()).isEmpty();
        assertThat(game.getStoredValue(QUEUE)).isEmpty();
        assertThat(draws.hasDraws()).isTrue();
        assertThat(draws.outcomes())
                .containsExactly(new QueuedSecretDraws.Draw(sol, false), new QueuedSecretDraws.Draw(jolnar, false));
    }

    @Test
    void aPotentialBlockerStopsLaterPlayersFromDrawing() {
        game.setStoredValue(QUEUE, "sol*jolnar*");
        game.setStoredValue(BLOCKERS, "hacan*");

        QueuedSecretDraws draws = Helper.resolveQueue(game);

        assertThat(sol.getSecrets()).hasSize(1);
        assertThat(jolnar.getSecrets()).isEmpty();
        assertThat(game.getStoredValue(QUEUE)).isEqualTo("jolnar*");
        assertThat(draws.outcomes())
                .containsExactly(new QueuedSecretDraws.Draw(sol, false), new QueuedSecretDraws.Blocker(hacan));
    }

    @Test
    void aBlockerWithNobodyQueuedBehindItIsNotReported() {
        game.setStoredValue(QUEUE, "sol*");
        game.setStoredValue(BLOCKERS, "hacan*");

        QueuedSecretDraws draws = Helper.resolveQueue(game);

        assertThat(draws.outcomes()).containsExactly(new QueuedSecretDraws.Draw(sol, false));
    }

    @Test
    void plausibleDeniabilityDrawsASecondSecret() {
        hacan.addAbility("plausible_deniability");
        game.setStoredValue(QUEUE, "hacan*");

        QueuedSecretDraws draws = Helper.resolveQueue(game);

        assertThat(hacan.getSecrets()).hasSize(2);
        assertThat(draws.outcomes()).containsExactly(new QueuedSecretDraws.Draw(hacan, true));
    }

    @Test
    void anEmptyQueueChangesNothing() {
        int deckSize = game.getSecretObjectives().size();

        QueuedSecretDraws draws = Helper.resolveQueue(game);

        assertThat(draws.hasDraws()).isFalse();
        assertThat(draws.outcomes()).isEmpty();
        assertThat(game.getSecretObjectives()).hasSize(deckSize);
    }
}
