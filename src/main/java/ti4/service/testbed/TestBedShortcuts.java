package ti4.service.testbed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.model.TestBedScript;
import ti4.model.TestBedScript.Shortcut;
import ti4.model.TestBedScript.Step;
import ti4.model.TestBedShortcutGroup;
import ti4.service.game.StartPhaseService;
import ti4.service.info.CardsInfoService;
import tools.jackson.core.JacksonException;

@UtilityClass
public class TestBedShortcuts {

    static final String STORED_KEY = "testBedShortcuts";
    public static final String SHORTCUT_FOLDER = TestBedPresetService.PRESET_FOLDER + "/shortcuts";
    public static final String SEAT_GROUP = "Seat";
    public static final String GAME_GROUP = "Game";
    public static final String PRESET_GROUP = "Preset";
    static final String FILE_KEY_PREFIX = "file-";
    static final String LOCAL_KEY_PREFIX = "local-";
    static final int MAX_KEY_LENGTH = 40;
    private static final int MAX_DUMP_LENGTH = 1500;
    private static final String NO_SEAT = "Pick a seat to act as first.";

    public record JavaShortcut(String group, String id, String label, Action action) {

        @FunctionalInterface
        public interface Action {
            String run(Game game, @Nullable Player target, GenericInteractionCreateEvent event);
        }
    }

    public record TestBedButton(
            String label,
            @Nullable JavaShortcut java,
            @Nullable List<Step> steps) {}

    public record ButtonGroup(String key, String name, String description, List<TestBedButton> buttons) {}

    record LoadedGroup(String key, TestBedShortcutGroup group) {}

    static final List<JavaShortcut> BUILT_IN = List.of(
            new JavaShortcut(SEAT_GROUP, "tg", "+1 TG", seatTool(TestBedShortcuts::addTg)),
            new JavaShortcut(SEAT_GROUP, "comm", "+1 Commodity", seatTool(TestBedShortcuts::addCommodity)),
            new JavaShortcut(SEAT_GROUP, "tactic", "+1 Tactic CC", seatTool(TestBedShortcuts::addTacticCc)),
            new JavaShortcut(SEAT_GROUP, "strategy", "+1 Strategy CC", seatTool(TestBedShortcuts::addStrategyCc)),
            new JavaShortcut(SEAT_GROUP, "ready", "Ready All", seatTool(TestBedShortcuts::readyAll)),
            new JavaShortcut(SEAT_GROUP, "cards", "Cards Info", TestBedShortcuts::cardsInfo),
            new JavaShortcut(SEAT_GROUP, "state", "Show Seat State", TestBedShortcuts::seatState),
            new JavaShortcut(GAME_GROUP, "strategy", "Start Strategy", startPhase("strategy")),
            new JavaShortcut(GAME_GROUP, "action", "Start Action", startPhase("action")),
            new JavaShortcut(GAME_GROUP, "status", "Start Status Scoring", startPhase("statusScoring")),
            new JavaShortcut(GAME_GROUP, "agenda", "Start Agenda", startPhase("agenda")),
            new JavaShortcut(GAME_GROUP, "clearActive", "Clear Active Player", TestBedShortcuts::clearActivePlayer),
            new JavaShortcut(GAME_GROUP, "zeroStrategy", "Zero All Strategy CCs", TestBedShortcuts::zeroStrategy));

    private static volatile List<LoadedGroup> fileGroups;

    public static List<ButtonGroup> groups(Game game) {
        Map<String, ButtonGroup> groups = new LinkedHashMap<>();
        for (JavaShortcut shortcut : javaShortcuts()) {
            String key = keyOf(shortcut.group());
            groups.computeIfAbsent(key, k -> new ButtonGroup(k, shortcut.group(), "", new ArrayList<>()))
                    .buttons()
                    .add(new TestBedButton(shortcut.label(), shortcut, null));
        }
        addJsonGroup(groups, keyOf(PRESET_GROUP), PRESET_GROUP, "From the applied preset.", load(game));
        for (LoadedGroup loaded : fileGroups()) {
            TestBedShortcutGroup group = loaded.group();
            if (group.getFog() != null && group.getFog() != game.isFowMode()) continue;
            addJsonGroup(groups, loaded.key(), group.getGroup(), descriptionOf(group), group.getShortcuts());
        }
        return groups.values().stream()
                .filter(group -> !group.buttons().isEmpty())
                .toList();
    }

    @Nullable
    public static ButtonGroup group(Game game, String key) {
        return groups(game).stream()
                .filter(group -> group.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    private static void addJsonGroup(
            Map<String, ButtonGroup> groups, String key, String name, String description, List<Shortcut> shortcuts) {
        if (shortcuts.isEmpty()) return;
        List<TestBedButton> buttons = shortcuts.stream()
                .map(shortcut -> new TestBedButton(shortcut.getLabel(), null, shortcut.getSteps()))
                .toList();
        groups.put(key, new ButtonGroup(key, name, description, new ArrayList<>(buttons)));
    }

    private static String descriptionOf(TestBedShortcutGroup group) {
        return group.getDescription() == null ? "" : group.getDescription();
    }

    static List<JavaShortcut> javaShortcuts() {
        List<JavaShortcut> all = new ArrayList<>(BUILT_IN);
        all.addAll(TestBedFeatureShortcuts.SHORTCUTS);
        return all;
    }

    static String keyOf(String groupName) {
        String key = groupName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        return StringUtils.left(key, MAX_KEY_LENGTH);
    }

    static List<LoadedGroup> fileGroups() {
        List<LoadedGroup> loaded = fileGroups;
        if (loaded == null) {
            loaded = loadFileGroups();
            fileGroups = loaded;
        }
        return loaded;
    }

    private static List<LoadedGroup> loadFileGroups() {
        List<LoadedGroup> groups = new ArrayList<>();
        readGroups(shortcutFiles(), FILE_KEY_PREFIX, groups);
        readGroups(localShortcutFiles(), LOCAL_KEY_PREFIX, groups);
        return List.copyOf(groups);
    }

    private static void readGroups(List<Path> files, String keyPrefix, List<LoadedGroup> groups) {
        for (Path file : files) {
            try {
                TestBedShortcutGroup group = parseGroup(Files.readString(file));
                groups.add(new LoadedGroup(keyPrefix + keyOf(fileName(file)), group));
            } catch (IOException | JacksonException e) {
                BotLogger.error("Could not read test bed shortcut file " + file, e);
            }
        }
    }

    public static void clearCache() {
        fileGroups = null;
    }

    static List<Path> shortcutFiles() {
        return TestBedPresetService.jsonFilesIn(SHORTCUT_FOLDER);
    }

    static List<Path> localShortcutFiles() {
        return TestBedPresetService.jsonFilesIn(TestBedPresetService.LOCAL_FOLDER + "/shortcuts");
    }

    static String fileName(Path file) {
        return file.getFileName().toString().replaceFirst("\\.json$", "");
    }

    public static TestBedShortcutGroup parseGroup(String json) {
        return TestBedPresetService.parse(json, TestBedShortcutGroup.class);
    }

    public static List<String> validateGroup(TestBedShortcutGroup group) {
        List<String> errors = new ArrayList<>();
        if (group.getGroup() == null || group.getGroup().isBlank()) errors.add("The file needs a `group` name.");
        if (group.getGroup() != null && group.getGroup().length() > 80) errors.add("`group` is longer than 80.");
        TestBedScriptService.validateShortcuts(group.getShortcuts(), null, errors);
        return errors;
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

    private interface SeatChange {
        String apply(Player seat);
    }

    private static JavaShortcut.Action seatTool(SeatChange change) {
        return (game, target, event) -> target == null || !target.isRealPlayer() ? NO_SEAT : change.apply(target);
    }

    private static JavaShortcut.Action startPhase(String phase) {
        return (game, target, event) -> {
            StartPhaseService.startPhase(event, game, phase);
            return "Started `" + phase + "`.";
        };
    }

    static String addTg(Player seat) {
        seat.setTg(seat.getTg() + 1);
        return seat.getFaction() + " now has " + seat.getTg() + " TG.";
    }

    static String addCommodity(Player seat) {
        seat.setCommodities(seat.getCommodities() + 1);
        return seat.getFaction() + " now has " + seat.getCommodities() + " commodities.";
    }

    static String addTacticCc(Player seat) {
        seat.setTacticalCC(seat.getTacticalCC() + 1);
        return seat.getFaction() + " CCs: " + seat.getCCRepresentation();
    }

    static String addStrategyCc(Player seat) {
        seat.setStrategicCC(seat.getStrategicCC() + 1);
        return seat.getFaction() + " CCs: " + seat.getCCRepresentation();
    }

    static String readyAll(Player seat) {
        seat.getExhaustedPlanets().clear();
        seat.getLeaders().forEach(leader -> leader.setExhausted(false));
        return "Readied all planets and leaders of " + seat.getFaction() + ".";
    }

    private static String cardsInfo(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        if (target == null || !target.isRealPlayer()) return NO_SEAT;
        CardsInfoService.sendCardsInfo(game, target, event);
        return "Sent cards info to " + target.getFaction() + ".";
    }

    private static String clearActivePlayer(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        game.setActivePlayerID(null);
        return "Cleared the active player.";
    }

    private static String zeroStrategy(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        game.getRealPlayers().forEach(player -> player.setStrategicCC(0));
        return "Every seat now has 0 strategy command tokens.";
    }

    static String seatState(Game game, @Nullable Player target, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        for (Player seat : game.getRealPlayers()) {
            Map<String, String> state = TestBedStateResolver.snapshot(seat);
            sb.append("\n**")
                    .append(seat.getFaction())
                    .append("** ")
                    .append(Stream.of("tg", "commodities", "ccs", "scs", "acs", "sos", "passed")
                            .map(field -> field + "=" + state.get(field))
                            .collect(Collectors.joining(" · ")));
        }
        String text = sb.toString().trim();
        return text.length() > MAX_DUMP_LENGTH ? text.substring(0, MAX_DUMP_LENGTH) + "…" : text;
    }
}
