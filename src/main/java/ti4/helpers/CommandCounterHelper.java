package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.Nullable;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.game.GameNameService;

public class CommandCounterHelper {

    public static void addCC(GenericInteractionCreateEvent event, String color, Tile tile) {
        addCC(event, color, tile, true);
    }

    public static void addCC(SlashCommandInteractionEvent event, String color, Tile tile) {
        addCC(event, color, tile, true);
    }

    public static void addCC(GenericInteractionCreateEvent event, String color, Tile tile, boolean ping) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        String gameName = GameNameService.getGameNameFromChannel(event);
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame.isFowMode() && ping) {
            String colorMention = Emojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(managedGame.getGame(), event, tile.getPosition(), colorMention + " has placed a token in the system");
        }
        tile.addCC(ccID);
    }

    public static void addCC(SlashCommandInteractionEvent event, String color, Tile tile, boolean ping, Game game) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        if (game.isFowMode() && ping) {
            String colorMention = Emojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " has placed a token in the system");
        }
        tile.addCC(ccID);
    }

    public static boolean hasCC(@Nullable GenericInteractionCreateEvent event, String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null && event != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        return tile.hasCC(ccID);
    }

    public static boolean hasCC(String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            return false;
        }
        return tile.hasCC(ccID);
    }

    public static boolean hasCC(Player player, Tile tile) {
        String color = player.getColor();
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            return false;
        }
        return tile.hasCC(ccID);
    }
}
