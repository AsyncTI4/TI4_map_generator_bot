package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

/**
 * The "when" labels for promissory notes were built by string-concatenating {@code getColor()}, which is an
 * Optional, so a held Political Secret read "Optional[red] Political Secret" and Political Favor, which has
 * no color, read "Optional.empty Political Favor".
 */
class AgendaWhensAftersHelperTest extends BaseTi4Test {

    private Game game;
    private Player holder;
    private Player owner;

    @BeforeEach
    void setUp() {
        game = new Game();
        owner = game.addPlayer("owner-id", "owner");
        owner.setFaction("xxcha");
        owner.setColor("red");
        holder = game.addPlayer("holder-id", "holder");
        holder.setFaction("sol");
        holder.setColor("blue");
    }

    private void giveNote(String pnID) {
        owner.addOwnedPromissoryNoteByID(pnID);
        holder.setPromissoryNote(pnID);
    }

    private void vetoPlayedByTheHolder() {
        owner.setUserID("owner-id");
        game.setSpeakerUserID(owner.getUserID());
        game.setPhaseOfGame("agendawaiting");
        game.setStoredValue("lastPlayerToPlayAWhen", holder.getFaction());
        game.setStoredValue("whenQueueHeldByVeto", holder.getFaction());
    }

    // The queue halts at a Veto. If the Veto is cancelled the next player in the queue is asked again.
    @Test
    void resumesTheQueueWhenTheVetoThatHeldItIsCancelled() {
        vetoPlayedByTheHolder();
        try (MockedStatic<MessageHelper> messages = Mockito.mockStatic(MessageHelper.class)) {
            AgendaWhensAftersHelper.resumeWhenQueueAfterCanceledVeto(
                    Mockito.mock(ButtonInteractionEvent.class), game, holder);

            messages.verify(
                    () -> MessageHelper.sendMessageToChannel(ArgumentMatchers.<MessageChannel>any(), contains("nudge")),
                    atLeastOnce());
            messages.verify(
                    () -> MessageHelper.sendMessageToChannelWithButtons(
                            ArgumentMatchers.<MessageChannel>any(), contains("play a \"when\""), anyList()),
                    atLeastOnce());
        }
    }

    @Test
    void doesNotResumeAQueueThatAnotherPlayerHolds() {
        vetoPlayedByTheHolder();
        game.setStoredValue("whenQueueHeldByVeto", owner.getFaction());

        assertThatNothingIsAskedOf(() -> {});
    }

    // While the Veto stands (its _Sabotage_ window is open) a later pass must not advance the queue.
    @Test
    void aPassWhileTheVetoStandsDoesNotMoveTheQueue() {
        vetoPlayedByTheHolder();
        game.setStoredValue("declinedWhens", holder.getFaction() + "_");
        ButtonInteractionEvent passing = Mockito.mock(ButtonInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);

        try (MockedStatic<MessageHelper> messages = Mockito.mockStatic(MessageHelper.class)) {
            AgendaWhensAftersHelper.declineToQueueAWhen(game, passing, owner);

            messages.verify(
                    () -> MessageHelper.sendMessageToChannel(
                            ArgumentMatchers.<MessageChannel>any(), contains("All players have passed")),
                    Mockito.never());
        }
        assertThat(game.getStoredValue("whensResolved")).isEmpty();
    }

    @Test
    void aPassAfterTheVetoWasCanceledResolvesTheQueue() {
        vetoPlayedByTheHolder();
        game.setStoredValue("declinedWhens", holder.getFaction() + "_");
        ButtonInteractionEvent passing = Mockito.mock(ButtonInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);

        try (MockedStatic<MessageHelper> messages = Mockito.mockStatic(MessageHelper.class)) {
            AgendaWhensAftersHelper.resumeWhenQueueAfterCanceledVeto(passing, game, holder);
            AgendaWhensAftersHelper.declineToQueueAWhen(game, passing, owner);
        }
        assertThat(game.getStoredValue("whensResolved")).isEqualTo("Yes");
    }

    @Test
    void doesNotResumeAQueueOutsideTheWhenWindow() {
        vetoPlayedByTheHolder();
        game.setPhaseOfGame("agendaVoting");

        assertThatNothingIsAskedOf(() -> {});
    }

    @Test
    void doesNotResumeAQueueWhoseWhensHaveAllBeenResolved() {
        vetoPlayedByTheHolder();
        game.setStoredValue("whensResolved", "Yes");

        assertThatNothingIsAskedOf(() -> {});
    }

    private void assertThatNothingIsAskedOf(Runnable setUp) {
        setUp.run();
        try (MockedStatic<MessageHelper> messages = Mockito.mockStatic(MessageHelper.class)) {
            AgendaWhensAftersHelper.resumeWhenQueueAfterCanceledVeto(
                    Mockito.mock(ButtonInteractionEvent.class), game, holder);

            messages.verifyNoInteractions();
        }
    }

    @Test
    void politicalSecretButtonShowsTheNoteColor() {
        giveNote("red_ps");

        assertThat(AgendaWhensAftersHelper.getPossibleWhenButtons(holder))
                .extracting(Button::getLabel)
                .containsExactly("Red Political Secret");
    }

    @Test
    void politicalSecretIsListedWithTheNoteColor() {
        giveNote("red_ps");

        assertThat(AgendaWhensAftersHelper.getPossibleWhenNames(holder)).containsExactly("Red Political Secret");
    }

    @Test
    void colorlessFactionNoteHasNoColorPrefix() {
        giveNote("favor");

        assertThat(AgendaWhensAftersHelper.getPossibleWhenButtons(holder))
                .extracting(Button::getLabel)
                .containsExactly("Political Favor");
        assertThat(AgendaWhensAftersHelper.getPossibleWhenNames(holder)).containsExactly("Political Favor");
    }
}
