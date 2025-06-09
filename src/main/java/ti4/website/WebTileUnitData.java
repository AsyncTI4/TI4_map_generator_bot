package ti4.website;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.DrawingUtil;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@Data
public class WebTileUnitData {
    private Map<String, List<WebEntityData>> space;
    private Map<String, WebTilePlanet> planets;
    private List<String> ccs;

    public WebTileUnitData() {
        this.space = new HashMap<>();
        this.planets = new HashMap<>();
        this.ccs = new ArrayList<>();
    }

    public static Map<String, WebTileUnitData> fromGame(Game game) {
        Map<String, WebTileUnitData> tileUnitData = new HashMap<>();

        for (Map.Entry<String, Tile> entry : game.getTileMap().entrySet()) {
            String position = entry.getKey();
            Tile tile = entry.getValue();

            if (tile != null && tile.getTileID() != null && !"-1".equals(tile.getTileID()) && !"null".equals(tile.getTileID())) {
                WebTileUnitData unitData = extractTileUnitData(tile, game);
                tileUnitData.put(position, unitData);
            }
        }

        return tileUnitData;
    }

    private static WebTileUnitData extractTileUnitData(Tile tile, Game game) {
        WebTileUnitData tileData = new WebTileUnitData();

        // Extract command tokens from space
        UnitHolder spaceHolder = tile.getUnitHolders().get(Constants.SPACE);
        if (spaceHolder != null) {
            for (String ccID : spaceHolder.getCcList()) {
                Player player = DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), ccID);
                if (player != null) {
                    tileData.ccs.add(player.getFaction());
                }
            }
        }

        for (Map.Entry<String, UnitHolder> holderEntry : tile.getUnitHolders().entrySet()) {
            String holderName = holderEntry.getKey();
            boolean isSpace = Constants.SPACE.equals(holderName);
            UnitHolder unitHolder = holderEntry.getValue();

            // Extract unit data
            if (unitHolder.hasUnits()) {
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

                    WebEntityData entityData = new WebEntityData(unitId, "unit", unitCount, sustainedDamage);
                    factionEntities.computeIfAbsent(faction, k -> new ArrayList<>()).add(entityData);
                }

                if (!factionEntities.isEmpty()) {
                    if (isSpace) {
                        // For space, merge all factions directly into the space map
                        for (Map.Entry<String, List<WebEntityData>> factionEntry : factionEntities.entrySet()) {
                            tileData.space.put(factionEntry.getKey(), factionEntry.getValue());
                        }
                    } else {
                        // For planets, create or get existing WebTilePlanet
                        WebTilePlanet planetData = tileData.planets.computeIfAbsent(holderName, k -> new WebTilePlanet());
                        for (Map.Entry<String, List<WebEntityData>> factionEntry : factionEntities.entrySet()) {
                            planetData.getEntities().put(factionEntry.getKey(), factionEntry.getValue());
                        }
                    }
                }
            }

            // Extract token data
            List<String> holderTokens = new ArrayList<>(unitHolder.getTokenList());
            // Remove null entries that might exist in token lists
            holderTokens.removeIf(token -> token == null || token.trim().isEmpty());

            if (!holderTokens.isEmpty()) {
                // Group tokens by faction and add them to the entity data
                Map<String, List<WebEntityData>> factionTokens = new HashMap<>();

                for (String token : holderTokens) {
                    // Check if this token is an attachment
                    String entityType = "token"; // default to token
                    if (ti4.image.Mapper.getAttachmentInfo(token) != null) {
                        entityType = "attachment";
                    }

                    // For now, we'll treat all tokens as non-faction specific
                    // If tokens become faction-specific in the future, we can update this logic
                    WebEntityData tokenData = new WebEntityData(token, entityType, 1);
                    factionTokens.computeIfAbsent("neutral", k -> new ArrayList<>()).add(tokenData);
                }

                if (isSpace) {
                    // Merge token data with existing space data
                    for (Map.Entry<String, List<WebEntityData>> factionEntry : factionTokens.entrySet()) {
                        tileData.space.computeIfAbsent(factionEntry.getKey(), k -> new ArrayList<>())
                                .addAll(factionEntry.getValue());
                    }
                } else {
                    // Merge token data with existing planet data
                    WebTilePlanet planetData = tileData.planets.computeIfAbsent(holderName, k -> new WebTilePlanet());

                    for (Map.Entry<String, List<WebEntityData>> factionEntry : factionTokens.entrySet()) {
                        planetData.getEntities().computeIfAbsent(factionEntry.getKey(), k -> new ArrayList<>())
                                .addAll(factionEntry.getValue());
                    }
                }
            }
        }

        // Determine planet control after processing all units and tokens
        for (Map.Entry<String, UnitHolder> holderEntry : tile.getUnitHolders().entrySet()) {
            String holderName = holderEntry.getKey();
            boolean isSpace = Constants.SPACE.equals(holderName);
            UnitHolder unitHolder = holderEntry.getValue();

            if (!isSpace && tileData.planets.containsKey(holderName)) {
                WebTilePlanet planetData = tileData.planets.get(holderName);

                // Determine controlling player from control tokens
                String controllingFaction = null;
                if (!unitHolder.getControlList().isEmpty()) {
                    // Get the first control token (there should only be one)
                    String controlToken = unitHolder.getControlList().iterator().next();
                    Player controllingPlayer = DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlToken);
                    if (controllingPlayer != null) {
                        controllingFaction = controllingPlayer.getFaction();
                    }
                }

                planetData.setControlledBy(controllingFaction);
            }
        }

        return tileData;
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