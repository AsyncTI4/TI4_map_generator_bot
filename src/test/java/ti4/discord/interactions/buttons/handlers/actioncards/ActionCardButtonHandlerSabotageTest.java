package ti4.discord.interactions.buttons.handlers.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
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

    private void windowIs(boolean open) {
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenReturn(
                        open
                                ? Optional.of(new GameMessage(
                                        WINDOW_MESSAGE, GameMessageType.ACTION_CARD, new LinkedHashSet<>(), 0L))
                                : Optional.empty());
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
        windowIs(true);

        sabotage("Mining Initiative");

        cards.verify(() -> ActionCardHelper.playAC(any(), any(), any(), anyString(), any()));
        messages.verify(() -> GameMessageManager.remove(game.getName(), WINDOW_MESSAGE));
        assertThat(playWasCanceled(0)).isTrue();
    }

    // The first Sabotage removes the window, so a second press (from a player who answered before it landed) spends
    // nothing and cancels nothing, in particular not an earlier play of the same card.
    @Test
    void refusesASecondSabotageOnAWindowThatIsAlreadyCanceled() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        game.getGameStats().markLatestPlayCanceled("Mining Initiative");
        windowIs(false);

        sabotage("Mining Initiative");

        cards.verify(() -> ActionCardHelper.playAC(any(), any(), any(), anyString(), any()), never());
        messages.verify(() -> GameMessageManager.remove(anyString(), anyString()), never());
        assertThat(playWasCanceled(0)).isFalse();
        assertThat(playWasCanceled(1)).isTrue();
        assertThat(nekro.getActionCards()).containsKey("sabo1");
    }

    @Test
    void refusesASabotageOnAWindowThatHasClosed() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        windowIs(false);

        sabotage("Mining Initiative");

        cards.verify(() -> ActionCardHelper.playAC(any(), any(), any(), anyString(), any()), never());
        assertThat(playWasCanceled(0)).isFalse();
    }

    // The card's effect is applied when it is played, before anyone can cancel it; cancelling it takes the effect
    // back from the card's player, not from the player who pressed the button.
    @Test
    void takesWarMachineBackFromItsPlayer() {
        game.getGameStats().recordAcPlay("War Machine", sol);
        sol.addSpentThing("warmachine");
        nekro.addSpentThing("warmachine");
        windowIs(true);

        sabotage("War Machine");

        assertThat(sol.getSpentThingsThisWindow()).doesNotContain("warmachine");
        assertThat(nekro.getSpentThingsThisWindow()).contains("warmachine");
    }

    @Test
    void takesFlankSpeedBack() {
        game.getGameStats().recordAcPlay("Flank Speed", sol);
        game.setStoredValue("flankspeedBoost", "1");
        windowIs(true);

        sabotage("Flank Speed");

        assertThat(game.getStoredValue("flankspeedBoost")).isEmpty();
    }

    @Test
    void leavesTheEffectsOfOtherCardsAlone() {
        game.getGameStats().recordAcPlay("Mining Initiative", sol);
        game.setStoredValue("flankspeedBoost", "1");
        sol.addSpentThing("warmachine");
        windowIs(true);

        sabotage("Mining Initiative");

        assertThat(game.getStoredValue("flankspeedBoost")).isEqualTo("1");
        assertThat(sol.getSpentThingsThisWindow()).contains("warmachine");
    }
}
