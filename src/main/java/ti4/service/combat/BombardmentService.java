package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.model.UnitModel;

@UtilityClass
public class BombardmentService {

    public static List<String> getBombardablePlanets(Player player, Game game, Tile tile) {
        List<String> planets = new ArrayList<>();
        for (Planet planetUH : tile.getPlanetUnitHolders()) {
            if (!player.getPlanetsAllianceMode().contains(planetUH.getName())
                    || FoWHelper.otherPlayersHaveUnitsOnPlanet(player, planetUH)) {
                if (!planetUH.getPlanetTypes().contains("cultural") || !ButtonHelper.isLawInPlay(game, "conventions")) {
                    planets.add(planetUH.getName());
                }
            }
        }

        return planets;
    }

    public static void autoAssignAllBombardmentToAPlanet(Player player, Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        game.removeStoredValue("assignedBombardment" + player.getFaction());
        if (tile == null) {
            return;
        }
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(tile, player, null);
        String planet = getBestBombardablePlanet(player, game, tile);
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            for (int x = 0; x < entry.getValue(); x++) {
                String name = entry.getKey().getAsyncId() + "_" + x;

                String assignedUnit = name + "_" + planet;
                game.setStoredValue(
                        "assignedBombardment" + player.getFaction(),
                        game.getStoredValue("assignedBombardment" + player.getFaction()) + assignedUnit + ";");
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    game.getStoredValue("assignedBombardment" + player.getFaction()) + "plasma_99_" + planet + ";");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    game.getStoredValue("assignedBombardment" + player.getFaction()) + "argentcommander_99_" + planet
                            + ";");
        }
    }

    private static String getBestBombardablePlanet(Player player, Game game, Tile tile) {
        String best = "";
        for (String planet : getBombardablePlanets(player, game, tile)) {
            best = planet;
            for (Player p2 : game.getRealPlayers()) {
                if (ButtonHelper.getNumberOfGroundForces(p2, game.getUnitHolderFromPlanet(planet)) > 0) {
                    return best;
                }
            }
        }
        return best;
    }
}
