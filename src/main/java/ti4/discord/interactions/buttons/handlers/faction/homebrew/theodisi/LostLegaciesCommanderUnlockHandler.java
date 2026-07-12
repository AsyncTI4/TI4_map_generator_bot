package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi;

import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.model.ExploreModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.UnitModel;

@UtilityClass
public class LostLegaciesCommanderUnlockHandler {

    public static boolean meetsCommanderUnlockCondition(Player player, Game game, String faction) {
        if (player == null || game == null || faction == null) {
            return false;
        }

        return switch (faction) {
            case "ardentia" -> {
                int qualifyingSystems = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (tile.hasPlayerCC(player)) {
                        qualifyingSystems++;
                    }
                }
                yield qualifyingSystems >= 4;
            }
            case "verydith" -> {
                boolean unlocked = false;
                for (Tile tile : game.getTileMap().values()) {
                    if (!tile.containsPlayersUnits(player)) {
                        continue;
                    }

                    int otherCommandTokens = 0;
                    for (Player otherPlayer : game.getRealPlayers()) {
                        if (otherPlayer != player && tile.hasPlayerCC(otherPlayer)) {
                            otherCommandTokens++;
                        }
                    }

                    if (otherCommandTokens >= 2) {
                        unlocked = true;
                        break;
                    }
                }
                yield unlocked;
            }
            case "myrr" -> {
                boolean unlocked = false;
                for (Tile tile : game.getTileMap().values()) {
                    UnitHolder space = tile.getSpaceUnitHolder();
                    if (space == null) {
                        continue;
                    }

                    for (UnitKey unitKey : space.getUnitKeys()) {
                        if (!player.unitBelongsToPlayer(unitKey)) {
                            continue;
                        }

                        UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                        if (unitModel == null || !unitModel.isNonFighterShip()) {
                            continue;
                        }

                        if (space.getUnitCount(unitKey) >= 4) {
                            unlocked = true;
                            break;
                        }
                    }

                    if (unlocked) {
                        break;
                    }
                }
                yield unlocked;
            }
            case "kairn" -> {
                Set<String> explorationDecks = new HashSet<>();

                for (String cardId : player.getFragments()) {
                    ExploreModel explore = Mapper.getExplore(cardId);
                    if (explore != null) {
                        explorationDecks.add(explore.getType().toLowerCase());
                    }
                }

                // Persistent explore cards are stored as relics even though they are not actual relics.
                for (String cardId : player.getRelics()) {
                    ExploreModel explore = Mapper.getExplore(cardId);
                    if (explore != null) {
                        explorationDecks.add(explore.getType().toLowerCase());
                    }
                }

                Set<String> controlledAttachments = new HashSet<>();
                for (String planetId : player.getPlanets()) {
                    Planet planet = game.getUnitHolderFromPlanet(planetId);
                    if (planet != null) {
                        controlledAttachments.addAll(planet.getAttachments());
                    }
                }

                for (ExploreModel explore : Mapper.getExplores().values()) {
                    if (!Constants.ATTACH.equalsIgnoreCase(explore.getResolution())
                            || explore.getAttachmentId().isEmpty()) {
                        continue;
                    }

                    String attachmentId = explore.getAttachmentId().get();
                    if (controlledAttachments.contains(Mapper.getAttachmentImagePath(attachmentId))
                            || controlledAttachments.contains(Mapper.getAttachmentImagePath(attachmentId + "stat"))) {
                        explorationDecks.add(explore.getType().toLowerCase());
                    }
                }

                yield explorationDecks.size() >= 2;
            }
            case "kryxos" -> ButtonHelper.getNumberOfUnitUpgrades(player) >= 2;
            case "arcanum" -> {
                int colorsWithTech = 0;

                for (TechnologyType type : TechnologyType.mainFour) {
                    if (ButtonHelper.getNumberOfCertainTypeOfTech(player, type) >= 1) {
                        colorsWithTech++;
                    }
                }

                yield colorsWithTech >= 2;
            }
            case "oblivion" -> {
                yield getPlayersUnitTilesAdjacentToEmptyTiles(game, player).size() >= 3;
            }
            case "revenant" -> {
                long otherPlayersWithUnlockedCommanders = game.getRealPlayers().stream()
                    .filter(otherPlayer -> otherPlayer != player)
                    .filter(otherPlayer -> otherPlayer.getLeaders().stream()
                        .anyMatch(leader -> Constants.COMMANDER.equals(leader.getType()) && !leader.isLocked()))
                    .count();

                 yield otherPlayersWithUnlockedCommanders >= 2;
            }
            case "revenantmyrr" -> {
                Set<String> qualifyingUnitTypes = new HashSet<>();

                for (var entry : player.getCurrentProducedUnits().entrySet()) {
                    String unitAlias = getProducedUnitAlias(entry.getKey());
                    if (unitAlias == null || entry.getValue() < 2) {
                        continue;
                    }

                    qualifyingUnitTypes.add(unitAlias);
                    if (qualifyingUnitTypes.size() >= 2) {
                        yield true;
                    }
                }

                yield false;
            }
            case "revenantoblivion" -> {
                int numberOfPlanetsWithUniqueTraits = 0;
                if (ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", false) >= 1) {
                    numberOfPlanetsWithUniqueTraits++;
                }
                if (ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", false) >= 1) {
                    numberOfPlanetsWithUniqueTraits++;
                }
                if (ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", false) >= 1) {
                    numberOfPlanetsWithUniqueTraits++;
                }
                yield numberOfPlanetsWithUniqueTraits >= 3;
            }
            case "revenantponthous" -> {
                int sustainUnitsOnBoard = 0;

                for (Tile tile : game.getTileMap().values()) {
                    if (!tile.containsPlayersUnits(player)) {
                        continue;
                    }

                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        for (UnitKey unitKey : unitHolder.getUnitsByStateForPlayer(player).keySet()) {
                            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                            if (unitModel == null || !unitModel.getSustainDamage()) {
                                continue;
                            }

                            sustainUnitsOnBoard += unitHolder.getUnitCount(unitKey);
                            if (sustainUnitsOnBoard >= 3) {
                                yield true;
                            }
                        }
                    }
                }

                yield false;
            }
            case "thrones" -> {
                int unitsAdjacentToAnomalies = 0;
                for (Tile tile : game.getTileMap().values()) {
                    if (!tile.containsPlayersUnits(player)) {
                        continue;
                    }

                    if (FoWHelper.isTileAdjacentToAnAnomaly(game, tile.getPosition(), player) || tile.isAnomaly(game, player)) {
                        unitsAdjacentToAnomalies++;
                    }
                }

                yield unitsAdjacentToAnomalies >= 3;
            }
            case "ponthous" -> {
                for (Tile tile : game.getTileMap().values()) {
                    if (!tile.containsPlayersUnits(player)) {
                        continue;
                    }

                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder.getDamagedUnitCount(player.getColorID()) > 0) {
                            yield true;
                        }
                    }
                }
                
                yield false;
            }
            default -> false;
        };
    }

    public static Set<Tile> getPlayersUnitTilesAdjacentToEmptyTiles(Game game, Player player) {
        Set<Tile> qualifyingUnitTiles = new HashSet<>();
        if (game == null || player == null) {
            return qualifyingUnitTiles;
        }

        for (Tile tile : game.getTileMap().values()) {
            if (!tile.containsPlayersUnits(player)) {
                continue;
            }
            for (String adjacentPosition : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
                Tile adjacentTile = game.getTileByPosition(adjacentPosition);
                if (adjacentTile != null && adjacentTile.getPlanetUnitHolders().isEmpty()) {
                    qualifyingUnitTiles.add(tile);
                    break;
                }
            }
        }

        return qualifyingUnitTiles;
    }

    private static String getProducedUnitAlias(String producedUnitKey) {
        int lastSeparator = producedUnitKey.lastIndexOf('_');
        if (lastSeparator < 0) {
            return null;
        }

        int middleSeparator = producedUnitKey.lastIndexOf('_', lastSeparator - 1);
        if (middleSeparator < 0) {
            return null;
        }

        return producedUnitKey.substring(0, middleSeparator);
    }
}
