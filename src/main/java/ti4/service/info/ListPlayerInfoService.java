package ti4.service.info;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogMessageOrigin;
import ti4.model.PublicObjectiveModel;
import ti4.model.Source;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class ListPlayerInfoService {

    public static int getObjectiveThreshold(String objID, Game game) {
        return switch (objID) {
            // stage 1's
            case "push_boundaries" -> 2;
            case "outer_rim",
                    "ancient_monuments",
                    "revolutionize",
                    "deep_space",
                    "lead",
                    "research_outposts",
                    "infrastructure" -> 3;
            case "make_history" -> 2;
            case "corner", "become_legend", "master_science", "build_defenses" -> 4;
            case "develop" -> 2;
            case "diversify" -> 2;
            case "monument", "command_armada", "sway_council" -> 8;
            case "expand_borders" -> 6;
            case "intimidate" -> 2;
            case "trade_routes" -> 5;
            case "amass_wealth" -> 9;
            case "lost_outposts" -> 2;
            case "engineer_marvel" -> 1;
            case "raise_fleet" -> 5;
            // stage 2's
            case "centralize_trade" -> 10;
            case "conquer" -> 1;
            case "brain_trust" -> 5;
            case "golden_age" -> 16;
            case "galvanize" -> 6;
            case "manipulate_law" -> 16;
            case "subdue" -> 11;
            case "unify_colonies" -> 6;
            case "supremacy" -> 1;
            case "massive_cities" -> 7;
            case "control_borderlands" -> 5;
            case "vast_reserves" -> 18;
            case "vast_territories" -> 5;
            case "protect_border" -> 5;
            case "distant_lands" -> 2;

            // Status Phase secrets
            case "pem" -> 8; // 8 production
            case "sai" -> 1; // legendary
            case "syc" -> 1; // control a planet in same system someone else does
            case "sb" -> 1; // have a PN in your play area
            case "otf" -> 9; // 9 gf on a planet without a dock
            case "mtm" -> 4; // 4 mechs on 4 planets
            case "hrm" -> 12; // 12 resources
            case "eh" -> 12; // 12 influence
            case "dp" -> 3; // 3 laws in play
            case "dhw" -> 2; // 2 frags
            case "dfat" -> 1; // nexus
            case "te" -> 1; // be next to HS
            case "ose" -> 3; // control rex and have 3 ships
            case "mrm" -> 4; // 4 hazardous
            case "mlp" -> 4; // 4 techs of a color
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

            // Omega Phase objectives
            case "corner_omegaphase" -> 4;
            case "galvanize_omegaphase" -> 6;
            case "manipulate_law_omegaphase", "golden_age_omegaphase" -> 16;
            case "centralize_trade_omegaphase" -> 10;
            case "revolutionize_omegaphase" -> 2;
            case "master_science_omegaphase" -> 4;
            case "subdue_omegaphase" -> 6;
            case "brain_trust_omegaphase" -> 3;
            case "distant_lands_omegaphase" -> 2;
            case "push_boundaries_omegaphase" -> 2;
            case "ancient_monuments_omegaphase" -> 3;
            case "vast_territories_omegaphase" -> 4;
            case "intimidate_omegaphase" -> 3;
            case "massive_cities_omegaphase" -> 4;
            case "become_legend_omegaphase" -> 3;
            case "command_armada_omegaphase" -> 6;
            case "supremacy_omegaphase" -> 1;
            case "control_borderlands_omegaphase" -> 4;
            case "imperium_rex_omegaphase" -> 0;

            default -> 0;
        };
    }

    record ObjectiveResult(boolean canFullyMeet, int closenessScore) {}

    private ObjectiveResult checkObjective(
            List<String> planets, int tradeGoods, int goal, Player player, Game game, int closestScore) {
        return backtrack(planets, 0, 0, 0, new HashSet<>(), tradeGoods, goal, player, game, closestScore);
    }

    private ObjectiveResult backtrack(
            List<String> planets,
            int currentResources,
            int currentInfluence,
            int index,
            Set<String> usedPlanets,
            int remainingTradeGoods,
            int goal,
            Player player,
            Game game,
            int closestScore) {
        // Success condition
        if (currentResources >= goal && currentInfluence >= goal) {
            return new ObjectiveResult(true, goal * 2);
        }
        int additionalResources2 = Math.min(remainingTradeGoods, Math.max(0, goal - currentResources));
        int additionalInfluence2 =
                Math.min(remainingTradeGoods - additionalResources2, Math.max(0, goal - currentInfluence));

        int newResources2 = currentResources + additionalResources2;
        int newInfluence2 = currentInfluence + additionalInfluence2;

        // Calculate closeness score
        int resourceShortfall2 = Math.max(0, goal - newResources2);
        int influenceShortfall2 = Math.max(0, goal - newInfluence2);
        int closenessScore2 = goal * 2 - (resourceShortfall2 + influenceShortfall2);
        closestScore = Math.max(closenessScore2, closestScore);

        // Failure condition - run out of options
        if (index >= planets.size() && remainingTradeGoods == 0) {
            // Calculate closeness score
            int resourceShortfall = Math.max(0, goal - currentResources);
            int influenceShortfall = Math.max(0, goal - currentInfluence);
            int closenessScore = goal * 2 - (resourceShortfall + influenceShortfall);
            closenessScore = Math.max(closenessScore, closestScore);
            return new ObjectiveResult(false, Math.max(0, closenessScore));
        }

        // If we've run out of planets, try using trade goods
        if (index >= planets.size()) {
            // Try using remaining trade goods for resources
            int additionalResources = Math.min(remainingTradeGoods, Math.max(0, goal - currentResources));
            int additionalInfluence =
                    Math.min(remainingTradeGoods - additionalResources, Math.max(0, goal - currentInfluence));

            int newResources = currentResources + additionalResources;
            int newInfluence = currentInfluence + additionalInfluence;

            // Calculate closeness score
            int resourceShortfall = Math.max(0, goal - newResources);
            int influenceShortfall = Math.max(0, goal - newInfluence);
            int closenessScore = goal * 2 - (resourceShortfall + influenceShortfall);
            closenessScore = Math.max(closenessScore, closestScore);
            boolean canMeet = newResources >= goal && newInfluence >= goal;
            return new ObjectiveResult(canMeet, canMeet ? goal * 2 : Math.max(0, closenessScore));
        }

        String current = planets.get(index);

        Planet planet = game.getPlanetsInfo().get(current);
        if (planet == null) {
            BotLogger.warning(
                    new LogMessageOrigin(player),
                    "ListPlayerInfoService: Planet \"" + current + "\" not found for game " + game.getName());
            return backtrack(
                    planets,
                    currentResources,
                    currentInfluence,
                    index + 1,
                    usedPlanets,
                    remainingTradeGoods,
                    goal,
                    player,
                    game,
                    closestScore);
        }
        int resources = planet.getResources();
        int influence = planet.getInfluence();
        if (player.hasLeaderUnlocked("xxchahero")) {
            resources += influence;
            influence += planet.getResources();
        }
        if (resources > 0) {
            ObjectiveResult resourceResult = backtrack(
                    planets,
                    currentResources + resources,
                    currentInfluence,
                    index + 1,
                    usedPlanets,
                    remainingTradeGoods,
                    goal,
                    player,
                    game,
                    closestScore);
            closestScore = Math.max(resourceResult.closenessScore, closestScore);
            if (resourceResult.canFullyMeet()) {
                return resourceResult;
            }
        }
        // Try using planet for influence
        if (influence > 0) {
            ObjectiveResult influenceResult = backtrack(
                    planets,
                    currentResources,
                    currentInfluence + influence,
                    index + 1,
                    usedPlanets,
                    remainingTradeGoods,
                    goal,
                    player,
                    game,
                    closestScore);
            closestScore = Math.max(influenceResult.closenessScore, closestScore);
            if (influenceResult.canFullyMeet()) {
                return influenceResult;
            }
        }

        // Skip this planet
        return backtrack(
                planets,
                currentResources,
                currentInfluence,
                index + 1,
                usedPlanets,
                remainingTradeGoods,
                goal,
                player,
                game,
                closestScore);
    }

    public static void displayerScoringProgression(
            Game game, boolean onlyThisGameObj, MessageChannel channel, String stage1sOrTwos) {
        StringBuilder msg = new StringBuilder();
        int x = 1;
        if (onlyThisGameObj) {
            for (String id : game.getRevealedPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id) != null) {
                    msg.append(representScoring(game, id, x)).append("\n\n");
                    x++;
                }
            }
            for (String id : game.getSoToPoList()) {
                msg.append(representScoring(game, id, x, true)).append("\n");
                x++;
            }
            msg.append(representSecrets(game)).append("\n");
            msg.append(representSupports(game)).append("\n");
            if (gameHasTransferablePoints(game)) {
                msg.append(representTransferablePoints(game)).append("\n");
            }
            msg.append(representTotalVPs(game)).append("\n");
        } else {
            for (String id : Mapper.getPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(id).getSource() == Source.ComponentSource.pok
                        || Mapper.getPublicObjective(id).getSource() == Source.ComponentSource.base) {
                    if (stage1sOrTwos.equalsIgnoreCase(
                                    "" + Mapper.getPublicObjective(id).getPoints())
                            || "both".equalsIgnoreCase(stage1sOrTwos)) {
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

    private static String representScoring(Game game, String objID, int x, boolean secret) {
        StringBuilder representation;
        if (secret) {
            representation = new StringBuilder(
                    x + ". " + SecretObjectiveInfoService.getSecretObjectiveRepresentation(objID) + "> ");
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
                    if (game.didPlayerScoreThisAlready(player.getUserID(), objID)
                            || game.didPlayerScoreThisAlready(
                                    player.getUserID(),
                                    Mapper.getSecretObjectivesJustNames().get(objID))) {
                        representation.append("✅  ");
                    } else {
                        if (getObjectiveThreshold(objID, game) > 0) {
                            representation
                                    .append(" (")
                                    .append(getPlayerProgressOnObjective(objID, game, player))
                                    .append("/")
                                    .append(getObjectiveThreshold(objID, game))
                                    .append(")  ");
                        } else {
                            representation.append("0/1  ");
                        }
                    }
                } else {
                    if (game.getRevealedPublicObjectives().containsKey(objID)
                            && game.didPlayerScoreThisAlready(player.getUserID(), objID)) {
                        representation.append("✅  ");
                    } else {
                        representation
                                .append(getPlayerProgressOnObjective(objID, game, player))
                                .append("/")
                                .append(getObjectiveThreshold(objID, game))
                                .append("  ");
                    }
                }
            }
        }
        return representation.toString();
    }

    private static String representSecrets(Game game) {
        StringBuilder representation = new StringBuilder("__**Scored Secret Objectives**__\n> ");
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation
                        .append(player.getFactionEmoji())
                        .append(": ")
                        .append(player.getSoScored())
                        .append("/")
                        .append(player.getMaxSOCount())
                        .append("  ");
            }
        }
        return representation.toString();
    }

    private static String representSupports(Game game) {
        StringBuilder representation = new StringBuilder("__**Support Victory Points**__\n> ");
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation
                        .append(player.getFactionEmoji())
                        .append(": ")
                        .append(player.getSupportForTheThroneVictoryPoints())
                        .append("/1  ");
            }
        }
        return representation.toString();
    }

    private static String getTransferablePointRepresentation(String objectiveId) {
        return switch (objectiveId) {
            case Constants.VOICE_OF_THE_COUNCIL_PO, "Shard of the Throne", "Political Censure" -> objectiveId;
            case "Shard of the Throne (1)", "Shard of the Throne (2)", "Shard of the Throne (3)" -> objectiveId;
            case "Ixthian Rex Point" -> objectiveId;
            default -> null;
        };
    }

    private static boolean gameHasTransferablePoints(Game game) {
        return game.getCustomPublicVP().keySet().stream()
                .anyMatch(obj -> getTransferablePointRepresentation(obj) != null);
    }

    private static String representTransferablePoints(Game game) {
        StringBuilder representation = new StringBuilder("__**Transferable Points**__");
        if (!game.isFowMode()) {
            for (var objective : game.getCustomPublicVP().entrySet()) {
                String mutablePointRepresentation = getTransferablePointRepresentation(objective.getKey());
                if (mutablePointRepresentation == null) continue;

                List<String> scoringPlayers = game.getScoredPublicObjectives().get(objective.getKey());
                if (scoringPlayers == null) {
                    scoringPlayers = Collections.emptyList();
                }
                representation
                        .append("\n> ")
                        .append(mutablePointRepresentation)
                        .append(" (")
                        .append(objective.getValue())
                        .append(" VP)")
                        .append(": ")
                        .append(scoringPlayers.stream()
                                .map(game::getPlayer)
                                .filter(Objects::nonNull)
                                .map(Player::getRepresentationNoPing)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Not currently held"));
            }
        }
        return representation.toString();
    }

    private static String representTotalVPs(Game game) {
        StringBuilder representation = new StringBuilder("__**Total Victory Points**__\n> ");
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                representation
                        .append(player.getFactionEmoji())
                        .append(": ")
                        .append(player.getTotalVictoryPoints())
                        .append("/")
                        .append(game.getVp())
                        .append("  ");
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
            case "push_boundaries", "push_boundaries_omegaphase" -> {
                int aboveN = 0;
                for (Player p2 : player.getNeighbouringPlayers(true)) {
                    int p1count = player.getPlanets().size();
                    int p2count = p2.getPlanets().size();
                    if (p1count > p2count) {
                        aboveN++;
                    }
                }
                return aboveN;
            }
            case "outer_rim", "control_borderlands", "control_borderlands_omegaphase" -> {
                int edge = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile)
                            && tile.isEdgeOfBoard(game)
                            && tile != player.getHomeSystemTile()) {
                        edge++;
                    }
                }
                return edge;
            }
            case "make_history", "become_legend", "become_legend_omegaphase" -> {
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
                int maxPlanets = counter;
                for (String planet : player.getPlanetsAllianceMode()) {
                    UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (uH != null && game.getTileFromPlanet(planet) != player.getHomeSystemTile()) {
                        if (uH.getUnitCount(Units.UnitType.Spacedock, player) > 0
                                || uH.getUnitCount(Units.UnitType.Pds, player) > 0) {
                            counter++;
                        }
                        maxPlanets++;
                    }
                }
                if (player.hasAbility("privileged_citizenry")) {
                    counter += ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false);
                }
                if (player.hasAbility("orbital_foundries")) {
                    counter += ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun", false);
                }
                return Math.min(counter, maxPlanets);
            }
            case "corner", "unify_colonies", "corner_omegaphase" -> {
                int max = 0;
                for (String type : List.of("cultural", "hazardous", "industrial")) {
                    int number = ButtonHelper.getNumberOfXTypePlanets(player, game, type, false);
                    if (number > max) max = number;
                }
                return max;
            }
            case "develop", "revolutionize", "revolutionize_omegaphase" -> {
                return ButtonHelper.getNumberOfUnitUpgrades(player);
            }
            case "diversify", "master_science" -> {
                int numAbove1 = 0;
                for (TechnologyType type : TechnologyType.mainFour) {
                    if (ButtonHelper.getNumberOfCertainTypeOfTech(player, type) >= 2) {
                        numAbove1++;
                    }
                }
                return numAbove1;
            }
            case "master_science_omegaphase" -> {
                return ButtonHelper.getNumberOfNonUnitUpgrades(player);
            }
            case "monument", "golden_age" -> {
                int x = Helper.getPlayerResourcesAvailable(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x += player.getTg() + comms;
                }
                return x;
            }
            case "golden_age_omegaphase" -> {
                int x = Helper.getPlayerResourcesTotal(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x += player.getTg() + comms;
                }
                return x;
            }
            case "expand_borders", "subdue", "subdue_omegaphase" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(p);
                    if (tile != null && !tile.isHomeSystem(game)) {
                        count++;
                    }
                }
                return count;
            }
            case "research_outposts", "brain_trust", "brain_trust_omegaphase" -> {
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
                for (String pos :
                        FoWHelper.getAdjacentTilesAndNotThisTile(game, mecatol.getPosition(), player, false)) {
                    Tile tile2 = game.getTileByPosition(pos);
                    if (FoWHelper.playerHasShipsInSystem(player, tile2)) {
                        count++;
                    }
                }
                return count;
            }
            case "intimidate_omegaphase" -> {
                int count = 0;
                Tile mecatol = game.getMecatolTile();
                if (mecatol == null) return 3;
                for (String pos :
                        FoWHelper.getAdjacentTilesAndNotThisTile(game, mecatol.getPosition(), player, false)) {
                    Tile tile2 = game.getTileByPosition(pos);
                    if (tile2.containsPlayersUnits(player)) {
                        count++;
                    }
                }
                return count;
            }
            case "lead", "galvanize", "galvanize_omegaphase" -> {
                return player.getTacticalCC() + player.getStrategicCC();
            }
            case "trade_routes", "centralize_trade", "centralize_trade_omegaphase" -> {
                return player.getTg() + comms;
            }
            case "amass_wealth" -> {
                int forTG = Math.min(3, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                if (player.hasTech("mc")) {
                    leftOverTg *= 2;
                }
                if (player.getReadiedPlanets().size() > 15) {
                    int forResources = Math.min(3, Helper.getPlayerResourcesAvailable(player, game));
                    int forInfluence = Math.min(3, Helper.getPlayerInfluenceAvailable(player, game));
                    return forTG + leftOverTg + forInfluence + forResources;
                } else {
                    return forTG
                            + checkObjective(player.getReadiedPlanets(), leftOverTg, 3, player, game, 0).closenessScore;
                }
            }
            case "build_defenses", "massive_cities", "massive_cities_omegaphase" -> {
                int counter = 0;
                if (player.hasAbility("orbital_foundries")) {
                    counter += ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun", false);
                }
                if (player.hasUnit("ghoti_flagship")) {
                    counter += ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship", false);
                }
                return counter
                        + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false)
                        + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "sd", false);
            }
            case "lost_outposts", "ancient_monuments", "ancient_monuments_omegaphase" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Planet planet = game.getPlanetsInfo().get(p);
                    if (planet.hasAttachment()) {
                        count++;
                    } else {
                        if (planet.getName().contains("custodia")
                                && game.getStoredValue("terraformedPlanet").equalsIgnoreCase(planet.getName())) {
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
            case "deep_space", "vast_territories", "vast_territories_omegaphase" -> {
                return ButtonHelper.getNumberOfTilesPlayerIsInWithNoPlanets(game, player);
            }
            case "raise_fleet", "command_armada", "command_armada_omegaphase" -> {
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
                    x += player.getTg() + comms;
                }
                return x;
            }
            case "manipulate_law_omegaphase" -> {
                int x = Helper.getPlayerInfluenceTotal(player, game) + player.getTg() + comms;
                if (player.hasTech("mc")) {
                    x += player.getTg() + comms;
                }
                return x;
            }
            case "conquer" -> {
                int count = 0;
                for (String planet : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    if (tile != null && tile.isHomeSystem(game) && tile != player.getHomeSystemTile()) {
                        count++;
                    }
                }
                return count;
            }
            case "supremacy", "supremacy_omegaphase" -> {
                int count = 0;
                for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(
                        game, player, Units.UnitType.Flagship, Units.UnitType.Warsun, Units.UnitType.Lady)) {
                    if ((tile.isHomeSystem(game) && tile != player.getHomeSystemTile()) || tile.isMecatol()) {
                        count++;
                    }
                }
                return count;
            }
            case "vast_reserves" -> {
                int forTG = Math.min(6, player.getTg() + comms);
                int leftOverTg = player.getTg() + comms - forTG;
                if (player.hasTech("mc")) {
                    leftOverTg *= 2;
                }
                if (player.getReadiedPlanets().size() > 15) {
                    int forResources = Math.min(6, Helper.getPlayerResourcesAvailable(player, game));
                    int forInfluence = Math.min(6, Helper.getPlayerInfluenceAvailable(player, game));
                    return forTG + leftOverTg + forInfluence + forResources;
                } else {
                    return forTG
                            + checkObjective(player.getReadiedPlanets(), leftOverTg, 6, player, game, 0).closenessScore;
                }
            }
            case "distant_lands", "distant_lands_omegaphase" -> {
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
            // Status Phase secrets
            case "pem" -> {
                return ButtonHelper.checkHighestProductionSystem(player, game); // 8 production
            }
            case "sai" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Planet planet = game.getPlanetsInfo().get(p);
                    if (planet == null) {
                        BotLogger.warning(
                                new BotLogger.LogMessageOrigin(player),
                                "Planet \"" + p + "\" not found for game " + game.getName());
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
                for (String p : player.getPlanetsAllianceMode()) {
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
                    resources += Helper.getPlanetResources(planet, game);
                }
                return resources;
            }
            case "eh" -> { // 12 influence
                int influence = 0;
                for (String planet : player.getPlanets()) {
                    influence += Helper.getPlanetInfluence(planet, game);
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
                Tile tile = Optional.ofNullable(game.getTileFromPlanet("mallice"))
                        .orElseGet(() -> game.getTileFromPlanet("hexmallice"));
                if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
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
                    for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
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
                boolean controlsMecatol = player.getPlanets().stream().anyMatch(Constants.MECATOLS::contains);
                if (!FoWHelper.playerHasUnitsInSystem(player, mecatol) || !controlsMecatol) {
                    return 0;
                } else {
                    return ButtonHelper.checkNumberShips(player, mecatol);
                }
            }
            case "mrm" -> {
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", false); // 4 hazardous
            }
            case "mlp" -> { // 4 techs of a color
                int maxNum = 0;
                for (TechnologyType type : TechnologyType.mainFour)
                    maxNum = Math.max(maxNum, ButtonHelper.getNumberOfCertainTypeOfTech(player, type));
                return maxNum;
            }
            case "mp" -> {
                return ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", false); // 4 industrial
            }
            case "lsc" -> {
                int count = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (ButtonHelper.checkNumberShips(player, tile) > 0) {
                        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
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
                        count += 1;
                    }
                }
                return count;
            }
            case "btgk" -> {
                int alpha = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.doesTileHaveAlpha(game, tile.getPosition())
                            && FoWHelper.playerHasShipsInSystem(player, tile)) {
                        alpha = 1;
                        break;
                    }
                }

                int beta = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.doesTileHaveBeta(game, tile.getPosition())
                            && FoWHelper.playerHasShipsInSystem(player, tile)) {
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
                    for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(
                            game, p2, Units.UnitType.Spacedock)) {
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
