package ti4.discord.interactions.buttons.handlers.relics.theodisi;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class LostLegaciesRelicHandler {
    private static final String USE_EBOON = "useEconomicBoon";

    public static Button getEconomicBoonCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_EBOON, "Ready Planet with Economic Boon", CardEmojis.RelicCard);
    }

    @ButtonHandler(USE_EBOON)
    public static void resolveEconomicBoon(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasRelicReady("economicboon")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        player.addExhaustedRelic("economicboon");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the exhausted planet you wish to ready with _Economic Boon_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
