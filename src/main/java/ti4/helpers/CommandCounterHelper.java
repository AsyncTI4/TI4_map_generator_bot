package ti4.helpers;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.thundersedge.TeHelperAgents;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

public class CommandCounterHelper {

    public static void addCC(GenericInteractionCreateEvent event, Game game, String color, Tile tile) {
        addCC(event, game.getPlayerFromColorOrFaction(color), tile);
    }

    public static void addCC(GenericInteractionCreateEvent event, Player player, Tile tile) {
        addCC(event, player, tile, true);
    }

    public static void addCC(GenericInteractionCreateEvent event, Player player, Tile tile, boolean ping) {
        if (player == null || !Mapper.isValidColor(player.getColor())) {
            if (event != null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Cannot find player for whom to place a command token.");
            } else if (player != null) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "Cannot find player for whom to place a command token.");
            }
            return;
        }
        String ccID = Mapper.getCCID(player.getColor());
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            if (event != null) {
                MessageHelper.sendMessageToChannel(
                        (MessageChannel) event.getChannel(),
                        "Command Counter: " + player.getColor() + " is not valid and not supported.");
            }
            return;
        }
        if (player.getGame().isFowMode() && ping) {
            String colorMention = ColorEmojis.getColorEmojiWithName(player.getColor());
            FoWHelper.pingSystem(
                    player.getGame(), tile.getPosition(), colorMention + " has placed a command token in the system.");
        }
        tile.addCC(ccID);
        for (Player p : player.getGame().getRealPlayers()) {
            if (p.hasUnexhaustedLeader("naaluagent-te")) {
                TeHelperAgents.serveNaaluAgentButtons(player.getGame(), p, tile, player);
            }
        }
    }

    public static boolean hasCC(@Nullable GenericInteractionCreateEvent event, String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null && event != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        return tile.hasCC(ccID);
    }

    private static boolean hasCC(String color, Tile tile) {
        return hasCC(null, color, tile);
    }

    public static boolean hasCC(Player player, Tile tile) {
        return hasCC(player.getColor(), tile);
    }
}
