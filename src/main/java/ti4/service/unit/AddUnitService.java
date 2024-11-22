package ti4.service.unit;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.service.combat.StartCombatService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.planet.FlipTileService;

@UtilityClass
public class AddUnitService {

    public static void addUnits(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            addUnit(event, tile, game, parsedUnit);
            tile = FlipTileService.flipTileIfNeeded(tile, game);
        }

        handleFogOfWar(event, tile, color, game, unitList);
        checkFleetCapacity(event, tile, color, game);
    }

    public static void addUnit(GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit) {
        int originalNumberOfPlayersInLocation = getNumberOfPlayersInTile(tile, game, parsedUnit);

        tile.addUnit(parsedUnit.getLocation(), parsedUnit.getUnitKey(), parsedUnit.getCount());
        AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.getLocation(), game);

        if (originalNumberOfPlayersInLocation != 0) {
            int newNumberOfPlayersInLocation = getNumberOfPlayersInTile(tile, game, parsedUnit);
            if (newNumberOfPlayersInLocation <= originalNumberOfPlayersInLocation) return;
        }

        startCombat(event, tile, game, parsedUnit);
    }

    private static void handleFogOfWar(GenericInteractionCreateEvent event, Tile tile, String color, Game game, String unitList) {
        if (!game.isFowMode()) return;

        if (isTileAlreadyPinged(game, tile)) return;

        String message = Emojis.getColorEmojiWithName(color) + " has modified units in the system.";
        message += " Specific units modified: " + unitList;
        message += " Refresh map to see changes.";
        FoWHelper.pingSystem(game, event, tile.getPosition(), message);

        markTileAsPinged(game, tile);
    }

    private static boolean isTileAlreadyPinged(Game game, Tile tile) {
        for (int i = 0; i < 10; i++) {
            String tilePinged = game.getListOfTilesPinged()[i];
            if (tilePinged != null && tilePinged.equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    private static void markTileAsPinged(Game game, Tile tile) {
        for (int i = 0; i < 10; i++) {
            if (game.getListOfTilesPinged()[i] == null) {
                game.setTileAsPinged(i, tile.getPosition());
                break;
            }
        }
    }

    private static void checkFleetCapacity(GenericInteractionCreateEvent event, Tile tile, String color, Game game) {
        Player player = game.getPlayerFromColorOrFaction(color);
        if (player != null) {
            ButtonHelper.checkFleetAndCapacity(player, game, tile, event);
            CommanderUnlockCheckService.checkPlayer(player, "naalu", "cabal");
        }
    }

    private static int getNumberOfPlayersInTile(Tile tile, Game game, ParsedUnit parsedUnit) {
        List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        if (!parsedUnit.getLocation().equalsIgnoreCase("space") && !game.isFowMode()) {
            playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, parsedUnit.getLocation());
        }
        return playersForCombat.size();
    }

    private static void startCombat(GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit) {
        List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        String combatType = "space";
        if (!parsedUnit.getLocation().equalsIgnoreCase("space")) {
            combatType = "ground";
            playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, parsedUnit.getLocation());
        }

        // Try to get players in order of [activePlayer, otherPlayer, ... (discarded players)]
        Player player1 = game.getActivePlayer();
        if (player1 == null) {
            player1 = playersForCombat.getFirst();
        }
        playersForCombat.remove(player1);
        Player player2 = player1;
        for (Player p2 : playersForCombat) {
            if (p2 != player1 && !player1.getAllianceMembers().contains(p2.getFaction())) {
                player2 = p2;
                break;
            }
        }
        if (player1 != player2 && !tile.getPosition().equalsIgnoreCase("nombox") && !player1.getAllianceMembers().contains(player2.getFaction())) {
            if ("ground".equals(combatType)) {
                StartCombatService.startGroundCombat(player1, player2, game, event, tile.getUnitHolderFromPlanet(parsedUnit.getLocation()), tile);
            } else {
                StartCombatService.startSpaceCombat(game, player1, player2, tile, event);
            }
        }
    }
}
