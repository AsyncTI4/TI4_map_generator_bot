package ti4.spring.api.webdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameSnapshots;
import ti4.helpers.Constants;
import ti4.json.JsonMapperManager;
import ti4.service.fow.FogMemoryService;
import ti4.service.fow.FogViewer;
import ti4.spring.security.FogGameAccess;
import ti4.website.model.WebTilePositions;
import ti4.website.model.WebTileUnitData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FogWebDataService {

    private static final JsonMapper MAPPER = JsonMapperManager.basic();
    private static final Set<String> PLANET_ID_FIELDS =
            Set.of("planets", "exhaustedPlanets", "exhaustedPlanetAbilities", "tilePositions");
    private static final Set<String> HIDDEN_PER_FACTION_FIELDS =
            Set.of("factionProgress", "scoreBreakdowns", "statTilePositions");
    private static final Set<String> KEPT_IDENTITY_FIELDS = Set.of("color", "colorDisplayName");

    public String forSeat(Game liveGame, String faction) {
        return serialize(foggedWebData(liveGame, faction, playerFogView(faction)));
    }

    public Optional<String> forGameMaster(Game liveGame, @Nullable String viewAs) {
        Map<String, Object> fogView = gameMasterFogView(liveGame, viewAs);
        if (StringUtils.isBlank(viewAs)) {
            Map<String, Object> webData = GameWebDataService.buildWebData(liveGame);
            webData.put("fogView", fogView);
            return Optional.of(serialize(webData));
        }
        if (seatOf(liveGame, viewAs) == null) {
            return Optional.empty();
        }
        return Optional.of(serialize(foggedWebData(liveGame, viewAs, fogView)));
    }

    static ObjectNode foggedWebData(Game liveGame, String faction, Map<String, Object> fogView) {
        Game game = GameSnapshots.copy(liveGame);
        Player viewer = seatOf(game, faction);
        FogViewer fogViewer = new FogViewer(game, true, viewer);
        FogMemoryService.refresh(game, fogViewer);
        Set<String> visibleSystems = fogViewer.visibleSystems();
        Map<String, String> anonymousIds = anonymousIdsOfUnknownPlayers(game, fogViewer, viewer);

        Map<String, Object> webData = GameWebDataService.buildWebData(game);
        Map<String, String> fogTiles = replaceUnseenSystemsWithMemory(game, viewer, visibleSystems);
        Map<String, WebTileUnitData> tileUnitData = WebTileUnitData.fromGame(game);
        tileUnitData.remove(Constants.SPECIAL);
        webData.put("tilePositions", WebTilePositions.fromGame(game).getTilePositions());
        webData.put("tileUnitData", tileUnitData);
        webData.put("fogTiles", fogTiles);

        ObjectNode tree = MAPPER.valueToTree(webData);
        forgetActivityInUnseenSystems(tree, visibleSystems);
        forgetRememberedTileContents(tree, fogTiles.keySet());
        anonymize(tree, anonymousIds);
        tree.set("fogView", MAPPER.valueToTree(fogView));
        return tree;
    }

    @Nullable
    static Player seatOf(Game game, @Nullable String faction) {
        if (StringUtils.isBlank(faction)) return null;
        return game.getRealPlayers().stream()
                .filter(FogGameAccess::holdsSeat)
                .filter(player -> faction.equals(player.getFaction()))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> anonymousIdsOfUnknownPlayers(Game game, FogViewer fogViewer, Player viewer) {
        Map<String, String> anonymousIds = new HashMap<>();
        for (Player player : game.getRealPlayersNNeutral()) {
            if (player == viewer || player.isNeutral() || fogViewer.canSeeStats(player)) continue;
            anonymousIds.put(player.getFaction(), player.getColor());
        }
        return anonymousIds;
    }

    private static Map<String, String> replaceUnseenSystemsWithMemory(
            Game game, Player viewer, Set<String> visibleSystems) {
        Map<String, Tile> tiles = new HashMap<>(game.getTileMap());
        Map<String, String> fogTiles = new LinkedHashMap<>();
        for (String position : game.getTileMap().keySet()) {
            if (visibleSystems.contains(position)) continue;
            tiles.remove(position);
            Tile memory = viewer.buildFogTile(position, viewer);
            if (memory == null) continue;
            tiles.put(position, memory);
            fogTiles.put(position, StringUtils.defaultString(memory.getFogLabel(viewer)));
        }
        game.setTileMap(tiles);
        return fogTiles;
    }

    static void forgetActivityInUnseenSystems(ObjectNode tree, Set<String> visibleSystems) {
        JsonNode gameState = tree.get("gameState");
        if (!(gameState instanceof ObjectNode state)) return;
        if (isUnseen(state.get("activeSystem"), visibleSystems)) {
            state.putNull("activeSystem");
        }
        JsonNode combat = state.get("activeCombat");
        if (combat != null && combat.isObject() && isUnseen(combat.get("system"), visibleSystems)) {
            state.putNull("activeCombat");
        }
    }

    private static boolean isUnseen(@Nullable JsonNode position, Set<String> visibleSystems) {
        return position != null && position.isTextual() && !visibleSystems.contains(position.asText());
    }

    static void forgetRememberedTileContents(ObjectNode tree, Set<String> rememberedPositions) {
        JsonNode tileUnitData = tree.get("tileUnitData");
        if (!(tileUnitData instanceof ObjectNode tiles)) return;
        for (String position : rememberedPositions) {
            if (!(tiles.get(position) instanceof ObjectNode tile)) continue;
            tile.putObject("space");
            tile.putArray("ccs");
            tile.putObject("production");
            tile.putNull("pds");
            if (tile.get("planets") instanceof ObjectNode planets) {
                for (Map.Entry<String, JsonNode> planet : planets.properties()) {
                    if (planet.getValue() instanceof ObjectNode planetNode) {
                        planetNode.putNull("controlledBy");
                        planetNode.putObject("entities");
                        planetNode.putNull("commodities");
                        planetNode.put("planetaryShield", false);
                        planetNode.put("exhausted", false);
                    }
                }
            }
        }
    }

    static void anonymize(ObjectNode tree, Map<String, String> anonymousIds) {
        if (anonymousIds.isEmpty()) return;
        if (tree.get("playerData") instanceof ArrayNode players) {
            for (JsonNode player : players) {
                if (player instanceof ObjectNode area && anonymousIds.containsKey(textOf(area.get("faction")))) {
                    blankPlayerArea(area, anonymousIds.get(textOf(area.get("faction"))));
                }
            }
        }
        renameFactions(tree, null, anonymousIds);
    }

    private static void blankPlayerArea(ObjectNode area, String anonymousId) {
        for (Map.Entry<String, JsonNode> field : new ArrayList<>(area.properties())) {
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (KEPT_IDENTITY_FIELDS.contains(name)) continue;
            if (value.isArray()) area.putArray(name);
            else if (value.isObject()) area.putObject(name);
            else if (value.isNumber()) area.put(name, 0);
            else if (value.isBoolean()) area.put(name, false);
            else if (value.isTextual()) area.put(name, "");
        }
        String colorName = StringUtils.defaultIfBlank(textOf(area.get("colorDisplayName")), anonymousId);
        area.put("faction", anonymousId);
        area.put("color", anonymousId);
        area.put("displayName", colorName);
        area.put("userName", colorName);
        area.put("fogHidden", true);
    }

    private static void renameFactions(JsonNode node, @Nullable String fieldName, Map<String, String> anonymousIds) {
        if (node instanceof ObjectNode object) {
            renameFactionsInObject(object, fieldName, anonymousIds);
        } else if (node instanceof ArrayNode array) {
            renameFactionsInArray(array, fieldName, anonymousIds);
        }
    }

    private static void renameFactionsInObject(
            ObjectNode object, @Nullable String fieldName, Map<String, String> anonymousIds) {
        boolean keysArePlanets = fieldName != null && PLANET_ID_FIELDS.contains(fieldName);
        boolean dropUnknownFactions = fieldName != null && HIDDEN_PER_FACTION_FIELDS.contains(fieldName);
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>(object.properties());
        object.removeAll();
        for (Map.Entry<String, JsonNode> field : fields) {
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (dropUnknownFactions && anonymousIds.containsKey(name)) continue;
            String renamed = keysArePlanets ? name : anonymousIds.getOrDefault(name, name);
            if (value.isTextual() && !PLANET_ID_FIELDS.contains(name)) {
                object.put(renamed, anonymousIds.getOrDefault(value.asText(), value.asText()));
                continue;
            }
            renameFactions(value, name, anonymousIds);
            object.set(renamed, value);
        }
    }

    private static void renameFactionsInArray(
            ArrayNode array, @Nullable String fieldName, Map<String, String> anonymousIds) {
        boolean valuesArePlanets = fieldName != null && PLANET_ID_FIELDS.contains(fieldName);
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        array.removeAll();
        for (JsonNode item : items) {
            if (item.isTextual() && !valuesArePlanets) {
                array.add(anonymousIds.getOrDefault(item.asText(), item.asText()));
                continue;
            }
            renameFactions(item, fieldName, anonymousIds);
            array.add(item);
        }
    }

    @Nullable
    private static String textOf(@Nullable JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static Map<String, Object> playerFogView(String faction) {
        Map<String, Object> fogView = new LinkedHashMap<>();
        fogView.put("role", "player");
        fogView.put("viewingAs", faction);
        return fogView;
    }

    private static Map<String, Object> gameMasterFogView(Game game, @Nullable String viewAs) {
        List<Map<String, String>> seats = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (!FogGameAccess.holdsSeat(player)) continue;
            Map<String, String> seat = new LinkedHashMap<>();
            seat.put("faction", player.getFaction());
            seat.put("color", player.getColor());
            seat.put("userName", player.getUserName());
            seats.add(seat);
        }
        Map<String, Object> fogView = new LinkedHashMap<>();
        fogView.put("role", "gm");
        fogView.put("viewingAs", StringUtils.isBlank(viewAs) ? null : viewAs);
        fogView.put("seats", seats);
        return fogView;
    }

    private static String serialize(Object webData) {
        return MAPPER.writeValueAsString(webData);
    }
}
