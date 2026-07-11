package ti4.service.explore;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.commands.tokens.AddTokenCommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class AddFrontierTokensService {

    public static void addFrontierTokens(GenericInteractionCreateEvent event, Game game) {
        Collection<Tile> tileList = game.getTiles();
        for (Tile tile : tileList) {
            if ("silver_flame".equalsIgnoreCase(tile.getTileID())) continue;
            if (!tile.hasPlanets()
                    && Mapper.getFrontierTileIds().contains(tile.getTileID())
                    && !game.isBaseGameMode()) {
                if (!tile.hasPlanets()) AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
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
