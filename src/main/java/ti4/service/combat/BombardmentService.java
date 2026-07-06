package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.model.UnitModel;
import tools.jackson.databind.ObjectMapper;

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

    public static void autoAssignAllBombardmentToAPlanet(Player player, Game game, Tile tile) {
        game.removeStoredValue("assignedBombardment" + player.getFaction());
        if (tile == null) {
            return;
        }
        Map<Pair<UnitModel, UnitHolder>, Integer> bombardUnits =
                CombatRollService.getUnitsInBombardment(tile, player, null);
        String planet = getBestBombardablePlanet(player, game, tile);
        List<BombardmentAssignment> assignments = new ArrayList<>();
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : bombardUnits.entrySet()) {
            int galvanizedCount = entry.getKey()
                    .getValue()
                    .getGalvanizedUnitCount(entry.getKey().getKey().getUnitType(), player.getColorID());
            for (int x = 0; x < entry.getValue(); x++) {
                String asyncId = entry.getKey().getKey().getAsyncId();
                assignments.add(new BombardmentAssignment(
                        asyncId, planet, galvanizedCount > 0, BombardmentAssignmentType.UNIT));
                galvanizedCount--;
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            assignments.add(new BombardmentAssignment("plasmascoring", planet, false, BombardmentAssignmentType.TECH));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            assignments.add(
                    new BombardmentAssignment("argentcommander", planet, false, BombardmentAssignmentType.LEADER));
        }
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(), new ObjectMapper().writeValueAsString(assignments));
    }

    private static String getBestBombardablePlanet(Player player, Game game, Tile tile) {
        String best = "";
        for (String planet : getBombardablePlanets(player, game, tile)) {
            best = planet;
            for (Player p2 : game.getRealPlayersExcludingThis(player)) {
                if (ButtonHelper.getNumberOfGroundForces(p2, game.getUnitHolderFromPlanet(planet)) > 0) {
                    return best;
                }
            }
        }
        return best;
    }
}
