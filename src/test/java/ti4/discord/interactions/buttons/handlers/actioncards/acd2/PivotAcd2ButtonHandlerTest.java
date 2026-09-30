package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.message.MessageHelper;

class PivotAcd2ButtonHandlerTest {

    @Test
    void drawsAllPivotReplacementsTogetherAfterFinalDiscard() {
        Game game = new Game();
        Player player = new Player("user", "name", game);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);

        try (MockedStatic<ActionCardHelper> actionCards = mockStatic(ActionCardHelper.class);
                MockedStatic<ButtonHelper> buttons = mockStatic(ButtonHelper.class);
                MockedStatic<SecretObjectiveHelper> secretObjectives = mockStatic(SecretObjectiveHelper.class);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class)) {
            secretObjectives
                    .when(() -> SecretObjectiveHelper.getSODiscardButtonsWithSuffix(player, "redraw"))
                    .thenReturn(List.of());

            PivotAcd2ButtonHandler.resolvePivotDiscardAc(player, game, event, "pivotDiscardAc_1_42_2");

            actionCards.verify(() -> ActionCardHelper.discardAC(event, game, player, 42));
            actionCards.verify(() -> ActionCardHelper.drawActionCards(player, 3));
        }
    }
}
