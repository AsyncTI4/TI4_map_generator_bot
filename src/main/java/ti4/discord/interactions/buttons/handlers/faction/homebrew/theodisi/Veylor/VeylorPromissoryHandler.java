package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class VeylorPromissoryHandler {
    private static final String DRAW_1AC = "drawNoneAcPlusOneMore";
    private static final String DRAW_2AC = "draw1AcPlusOneMore";
    
    public static void sendDiscardButtonsForPn(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        ActionCardHelper.getDiscardActionCardButtons(player, false);

        List<Button> buttons = List.of(Buttons.green(player.factionButtonChecker() + DRAW_1AC, "Draw 1 AC", CardEmojis.ActionCard), Buttons.green(player.factionButtonChecker() + DRAW_2AC, "Draw 2 AC", CardEmojis.ActionCard));

        MessageHelper.sendMessageToChannelWithButtons(
            player.getCorrectChannel(),
            player.getRepresentation()
                + ", please tell the bot how many action cards to draw (number discarded + 1, and don't fib it, the bot is watching you 👀.",
            buttons);
    }

    @ButtonHandler(DRAW_1AC)
    @ButtonHandler(DRAW_2AC)
    public static void drawAcForPn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String acToDraw = buttonID.replace("draw", "");
        if (acToDraw.contains("NoneAcPlusOneMore")) {
            ActionCardHelper.drawActionCards(player, 1);
        } else if (acToDraw.contains("1AcPlusOneMore")) {
            ActionCardHelper.drawActionCards(player, 2);
        }

        ButtonHelper.deleteMessage(event);
    }
}
