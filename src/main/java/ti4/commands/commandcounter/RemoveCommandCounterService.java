package ti4.commands.commandcounter;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

@UtilityClass
public class RemoveCommandCounterService {

    public static void fromTile(GenericInteractionCreateEvent event, Player player, Tile tile) {
        if (player == null) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(event), "Player cannot be found for removing command counter");
            return;
        }
        fromTile(event, player.getColor(), tile, player.getGame());
    }

    public static void fromTile(GenericInteractionCreateEvent event, String color, Tile tile, Game game) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        if (game.isFowMode()) {
            String colorMention = ColorEmojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " command token has been removed from the system.");
        }
        tile.removeCC(ccID);
    }

    public static void fromTacticsPool(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        for (Player player : game.getPlayers().values()) {
            if (color.equals(player.getColor())) {
                int cc = player.getTacticalCC();
                if (cc == 0) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "You don't have a command token in your tactic pool.");
                    break;
                } else if (!CommandCounterHelper.hasCC(event, color, tile)) {
                    cc -= 1;
                    player.setTacticalCC(cc);
                    break;
                }
            }
        }
    }
}
