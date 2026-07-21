package ti4.website.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PdsCoverage;
import ti4.helpers.PdsCoverageHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.DrawingUtil;
import ti4.image.TileGenerator;
import ti4.service.map.CustomHyperlaneService;
import ti4.website.WebPdsCoverage;

@Data
public final class WebTileUnitData {
    private Map<String, List<WebEntityData>> space;
    private Map<String, WebTilePlanet> planets;
    private List<String> ccs;
    private boolean isAnomaly;
    private Map<String, Integer> production;
    private Map<String, WebPdsCoverage> pds; // PDS coverage data per faction
    // Connection matrix (6x6 binary, "i,j,...;..." rows) for hyperlane tiles; null if not a
    // hyperlane or no connection data is configured. See CustomHyperlaneService for the format.
    private String hyperlaneMatrix;
    // True if this position is outside the viewer's current vision and the tile/unit data above
    // is a remembered "ghost" snapshot rather than the live tile (see #markGhostTiles). Always
    // false in the unfiltered (GM/non-FoW) view.
    private boolean isGhost;
    // The viewer's last-seen label for this position (e.g. "Rnd 4"), same string shown on the
    // Discord PNG fog overlay (Player#getFogLabels). Null unless isGhost is true.
    private String fogLabel;

    private WebTileUnitData() {
        space = new HashMap<>();
        planets = new HashMap<>();
        ccs = new ArrayList<>();
        isAnomaly = false;
        production = new HashMap<>();
        pds = null; // Only populated if there is PDS coverage
    }

    // Marks a player the viewer can't identify: the real color is kept so the frontend can still
    // render it, but the faction behind it is withheld. Shared with WebLaw#redactElectedFaction.
    public static final String UNKNOWN_FACTION_PREFIX = "fow:";

    public static Map<String, WebTileUnitData> fromGame(Game game) {
        return fromTileMap(game, game.getTileMap());
    }

    /**
     * Redacts control-token/CC faction identity in place for players the viewer can't identify
     * (FoWHelper#canSeeStatsOfPlayer), substituting "fow:&lt;color&gt;" so the frontend can still
     * render the colored token without the faction seal - matching how DrawingUtil#drawControlToken
     * always draws the real-colored base token on Discord but skips the faction icon overlay.
     */
    public static void redactControlIdentities(Map<String, WebTileUnitData> tileUnitData, Game game, Player viewer) {
        for (WebTileUnitData tileData : tileUnitData.values()) {
            for (WebTilePlanet planet : tileData.planets.values()) {
                if (planet.getControlledBy() != null) {
                    planet.setControlledBy(obscureIfHidden(game, viewer, planet.getControlledBy()));
                }
            }
            tileData.ccs.replaceAll(faction -> obscureIfHidden(game, viewer, faction));
        }
    }

    /**
     * Flags positions outside the viewer's current vision as ghost tiles and stamps them with the
     * viewer's remembered last-seen label, mirroring the greyed-out "Rnd N" fog tile the Discord PNG
     * draws (TileGenerator#createTileImage, Player#getFogLabels). The tile/unit data at these
     * positions is already the remembered snapshot (see GameWebDataService#buildFogSubstitutedTileMap)
     * - this only adds the metadata the frontend needs to render it distinctly from a live tile.
     */
    public static void markGhostTiles(
            Map<String, WebTileUnitData> tileUnitData, Set<String> visiblePositions, Player viewer) {
        Map<String, String> fogLabels = viewer.getFogLabels();
        for (Map.Entry<String, WebTileUnitData> entry : tileUnitData.entrySet()) {
            if (visiblePositions.contains(entry.getKey())) {
                continue;
            }
            WebTileUnitData tileData = entry.getValue();
            tileData.isGhost = true;
            tileData.fogLabel = fogLabels.get(entry.getKey());
        }
    }

    private static String obscureIfHidden(Game game, Player viewer, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        if (player == null || player.isNeutral() || FoWHelper.canSeeStatsOfPlayer(game, player, viewer)) {
            return faction;
        }
        return UNKNOWN_FACTION_PREFIX + player.getColor();
    }

    /**
     * Strips production entries for players the viewer can't identify - unlike control
     * tokens/units, there's no meaningful "unidentified" placeholder for this number, so
     * unidentified entries are removed outright rather than obscured.
     */
    public static void redactProduction(Map<String, WebTileUnitData> tileUnitData, Game game, Player viewer) {
        for (WebTileUnitData tileData : tileUnitData.values()) {
            tileData.production.keySet().removeIf(color -> !canSeeStatsByColor(game, viewer, color));
        }
    }

    private static boolean canSeeStatsByColor(Game game, Player viewer, String color) {
        Player player = game.getPlayerFromColorOrFaction(color);
        return player == null || FoWHelper.canSeeStatsOfPlayer(game, player, viewer);
    }

    /**
     * Redacts unit/token faction identity in place for players the viewer can't identify, the same
     * way {@link #redactControlIdentities} does for control tokens/CC: the real faction key is
     * replaced with "fow:&lt;color&gt;" so the frontend can still render the correct unit color
     * without exposing which faction it belongs to (which would otherwise also leak faction-specific
     * unit upgrades/tech via the unit tooltip).
     */
    public static void redactUnitIdentities(Map<String, WebTileUnitData> tileUnitData, Game game, Player viewer) {
        for (WebTileUnitData tileData : tileUnitData.values()) {
            tileData.space = obscureEntityFactionKeys(tileData.space, game, viewer);
            for (WebTilePlanet planet : tileData.planets.values()) {
                planet.setEntities(obscureEntityFactionKeys(planet.getEntities(), game, viewer));
            }
        }
    }

    private static Map<String, List<WebEntityData>> obscureEntityFactionKeys(
            Map<String, List<WebEntityData>> entities, Game game, Player viewer) {
        Map<String, List<WebEntityData>> result = new HashMap<>();
        for (Map.Entry<String, List<WebEntityData>> entry : entities.entrySet()) {
            String obscured = obscureIfHidden(game, viewer, entry.getKey());
            result.computeIfAbsent(obscured, _ -> new ArrayList<>()).addAll(entry.getValue());
        }
        return result;
    }

    public static Map<String, WebTileUnitData> fromTileMap(Game game, Map<String, Tile> tileMap) {
        Map<String, WebTileUnitData> tileUnitData = new HashMap<>();

        for (Map.Entry<String, Tile> entry : tileMap.entrySet()) {
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
        if (!specialTileData.planets.isEmpty()) {
            tileUnitData.put(Constants.SPECIAL, specialTileData);
        }

        return tileUnitData;
    }

    private static WebTileUnitData extractTileUnitData(Game game, Tile tile) {
        WebTileUnitData tileData = new WebTileUnitData();

        // Set anomaly status
        tileData.isAnomaly = tile.isAnomaly(game, null);

        // Hyperlane connection matrix, if applicable
        tileData.hyperlaneMatrix = CustomHyperlaneService.getHyperlaneDataForTile(tile, game);

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
                    WebTilePlanet planetData = tileData.planets.computeIfAbsent(holderName, _ -> new WebTilePlanet());

                    // Extract units, tokens, and action cards
                    extractUnits(game, unitHolder, planetData.getEntities());
                    extractTokens(unitHolder, planetData.getEntities(), false);
                    extractActionCards(game, planet, planetData.getEntities());

                    // Determine controller
                    String controllingFaction;
                    if (planet.isSpaceStation()) {
                        controllingFaction = getSpaceStationController(game, planet);
                    } else {
                        controllingFaction = getPlanetController(game, planet);
                    }
                    planetData.setControlledBy(controllingFaction);

                    // Exhausted status is visible to everyone with vision of this tile regardless
                    // of faction identification (it doesn't reveal who controls the planet, only
                    // that a visible planet is exhausted), so it's set here unconditionally rather
                    // than sourced from the viewer-filtered per-player WebPlayerArea data.
                    Player controllingPlayer = game.getPlayerFromColorOrFaction(controllingFaction);
                    planetData.setExhausted(controllingPlayer != null
                            && controllingPlayer.getExhaustedPlanets().contains(holderName));

                    // Update metadata (commodities, shields)
                    updatePlanetMetadata(game, planet, planetData);
                }
            }
        }

        // Calculate production for each player
        for (Player player : game.getRealPlayers()) {
            String color = player.getColor();

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
            tileData.pds = pdsCoverage;
        }

        return tileData;
    }

    /**
     * Handles setting commodities (Discordant Stars "CommsOnPlanet"), planetary shields, and resources/influence.
     */
    private static void updatePlanetMetadata(Game game, Planet planet, WebTilePlanet planetData) {
        // Set resources and influence
        planetData.setResources(planet.getResources());
        planetData.setInfluence(planet.getInfluence());

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
            String unitId = getUnitIdFromType(unitKey.unitType());

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
            factionEntities.computeIfAbsent(faction, _ -> new ArrayList<>()).add(entityData);
        }

        if (!factionEntities.isEmpty()) {
            // Add units to target entities
            for (Map.Entry<String, List<WebEntityData>> factionEntry : factionEntities.entrySet()) {
                targetEntities
                        .computeIfAbsent(factionEntry.getKey(), _ -> new ArrayList<>())
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
            factionTokens.computeIfAbsent("neutral", _ -> new ArrayList<>()).add(tokenData);
        }

        // Merge token data with existing data
        for (Map.Entry<String, List<WebEntityData>> factionEntry : factionTokens.entrySet()) {
            targetEntities
                    .computeIfAbsent(factionEntry.getKey(), _ -> new ArrayList<>())
                    .addAll(factionEntry.getValue());
        }
    }

    /**
     * Extract action cards from a planet (Garbozia feature) and add them to the target entities map.
     *
     * @param game The game context
     * @param planet The planet to extract action cards from
     * @param targetEntities The map to add the extracted entities to
     */
    private static void extractActionCards(Game game, Planet planet, Map<String, List<WebEntityData>> targetEntities) {
        if (!"garbozia".equals(planet.getName())) {
            return;
        }

        for (String cardId : ActionCardHelper.getGarboziaActionCards(game).keySet()) {
            WebEntityData cardData = new WebEntityData(cardId, "actioncard", 1);
            targetEntities.computeIfAbsent("neutral", _ -> new ArrayList<>()).add(cardData);
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
                "triad",
                "grove",
                "aurelionstation");

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

            // Set resources and influence
            planetData.setResources(planet.getResources());
            planetData.setInfluence(planet.getInfluence());

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
            case Celagrom -> "celagrom";
            case Aurelion -> "aurelion";
            case Cavalry -> "cavalry";
            case StarfallPds -> "starfallpds";
            default -> null;
        };
    }
}
