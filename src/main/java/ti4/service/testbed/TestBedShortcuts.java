package ti4.service.testbed;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.TestBedScript;
import ti4.model.TestBedScript.Shortcut;
import tools.jackson.core.JacksonException;

@UtilityClass
public class TestBedShortcuts {

    static final String STORED_KEY = "testBedShortcuts";
    private static final int MAX_DUMP_LENGTH = 1900;

    public record JavaShortcut(String id, String label, Action action) {

        @FunctionalInterface
        public interface Action {
            String run(Game game, @Nullable Player target, GenericInteractionCreateEvent event);
        }
    }

    public static final List<JavaShortcut> JAVA_SHORTCUTS = List.of(
            new JavaShortcut("clearActive", "Clear Active Player", TestBedShortcuts::clearActivePlayer),
            new JavaShortcut("zeroStrategy", "Zero All Strategy CCs", TestBedShortcuts::zeroStrategyTokens),
            new JavaShortcut("dumpState", "Dump Seat State", TestBedShortcuts::dumpState));

    @Nullable
    public static JavaShortcut javaShortcut(String id) {
        return JAVA_SHORTCUTS.stream()
                .filter(shortcut -> shortcut.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static void store(Game game, List<Shortcut> shortcuts) {
        if (shortcuts.isEmpty()) {
            game.removeStoredValue(STORED_KEY);
            return;
        }
        TestBedScript holder = new TestBedScript();
        holder.setShortcuts(shortcuts);
        String json = TestBedPresetService.toJson(holder);
        TestBedService.store(
                game,
                STORED_KEY,
                Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    public static List<Shortcut> load(Game game) {
        String stored = game.getStoredValue(STORED_KEY);
        if (stored.isBlank()) return List.of();
        try {
            String json = new String(Base64.getUrlDecoder().decode(stored), StandardCharsets.UTF_8);
            return TestBedScriptService.parse(json).getShortcuts();
        } catch (IllegalArgumentException | JacksonException e) {
            return List.of();
        }
    }

    private static String clearActivePlayer(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        game.setActivePlayerID(null);
        return "Cleared the active player.";
    }

    private static String zeroStrategyTokens(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        game.getRealPlayers().forEach(player -> player.setStrategicCC(0));
        return "Every seat now has 0 strategy command tokens.";
    }

    private static String dumpState(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder("Phase `")
                .append(game.getPhaseOfGame())
                .append("`, round ")
                .append(game.getRound());
        for (Player seat : game.getRealPlayers()) {
            Map<String, String> state = TestBedStateResolver.snapshot(seat);
            sb.append("\n**")
                    .append(seat.getFaction())
                    .append("** ")
                    .append(TestBedStateResolver.SEAT_FIELDS.stream()
                            .filter(field -> !state.get(field).isEmpty())
                            .map(field -> field + "=" + state.get(field))
                            .collect(Collectors.joining(" · ")));
        }
        return sb.length() > MAX_DUMP_LENGTH ? sb.substring(0, MAX_DUMP_LENGTH) + "…" : sb.toString();
    }
}
