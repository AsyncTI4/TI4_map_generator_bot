package ti4.discord.interactions.buttons.handlers.spending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;

/**
 * Reset Spent rebuilds every spend button and keeps the extra buttons already on the message (Done etc.).
 * A player with many planets used to overflow Discord's 5 action rows and crash the edit; the buttons
 * now page on the same message, with Reset and the carried-over buttons pinned to every page.
 */
class SpendingButtonHandlerPagingTest {

    private static final int MAX_BUTTONS = 25;
    private static final int MAX_ROWS = 5;

    private final Game game = new Game();
    private final Player player = new Player("user", "name", game);

    @Test
    void resetWithFewButtonsShowsEverythingWithoutPaging() {
        List<Button> shown = pressOnMessage(
                "resetSpend_inf", "old text", planetButtons(10), List.of(done(), resetButton()), SpendCall.RESET_);

        assertThat(ids(shown)).hasSize(12).contains("deleteButtons_spitItOut", "resetSpend_inf");
        assertThat(ids(shown)).noneMatch(id -> id.startsWith("spendPage_"));
    }

    @Test
    void resetWithTooManyButtonsPagesInsteadOfOverflowing() {
        List<Button> shown = pressOnMessage(
                "resetSpend_inf", "old text", planetButtons(30), List.of(done(), resetButton()), SpendCall.RESET_);

        assertThat(shown).hasSizeLessThanOrEqualTo(MAX_BUTTONS);
        assertThat(ids(shown)).contains("deleteButtons_spitItOut", "resetSpend_inf", "spendPage_inf_page1");
        assertThat(ids(shown)).contains("exhaustPlanet_planet0").doesNotContain("exhaustPlanet_planet29");
    }

    @Test
    void fullResetAlsoPages() {
        List<Button> shown =
                pressOnMessage("resetSpend_Spend_inf", "old text", planetButtons(30), List.of(done()), SpendCall.RESET);

        assertThat(shown).hasSizeLessThanOrEqualTo(MAX_BUTTONS);
        assertThat(ids(shown)).contains("deleteButtons_spitItOut", "spendPage_inf_page1");
    }

    @Test
    void nextPageShowsRemainingPlanetsAndKeepsPinnedButtonsAndText() {
        List<Button> onMessage = List.of(Buttons.blue("spendPage_inf_page1", "Next Page (2/2)"), done(), resetButton());

        List<Button> shown =
                pressOnMessage("spendPage_inf_page1", "Spent so far", planetButtons(30), onMessage, SpendCall.PAGE);

        assertThat(shown).hasSizeLessThanOrEqualTo(MAX_BUTTONS);
        assertThat(ids(shown)).contains("exhaustPlanet_planet29", "deleteButtons_spitItOut", "resetSpend_inf");
        assertThat(ids(shown)).contains("spendPage_inf_page0").doesNotContain("spendPage_inf_page1");
        assertThat(ids(shown)).filteredOn("deleteButtons_spitItOut"::equals).hasSize(1);
        assertThat(lastText).isEqualTo("Spent so far");
    }

    private enum SpendCall {
        RESET_,
        RESET,
        PAGE
    }

    private String lastText;

    private List<Button> pressOnMessage(
            String buttonId, String messageText, List<Button> planets, List<Button> onMessage, SpendCall call) {
        List<Button> freshButtons = new ArrayList<>(planets);
        freshButtons.add(resetButton());

        Message message = mock(Message.class);
        when(message.getContentRaw()).thenReturn(messageText);
        when(message.getComponentTree()).thenReturn(MessageComponentTree.of(rowsOf(onMessage)));
        MessageEditAction editAction = mock(MessageEditAction.class, RETURNS_SELF);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        when(message.editMessage(text.capture())).thenReturn(editAction);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getMessage()).thenReturn(message);

        try (MockedStatic<ButtonHelper> buttonHelper = mockStatic(ButtonHelper.class, CALLS_REAL_METHODS);
                MockedStatic<Helper> helper = mockStatic(Helper.class, CALLS_REAL_METHODS)) {
            buttonHelper
                    .when(() -> ButtonHelper.getExhaustButtonsWithTG(any(), any(), anyString()))
                    .thenReturn(freshButtons);
            helper.when(() -> Helper.refreshPlanetsOnTheRespend(any(), any())).thenAnswer(inv -> null);
            helper.when(() -> Helper.refreshPlanetsOnTheRevote(any(), any())).thenAnswer(inv -> null);
            helper.when(() -> Helper.buildSpentThingsMessage(any(), any(), anyString()))
                    .thenReturn("Spent so far");

            switch (call) {
                case RESET_ -> SpendingButtonHandler.resetSpend_(event, player, buttonId, game);
                case RESET -> SpendingButtonHandler.resetSpend(event, player, buttonId, game);
                case PAGE -> SpendingButtonHandler.turnSpendPage(event, player, buttonId, game);
            }
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ActionRow>> rows = ArgumentCaptor.forClass(Collection.class);
        verify(editAction).setComponents(rows.capture());
        lastText = text.getValue();
        assertThat(rows.getValue()).hasSizeLessThanOrEqualTo(MAX_ROWS);
        return rows.getValue().stream()
                .flatMap(row -> row.getButtons().stream())
                .toList();
    }

    private static List<ActionRow> rowsOf(List<Button> buttons) {
        List<ActionRow> rows = new ArrayList<>();
        for (int start = 0; start < buttons.size(); start += 5) {
            rows.add(ActionRow.of(buttons.subList(start, Math.min(start + 5, buttons.size()))));
        }
        return rows;
    }

    private static List<Button> planetButtons(int count) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            buttons.add(Buttons.green("exhaustPlanet_planet" + i, "Planet " + i));
        }
        return buttons;
    }

    private static Button done() {
        return Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
    }

    private static Button resetButton() {
        return Buttons.gray("resetSpend_inf", "Reset Spent Planets and Trade Goods");
    }

    private static List<String> ids(List<Button> buttons) {
        return buttons.stream().map(Button::getCustomId).toList();
    }
}
