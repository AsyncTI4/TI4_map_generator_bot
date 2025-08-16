package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.EventModel;
import ti4.service.emoji.TileEmojis;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class EventHelper {

    public static void revealEvent(GenericInteractionCreateEvent event, Game game, MessageChannel channel) {
        revealEvent(event, channel, game.revealEvent(false));
    }

    public static void revealEvent(GenericInteractionCreateEvent event, MessageChannel channel, String eventID) {
        EventModel eventModel = Mapper.getEvent(eventID);
        if (eventModel != null) {
            channel.sendMessageEmbeds(eventModel.getRepresentationEmbed()).queue();
        } else {
            MessageHelper.sendMessageToEventChannel(
                    event, "Something went wrong revealing an event; eventID: " + eventID);
        }
    }

    public static void showRemainingTiles(Game game, ButtonInteractionEvent event) {
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This command is disabled for fog mode.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("__Game: ").append(game.getName()).append("__\n");
        sb.append("__Tiles Not Constituting the Map__:");

        List<Tile> unusedBlueTiles = new ArrayList<>(Helper.getUnusedTiles(game).stream()
                .filter(tile -> tile.getTierList().isBlue())
                .map(MiltyDraftTile::getTile)
                .toList());

        List<Tile> unusedRedTiles = new ArrayList<>(Helper.getUnusedTiles(game).stream()
                .filter(tile -> !tile.getTierList().isBlue())
                .map(MiltyDraftTile::getTile)
                .toList());

        for (Tile tile : unusedBlueTiles) {
            sb.append("\n")
                    .append(TileEmojis.TileBlueBack)
                    .append(" ")
                    .append(tile.getRepresentation())
                    .append(" (`")
                    .append(tile.getTileID())
                    .append("`)");
        }

        for (Tile tile : unusedRedTiles) {
            sb.append("\n")
                    .append(TileEmojis.TileRedBack)
                    .append(" ")
                    .append(tile.getRepresentation())
                    .append(" (`")
                    .append(tile.getTileID())
                    .append("`)");
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
