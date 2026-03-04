package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.fow.BlindSelectionService;

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
            for (Map.Entry<String, UnitHolder> unitHolderEntry :
                    tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet
                        && unitHolderEntry.getKey().equals(planetName)) {
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

    @ButtonHandler("selectPlayerToSleeper")
    public static void selectPlayerToSleeper(ButtonInteractionEvent event, Game game, Player player) {
        String msg =
                "Choose the player who owns the planet that you wish to put a Sleeper token on. You need their permission, but this is not a transaction.";
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            buttons.add(Buttons.gray("addSleeperViaBt_" + p2.getFaction(), p2.getFactionNameOrColor()));
        }
        buttons.add(Buttons.red("deleteButtons", "Don't Place Sleeper"));
        MessageHelper.sendMessageToChannel(event.getChannel(), msg, buttons);
    }

    @ButtonHandler("addSleeperViaBt_")
    public static void addSleeperViaBt(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String msg =
                "Choose the planet that you wish to put a sleeper on. Note that you need their permission, but this is not a transaction.";
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String planet : p2.getPlanets()) {
            if (game.getUnitHolderFromPlanet(planet) == null
                    || game.getUnitHolderFromPlanet(planet).isSpaceStation()) {
                continue;
            }
            buttons.add(Buttons.gray("putSleeperOnPlanet_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        BlindSelectionService.filterForBlindPlanetSelection(game, player, buttons, "putSleeperOnPlanet");
        buttons.add(Buttons.red("deleteButtons", "Don't Place Sleeper"));
        MessageHelper.sendMessageToChannel(event.getChannel(), msg, buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
