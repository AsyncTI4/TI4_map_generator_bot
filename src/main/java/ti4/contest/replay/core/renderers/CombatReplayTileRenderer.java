package ti4.contest.replay.core.renderers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.json.JsonMapperManager;
import ti4.json.UnitKeyMapKeyDeserializer;
import ti4.json.UnitKeyMapKeySerializer;
import ti4.model.BorderAnomalyHolder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Renders contest tile images from a stable combat context plus a per-event unit-state snapshot.
 */
@UtilityClass
public class CombatReplayTileRenderer {

    private static final JsonMapper TILE_PAYLOAD_MAPPER = JsonMapperManager.basic()
            .rebuild()
            .addModule(new SimpleModule()
                    .addKeySerializer(UnitKey.class, new UnitKeyMapKeySerializer())
                    .addKeyDeserializer(UnitKey.class, new UnitKeyMapKeyDeserializer()))
            .build();

    public String captureInitialSnapshot(Game game, String tilePosition) {
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return null;

        CombatReplayTilePayload payload = new CombatReplayTilePayload();
        CombatReplayTileContext context = new CombatReplayTileContext();
        context.setTilePosition(tilePosition);
        context.setTileTemplate(cloneTileWithoutUnits(tile));
        context.setPlayers(capturePlayers(game, tile));
        captureGameContext(game, context);
        payload.setContext(context);
        payload.setUnitState(captureUnitState(tilePosition, tile));
        return write(payload);
    }

    public String captureUnitStateSnapshot(Game game, String tilePosition) {
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return null;
        return write(captureUnitState(tilePosition, tile));
    }

    private boolean isInitialSnapshot(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return false;
        return readPayload(payloadJson).getContext() != null;
    }

    public Game render(String initialContextJson, String unitStateJson) {
        return render(initialContextJson, unitStateJson, null);
    }

    public Game render(String initialContextJson, String unitStateJson, @Nullable Game sourceGame) {
        if (!isInitialSnapshot(initialContextJson)) {
            return LegacyCombatReplayTileRenderer.restoreGame(unitStateJson, sourceGame);
        }
        return renderNew(initialContextJson, unitStateJson);
    }

    public String buildReplaySnapshotName(@Nullable String attackerFaction, @Nullable String defenderFaction) {
        String attackerLabel = normalizeFactionLabel(attackerFaction);
        String defenderLabel = normalizeFactionLabel(defenderFaction);
        if (attackerLabel == null || defenderLabel == null) {
            return "combat-snapshot";
        }
        return attackerLabel + "-" + defenderLabel;
    }

    private Game renderNew(String initialContextJson, String unitStateJson) {
        CombatReplayTilePayload contextPayload = readPayload(initialContextJson);
        CombatReplayTilePayload eventPayload = readPayload(unitStateJson);
        CombatReplayTileContext context = contextPayload.getContext();
        CombatReplayTileUnitState unitState =
                eventPayload.getContext() == null ? readUnitState(unitStateJson) : eventPayload.getUnitState();
        return buildGame(context, unitState);
    }

    private CombatReplayTileUnitState captureUnitState(String tilePosition, Tile tile) {
        CombatReplayTileUnitState snapshot = new CombatReplayTileUnitState();
        snapshot.setTilePosition(tilePosition);
        List<UnitHolderUnitState> holders = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            UnitHolderUnitState holderSnapshot = new UnitHolderUnitState();
            holderSnapshot.setName(holder.getName());
            holderSnapshot.setUnits(captureUnits(holder.getUnitsByState()));
            holders.add(holderSnapshot);
        }
        snapshot.setUnitHolders(holders);
        return snapshot;
    }

    private List<ReplayPlayerSnapshot> capturePlayers(Game game, Tile tile) {
        Set<String> colorIds = new LinkedHashSet<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitsByState().keySet()) {
                colorIds.add(unitKey.getColorID());
            }
        }

        List<ReplayPlayerSnapshot> players = new ArrayList<>();
        for (String colorId : colorIds) {
            Player player = game.getPlayerFromColorOrFaction(colorId);
            if (player != null) players.add(capturePlayer(player));
        }
        return players;
    }

    private ReplayPlayerSnapshot capturePlayer(Player player) {
        ReplayPlayerSnapshot snapshot = new ReplayPlayerSnapshot();
        snapshot.setUserId(player.getUserID());
        snapshot.setUserName(player.getUserName());
        snapshot.setFaction(player.getFaction());
        snapshot.setColor(player.getColor());
        snapshot.setFactionEmoji(player.getFactionEmoji());
        snapshot.setDisplayName(player.getDisplayName());
        snapshot.setDecalSet(player.getDecalSet());
        snapshot.setDummy(player.isDummy());
        snapshot.setNpc(player.isNpc());
        snapshot.setUnitsOwned(new ArrayList<>(player.getUnitsOwned()));
        snapshot.setExhaustedPlanets(new ArrayList<>(player.getExhaustedPlanets()));
        return snapshot;
    }

    private Game buildGame(CombatReplayTileContext context, CombatReplayTileUnitState unitState) {
        String tilePosition = unitState.getTilePosition();

        Tile tile = cloneTile(context.getTileTemplate());
        tile.setPosition(tilePosition);
        clearUnitState(tile);
        applyUnitState(tile, unitState);

        Game game = new Game();
        game.setName("replay-snapshot");
        game.setActivePlayerID(null);
        game.setHexBorderStyle(context.getHexBorderStyle());
        game.setShowGears(context.isShowGears());
        game.setShowUnitTags(context.isShowUnitTags());
        game.setAllianceMode(context.isAllianceMode());
        game.setTwilightsFallMode(context.isTwilightsFallMode());
        game.setLiberationC4Mode(context.isLiberationC4Mode());
        game.setConventionsOfWarAbandonedMode(context.isConventionsOfWarAbandonedMode());
        game.setWildWildGalaxyMode(context.isWildWildGalaxyMode());
        game.setFowMode(context.isFowMode());
        game.setLaws(new java.util.LinkedHashMap<>(context.getLaws()));
        game.setLawsInfo(new java.util.LinkedHashMap<>(context.getLawsInfo()));
        game.setBorderAnomalies(new ArrayList<>(context.getBorderAnomalies()));
        game.setTileMap(Map.of(tilePosition, tile));

        for (ReplayPlayerSnapshot playerSnapshot : context.getPlayers()) {
            Player player = restorePlayer(playerSnapshot, game);
            game.getPlayers().put(player.getUserID(), player);
        }
        game.setActiveSystem(tilePosition);
        return game;
    }

    private void captureGameContext(Game game, CombatReplayTileContext context) {
        context.setHexBorderStyle(game.getHexBorderStyle());
        context.setShowGears(game.isShowGears());
        context.setShowUnitTags(game.isShowUnitTags());
        context.setAllianceMode(game.isAllianceMode());
        context.setTwilightsFallMode(game.isTwilightsFallMode());
        context.setLiberationC4Mode(game.isLiberationC4Mode());
        context.setConventionsOfWarAbandonedMode(game.isConventionsOfWarAbandonedMode());
        context.setWildWildGalaxyMode(game.isWildWildGalaxyMode());
        context.setFowMode(game.isFowMode());
        context.setLaws(new java.util.LinkedHashMap<>(game.getLaws()));
        context.setLawsInfo(new java.util.LinkedHashMap<>(game.getLawsInfo()));
        context.setBorderAnomalies(new ArrayList<>(game.getBorderAnomalies()));
    }

    private Player restorePlayer(ReplayPlayerSnapshot playerSnapshot, Game game) {
        Player player = new Player(playerSnapshot.getUserId(), playerSnapshot.getUserName(), game);
        player.setColor(playerSnapshot.getColor());
        if (playerSnapshot.getFaction() != null) {
            player.setFaction(game, playerSnapshot.getFaction());
        }
        player.setFactionEmoji(playerSnapshot.getFactionEmoji());
        player.setDisplayName(playerSnapshot.getDisplayName());
        player.setDecalSet(playerSnapshot.getDecalSet());
        player.setDummy(playerSnapshot.isDummy());
        player.setNpc(playerSnapshot.isNpc());
        player.setUnitsOwned(new HashSet<>(playerSnapshot.getUnitsOwned()));
        player.getExhaustedPlanets().addAll(playerSnapshot.getExhaustedPlanets());
        return player;
    }

    private void applyUnitState(Tile tile, CombatReplayTileUnitState unitState) {
        for (UnitHolderUnitState holderState : unitState.getUnitHolders()) {
            UnitHolder holder = tile.getUnitHolders().get(holderState.getName());
            holder.getUnitsByState().clear();
            for (UnitStateEntry unit : holderState.getUnits()) {
                holder.getUnitsByState()
                        .put(
                                Units.getUnitKey(unit.getUnitType(), unit.getColorId()),
                                new ArrayList<>(unit.getCounts()));
            }
        }
    }

    private Tile cloneTileWithoutUnits(Tile tile) {
        Tile clone = cloneTile(tile);
        clearUnitState(clone);
        return clone;
    }

    private void clearUnitState(Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            holder.getUnitsByState().clear();
        }
    }

    private List<UnitStateEntry> captureUnits(Map<UnitKey, List<Integer>> unitsByState) {
        List<UnitStateEntry> units = new ArrayList<>();
        for (Map.Entry<UnitKey, List<Integer>> entry : unitsByState.entrySet()) {
            UnitStateEntry unit = new UnitStateEntry();
            unit.setUnitType(entry.getKey().getUnitType());
            unit.setColorId(entry.getKey().getColorID());
            unit.setCounts(new ArrayList<>(entry.getValue()));
            units.add(unit);
        }
        return units;
    }

    @SneakyThrows
    private Tile cloneTile(Tile tile) {
        return TILE_PAYLOAD_MAPPER.readValue(TILE_PAYLOAD_MAPPER.writeValueAsString(tile), Tile.class);
    }

    @Nullable
    private String normalizeFactionLabel(@Nullable String faction) {
        if (StringUtils.isBlank(faction)) return null;
        String normalized = faction.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    @SneakyThrows
    private String write(Object payload) {
        return TILE_PAYLOAD_MAPPER.writeValueAsString(payload);
    }

    @SneakyThrows
    private CombatReplayTilePayload readPayload(String payloadJson) {
        return TILE_PAYLOAD_MAPPER.readValue(payloadJson, CombatReplayTilePayload.class);
    }

    @SneakyThrows
    private CombatReplayTileUnitState readUnitState(String payloadJson) {
        return TILE_PAYLOAD_MAPPER.readValue(payloadJson, CombatReplayTileUnitState.class);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CombatReplayTilePayload {
        private CombatReplayTileContext context;
        private CombatReplayTileUnitState unitState;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CombatReplayTileContext {
        private String tilePosition;
        private Tile tileTemplate;
        private List<ReplayPlayerSnapshot> players = new ArrayList<>();
        private String hexBorderStyle = "off";
        private boolean showGears;
        private boolean showUnitTags;
        private boolean allianceMode;
        private boolean twilightsFallMode;
        private boolean liberationC4Mode;
        private boolean conventionsOfWarAbandonedMode;
        private boolean wildWildGalaxyMode;
        private boolean fowMode;
        private Map<String, Integer> laws = Map.of();
        private Map<String, String> lawsInfo = Map.of();
        private List<BorderAnomalyHolder> borderAnomalies = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CombatReplayTileUnitState {
        private String tilePosition;
        private List<UnitHolderUnitState> unitHolders = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UnitHolderUnitState {
        private String name;
        private List<UnitStateEntry> units = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UnitStateEntry {
        private UnitType unitType;
        private String colorId;
        private List<Integer> counts = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReplayPlayerSnapshot {
        private String userId;
        private String userName;
        private String faction;
        private String color;
        private String factionEmoji;
        private String displayName;
        private String decalSet;
        private boolean dummy;
        private boolean npc;
        private List<String> unitsOwned = new ArrayList<>();
        private List<String> exhaustedPlanets = new ArrayList<>();
    }
}
