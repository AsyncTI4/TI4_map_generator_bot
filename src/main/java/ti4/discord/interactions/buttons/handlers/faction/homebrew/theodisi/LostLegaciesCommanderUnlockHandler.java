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
            default -> false;
        };
    }
}
