package ti4.helpers;

import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

@UtilityClass
public class SleeperTokenHelper {

    public void addOrRemoveSleeper(GenericInteractionCreateEvent event, Game game, String planetName, Player player) {
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }
        Tile tile = null;
        UnitHolder unitHolder = null;
        for (Tile tile_ : game.getTileMap().values()) {
            if (tile != null) {
                break;
            }
            for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    tile = tile_;
                    unitHolder = unitHolderEntry.getValue();
                    break;
                }
            }
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet");
            return;
        }

        if (unitHolder.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)) {
            tile.removeToken(Constants.TOKEN_SLEEPER_PNG, unitHolder.getName());
        } else {
            tile.addToken(Constants.TOKEN_SLEEPER_PNG, unitHolder.getName());
            String ident = player.getFactionEmoji();
            if (game.getSleeperTokensPlacedCount() > 5) {
                String message2 = ident + " has more than 5 Sleeper tokens out. Use buttons to remove a Sleeper token.";
                List<Button> buttons = ButtonHelper.getButtonsForRemovingASleeper(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
            }
        }
    }
}
