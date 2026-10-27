package ti4.discord.interactions.buttons.handlers.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class ActionCardButtonHandlerSabotageTest extends BaseTi4Test {

    private static final String WINDOW_MESSAGE = "123456";

    private Game game;
    private Player sol;
    private Player nekro;
    private ButtonInteractionEvent event;
    private MockedStatic<GameMessageManager> messages;
    private MockedStatic<ActionCardHelper> cards;
    private MockedStatic<MessageHelper> helper;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("sabotage-handler-test");
        sol = game.addPlayer("200000000000000001", "Sol");
        sol.setFaction("sol");
        sol.setColor("blue");
        nekro = game.addPlayer("200000000000000002", "Nekro");
        nekro.setFaction("nekro");
        nekro.setColor("black");
        nekro.setActionCard("sabo1", 1);
        event = mock(ButtonInteractionEvent.class);
        when(event.getMessageId()).thenReturn(WINDOW_MESSAGE);
        messages = mockStatic(GameMessageManager.class);
        cards = mockStatic(ActionCardHelper.class);
        helper = mockStatic(MessageHelper.class);
    }

    @AfterEach
    void tearDown() {
        helper.close();
        cards.close();
        messages.close();
    }

    private void sabotage(String title) {
        ActionCardButtonHandler.sabotage(event, nekro, "sabotage_ac_" + title + "_sol", game);
    }

    private boolean playWasCanceled(int index) {
        return game.getGameStats().getActionCardPlays().get(index).isCanceled();
    }

    @Test
    void cancelsACardWhoseWindowIsOpen() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);

        sabotage("Mining Initiative");

        cards.verify(() -> ActionCardHelper.playAC(any(), any(), any(), anyString(), any()));
        messages.verify(() -> GameMessageManager.remove(game.getName(), WINDOW_MESSAGE));
        assertThat(playWasCanceled(0)).isTrue();
    }

    // A player who answered before the first Sabotage landed can still press the button. That second press spends
    // nothing and cancels nothing, in particular not an earlier play of the same card.
    @Test
    void refusesASecondSabotageOnAWindowThatIsAlreadyCanceled() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        sabotage("Mining Initiative");

        sabotage("Mining Initiative");

        cards.verify(() -> ActionCardHelper.playAC(any(), any(), any(), anyString(), any()), times(1));
        assertThat(playWasCanceled(0)).isFalse();
        assertThat(playWasCanceled(1)).isTrue();
    }

    // Only a window that was canceled refuses a press. One the bot stopped tracking for another reason (everyone
    // answered, it went stale) still cancels the card, as it did before.
    @Test
    void stillCancelsACardWhoseWindowWasNotCanceled() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString())).thenReturn(Optional.empty());

        sabotage("Mining Initiative");

        cards.verify(() -> ActionCardHelper.playAC(any(), any(), any(), anyString(), any()));
        assertThat(playWasCanceled(0)).isTrue();
    }

    // The card's effect is applied when it is played, before anyone can cancel it; cancelling it takes the effect
    // back from the card's player, not from the player who pressed the button.
    @Test
    void takesWarMachineBackFromItsPlayer() {
        game.getGameStats().recordAcPlay("War Machine", sol);
        sol.addSpentThing("warmachine");
        nekro.addSpentThing("warmachine");

        sabotage("War Machine");

        assertThat(sol.getSpentThingsThisWindow()).doesNotContain("warmachine");
        assertThat(nekro.getSpentThingsThisWindow()).contains("warmachine");
    }

    @Test
    void takesFlankSpeedBack() {
        game.getGameStats().recordAcPlay("Flank Speed", sol);
        game.setStoredValue("flankspeedBoost", "1");

        sabotage("Flank Speed");

        assertThat(game.getStoredValue("flankspeedBoost")).isEmpty();
    }

    // A rider is erased because its card was cancelled, not because someone pressed the button: a player with no
    // Sabotage in hand cancels nothing, and the rider stays.
    @Test
    void keepsTheRiderWhenTheSabotageFailsForWantOfASabotageInHand() {
        nekro.getActionCards().clear();
        game.getGameStats().recordAcPlay("Imperial Rider", sol);
        game.setCurrentAgendaVote("for", "sol_Imperial Rider");

        sabotage("Imperial Rider");

        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("sol_Imperial Rider");
        assertThat(playWasCanceled(0)).isFalse();
    }

    @Test
    void erasesTheRiderWhenTheSabotageCancelsIt() {
        game.getGameStats().recordAcPlay("Imperial Rider", sol);
        game.setCurrentAgendaVote("for", "sol_Imperial Rider");

        sabotage("Imperial Rider");

        assertThat(game.getCurrentAgendaVotes().get("for")).isEmpty();
        assertThat(playWasCanceled(0)).isTrue();
    }

    // The when queue stops at a Veto until it is resolved; if the Veto is cancelled nothing else would start it again.
    @Test
    void resumesTheWhenQueueWhenTheVetoThatHeldItIsCancelled() {
        game.setSpeakerUserID(nekro.getUserID());
        game.setPhaseOfGame("agendawaiting");
        game.setStoredValue("lastPlayerToPlayAWhen", "sol");
        game.setStoredValue("whenQueueHeldByVeto", "sol");
        game.getGameStats().recordAcPlay("Veto", sol);

        sabotage("Veto");

        helper.verify(
                () -> MessageHelper.sendMessageToChannel(ArgumentMatchers.<MessageChannel>any(), contains("nudge")));
    }

    @Test
    void doesNotTouchTheWhenQueueForACardThatIsNotAVeto() {
        game.setSpeakerUserID(nekro.getUserID());
        game.setPhaseOfGame("agendawaiting");
        game.setStoredValue("lastPlayerToPlayAWhen", "sol");
        game.getGameStats().recordAcPlay("Diplomatic Pressure", sol);

        sabotage("Diplomatic Pressure");

        helper.verify(
                () -> MessageHelper.sendMessageToChannel(ArgumentMatchers.<MessageChannel>any(), contains("nudge")),
                never());
    }

    @Test
    void leavesTheEffectsOfOtherCardsAlone() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        game.setStoredValue("flankspeedBoost", "1");
        sol.addSpentThing("warmachine");

        sabotage("Mining Initiative");

        assertThat(game.getStoredValue("flankspeedBoost")).isEqualTo("1");
        assertThat(sol.getSpentThingsThisWindow()).contains("warmachine");
    }
}
