package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.image.TileGenerator;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class DisasterWatchHelper {

    public static void postTileInDisasterWatch(Game game, GenericInteractionCreateEvent event, Tile tile, Integer rings, String message) {
        if (!AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).isEmpty() && !game.isFowMode()) {
            TextChannel watchParty = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).getFirst();
            FileUpload systemWithContext = new TileGenerator(game, event, null, rings, tile.getPosition()).createFileUpload();
            MessageHelper.sendMessageWithFile(watchParty, systemWithContext, message, false);
        }
    }
}
