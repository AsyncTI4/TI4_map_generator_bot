package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class ArmsDealAcd2ButtonHandler {

    @ButtonHandler("resolveArmsDeal")
    public static void resolveArmsDeal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers(true).contains(p2)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("armsDealStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "armsDealStep2_" + p2.getFaction(), p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which neighbor gets 1 cruiser and 1 destroyer.",
                buttons);
    }

    @ButtonHandler("armsDealStep2_")
    public static void resolveArmsDealStep2(Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(p2, game, "cruiser", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't Place"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", please choose where you wish to place the _Arms Deal_ cruiser.",
                buttons);
        buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(p2, game, "destroyer", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't Place"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", please choose where you wish to place the _Arms Deal_ destroyer.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
