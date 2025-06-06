package ti4.helpers;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@Data
public class WebTileUnitData {
    private Map<String, Map<String, Integer>> space;
    private Map<String, Map<String, Map<String, Integer>>> planets;

    public WebTileUnitData() {
        this.space = new HashMap<>();
        this.planets = new HashMap<>();
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

        for (Map.Entry<String, UnitHolder> holderEntry : tile.getUnitHolders().entrySet()) {
            String holderName = holderEntry.getKey();
            UnitHolder unitHolder = holderEntry.getValue();

            if (!unitHolder.hasUnits()) {
                continue; // Skip holders with no units
            }

            boolean isSpace = Constants.SPACE.equals(holderName);

            // Group units by faction
            Map<String, Map<String, Integer>> factionUnits = new HashMap<>();

            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
                if (player == null) {
                    continue; // Skip units without valid players
                }

                String faction = player.getFaction();
                int unitCount = unitHolder.getUnitCount(unitKey);

                if (unitCount <= 0) {
                    continue; // Skip units with zero count
                }

                String unitId = getUnitIdFromType(unitKey.getUnitType());
                if (unitId == null) {
                    continue; // Skip unknown unit types
                }

                factionUnits
                    .computeIfAbsent(faction, k -> new HashMap<>())
                    .put(unitId, unitCount);
            }

            if (!factionUnits.isEmpty()) {
                if (isSpace) {
                    // For space, merge all factions directly into the space map
                    for (Map.Entry<String, Map<String, Integer>> factionEntry : factionUnits.entrySet()) {
                        tileData.space.put(factionEntry.getKey(), factionEntry.getValue());
                    }
                } else {
                    // For planets, organize by planet name
                    tileData.planets.put(holderName, factionUnits);
                }
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