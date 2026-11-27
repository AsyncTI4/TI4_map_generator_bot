package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.xan;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class XanLeaderHandler {

    public static void offerStructureMoveButton(Game game, Player player, String gainedPlanet) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock, UnitType.Pds)) {
            if (CommandCounterHelper.hasCC(player, tile)) {
                continue;
            }
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getName().equals(gainedPlanet)) {
                    continue;
                }
                if (planet.getUnitCount(UnitType.Spacedock, player) > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "xanCommanderMove_sd_" + planet.getName() + "_"
                                    + gainedPlanet,
                            "Move Space Dock from " + Helper.getPlanetRepresentation(planet.getName(), game)));
                }
                if (planet.getUnitCount(UnitType.Pds, player) > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "xanCommanderMove_pds_" + planet.getName() + "_"
                                    + gainedPlanet,
                            "Move PDS from " + Helper.getPlanetRepresentation(planet.getName(), game)));
                }
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", due to Pospician Meraldo, the Xan commander, you may move 1 structure from a system"
                        + " that doesn't contain 1 of your command tokens onto "
                        + Helper.getPlanetRepresentation(gainedPlanet, game) + ".",
                buttons);
    }

    @ButtonHandler("xanCommanderMove_")
    public static void resolveStructureMove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("xanCommanderMove_", "").split("_");
        String unitAlias = parts[0];
        String fromPlanet = parts[1];
        String toPlanet = parts[2];
        Tile fromTile = game.getTileFromPlanet(fromPlanet);
        Tile toTile = game.getTileFromPlanet(toPlanet);
        UnitHolder fromHolder = fromTile == null ? null : fromTile.getUnitHolderFromPlanet(fromPlanet);
        UnitHolder toHolder = toTile == null ? null : toTile.getUnitHolderFromPlanet(toPlanet);
        UnitType structure = "sd".equals(unitAlias) ? UnitType.Spacedock : UnitType.Pds;
        if (fromHolder == null || toHolder == null || fromHolder.getUnitCount(structure, player) <= 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, fromTile, game, player, fromHolder, structure, 1, false);
        AddUnitService.addUnits(
                event,
                game,
                removed.stream().map(unit -> unit.onUnitHolder(toHolder)).toList());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " moved a structure from "
                        + Helper.getPlanetRepresentation(fromPlanet, game) + " to "
                        + Helper.getPlanetRepresentation(toPlanet, game) + ".");
        ButtonHelper.deleteMessage(event);
    }
}
