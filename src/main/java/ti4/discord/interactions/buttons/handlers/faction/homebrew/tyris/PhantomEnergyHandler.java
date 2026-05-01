package ti4.discord.interactions.buttons.handlers.faction.homebrew.tyris;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class PhantomEnergyHandler {

    public static void postInitialButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (String asyncID : tile.getSpaceUnitHolder()
                .getUnitAsyncIdsOnHolder(player.getColorID())
                .keySet()) {
            buttons.add(Buttons.green(
                    "resolvePhantomEnergy_" + asyncID,
                    StringUtils.capitalize(Mapper.getUnitBaseTypeFromAsyncID(asyncID))));
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose the ship type to use **Phantom Energy** on.",
                buttons);
    }

    @ButtonHandler("resolvePhantomEnergy_")
    public static void resolvePhantomEnergy(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String asyncID = buttonID.split("_")[1];
        game.setStoredValue("phantomEnergy", game.getStoredValue("phantomEnergy") + asyncID);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " has used **Phantom Energy** on the "
                        + Mapper.getUnitBaseTypeFromAsyncID(asyncID) + " ship type.");
        ButtonHelper.deleteMessage(event);
    }

    public static void cleanupEndOfTurn(Game game, Player player) {
        if (game.getStoredValue("phantomEnergy").isEmpty()) {
            return;
        }
        for (Tile tile : game.getTileMap().values()) {
            if (tile.hasPlayerCC(player)) {
                for (String asyncID : tile.getSpaceUnitHolder()
                        .getUnitAsyncIdsOnHolder(player.getColorID())
                        .keySet()) {
                    game.setStoredValue(
                            "phantomEnergy",
                            game.getStoredValue("phantomEnergy").replace(asyncID, ""));
                }
            }
        }
    }

    public static void checkFlagshipPhantomEnergy(Game game, Player player) {
        if (player.hasUnit("tyris_flagship")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship", false) > 0) {
            game.setStoredValue("phantomEnergy", game.getStoredValue("phantomEnergy") + "fs");
        }
    }
}
