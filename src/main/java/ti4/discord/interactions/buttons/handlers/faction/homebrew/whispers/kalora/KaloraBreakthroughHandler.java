package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class KaloraBreakthroughHandler {

    public static void bypassOperationsRetreat(
            Player player, Game game, Tile activeTile, GenericInteractionCreateEvent event) {
        if (!CommandCounterHelper.hasCC(player, activeTile)) return;
        RemoveCommandCounterService.fromTile(player.getColor(), activeTile, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji()
                        + " returned 1 command token from the active system to their reinforcements via **Bypass Operations**.");
    }

    public static void offerCommitInfantryButton(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String bombardPlanet) {
        if (bombardPlanet == null || bombardPlanet.isEmpty()) return;
        if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player.getColor()) <= 0) return;
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "kaloraCommitInfantry_" + bombardPlanet,
                "Commit 1 Infantry to " + Helper.getPlanetRepresentation(bombardPlanet, game)));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String msg = player.toString()
                + ", if you just destroyed the last of the controlling player's ground forces on "
                + Helper.getPlanetRepresentation(bombardPlanet, game)
                + ", you may commit 1 infantry from the space area onto that planet using **Bypass Operations**.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("kaloraCommitInfantry_")
    public static void commitInfantryFromSpace(
            Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String planet = buttonID.replace("kaloraCommitInfantry_", "");
        Tile tile = game.getTileContainingPlanet(planet);
        if (tile == null) return;
        MoveUnitService.moveUnits(event, tile, game, player.getColor(), "inf", tile, planet);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.toString() + " committed 1 infantry from the space area onto "
                        + Helper.getPlanetRepresentation(planet, game) + " using **Bypass Operations**.");
        ButtonHelper.deleteMessage(event);
    }
}
