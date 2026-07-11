package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class StrandedShipAcd2ButtonHandler {

    @ButtonHandler("strandedShipStep1")
    public static void resolveStrandedShipStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getStrandedShipButtons(game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the system you wish to place the _Ghost Ship_ in.",
                buttons);
    }

    @ButtonHandler("strandedShipStep2_")
    public static void resolveStrandedShipStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = FlipTileService.flipTileIfNeeded(event, tile, game);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " put 1 cruiser in " + tile.toString() + ".");

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        CommanderUnlockCheckService.checkPlayer(p2, "empyrean");
    }

    private static List<Button> getStrandedShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTiles()) {
            if (!tile.hasPlanets() && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.green(
                        "strandedShipStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }
}
