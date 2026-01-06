package ti4.service.explore;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class AddFrontierTokensService {

    public static void addFrontierTokens(GenericInteractionCreateEvent event, Game game) {
        Collection<Tile> tileList = game.getTileMap().values();
        for (Tile tile : tileList) {
            if ("silver_flame".equalsIgnoreCase(tile.getTileID())) continue;
            if (tile.getPlanetUnitHolders().isEmpty()
                    && Mapper.getFrontierTileIds().contains(tile.getTileID())
                    && !game.isBaseGameMode()) {
                if (tile.getPlanetUnitHolders().isEmpty())
                    AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
            }
        }
    }

    @ButtonHandler("addFrontierTokens")
    public static void addFrontierTokens(Game game, ButtonInteractionEvent event) {
        addFrontierTokens(event, game);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), ExploreEmojis.Frontier + " Frontier tokens have been added to empty spaces.");
        ButtonHelper.deleteMessage(event);
    }
}
