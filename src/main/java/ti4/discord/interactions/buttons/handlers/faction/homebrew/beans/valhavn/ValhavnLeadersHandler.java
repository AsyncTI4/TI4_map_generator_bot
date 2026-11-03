package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.valhavn;

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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.PurgeHeroService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ValhavnLeadersHandler {
    private static final String HERO = "valhavnhero";
    private static final String HEARTH_CALL = "valhavnHearthCall_";
    private static final String HEARTH_CALL_SOURCE = "valhavnHearthCallSource_";
    private static final String HEARTH_CALL_MOVE = "valhavnHearthCallMove_";
    private static final String HEARTH_CALL_DONE = "valhavnHearthCallDone_";

    public static void addHearthCallButton(List<Button> buttons, Player player, Tile tile, UnitHolder combatHolder) {
        if (!player.hasLeaderUnlocked(HERO) || !(combatHolder instanceof Planet planet)) {
            return;
        }
        buttons.add(
                0,
                Buttons.gray(
                        player.factionButtonChecker() + HEARTH_CALL + tile.getPosition() + "_" + planet.getName(),
                        "Purge Valhavn Hero",
                        FactionEmojis.valhavn));
    }

    @ButtonHandler(HEARTH_CALL)
    public static void purgeHearthCall(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(HEARTH_CALL.length()).split("_", 2);
        if (parts.length != 2 || !player.hasLeaderUnlocked(HERO)) {
            return;
        }
        String destinationPlanet = parts[1];
        Tile destinationTile = game.getTileByPosition(parts[0]);
        if (destinationTile == null || destinationTile.getUnitHolderFromPlanet(destinationPlanet) == null) {
            return;
        }
        PurgeHeroService.purgeHeroPreamble(event, player, game, HERO, "Brünnhilde Buthladóttir, the Valhavn hero");
        offerHearthCallSources(event.getMessageChannel(), game, player, destinationPlanet);
    }

    @ButtonHandler(HEARTH_CALL_SOURCE)
    public static void selectHearthCallSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(HEARTH_CALL_SOURCE.length()).split("_", 2);
        if (parts.length != 2) {
            return;
        }
        String destinationPlanet = parts[0];
        String sourcePosition = parts[1];
        String sourceMessage = hearthCallSourceMessage(player);
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                sourceMessage,
                () -> hearthCallSourceButtons(game, player, destinationPlanet),
                List.of(Buttons.DONE_DELETE_BUTTONS),
                player.factionButtonChecker() + HEARTH_CALL_SOURCE + destinationPlanet + "_")) {
            return;
        }
        List<Button> buttons = sourceGroundForceButtons(game, player, destinationPlanet, sourcePosition);
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + HEARTH_CALL_DONE + destinationPlanet, "Done With This System"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", move up to 3 ground forces from "
                        + sourceTileRepresentation(game, player, sourcePosition) + " to "
                        + Helper.getPlanetRepresentation(destinationPlanet, game) + " with _Hearth-Call_:",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HEARTH_CALL_MOVE)
    public static void moveHearthCallGroundForce(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(HEARTH_CALL_MOVE.length()).split("_", 4);
        if (parts.length != 4) {
            return;
        }
        String destinationPlanet = parts[0];
        Tile destinationTile = game.getTileFromPlanet(destinationPlanet);
        Tile sourceTile = game.getTileByPosition(parts[1]);
        UnitHolder sourceHolder =
                sourceTile == null ? null : sourceTile.getUnitHolders().get(parts[2]);
        UnitType unitType = List.of(UnitType.Infantry, UnitType.Mech).stream()
                .filter(type -> type.value.equals(parts[3]))
                .findFirst()
                .orElse(null);
        if (destinationTile == null
                || sourceHolder == null
                || unitType == null
                || !isGroundForce(unitType)
                || sourceHolder.getUnitCount(unitType, player) < 1) {
            return;
        }
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, sourceTile, game, player, sourceHolder, unitType, 1);
        if (removed.isEmpty()) {
            return;
        }
        Planet destination = destinationTile.getUnitHolderFromPlanet(destinationPlanet);
        AddUnitService.addUnits(
                event,
                destinationTile,
                game,
                player.getColor(),
                removed.stream().map(unit -> unit.onUnitHolder(destination)).toList());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 " + unitType.humanReadableName() + " from "
                        + holderRepresentation(game, player, sourceTile, sourceHolder) + " to "
                        + Helper.getPlanetRepresentation(destinationPlanet, game) + " with _Hearth-Call_.");
        if (sourceHolder.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    @ButtonHandler(HEARTH_CALL_DONE)
    public static void finishHearthCallSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        offerHearthCallSources(event.getMessageChannel(), game, player, buttonID.substring(HEARTH_CALL_DONE.length()));
        ButtonHelper.deleteMessage(event);
    }

    private static void offerHearthCallSources(
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel,
            Game game,
            Player player,
            String destinationPlanet) {
        List<Button> buttons = hearthCallSourceButtons(game, player, destinationPlanet);
        String message = hearthCallSourceMessage(player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentationNoPing() + " has no remaining ground forces to move with _Hearth-Call_.");
            return;
        }
        NewStuffHelper.sendPaginated(
                channel,
                message,
                buttons,
                List.of(Buttons.DONE_DELETE_BUTTONS),
                player.factionButtonChecker() + HEARTH_CALL_SOURCE + destinationPlanet + "_");
    }

    private static List<Button> hearthCallSourceButtons(Game game, Player player, String destinationPlanet) {
        return sourceTiles(game, player, destinationPlanet).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + HEARTH_CALL_SOURCE + destinationPlanet + "_"
                                + tile.getPosition(),
                        "Move Ground Forces From " + tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static String hearthCallSourceMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", choose a system containing your PDS units to move ground forces from with _Hearth-Call_.";
    }

    private static List<Tile> sourceTiles(Game game, Player player, String destinationPlanet) {
        return game.getTileMap().values().stream()
                .filter(tile -> containsPds(tile, player))
                .filter(tile -> !sourceGroundForceButtons(game, player, destinationPlanet, tile.getPosition())
                        .isEmpty())
                .toList();
    }

    private static List<Button> sourceGroundForceButtons(
            Game game, Player player, String destinationPlanet, String sourcePosition) {
        Tile sourceTile = game.getTileByPosition(sourcePosition);
        if (sourceTile == null || !containsPds(sourceTile, player)) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : sourceTile.getUnitHolders().values()) {
            if (destinationPlanet.equals(holder.getName())) {
                continue;
            }
            for (UnitType unitType : List.of(UnitType.Infantry, UnitType.Mech)) {
                if (holder.getUnitCount(unitType, player) < 1) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + HEARTH_CALL_MOVE + destinationPlanet + "_" + sourcePosition
                                + "_" + holder.getName() + "_" + unitType.value,
                        "Move 1 " + unitType.humanReadableName() + " From "
                                + holderRepresentation(game, player, sourceTile, holder)));
            }
        }
        return buttons;
    }

    private static boolean containsPds(Tile tile, Player player) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> holder.getUnitCount(UnitType.Pds, player) > 0);
    }

    private static boolean isGroundForce(UnitType unitType) {
        return unitType == UnitType.Infantry || unitType == UnitType.Mech;
    }

    private static String sourceTileRepresentation(Game game, Player player, String sourcePosition) {
        Tile sourceTile = game.getTileByPosition(sourcePosition);
        return sourceTile == null ? sourcePosition : sourceTile.getRepresentationForButtons(game, player);
    }

    private static String holderRepresentation(Game game, Player player, Tile tile, UnitHolder holder) {
        return holder instanceof Planet
                ? Helper.getPlanetRepresentation(holder.getName(), game)
                : "the space area of " + tile.getRepresentationForButtons(game, player);
    }
}
