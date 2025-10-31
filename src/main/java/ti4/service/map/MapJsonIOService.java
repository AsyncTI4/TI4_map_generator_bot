package ti4.service.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.URLReaderHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.service.fow.LoreService;

@UtilityClass
public class MapJsonIOService {
    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    ;

    @ModalHandler("importMapFromJSON")
    public void importMapFromJSON(ModalInteractionEvent event, Game game) {
        String url = event.getValue("url").getAsString();
        String jsonString = URLReaderHelper.readFromURL(url, event.getChannel());
        if (jsonString == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Failed to read JSON from URL.");
            return;
        }
        importMapFromJson(game, jsonString, event.getChannel());
    }

    public static String exportMapAsJson(
            GenericInteractionCreateEvent event,
            Game game,
            boolean includeTokens,
            boolean includeAttachments,
            boolean includeLore) {
        try {
            MapDataIO mapData = new MapDataIO();
            List<TileIO> tiles = new ArrayList<>();
            Map<String, String[]> savedLoreMap = LoreService.getSavedLore(game);

            for (Tile tile : game.getTileMap().values()) {
                TileIO t = new TileIO();
                t.setPosition(tile.getPosition());
                t.setTileID(tile.getTileID());

                // tokens
                if (includeTokens
                        && tile.getSpaceUnitHolder() != null
                        && !tile.getSpaceUnitHolder().getTokenList().isEmpty()) {
                    t.setTokens(new ArrayList<>(tile.getSpaceUnitHolder().getTokenList()));
                }

                // custom hyperlane string if present
                String custom = game.getCustomHyperlaneData().get(tile.getPosition());
                if (custom != null) {
                    t.setCustomHyperlaneString(custom);
                }

                // border anomalies
                List<BorderAnomalyIO> baList = new ArrayList<>();
                for (BorderAnomalyHolder bah : game.getBorderAnomalies()) {
                    if (bah.getTile().equals(tile.getPosition())) {
                        BorderAnomalyIO bi = new BorderAnomalyIO();
                        bi.setDirection(bah.getDirection());
                        bi.setType(bah.getType().toString());
                        baList.add(bi);
                    }
                }
                if (!baList.isEmpty()) {
                    t.setBorderAnomalies(baList);
                }

                // planets
                List<PlanetIO> pis = new ArrayList<>();
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    boolean planetHasExportedData = false;
                    PlanetIO pi = new PlanetIO();
                    pi.setPlanetID(planet.getName());
                    if (includeAttachments
                            && planet.getAttachments() != null
                            && !planet.getAttachments().isEmpty()) {
                        pi.setAttachments(new ArrayList<>(planet.getAttachments()));
                        planetHasExportedData = true;
                    }
                    String[] loreData = savedLoreMap.get(planet.getName());
                    if (includeLore && loreData != null) {
                        pi.setPlanetLore(Arrays.asList(loreData[0], loreData[1]));
                        planetHasExportedData = true;
                    }
                    if (planetHasExportedData) {
                        pis.add(pi);
                    }
                }
                if (!pis.isEmpty()) {
                    t.setPlanets(pis);
                }

                // system lore
                String[] loreData = savedLoreMap.get(tile.getPosition());
                if (includeLore && loreData != null) {
                    t.setSystemLore(Arrays.asList(loreData[0], loreData[1]));
                }

                // custom adjacencies / overrides
                if (game.getCustomAdjacentTiles().containsKey(tile.getPosition())) {
                    t.setCustomAdjacencies(
                            new ArrayList<>(game.getCustomAdjacentTiles().get(tile.getPosition())));
                }
                List<AdjacencyOverrideIO> overrides = new ArrayList<>();
                for (Pair<String, Integer> adjPair :
                        game.getAdjacentTileOverrides().keySet()) {
                    if (adjPair.getLeft().equals(tile.getPosition())) {
                        AdjacencyOverrideIO ai = new AdjacencyOverrideIO();
                        ai.setDirection(adjPair.getRight());
                        ai.setSecondary(game.getAdjacentTileOverrides().get(adjPair));
                        overrides.add(ai);
                    }
                }
                if (!overrides.isEmpty()) {
                    t.setAdjacencyOverrides(overrides);
                }

                tiles.add(t);
            }

            mapData.setMapInfo(tiles);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapData);
        } catch (Exception e) {
            BotLogger.error("Failed to export map to JSON", e);
            return null;
        }
    }

    public static void importMapFromJson(Game game, String jsonString, MessageChannel feedbackChannel) {
        StringBuilder errorSb = new StringBuilder();
        try {
            MapDataIO mapData = mapper.readValue(jsonString, MapDataIO.class);

            game.removeAllTiles();
            game.clearAdjacentTileOverrides();
            game.clearCustomAdjacentTiles();
            game.setBorderAnomalies(Collections.emptyList());
            game.setCustomHyperlaneData(Collections.emptyMap());
            LoreService.clearLore(game);

            for (TileIO tileIO : mapData.getMapInfo()) {
                if (handleTile(tileIO, game, errorSb)) {
                    handleTokens(tileIO, game, errorSb);
                    handleCustomHyperlane(tileIO, game, errorSb);
                    handleBorderAnomalies(tileIO, game, errorSb);
                    handleSystemLore(tileIO, game, errorSb);
                    handlePlanetAttachments(tileIO, game, errorSb);
                    handlePlanetLore(tileIO, game, errorSb);
                    handleAdjacencyOverrides(tileIO, game, errorSb);
                    handleCustomAdjacencies(tileIO, game, errorSb);
                }
            }

            MessageHelper.sendMessageToChannel(feedbackChannel, "Map imported from JSON.");
        } catch (Exception e) {
            BotLogger.error("Failed to import map from JSON", e);
            MessageHelper.sendMessageToChannel(feedbackChannel, "Failed to import map from JSON: " + e.getMessage());
        }

        if (errorSb.length() > 0) {
            MessageHelper.sendMessageToChannel(feedbackChannel, "Some tiles failed to import:\n" + errorSb.toString());
        }
    }

    private static boolean handleTile(TileIO tileIO, Game game, StringBuilder sb) {
        if (!PositionMapper.isTilePositionValid(tileIO.getPosition())) {
            appendError(sb, tileIO, "Invalid tile position: " + tileIO.getPosition());
            return false;
        }

        String tileID = AliasHandler.resolveTile(tileIO.getTileID());

        String tileName = Mapper.getTileID(tileID);
        if (tileName == null) {
            appendError(sb, tileIO, "Could not find tile: " + tileID);
            return false;
        }
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            appendError(sb, tileIO, "Could not find tile: " + tileID);
            return false;
        }

        Tile tile = new Tile(tileID, tileIO.getPosition());
        AddTileService.addTile(game, tile);

        return true;
    }

    private static void handleTokens(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getTokens() == null) return;

        for (String token : tileIO.getTokens()) {
            Tile tile = game.getTileByPosition(tileIO.getPosition());
            String tokenFileName = token;
            String tokenPath = tile.getTokenPath(tokenFileName);
            if (tokenPath == null) {
                tokenFileName = Mapper.getTokenID(token);
                tokenPath = tile.getTokenPath(tokenFileName);
                if (tokenPath == null) {
                    appendError(sb, tileIO, "Token not found: " + token);
                    continue;
                }
            }
            tile.getSpaceUnitHolder().addToken(tokenFileName);
        }
    }

    private static void handleCustomHyperlane(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getCustomHyperlaneString() == null) return;

        String customHypeString = tileIO.getCustomHyperlaneString();
        if (!StringUtils.isBlank(customHypeString)) {
            if (!CustomHyperlaneService.isValidConnectionMatrix(customHypeString)) {
                appendError(sb, tileIO, "Hyperlane data `" + customHypeString + "` is invalid.");
                return;
            }
            CustomHyperlaneService.insertData(game, tileIO.getPosition(), customHypeString);
        }
    }

    private static void handleBorderAnomalies(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getBorderAnomalies() == null) return;

        for (BorderAnomalyIO anomalyIO : tileIO.getBorderAnomalies()) {
            BorderAnomalyModel.BorderAnomalyType anomalyType = null;
            try {
                anomalyType = BorderAnomalyModel.BorderAnomalyType.valueOf(
                        anomalyIO.getType().toUpperCase());
            } catch (Exception e) {
                anomalyType = new BorderAnomalyModel().getBorderAnomalyTypeFromString(anomalyIO.getType());
            }

            if (anomalyType == null) {
                appendError(
                        sb,
                        tileIO,
                        "Invalid border anomaly: type=" + anomalyIO.getType() + ", direction="
                                + anomalyIO.getDirection());
                continue;
            }
            game.addBorderAnomaly(tileIO.getPosition(), anomalyIO.getDirection(), anomalyType);
        }
    }

    private static void handleSystemLore(TileIO tileIO, Game game, StringBuilder sb) {
        List<String> systemLore = tileIO.getSystemLore();
        if (systemLore != null && !systemLore.isEmpty()) {
            LoreService.addLore(tileIO.getPosition(), systemLore.get(0), systemLore.get(1), game);
        }
    }

    private static void handlePlanetAttachments(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getPlanets() == null) return;

        for (PlanetIO planetIO : tileIO.getPlanets()) {
            if (planetIO.getAttachments() == null) continue;

            for (String attachment : planetIO.getAttachments()) {
                String attachmentFileName = attachment;
                Tile tile = game.getTileByPosition(tileIO.getPosition());
                String attachmentPath = tile.getAttachmentPath(attachmentFileName);
                if (attachmentPath == null) {
                    attachmentFileName = Mapper.getAttachmentImagePath(attachment);
                    attachmentPath = tile.getAttachmentPath(attachmentFileName);
                    if (attachmentPath == null) {
                        appendError(sb, tileIO, "Attachment not found: " + attachment);
                        continue;
                    }
                }
                UnitHolder planetHolder = tile.getUnitHolderFromPlanet(planetIO.getPlanetID());
                if (planetHolder == null) {
                    appendError(sb, tileIO, "Planet unitHolder not found: " + planetIO.getPlanetID());
                    continue;
                }
                planetHolder.addToken(attachmentFileName);
            }
        }
    }

    private static void handlePlanetLore(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getPlanets() == null) return;

        for (PlanetIO planetIO : tileIO.getPlanets()) {
            List<String> planetLore = planetIO.getPlanetLore();
            if (planetLore != null && !planetLore.isEmpty()) {
                LoreService.addLore(planetIO.getPlanetID(), planetLore.get(0), planetLore.get(1), game);
            }
        }
    }

    private static void handleAdjacencyOverrides(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getAdjacencyOverrides() == null) return;

        for (AdjacencyOverrideIO adjacencyOverride : tileIO.getAdjacencyOverrides()) {
            game.addAdjacentTileOverride(
                    tileIO.getPosition(), adjacencyOverride.getDirection(), adjacencyOverride.getSecondary());
        }
    }

    private static void handleCustomAdjacencies(TileIO tileIO, Game game, StringBuilder sb) {
        if (tileIO.getCustomAdjacencies() == null) return;

        String primary = tileIO.getPosition();
        for (String secondary : tileIO.getCustomAdjacencies()) {

            List<String> customAdjacencies =
                    new ArrayList<>(game.getCustomAdjacentTiles().getOrDefault(primary, Collections.emptyList()));
            if (customAdjacencies.contains(secondary)) {
                continue;
            }

            customAdjacencies.add(secondary);
            game.addCustomAdjacentTiles(primary, customAdjacencies);
        }
    }

    private static void appendError(StringBuilder sb, TileIO tileIO, String message) {
        sb.append("- Error in tile ")
                .append(tileIO.getTileID())
                .append(" at position ")
                .append(tileIO.getPosition())
                .append(": ")
                .append(message)
                .append("\n");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MapDataIO {
        private List<TileIO> mapInfo;

        public List<TileIO> getMapInfo() {
            return mapInfo;
        }

        public void setMapInfo(List<TileIO> mapInfo) {
            this.mapInfo = mapInfo;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TileIO {
        private String position;
        private String tileID;
        private List<PlanetIO> planets;
        private List<String> tokens;
        private String customHyperlaneString;
        private List<BorderAnomalyIO> borderAnomalies;
        private List<String> systemLore;
        private List<String> customAdjacencies;
        private List<AdjacencyOverrideIO> adjacencyOverrides;

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position.toLowerCase();
        }

        public String getTileID() {
            return tileID;
        }

        public void setTileID(String tileID) {
            this.tileID = tileID.toLowerCase();
        }

        public List<PlanetIO> getPlanets() {
            return planets;
        }

        public void setPlanets(List<PlanetIO> planets) {
            this.planets = planets;
        }

        public List<String> getTokens() {
            return tokens;
        }

        public void setTokens(List<String> tokens) {
            this.tokens = tokens;
        }

        public String getCustomHyperlaneString() {
            return customHyperlaneString;
        }

        public void setCustomHyperlaneString(String customHyperlaneString) {
            this.customHyperlaneString = customHyperlaneString;
        }

        public List<BorderAnomalyIO> getBorderAnomalies() {
            return borderAnomalies;
        }

        public void setBorderAnomalies(List<BorderAnomalyIO> borderAnomalies) {
            this.borderAnomalies = borderAnomalies;
        }

        public List<String> getSystemLore() {
            return systemLore;
        }

        public void setSystemLore(List<String> systemLore) {
            this.systemLore = systemLore;
        }

        public List<String> getCustomAdjacencies() {
            return customAdjacencies;
        }

        public void setCustomAdjacencies(List<String> customAdjacencies) {
            this.customAdjacencies = customAdjacencies;
        }

        public List<AdjacencyOverrideIO> getAdjacencyOverrides() {
            return adjacencyOverrides;
        }

        public void setAdjacencyOverrides(List<AdjacencyOverrideIO> adjacencyOverrides) {
            this.adjacencyOverrides = adjacencyOverrides;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanetIO {
        private String planetID;
        private List<String> attachments;
        private List<String> planetLore;

        public String getPlanetID() {
            return planetID;
        }

        public void setPlanetID(String planetID) {
            this.planetID = planetID;
        }

        public List<String> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<String> attachments) {
            this.attachments = attachments;
        }

        public List<String> getPlanetLore() {
            return planetLore;
        }

        public void setPlanetLore(List<String> planetLore) {
            this.planetLore = planetLore;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BorderAnomalyIO {
        private Integer direction;
        private String type;

        public Integer getDirection() {
            return direction;
        }

        public void setDirection(Integer direction) {
            this.direction = direction;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdjacencyOverrideIO {
        private String secondary;
        private Integer direction;

        public String getSecondary() {
            return secondary;
        }

        public void setSecondary(String secondary) {
            this.secondary = secondary;
        }

        public Integer getDirection() {
            return direction;
        }

        public void setDirection(Integer direction) {
            this.direction = direction;
        }
    }
}
