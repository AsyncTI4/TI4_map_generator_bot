package ti4.service.info;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;
import ti4.model.Source;
import ti4.model.TechnologyModel;

@UtilityClass
public class ListPlayerInfoService {

    public static int getObjectiveThreshold(String objID, Game game) {
        return switch (objID) {
            // stage 1's
            case "push_boundaries" -> 2;
            case "outer_rim" -> 3;
            case "make_history" -> 2;
            case "infrastructure" -> 3;
            case "corner" -> 4;
            case "develop" -> 2;
            case "diversify" -> 2;
            case "monument" -> 8;
            case "expand_borders" -> 6;
            case "research_outposts" -> 3;
            case "intimidate" -> 2;
            case "lead" -> 3;
            case "trade_routes" -> 5;
            case "amass_wealth" -> 9;
            case "build_defenses" -> 4;
            case "lost_outposts" -> 2;
            case "engineer_marvel" -> 1;
            case "deep_space" -> 3;
            case "raise_fleet" -> 5;
            case "sway_council" -> 8;
            // stage 2's
            case "centralize_trade" -> 10;
            case "conquer" -> 1;
            case "brain_trust" -> 5;
            case "golden_age" -> 16;
            case "galvanize" -> 6;
            case "manipulate_law" -> 16;
            case "master_science" -> 4;
            case "revolutionize" -> 3;
            case "subdue" -> 11;
            case "unify_colonies" -> 6;
            case "supremacy" -> 1;
            case "become_legend" -> 4;
            case "command_armada" -> 8;
            case "massive_cities" -> 7;
            case "control_borderlands" -> 5;
            case "vast_reserves" -> 18;
            case "vast_territories" -> 5;
            case "protect_border" -> 5;
            case "ancient_monuments" -> 3;
            case "distant_lands" -> 2;

            //status phase secrets
            case "pem" -> 8; // 8 production
            case "sai" -> 1; //legendary
            case "syc" -> 1; // control a planet in same system someone else does
            case "sb" -> 1; // have a PN in your play area
            case "otf" -> 9; // 9 gf on a planet without a dock
            case "mtm" -> 4; // 4 mechs on 4 planets
            case "hrm" -> 12; // 12 resources
            case "eh" -> 12; // 12 influence
            case "dp" -> 3; // 3 laws in play
            case "dhw" -> 2; // 2 frags
            case "dfat" -> 1; //nexus
            case "te" -> 1; // be next to HS
            case "ose" -> 3; // control rex and have 3 ships
            case "mrm" -> 4; //4 hazardous
            case "mlp" -> 4; //4 techs of a color
            case "mp" -> 4; // 4 industrial
            case "lsc" -> 3; // 3 anomalies
            case "fwm" -> 3; // 3 SD
            case "fsn" -> 5; // 5 AC
            case "gamf" -> 5; // 5 dreads
            case "ans" -> 2; // 2 faction tech
            case "btgk" -> 2; // in systems with alphas and betas
            case "ctr" -> 6; // ships in 6 systems
            case "csl" -> 1; // blockade space dock
            case "eap" -> 4; // 4 PDS
            case "faa" -> 4; // 4 cultural
            case "fc" -> (game.getRealPlayers().size() - 1); // neighbors

            default -> 0;
        };
    }

    public static void displayerScoringProgression(Game game, boolean onlyThisGameObj,
        MessageChannel channel, String stage1sOrTwos) {
        StringBuilder msg = new StringBuilder();
        int x = 1;
        if (onlyThisGameObj) {
            for (String id : game.getRevealedPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id) != null) {
                    msg.append(representScoring(game, id, x)).append("\n");
                    x++;
                }
            }
            for (String id : game.getSoToPoList()) {
                msg.append(representScoring(game, id, x, true)).append("\n");
                x++;
            }
            msg.append(representSecrets(game)).append("\n");
            msg.append(representSupports(game)).append("\n");
            msg.append(representTotalVPs(game)).append("\n");
        } else {
            for (String id : Mapper.getPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id).getSource() == Source.ComponentSource.pok
                    || Mapper.getPublicObjective(id).getSource() == Source.ComponentSource.base) {
                    if (stage1sOrTwos.equalsIgnoreCase("" + Mapper.getPublicObjective(id).getPoints())
                        || stage1sOrTwos.equalsIgnoreCase("both")) {
                        msg.append(representScoring(game, id, x)).append("\n");
                        x++;
                    }

                }
            }
        }
        MessageHelper.sendMessageToChannel(channel, msg.toString());
    }

    public static String representScoring(Game game, String objID, int x) {
        return representScoring(game, objID, x, false);
    }

    public static String representScoring(Game game, String objID, int x, boolean secret) {
        StringBuilder representation;
        if (secret) {
            representation = new StringBuilder(x + ". " + SecretObjectiveInfoService.getSecretObjectiveRepresentation(objID) + "> ");
        } else {
            PublicObjectiveModel model = Mapper.getPublicObjective(objID);
            if (x > 0) {
                representation = new StringBuilder(x + ". " + model.getRepresentation() + "\n> ");
            } else {
                representation = new StringBuilder(model.getRepresentation() + "\n> ");
            }
        }
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation.append(player.getFactionEmoji()).append(": ");
                if (secret) {
                    if (game.didPlayerScoreThisAlready(player.getUserID(), objID)) {
                        representation.append("✅  ");
                    } else {
                        if (getObjectiveThreshold(objID, game) > 0) {
                            representation.append(" (").append(getPlayerProgressOnObjective(objID, game, player)).append("/").append(getObjectiveThreshold(objID, game)).append(")  ");
                        } else {
                            representation.append("0/1  ");
                        }
                    }
                } else {
                    if (game.getRevealedPublicObjectives().containsKey(objID)
                        && game.didPlayerScoreThisAlready(player.getUserID(), objID)) {
                        representation.append("✅  ");
                    } else {
                        representation.append(getPlayerProgressOnObjective(objID, game, player)).append("/").append(getObjectiveThreshold(objID, game)).append("  ");
                    }
                }
            }
        }
        return representation.toString();
    }

    public static String representSecrets(Game game) {
        StringBuilder representation = new StringBuilder("__**Scored Secrets**__\n> ");
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation.append(player.getFactionEmoji()).append(": ").append(player.getSoScored()).append("/").append(player.getMaxSOCount()).append("  ");
            }
        }
        return representation.toString();
    }

    public static String representSupports(Game game) {
        StringBuilder representation = new StringBuilder("__**Support VPs**__\n> ");
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation.append(player.getFactionEmoji()).append(": ").append(player.getSupportForTheThroneVictoryPoints()).append("/1  ");
            }
        }
        return representation.toString();
    }

    public static String representTotalVPs(Game game) {
        StringBuilder representation = new StringBuilder("__**Total VPs**__\n> ");
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation.append(player.getFactionEmoji()).append(": ").append(player.getTotalVictoryPoints()).append("/").append(game.getVp()).append("  ");
            }
        }
        return representation.toString();
    }

    public static int getPlayerProgressOnObjective(String objID, Game game, Player player) {
        int comms = 0;
        if (player.hasUnexhaustedLeader("keleresagent")) {
            comms = player.getCommodities();
        }
        switch (objID) {
            case "push_boundaries" -> {
                int aboveN = 0;
                for (Player p2 : player.getNeighbouringPlayers()) {
                    int p1count = player.getPlanets().size();
                    int p2count = p2.getPlanets().size();
                    if (p1count > p2count) {
                        aboveN++;
                    }
                }
                return aboveN;
            }
            case "outer_rim", "control_borderlands" -> {
                int edge = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && tile.isEdgeOfBoard(game)
                        && tile != player.getHomeSystemTile()) {
                        edge++;
                    }
                }
                return edge;
            }
            case "make_history", "become_legend" -> {
                int counter = 0;
                for (Tile tile : game.getTileMap().values()) {
                    boolean tileCounts = tile.isMecatol() || tile.isAnomaly(game) || ButtonHelper.isTileLegendary(tile);
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && tileCounts) {
                        counter++;
                    }
                }
                return counter;
            }
            case "infrastructure", "protect_border" -> {
                int counter = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && game.getTileFromPlanet(planet) != player.getHomeSystemTile()
                        && (uH.getUnitCount(Units.UnitType.Spacedock, player) > 0
                        || uH.getUnitCount(Units.UnitType.Pds, player) > 0)) {
                        counter++;
                    }
                }
                if (player.hasAbility("privileged_citizenry")) {
                    counter = counter + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false);
                }
                return counter;
            }
            case "corner", "unify_colonies" -> {
                int max = 0;
                for (String type : List.of("cultural", "hazardous", "industrial")) {
                    int number = ButtonHelper.getNumberOfXTypePlanets(player, game, type, false);
                    if (number > max) max = number;
                }
                return max;
            }
            case "develop", "revolutionize" -> {
                return ButtonHelper.getNumberOfUnitUpgrades(player);
            }
            case "diversify", "master_science" -> {
                int numAbove1 = 0;
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.WARFARE) > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.PROPULSION) > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.BIOTIC) > 1) {
                    numAbove1++;
                }
                if (ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.CYBERNETIC) > 1) {
                    numAbove1++;
                }
                return numAbove1;
            }
            case "monument", "golden_age" -> {
                int x = Helper.getPlayerResourcesAvailable(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x = x + player.getTg() + comms;
                }
                return x;
            }
            case "expand_borders", "subdue" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(p);
                    if (tile != null && !tile.isHomeSystem(game)) {
                        count++;
                    }
                }
                return count;
            }
            case "research_outposts", "brain_trust" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    if (ButtonHelper.checkForTechSkips(game, planet)) {
                        count++;
                    }
                }
                return count;
            }
            case "intimidate" -> {
                int count = 0;
                Tile mecatol = game.getMecatolTile();
                if (mecatol == null) return 2;
                for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, mecatol.getPosition(), player, false)) {
                    Tile tile2 = game.getTileByPosition(pos);
                    if (FoWHelper.playerHasShipsInSystem(player, tile2)) {
                        count++;
                    }
                }
                return count;
            }
            case "lead", "galvanize" -> {
                return player.getTacticalCC() + player.getStrategicCC();
            }
            case "trade_routes", "centralize_trade" -> {
                return player.getTg() + comms;
            }
            case "amass_wealth" -> {
                int forTG = Math.min(3, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                int forResources = Math.min(3, Helper.getPlayerResourcesAvailable(player, game));
                int forInfluence = Math.min(3, Helper.getPlayerInfluenceAvailable(player, game));
                if (player.hasTech("mc")) {
                    leftOverTg = leftOverTg * 2;
                }
                return forTG + leftOverTg + forInfluence + forResources;
            }
            case "build_defenses", "massive_cities" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "sd", false);
            }
            case "lost_outposts", "ancient_monuments" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Planet planet = game.getPlanetsInfo().get(p);
                    if (planet.hasAttachment()) {
                        count++;
                    } else {
                        if (planet.getName().contains("custodia") && game.getStoredValue("terraformedPlanet").equalsIgnoreCase(planet.getName())) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "engineer_marvel" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fs", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "lady", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun", false);
            }
            case "deep_space", "vast_territories" -> {
                return ButtonHelper.getNumberOfTilesPlayerIsInWithNoPlanets(game, player);
            }
            case "raise_fleet", "command_armada" -> {
                int x = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                        x = Math.max(x, ButtonHelper.checkNumberNonFighterShips(player, tile));
                    }
                }
                return x;
            }
            case "sway_council", "manipulate_law" -> {
                int x = Helper.getPlayerInfluenceAvailable(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x = x + player.getTg() + comms;
                }
                return x;
            }
            case "conquer" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    if (tile != null && tile.isHomeSystem() && tile != player.getHomeSystemTile()) {
                        count++;
                    }
                }
                return count;
            }
            case "supremacy" -> {
                int count = 0;
                for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Flagship, Units.UnitType.Warsun, Units.UnitType.Lady)) {
                    if ((tile.isHomeSystem() && tile != player.getHomeSystemTile()) || tile.isMecatol()) {
                        count++;
                    }
                }
                return count;
            }
            case "vast_reserves" -> {
                int forTG = Math.min(6, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                int forResources = Math.min(6, Helper.getPlayerResourcesAvailable(player, game));
                int forInfluence = Math.min(6, Helper.getPlayerInfluenceAvailable(player, game));
                if (player.hasTech("mc")) {
                    leftOverTg = leftOverTg * 2;
                }
                return forTG + leftOverTg + forInfluence + forResources;
            }
            case "distant_lands" -> {
                Set<String> planetsAdjToHomes = new HashSet<>();
                Set<Tile> homesAdjTo = new HashSet<>();
                for (Player p2 : game.getRealAndEliminatedPlayers()) {
                    Tile tile = p2.getHomeSystemTile();
                    if (p2 == player || tile == null) continue;

                    Set<String> adjPositions = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
                    for (String planet : player.getPlanets()) {
                        Tile planetTile = game.getTileFromPlanet(planet);
                        if (planetTile != null && adjPositions.contains(planetTile.getPosition())) {
                            planetsAdjToHomes.add(planet);
                            homesAdjTo.add(tile);
                        }
                    }
                }
                // This number may be inaccurate when it's greater than 3, but it is always accurate for 2
                return Math.min(planetsAdjToHomes.size(), homesAdjTo.size());
            }
            //status phase secrets
            case "pem" -> {
                return ButtonHelper.checkHighestProductionSystem(player, game); // 8 production
            }
            case "sai" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Planet planet = game.getPlanetsInfo().get(p);
                    if (planet == null) {
                        BotLogger.log("Planet \"" + p + "\" not found for game " + game.getName());
                    } else if (planet.isLegendary()) {
                        count++;
                    }
                }
                return count;
            }
            case "syc" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(p);
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2 == player) {
                            continue;
                        }
                        if (FoWHelper.playerHasPlanetsInSystem(p2, tile)) {
                            count++;
                            break;
                        }
                    }
                }
                return count;
            }
            case "sb" -> {
                return player.getPromissoryNotesInPlayArea().size();
            }
            case "otf" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Planet planet = game.getPlanetsInfo().get(p);
                    if (planet != null && planet.getUnitCount(Units.UnitType.Spacedock, player) < 1) {
                        count = Math.max(count, ButtonHelper.getNumberOfGroundForces(player, planet));
                    }
                }
                return count;
            }
            case "mtm" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && uH.getUnitCount(Units.UnitType.Mech, player) > 0) {
                        count++;
                    }
                }
                return count;
            }
            case "hrm" -> { // 12 resources
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources = resources + Helper.getPlanetResources(planet, game);
                }
                return resources;
            }
            case "eh" -> { // 12 influence
                int influence = 0;
                for (String planet : player.getPlanets()) {
                    influence = influence + Helper.getPlanetInfluence(planet, game);
                }
                return influence;
            }
            case "dp" -> {
                return game.getLaws().size(); // 3 laws in play
            }
            case "dhw" -> {
                return player.getFragments().size(); // 2 frags
            }
            case "dfat" -> {
                Tile tile = game.getTileFromPlanet("mallice");
                if (tile == null || !FoWHelper.playerHasUnitsInSystem(player, tile)) {
                    return 0;
                } else {
                    return 1;
                }
            }
            case "te" -> {
                int count = 0;
                for (Player p2 : game.getRealPlayersNDummies()) {
                    if (p2 == player) {
                        continue;
                    }
                    Tile tile = p2.getHomeSystemTile();
                    if (tile == null) {
                        continue;
                    }
                    for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player,
                        false, false)) {
                        Tile tile2 = game.getTileByPosition(pos);
                        if (ButtonHelper.checkNumberShips(player, tile2) > 0) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "ose" -> {
                Tile mecatol = game.getMecatolTile();
                boolean controlsMecatol = player.getPlanets().stream()
                    .anyMatch(Constants.MECATOLS::contains);
                if (mecatol == null || !FoWHelper.playerHasUnitsInSystem(player, mecatol) || !controlsMecatol) {
                    return 0;
                } else {
                    return ButtonHelper.checkNumberShips(player, mecatol);
                }
            }
            case "mrm" -> {
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", false); //4 hazardous
            }
            case "mlp" -> {//4 techs of a color
                int maxNum = 0;
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.WARFARE));
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.PROPULSION));
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.CYBERNETIC));
                maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, TechnologyModel.TechnologyType.BIOTIC));
                return maxNum;
            }
            case "mp" -> {
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", false); // 4 industrial
            }
            case "lsc" -> {
                int count = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (ButtonHelper.checkNumberShips(player, tile) > 0) {
                        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player,
                            false, false)) {
                            Tile tile2 = game.getTileByPosition(pos);
                            if (tile2.isAnomaly(game)) {
                                count++;
                                break;
                            }
                        }
                    }
                }
                return count;
            }
            case "fwm" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "spacedock"); // 3 SD
            }
            case "fsn" -> {
                return player.getAc(); // 5 AC
            }
            case "gamf" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false); // 5 dreads
            }
            case "ans" -> {
                int count = 0;
                for (String nekroTech : player.getTechs()) {
                    if ("vax".equalsIgnoreCase(nekroTech) || "vay".equalsIgnoreCase(nekroTech)) {
                        continue;
                    }
                    if (!Mapper.getTech(nekroTech).getFaction().orElse("").isEmpty()) {
                        count = count + 1;
                    }

                }
                return count;
            }
            case "btgk" -> {
                int alpha = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.doesTileHaveAlpha(game, tile.getPosition()) && FoWHelper.playerHasShipsInSystem(player, tile)) {
                        alpha = 1;
                        break;
                    }
                }

                int beta = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.doesTileHaveBeta(game, tile.getPosition()) && FoWHelper.playerHasShipsInSystem(player, tile)) {
                        beta = 1;
                        break;
                    }
                }
                return alpha + beta;
            }
            case "ctr" -> {
                int count = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (ButtonHelper.checkNumberShips(player, tile) > 0) {
                        count++;
                    }
                }
                return count;
            }
            case "csl" -> {
                int count = 0;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, p2, Units.UnitType.Spacedock)) {
                        if (ButtonHelper.checkNumberShips(player, tile) > 0) {
                            count++;
                        }
                    }
                }
                return count;
            }
            case "eap" -> {
                return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds"); // 4 PDS
            }
            case "faa" -> { // 4 cultural
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", false);
            }
            case "fc" -> {
                return player.getNeighbourCount(); // neighbors
            }

        }
        return 0;
    }
}
