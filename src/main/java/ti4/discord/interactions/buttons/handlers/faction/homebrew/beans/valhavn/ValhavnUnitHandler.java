package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.valhavn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.tactical.LandingContext;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ValhavnUnitHandler {
    private static final String BILROST_COMMIT = "valhavnBilrostCommit_";

    public static Set<String> getSleipnirPdsCoverageSystems(Game game, Player player, Tile targetTile) {
        if (!ButtonHelper.doesPlayerHaveFSHere("valhavn_flagship", player, targetTile)) {
            return Set.of();
        }

        return game.getTileMap().values().stream()
                .filter(tile -> tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getUnitCount(UnitType.Pds, player) > 0))
                .map(Tile::getPosition)
                .collect(Collectors.toSet());
    }

    public static boolean canCommitThroughBilrost(LandingContext ctx) {
        return !getBilrostCommitButtons(ctx).isEmpty();
    }

    public static boolean hasBilrostPds(Player player) {
        return player.hasUnit("valhavn_pds") || player.hasUnit("valhavn_pds2");
    }

    public static List<Button> getBilrostCommitButtons(LandingContext ctx) {
        List<Button> buttons = new ArrayList<>();
        for (String sourcePlanet : getBilrostSourcePlanets(ctx)) {
            if (sourcePlanet.equals(ctx.planetName)) {
                continue;
            }
            Planet sourceHolder = ctx.game.getUnitHolderFromPlanet(sourcePlanet);
            if (sourceHolder == null) {
                continue;
            }
            for (UnitType unitType : List.of(UnitType.Infantry, UnitType.Mech)) {
                if (sourceHolder.getUnitCount(unitType, ctx.mainPlayer) < 1) {
                    continue;
                }
                buttons.add(Buttons.green(
                        ctx.mainPlayer.factionButtonChecker() + BILROST_COMMIT + ctx.planetName + "|" + sourcePlanet
                                + "|" + unitType.getValue(),
                        "Commit " + unitType.humanReadableName() + ": "
                                + Helper.getPlanetRepresentation(sourcePlanet, ctx.game) + " → " + ctx.planetRep));
            }
        }
        return buttons;
    }

    @ButtonHandler(BILROST_COMMIT)
    public static void commitThroughBilrost(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(BILROST_COMMIT, "").split("\\|", -1);
        if (payload.length != 3) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Tile destinationTile = game.getTileFromPlanet(payload[0]);
        Planet destinationPlanet = game.getUnitHolderFromPlanet(payload[0]);
        Tile sourceTile = game.getTileFromPlanet(payload[1]);
        Planet sourcePlanet = game.getUnitHolderFromPlanet(payload[1]);
        UnitType unitType = Units.findUnitType(payload[2]);
        if (destinationTile == null
                || destinationPlanet == null
                || sourceTile == null
                || sourcePlanet == null
                || unitType == null
                || sourcePlanet.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, sourceTile, game, player, sourcePlanet, unitType, 1);
        if (!removed.isEmpty()) {
            List<RemovedUnit> committed = removed.stream()
                    .map(unit -> unit.onUnitHolder(destinationPlanet))
                    .toList();
            AddUnitService.addUnits(event, destinationTile, game, player.getColor(), committed);
            if (unitType == UnitType.Infantry) {
                ValhavnPromissoryHandler.offerEinherjarCommit(
                        event,
                        game,
                        player,
                        destinationTile,
                        destinationPlanet.getName(),
                        committed.stream()
                                .mapToInt(RemovedUnit::getTotalRemoved)
                                .sum());
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " committed 1 " + unitType.humanReadableName() + " from "
                            + Helper.getPlanetRepresentation(sourcePlanet.getName(), game) + " to "
                            + Helper.getPlanetRepresentation(destinationPlanet.getName(), game)
                            + " using Bilröst.");
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    private static Set<String> getBilrostSourcePlanets(LandingContext ctx) {
        if (!hasBilrostPds(ctx.mainPlayer)) {
            return Set.of();
        }

        Set<String> sources = new LinkedHashSet<>();
        for (Planet pdsPlanet : getBilrostPlanets(ctx.game, ctx.mainPlayer)) {
            Tile pdsTile = ctx.game.getTileFromPlanet(pdsPlanet.getName());
            if (pdsTile == null || !canSupportInvasion(ctx, pdsTile)) {
                continue;
            }
            sources.add(pdsPlanet.getName());
        }
        return sources;
    }

    private static List<Planet> getBilrostPlanets(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> planet.getUnitCount(UnitType.Pds, player) > 0)
                .toList();
    }

    private static boolean canSupportInvasion(LandingContext ctx, Tile pdsTile) {
        return pdsTile == ctx.tile
                || FoWHelper.getAdjacentTiles(ctx.game, ctx.tile.getPosition(), ctx.mainPlayer, false)
                        .contains(pdsTile.getPosition());
    }
}
