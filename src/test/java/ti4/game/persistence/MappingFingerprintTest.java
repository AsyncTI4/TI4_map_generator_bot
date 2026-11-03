package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class MappingFingerprintTest extends BaseTi4Test {

    // Warmup trusts a stored row, and the nightly reconcile skips a game, only while the row's mapping_version
    // equals GameEntityMapper.MAPPING_VERSION. So any change to what gets stored for a game must raise
    // MAPPING_VERSION, or rows written by the old code are kept forever for games that are never saved again.
    // ManagedGameStateVersionTest catches added or removed fields. This test catches changed values: it builds
    // the stored state of fixture games, serializes it with MAPPING_VERSION, and compares the hash with the one
    // recorded for that version. It only sees logic that the fixtures exercise, so add a variant when you add a
    // derived value.
    private static final String EXPECTED_FINGERPRINT =
            "0110feb440555617c4d4c8b1aab35ffc882e632ed25d66a410d9554b8274d3a9";
    private static final GameFileStamp FIXED_STAMP = new GameFileStamp(1L, 1L);
    private static final Path FINGERPRINT_INPUT = Path.of("target", "mapping-fingerprint.json");

    @Test
    void storedStateOfFixtureGamesMatchesTheFingerprintForThisMappingVersion() throws Exception {
        String json = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build()
                .writeValueAsString(storedStateOfFixtures());
        String fingerprint = sha256("mapping_version=" + GameEntityMapper.MAPPING_VERSION + "\n" + json);

        if (!EXPECTED_FINGERPRINT.equals(fingerprint)) {
            Files.createDirectories(FINGERPRINT_INPUT.getParent());
            Files.writeString(FINGERPRINT_INPUT, json);
        }
        assertThat(fingerprint)
                .as("What is stored for the fixture games changed (see " + FINGERPRINT_INPUT.toAbsolutePath()
                        + "). If stored rows or ManagedGameState now hold different values, raise"
                        + " GameEntityMapper.MAPPING_VERSION so existing rows are rebuilt from their game files."
                        + " Then set EXPECTED_FINGERPRINT to " + fingerprint)
                .isEqualTo(EXPECTED_FINGERPRINT);
    }

    private static Map<String, Map<String, Object>> storedStateOfFixtures() {
        Map<String, Consumer<Game>> variants = new LinkedHashMap<>();
        variants.put("pbd15036", _ -> {});
        variants.put("pbd15036-active", game -> {
            game.setHasEnded(false);
            game.setEndedDate(0L);
        });
        variants.put("pbd15036-competitive-export", game -> {
            game.setThundersEdge(true);
            game.setProphecyOfKings(true);
            game.setVp(10);
            game.setRound(6);
            game.setTags(new ArrayList<>());
        });
        variants.put("pbd15036-fog-tigl-absol", game -> {
            game.setFowMode(true);
            game.setCompetitiveTIGLGame(true);
            game.setAbsolMode(true);
        });
        variants.put("pbd15036-eliminated-player", game -> firstRealPlayer(game).setEliminated(true));
        variants.put("pbd15036-community-team", game -> {
            game.setCommunityMode(true);
            List<Player> players = game.getRealPlayers();
            players.getFirst()
                    .setTeamMateIDs(new ArrayList<>(List.of(
                            players.getFirst().getUserID(), players.get(1).getUserID())));
        });
        variants.put("pbd15036-ping-settings", game -> {
            game.setAutoPing(false);
            game.setTemporaryPingDisable(true);
        });
        variants.put(
                "pbd15036-two-players",
                game -> game.getRealPlayers().stream()
                        .skip(2)
                        .map(Player::getUserID)
                        .toList()
                        .forEach(game::removePlayer));
        variants.put("game-with-border-anomalies", _ -> {});
        variants.put("game-with-displaced-units", _ -> {});

        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        variants.forEach((label, variant) -> {
            String sourceGameName = label.startsWith("pbd15036") ? "pbd15036" : label;
            Game game = GameLoadService.load(sourceGameName);
            variant.accept(game);
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("managedGameState", ManagedGameState.of(game));
            state.put("rows", PersistedGameState.of(GameEntityMapper.toSnapshot(game, FIXED_STAMP)));
            stored.put(label, state);
        });
        return stored;
    }

    private static Player firstRealPlayer(Game game) {
        return game.getRealPlayers().getFirst();
    }

    private static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
