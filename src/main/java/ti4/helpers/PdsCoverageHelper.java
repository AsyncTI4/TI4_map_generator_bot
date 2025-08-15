package ti4.helpers;

import java.util.*;
import ti4.map.*;
import ti4.model.UnitModel;

public class PdsCoverageHelper {

    /**
     * Calculate PDS coverage for a specific tile, returning comprehensive coverage data per faction.
     * This method contains all the logic from TileGenerator's SpaceCannon case.
     *
     * @param game The game instance
     * @param tile The tile to calculate coverage for
     * @return Map of faction -> comprehensive PDS coverage data, null if no coverage
     */
    public static Map<String, PdsCoverage> calculatePdsCoverage(Game game, Tile tile) {
        if (game.isFowMode() || tile.getTileModel().isHyperlane()) {
            return null;
        }

        String tilePos = tile.getPosition();
        Map<String, List<Integer>> pdsDiceByPlayer = new HashMap<>();

        for (Player player : game.getRealPlayers()) {
            List<Integer> diceCount = new ArrayList<>();
            List<Integer> diceCountMirveda = new ArrayList<>();
            int mod = (game.playerHasLeaderUnlockedOrAlliance(player, "kolumecommander") ? 1 : 0);

            // Starfall Gunnery ability - ships without space cannon can shoot
            if (player.hasAbility("starfall_gunnery")) {
                for (int i = checkNumberNonFighterShipsWithoutSpaceCannon(player, tile); i > 0; i--) {
                    diceCount.add(8 - mod);
                }
            }

            // Check adjacent tiles for PDS coverage
            for (String adjTilePos : FoWHelper.getAdjacentTiles(game, tilePos, player, false, true)) {
                Tile adjTile = game.getTileByPosition(adjTilePos);
                if (adjTile == null) {
                    continue;
                }
                boolean sameTile = tilePos.equalsIgnoreCase(adjTilePos);

                for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                    // Check for Imperial II HQ on Mecatol Rex
                    if (sameTile && Constants.MECATOLS.contains(unitHolder.getName())) {
                        if (player.controlsMecatol(false) && player.getTechs().contains("iihq")) {
                            diceCount.add(5 - mod);
                        }
                    }

                    // Check units for PDS
                    for (Map.Entry<Units.UnitKey, Integer> unitEntry :
                            unitHolder.getUnits().entrySet()) {
                        if (unitEntry.getValue() == 0) {
                            continue;
                        }

                        Units.UnitKey unitKey = unitEntry.getKey();
                        if (game.getPlayerByColorID(unitKey.getColorID()).orElse(null) != player) {
                            continue;
                        }

                        UnitModel model = player.getUnitFromUnitKey(unitKey);
                        if (model == null
                                || ("xxcha_mech".equalsIgnoreCase(model.getId())
                                        && ButtonHelper.isLawInPlay(game, "articles_war"))) {
                            continue;
                        }

                        int tempMod = 0;
                        if ("bentor_flagship".equalsIgnoreCase(model.getId())) {
                            tempMod += player.getNumberOfBluePrints();
                        }

                        // Check if PDS can shoot (deep space cannon or same tile)
                        if (model.getDeepSpaceCannon() || sameTile) {
                            for (int i = model.getSpaceCannonDieCount() * unitEntry.getValue(); i > 0; i--) {
                                diceCount.add(model.getSpaceCannonHitsOn() - mod - tempMod);
                            }
                        } else if (game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")) {
                            diceCountMirveda.add(model.getSpaceCannonHitsOn() - mod - tempMod);
                        }
                    }

                    // Check planets for space cannon abilities
                    if (sameTile && player.getPlanets().contains(unitHolder.getName())) {
                        Planet planet = game.getPlanetsInfo().get(unitHolder.getName());
                        for (int i = planet.getSpaceCannonDieCount(); i > 0; i--) {
                            diceCount.add(planet.getSpaceCannonHitsOn() - mod);
                        }
                    }
                }
            }

            // Apply Mirveda Commander ability (can use one non-deep space cannon PDS)
            if (!diceCountMirveda.isEmpty()) {
                Collections.sort(diceCountMirveda);
                diceCount.add(diceCountMirveda.getFirst());
            }

            if (!diceCount.isEmpty()) {
                Collections.sort(diceCount);

                // Apply Plasma Scoring tech (duplicate best die)
                if (player.getTechs().contains("ps")) {
                    diceCount.addFirst(diceCount.getFirst());
                }

                // Apply Argent Commander (duplicate best die)
                if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
                    diceCount.addFirst(diceCount.getFirst());
                }

                pdsDiceByPlayer.put(player.getUserID(), diceCount);
            }
        }

        if (pdsDiceByPlayer.isEmpty()) {
            return null;
        }

        // Calculate coverage data for each player
        Map<String, PdsCoverage> pdsCoverage = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : pdsDiceByPlayer.entrySet()) {
            Player player = game.getPlayer(entry.getKey());
            List<Integer> diceList = entry.getValue();
            int numberOfDice = diceList.size();
            boolean rerolls = game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander");

            float expectedHits;
            if (rerolls) {
                // With rerolls: probability of success = 1 - ((X-1)/10)²
                expectedHits = (100.0f * numberOfDice
                                - diceList.stream()
                                        .mapToInt(value -> (value - 1) * (value - 1))
                                        .sum())
                        / 100;
            } else {
                // Without rerolls: probability of success = (11-X)/10
                expectedHits = (11.0f * numberOfDice
                                - diceList.stream().mapToInt(Integer::intValue).sum())
                        / 10;
            }

            pdsCoverage.put(player.getFaction(), new PdsCoverage(numberOfDice, expectedHits, diceList, rerolls));
        }

        return pdsCoverage;
    }

    public static int checkNumberNonFighterShipsWithoutSpaceCannon(Player player, Tile tile) {
        int count = 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        for (Units.UnitKey unit : space.getUnitKeys()) {
            if (!player.unitBelongsToPlayer(unit)) continue;

            UnitModel removedUnit = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
            if (removedUnit.getIsShip()
                    && !removedUnit.getAsyncId().contains("ff")
                    && removedUnit.getSpaceCannonDieCount() == 0) {
                count += space.getUnitCount(unit);
            }
        }
        return count;
    }
}
