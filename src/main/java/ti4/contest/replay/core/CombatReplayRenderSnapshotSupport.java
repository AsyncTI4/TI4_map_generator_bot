package ti4.contest.replay.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashSet;
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
import ti4.helpers.Units.UnitKey;
import ti4.json.JsonMapperManager;
import ti4.json.UnitKeyMapKeyDeserializer;
import ti4.json.UnitKeyMapKeySerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@UtilityClass
/**
 * Captures and restores the minimal game state needed to render replay hit-assignment images.
 */
public class CombatReplayRenderSnapshotSupport {

    private static final JsonMapper SNAPSHOT_MAPPER = JsonMapperManager.basic()
            .rebuild()
            .addModule(new SimpleModule()
                    .addKeySerializer(UnitKey.class, new UnitKeyMapKeySerializer())
                    .addKeyDeserializer(UnitKey.class, new UnitKeyMapKeyDeserializer()))
            .build();

    public String captureHitAssignmentSnapshot(Game game, String tilePosition) {
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return null;

        ReplayTileRenderSnapshot snapshot = new ReplayTileRenderSnapshot();
        snapshot.setFocusTilePosition(tilePosition);
        snapshot.setTile(tile);
        snapshot.setPlayers(capturePlayers(game));
        return writeSnapshot(snapshot);
    }

    public Game restoreGame(String snapshotJson) {
        return restoreGame(snapshotJson, null);
    }

    public Game restoreGame(String snapshotJson, @Nullable Game sourceGame) {
        if (snapshotJson == null || snapshotJson.isBlank()) return null;
        ReplayTileRenderSnapshot snapshot = readSnapshot(snapshotJson);
        if (snapshot == null || snapshot.getTile() == null) return null;

        Game game = new Game();
        game.setName("replay-snapshot");
        game.setActivePlayerID(null);
        game.setHexBorderStyle("off");
        game.setShowGears(false);
        game.setShowUnitTags(false);
        game.setAllianceMode(false);
        game.setTwilightsFallMode(false);
        game.setLiberationC4Mode(false);
        game.setConventionsOfWarAbandonedMode(false);
        game.setWildWildGalaxyMode(false);
        game.setFowMode(false);
        game.setLaws(Map.of());
        game.setLawsInfo(Map.of());
        game.setBorderAnomalies(List.of());
        game.setTileMap(Map.of(snapshot.getFocusTilePosition(), snapshot.getTile()));

        if (snapshot.getPlayers() != null) {
            for (ReplayPlayerSnapshot playerSnapshot : snapshot.getPlayers()) {
                if (playerSnapshot.getUserId() == null || playerSnapshot.getUserName() == null) continue;

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
                player.setUnitsOwned(resolveUnitsOwned(playerSnapshot, sourceGame));
                player.getExhaustedPlanets().addAll(playerSnapshot.getExhaustedPlanets());
                game.getPlayers().put(player.getUserID(), player);
            }
        }

        game.setActiveSystem(snapshot.getFocusTilePosition());
        return game;
    }

    private List<ReplayPlayerSnapshot> capturePlayers(Game game) {
        List<ReplayPlayerSnapshot> players = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
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
            players.add(snapshot);
        }
        return players;
    }

    private Set<String> resolveUnitsOwned(ReplayPlayerSnapshot playerSnapshot, @Nullable Game sourceGame) {
        if (playerSnapshot.getUnitsOwned() != null
                && !playerSnapshot.getUnitsOwned().isEmpty()) {
            return new HashSet<>(playerSnapshot.getUnitsOwned());
        }

        Player sourcePlayer = findSourcePlayer(playerSnapshot, sourceGame);
        if (sourcePlayer != null && !sourcePlayer.getUnitsOwned().isEmpty()) {
            return new HashSet<>(sourcePlayer.getUnitsOwned());
        }

        Player fallbackPlayer = new Player(playerSnapshot.getUserId(), playerSnapshot.getUserName(), new Game());
        fallbackPlayer.setFaction(playerSnapshot.getFaction());
        if (fallbackPlayer.getFactionSetupInfo() != null) {
            return new HashSet<>(fallbackPlayer.getFactionSetupInfo().getUnits());
        }

        return new HashSet<>();
    }

    @Nullable
    private Player findSourcePlayer(ReplayPlayerSnapshot playerSnapshot, @Nullable Game sourceGame) {
        if (sourceGame == null) return null;

        Player player = sourceGame.getPlayer(playerSnapshot.getUserId());
        if (player != null) return player;

        player = sourceGame.getPlayerFromColorOrFaction(playerSnapshot.getFaction());
        if (player != null) return player;

        return sourceGame.getPlayerFromColorOrFaction(playerSnapshot.getColor());
    }

    public String buildReplaySnapshotName(@Nullable String attackerFaction, @Nullable String defenderFaction) {
        String attackerLabel = normalizeFactionLabel(attackerFaction);
        String defenderLabel = normalizeFactionLabel(defenderFaction);
        if (attackerLabel == null || defenderLabel == null) {
            return "combat-snapshot";
        }
        return attackerLabel + "-" + defenderLabel;
    }

    @Nullable
    private String normalizeFactionLabel(@Nullable String faction) {
        if (StringUtils.isBlank(faction)) return null;
        String normalized = faction.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    @SneakyThrows
    private String writeSnapshot(ReplayTileRenderSnapshot snapshot) {
        return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
    }

    @SneakyThrows
    private ReplayTileRenderSnapshot readSnapshot(String snapshotJson) {
        return SNAPSHOT_MAPPER.readValue(snapshotJson, ReplayTileRenderSnapshot.class);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReplayTileRenderSnapshot {
        private String focusTilePosition;
        private Tile tile;
        private List<ReplayPlayerSnapshot> players = new ArrayList<>();
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
