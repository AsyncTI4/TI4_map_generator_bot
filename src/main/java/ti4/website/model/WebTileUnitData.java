package ti4.website.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PdsCoverage;
import ti4.helpers.PdsCoverageHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.DrawingUtil;
import ti4.image.TileGenerator;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.website.WebPdsCoverage;

@Data
public class WebTileUnitData {
    private Map<String, List<WebEntityData>> space;
    private Map<String, WebTilePlanet> planets;
    private List<String> ccs;
    private boolean isAnomaly;
    private Map<String, Integer> production;
    private Map<String, WebPdsCoverage> pds; // PDS coverage data per faction

    private WebTileUnitData() {
        space = new HashMap<>();
        planets = new HashMap<>();
        ccs = new ArrayList<>();
        isAnomaly = false;
        production = new HashMap<>();
        pds = null; // Only populated if there is PDS coverage
    }

    public static Map<String, WebTileUnitData> fromGame(Game game) {
        Map<String, WebTileUnitData> tileUnitData = new HashMap<>();

        for (Map.Entry<String, Tile> entry : game.getTileMap().entrySet()) {
            String position = entry.getKey();
            Tile tile = entry.getValue();

            if (tile != null
                    && tile.getTileID() != null
                    && !"-1".equals(tile.getTileID())
                    && !"null".equals(tile.getTileID())) {
                WebTileUnitData unitData = extractTileUnitData(game, tile);
                tileUnitData.put(position, unitData);
            }
        }

        // Add virtual "special" tile for off-tile planets (custodiavigilia, oceans, etc.)
        WebTileUnitData specialTileData = extractOffTilePlanetsData(game);
        if (specialTileData != null && !specialTileData.planets.isEmpty()) {
            tileUnitData.put(Constants.SPECIAL, specialTileData);
        }

        return tileUnitData;
    }

    private static WebTileUnitData extractTileUnitData(Game game, Tile tile) {
        WebTileUnitData tileData = new WebTileUnitData();

        // Set anomaly status
        tileData.setAnomaly(tile.isAnomaly(game));

        // Extract command tokens from space
        UnitHolder spaceHolder = tile.getUnitHolders().get(Constants.SPACE);
        if (spaceHolder != null) {
            for (String ccID : spaceHolder.getCcList()) {
                Player player =
                        DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), ccID);
                if (player != null) {
                    tileData.ccs.add(player.getFaction());
                }
            }
        }

        // Main Loop: Iterate over all UnitHolders
        for (Map.Entry<String, UnitHolder> holderEntry : tile.getUnitHolders().entrySet()) {
            String holderName = holderEntry.getKey();
            boolean isSpace = Constants.SPACE.equals(holderName);
            UnitHolder unitHolder = holderEntry.getValue();

            if (isSpace) {
                extractUnits(game, unitHolder, tileData.space);
                extractTokens(unitHolder, tileData.space, false);
            } else {
                // For planets and space stations
                if (unitHolder instanceof Planet planet) {
                    WebTilePlanet planetData = tileData.planets.computeIfAbsent(holderName, k -> new WebTilePlanet());

                    // Extract units and tokens
                    extractUnits(game, unitHolder, planetData.getEntities());
                    extractTokens(unitHolder, planetData.getEntities(), false);

                    // Determine controller
                    String controllingFaction;
                    if (planet.isSpaceStation()) {
                        controllingFaction = getSpaceStationController(game, planet);
                    } else {
                        controllingFaction = getPlanetController(game, planet);
                    }
                    planetData.setControlledBy(controllingFaction);

                    // Update metadata (commodities, shields)
                    updatePlanetMetadata(game, planet, planetData);
                }
            }
        }

        // Calculate production and capacity for each player
        for (Player player : game.getRealPlayers()) {
            String color = player.getColor();

            // Calculate production value for this player in this tile
            int productionValue = Helper.getProductionValue(player, game, tile, false);
            if (productionValue > 0) {
                tileData.production.put(color, productionValue);
            }
        }

        // Calculate PDS coverage for this tile
        Map<String, PdsCoverage> pdsCoverageDetailed = PdsCoverageHelper.calculatePdsCoverage(game, tile);
        if (pdsCoverageDetailed != null && !pdsCoverageDetailed.isEmpty()) {
            Map<String, WebPdsCoverage> pdsCoverage = new HashMap<>();
            for (Map.Entry<String, PdsCoverage> entry : pdsCoverageDetailed.entrySet()) {
                ti4.helpers.PdsCoverage detailed = entry.getValue();
                pdsCoverage.put(entry.getKey(), new WebPdsCoverage(detailed.getCount(), detailed.getExpected()));
            }
            tileData.setPds(pdsCoverage);
        }

        return tileData;
    }

    /**
     * Handles setting commodities (Discordant Stars "CommsOnPlanet") and planetary shields.
     */
    private static void updatePlanetMetadata(Game game, Planet planet, WebTilePlanet planetData) {
        // Set commodities count for Discordant Stars comms on planets functionality
        String commsStorageKey = "CommsOnPlanet" + planet.getName();
        if (!game.getStoredValue(commsStorageKey).isEmpty()) {
            try {
                int comms = Integer.parseInt(game.getStoredValue(commsStorageKey));
                if (comms > 0) {
                    planetData.setCommodities(comms);
                }
            } catch (NumberFormatException e) {
                // Ignore invalid stored values
            }
        }

        // Set planetary shield status
        planetData.setPlanetaryShield(TileGenerator.shouldPlanetHaveShield(planet, game));
    }

    /**
     * Determines the controller based on the control token (standard planets).
     */
    private static String getPlanetController(Game game, Planet planet) {
        if (planet.getControlList().isEmpty()) {
            return null;
        }
        // Get the first control token (there should only be one)
        String controlToken = planet.getControlList().iterator().next();
        Player controllingPlayer =
                DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlToken);
        return controllingPlayer != null ? controllingPlayer.getFaction() : null;
    }

    /**
     * Determines the controller based on player planet ownership (space stations).
     */
    private static String getSpaceStationController(Game game, Planet station) {
        String holderName = station.getName();
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains(holderName)) {
                return player.getFaction();
            }
        }
        return null;
    }

    /**
     * Extract units from a UnitHolder and add them to the target entities map.
     */
    private static void extractUnits(
            Game game, UnitHolder unitHolder, Map<String, List<WebEntityData>> targetEntities) {
        if (!unitHolder.hasUnits()) {
            return;
        }

        // Group units by faction
        Map<String, List<WebEntityData>> factionEntities = new HashMap<>();

        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
            int unitCount = unitHolder.getUnitCount(unitKey);
            String unitId = getUnitIdFromType(unitKey.getUnitType());

            if (player == null || unitId == null || unitCount <= 0) {
                continue;
            }

            String faction = player.getFaction();

            // Get sustained damage count for this unit
            Integer sustainedDamage = null;
            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().containsKey(unitKey)) {
                int damagedCount = unitHolder.getUnitDamage().get(unitKey);
                if (damagedCount > 0) {
                    sustainedDamage = damagedCount;
                }
            }

            // Get unit state counts: [healthy, damaged, galvanized, damaged+galvanized]
            List<Integer> unitStates = unitHolder.getUnitStates(unitKey);
            WebEntityData entityData = new WebEntityData(unitId, "unit", unitCount, sustainedDamage, unitStates);
            factionEntities.computeIfAbsent(faction, k -> new ArrayList<>()).add(entityData);
        }

        if (!factionEntities.isEmpty()) {
            // Add units to target entities
            for (Map.Entry<String, List<WebEntityData>> factionEntry : factionEntities.entrySet()) {
                targetEntities
                        .computeIfAbsent(factionEntry.getKey(), k -> new ArrayList<>())
                        .addAll(factionEntry.getValue());
            }
        }
    }

    /**
     * Extract tokens and/or attachments from a UnitHolder and add them to the target entities map.
     *
     * @param unitHolder The source of tokens
     * @param targetEntities The map to add the extracted entities to
     * @param attachmentsOnly If true, only extracts tokens that are identified as attachments
     */
    private static void extractTokens(
            UnitHolder unitHolder, Map<String, List<WebEntityData>> targetEntities, boolean attachmentsOnly) {
        List<String> holderTokens = new ArrayList<>(unitHolder.getTokenList());
        // Remove null entries that might exist in token lists
        holderTokens.removeIf(token -> token == null || token.trim().isEmpty());

        if (holderTokens.isEmpty()) {
            return;
        }

        // Group tokens by faction and add them to the entity data
        Map<String, List<WebEntityData>> factionTokens = new HashMap<>();

        for (String token : holderTokens) {
            boolean isAttachment = ti4.image.Mapper.getAttachmentInfo(token) != null;

            // Skip if we only want attachments and this isn't one
            if (attachmentsOnly && !isAttachment) {
                continue;
            }

            String entityType = isAttachment ? "attachment" : "token";

            // For now, we'll treat all tokens as non-faction specific
            WebEntityData tokenData = new WebEntityData(ti4.image.Mapper.getTokenIDFromTokenPath(token), entityType, 1);
            factionTokens.computeIfAbsent("neutral", k -> new ArrayList<>()).add(tokenData);
        }

        // Merge token data with existing data
        for (Map.Entry<String, List<WebEntityData>> factionEntry : factionTokens.entrySet()) {
            targetEntities
                    .computeIfAbsent(factionEntry.getKey(), k -> new ArrayList<>())
                    .addAll(factionEntry.getValue());
        }
    }

    /**
     * Extract data for off-tile planets (custodiavigilia, oceans, etc.) that don't exist on any tile
     * but are stored in game.getPlanetsInfo()
     */
    private static WebTileUnitData extractOffTilePlanetsData(Game game) {
        WebTileUnitData specialTileData = new WebTileUnitData();

        // List of off-tile planets that don't exist on tiles
        List<String> offTilePlanetIds = List.of(
                "custodiavigilia",
                "custodiavigiliaplus",
                "ghoti",
                "nevermore",
                "ocean1",
                "ocean2",
                "ocean3",
                "ocean4",
                "ocean5",
                "triad");

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();

        for (String planetId : offTilePlanetIds) {
            Planet planet = planetsInfo.get(planetId);
            if (planet == null) {
                continue;
            }

            // Create planet data entry
            WebTilePlanet planetData = new WebTilePlanet();

            // Determine controlling player by checking which players have the planet
            String controllingFaction = null;
            Player controllingPlayer = null;
            for (Player player : game.getRealPlayers()) {
                if (player.getPlanets().contains(planetId)) {
                    controllingFaction = player.getFaction();
                    controllingPlayer = player;
                    break;
                }
            }
            planetData.setControlledBy(controllingFaction);

            // Set exhausted status based on controlling player's exhausted planets
            boolean isExhausted = false;
            if (controllingPlayer != null) {
                isExhausted = controllingPlayer.getExhaustedPlanets().contains(planetId);
            }
            planetData.setExhausted(isExhausted);

            // Extract tokens and attachments (no units, no commodities)
            extractTokens(planet, planetData.getEntities(), false);

            // Set planetary shield status
            planetData.setPlanetaryShield(TileGenerator.shouldPlanetHaveShield(planet, game));

            // Add planet to special tile
            specialTileData.planets.put(planetId, planetData);
        }

        return specialTileData;
    }

    private static String getUnitIdFromType(UnitType unitType) {
        return switch (unitType) {
            case Infantry -> "gf";
            case Mech -> "mf";
            case Pds -> "pd";
            case Spacedock -> "sd";
            case Monument -> "monument";
            case Fighter -> "ff";
            case Destroyer -> "dd";
            case Cruiser -> "ca";
            case Carrier -> "cv";
            case Dreadnought -> "dn";
            case Flagship -> "fs";
            case Warsun -> "ws";
            case PlenaryOrbital -> "plenaryorbital";
            case TyrantsLament -> "tyrantslament";
            case Lady -> "lady";
            case Cavalry -> "cavalry";
            case StarfallPds -> "starfallpds";
            default -> null;
        };
    }
}
