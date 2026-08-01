package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
public class OblivionUnitHandler {

    public static void doOblivionMechCheck(Game game, Player player) {
        OblivionLeadersHandler.offerCommanderProduction(game, player);
        if (!player.hasUnit("oblivion_mech")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isSpaceStation(game)
                    || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "placeOneNDone_skipbuild_mech_" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may place 1 mech from your reinforcements on a planet you control.",
                buttons);
    }

    public static void addObsidianMirrorAdjacencies(
            Game game, Player player, String position, Set<String> adjacentPositions) {
        if (game == null || player == null || position == null || !player.hasUnit("oblivion_flagship")) {
            return;
        }

        Tile queriedTile = game.getTileByPosition(position);
        if (queriedTile == null || !queriedTile.getPlanetUnitHolders().isEmpty()) {
            return;
        }

        Set<String> flagshipPositions = game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0)
                .map(Tile::getPosition)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        boolean flagshipIsMoving = false;
        for (var entry : game.getTacticalActionDisplacement().entrySet()) {
            boolean containsFlagship = entry.getValue().entrySet().stream()
                    .filter(unit -> player.unitBelongsToPlayer(unit.getKey()))
                    .filter(unit -> unit.getKey().unitType() == UnitType.Flagship)
                    .anyMatch(unit ->
                            unit.getValue().stream().mapToInt(Integer::intValue).sum() > 0);
            if (!containsFlagship) {
                continue;
            }

            flagshipIsMoving = true;
            int holderSeparator = entry.getKey().indexOf('-');
            String origin =
                    holderSeparator < 0 ? entry.getKey() : entry.getKey().substring(0, holderSeparator);
            Tile originTile = game.getTileByPosition(origin);
            if (originTile != null && originTile.getPlanetUnitHolders().isEmpty()) {
                flagshipPositions.add(origin);
            }
        }
        if (flagshipIsMoving) {
            Tile activeTile = game.getTileByPosition(game.getActiveSystem());
            if (activeTile != null && activeTile.getPlanetUnitHolders().isEmpty()) {
                flagshipPositions.add(activeTile.getPosition());
            }
        }
        if (flagshipPositions.isEmpty()) {
            return;
        }

        if (flagshipPositions.contains(position)) {
            game.getTileMap().values().stream()
                    .filter(tile -> !tile.getPosition().startsWith("frac"))
                    .filter(tile -> tile.getPlanetUnitHolders().isEmpty())
                    .map(Tile::getPosition)
                    .forEach(adjacentPositions::add);
        } else if (!position.startsWith("frac")) {
            adjacentPositions.addAll(flagshipPositions);
        }
    }
}
