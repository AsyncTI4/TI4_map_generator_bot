package ti4.service.testbed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.Seat;
import ti4.model.TestBedScript;
import ti4.model.TestBedScript.Expect;
import ti4.model.TestBedScript.Shortcut;
import ti4.model.TestBedScript.Step;
import tools.jackson.core.JacksonException;

@UtilityClass
public class TestBedScriptService {

    public static final String SCRIPT_FOLDER = TestBedPresetService.PRESET_FOLDER + "/scripts";
    public static final String ALL_SEATS = "all";
    public static final String YOU = "you";
    public static final Set<String> SHARED_SCOPES = Set.of("main", "actions", "gm");
    public static final Set<String> SEAT_SCOPES = Set.of("private", "cards-info", "combat");
    public static final String VIRTUAL_SEAT_NAME = "seat";
    private static final int BUTTON_ID_LIMIT = 100;

    public static TestBedScript parse(String json) {
        return TestBedPresetService.parse(json, TestBedScript.class);
    }

    private static volatile Map<String, TestBedScript> scripts;

    public static Map<String, TestBedScript> loadScripts() {
        Map<String, TestBedScript> cached = scripts;
        if (cached == null) {
            cached = Collections.unmodifiableMap(readScripts(allScriptFiles()));
            scripts = cached;
        }
        return cached;
    }

    static Map<String, TestBedScript> readScripts(List<Path> files) {
        Map<String, TestBedScript> read = new TreeMap<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString().replaceFirst("\\.json$", "");
            try {
                TestBedScript script = parse(Files.readString(file));
                read.put(script.getName() == null ? fileName : script.getName(), script);
            } catch (IOException | JacksonException e) {
                BotLogger.error("Could not read test bed script " + file, e);
            }
        }
        return read;
    }

    public static void clearCache() {
        scripts = null;
    }

    static List<Path> allScriptFiles() {
        List<Path> files = new ArrayList<>(shippedScriptFiles());
        files.addAll(localScriptFiles());
        return files;
    }

    static List<Path> localScriptFiles() {
        return TestBedPresetService.jsonFilesIn(TestBedPresetService.LOCAL_FOLDER + "/scripts");
    }

    @Nullable
    public static TestBedScript getScript(String name) {
        return loadScripts().get(name);
    }

    static List<Path> shippedScriptFiles() {
        return TestBedPresetService.jsonFilesIn(SCRIPT_FOLDER);
    }

    public static List<String> validate(TestBedScript script) {
        List<String> errors = new ArrayList<>();
        TestBedPreset preset = null;
        if (script.getPreset() != null) {
            preset = TestBedPresetService.getPreset(script.getPreset());
            if (preset == null) errors.add("Unknown preset `" + script.getPreset() + "`.");
            if (preset != null) {
                TestBedPresetService.validate(preset)
                        .forEach(error -> errors.add("Preset `" + script.getPreset() + "`: " + error));
            }
        }
        if (script.getSteps().isEmpty()) errors.add("The script has no `steps`.");
        if (script.getSettleSeconds() < 0) errors.add("`settleSeconds` may not be negative.");
        if (script.getTimeoutSeconds() < 0) errors.add("`timeoutSeconds` may not be negative.");
        Set<String> seatNames = knownSeatNames(preset);
        validateSteps(script.getSteps(), "step", seatNames, errors);
        validateShortcuts(script.getShortcuts(), seatNames, errors);
        if (seatNames != null) validateCardsInHand(script.getSteps(), preset, errors);
        return errors;
    }

    private static void validateCardsInHand(List<Step> steps, TestBedPreset preset, List<String> errors) {
        Map<String, String> factionByName = factionsByName(preset);
        Map<String, Set<String>> cardsByFaction = new HashMap<>();
        for (Seat seat : preset.allSeats()) {
            giveCards(cardsByFaction, seat.getFaction(), TestBedPresetService.withDefaults(seat, preset.getDefaults()));
        }
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if ("hand".equals(step.getAction()) && step.getHand() != null) {
                List<String> targets = ALL_SEATS.equals(step.getAs())
                        ? List.copyOf(new TreeSet<>(factionByName.values()))
                        : listOfNullable(factionByName.get(step.getAs()));
                targets.forEach(faction -> giveCards(cardsByFaction, faction, step.getHand()));
            }
            String faction = factionByName.get(step.getAs());
            if (faction == null) continue;
            for (TestBedPlaceholders.CardReference card :
                    TestBedPlaceholders.cardReferences(TestBedPresetService.toJson(step))) {
                boolean tracked = ("ac".equals(card.kind()) || "so".equals(card.kind()))
                        && TestBedPlaceholders.isKnownCard(card.kind(), card.cardId());
                if (tracked && !cardsByFaction.getOrDefault(faction, Set.of()).contains(card.cardId())) {
                    errors.add("step " + (i + 1) + ": `{" + card.kind() + ":" + card.cardId() + "}` but nothing gives `"
                            + card.cardId() + "` to " + step.getAs() + " (the preset or an earlier `do: hand`).");
                }
            }
        }
    }

    private static List<String> listOfNullable(@Nullable String value) {
        return value == null ? List.of() : List.of(value);
    }

    private static void giveCards(Map<String, Set<String>> cardsByFaction, String faction, Seat hand) {
        Set<String> cards = cardsByFaction.computeIfAbsent(faction, key -> new HashSet<>());
        if (hand.getAcs() != null) cards.addAll(hand.getAcs().ids());
        if (hand.getSos() != null) cards.addAll(hand.getSos().ids());
    }

    private static Map<String, String> factionsByName(TestBedPreset preset) {
        Map<String, String> factions = new HashMap<>();
        for (Seat seat : preset.allSeats()) {
            factions.put(seat.getFaction(), seat.getFaction());
            if (seat.getColor() != null) factions.put(seat.getColor(), seat.getFaction());
        }
        if (preset.getYou() != null) factions.put(YOU, preset.getYou().getFaction());
        for (int number = 1; number <= preset.getSeats().size(); number++) {
            factions.put(
                    VIRTUAL_SEAT_NAME + number,
                    preset.getSeats().get(number - 1).getFaction());
        }
        return factions;
    }

    @Nullable
    static Set<String> knownSeatNames(@Nullable TestBedPreset preset) {
        if (preset == null) return null;
        List<Seat> seats = preset.allSeats();
        if (seats.stream().anyMatch(Seat::hasRandomFaction)) return null;
        Set<String> names = new TreeSet<>();
        names.add(ALL_SEATS);
        if (preset.getYou() != null) names.add(YOU);
        for (Seat seat : seats) {
            names.add(seat.getFaction());
            if (seat.getColor() != null) names.add(seat.getColor());
        }
        for (int number = 1; number <= preset.getSeats().size(); number++) names.add(VIRTUAL_SEAT_NAME + number);
        return names;
    }

    public static void validateShortcuts(
            List<Shortcut> shortcuts, @Nullable Set<String> seatNames, List<String> errors) {
        for (int i = 0; i < shortcuts.size(); i++) {
            Shortcut shortcut = shortcuts.get(i);
            String label = "shortcut " + (i + 1);
            if (shortcut.getLabel() == null || shortcut.getLabel().isBlank()) {
                errors.add(label + ": needs a `label`.");
            }
            if (shortcut.getSteps().isEmpty()) errors.add(label + ": has no `steps`.");
            validateSteps(shortcut.getSteps(), label + " step", seatNames, errors);
        }
    }

    private static void validateSteps(
            List<Step> steps, String prefix, @Nullable Set<String> seatNames, List<String> errors) {
        for (int i = 0; i < steps.size(); i++) {
            String label = prefix + " " + (i + 1);
            validateStep(steps.get(i), label, errors);
            TestBedPlaceholders.validate(TestBedPresetService.toJson(steps.get(i)), label, errors);
            if (seatNames != null) validateSeatNames(steps.get(i), seatNames, label, errors);
        }
    }

    private static void validateSeatNames(Step step, Set<String> seatNames, String label, List<String> errors) {
        for (String name : referencedSeats(step)) {
            if (!seatNames.contains(name) && !Mapper.isValidColor(name)) {
                errors.add(label + ": unknown seat `" + name + "`; the preset has " + seatNames + ".");
            }
        }
    }

    private static List<String> referencedSeats(Step step) {
        List<String> names = new ArrayList<>(TestBedPlaceholders.referencedSeats(TestBedPresetService.toJson(step)));
        if (step.getAs() != null) names.add(step.getAs());
        addScopeSeat(step.getIn(), names);
        Expect expect = step.getExpect();
        if (expect == null) return names;
        addScopeSeat(expect.getIn(), names);
        String state = expect.getState();
        if (state != null
                && !state.startsWith(TestBedStateResolver.GAME_PREFIX)
                && !state.startsWith(TestBedStateResolver.STORED_PREFIX)
                && state.indexOf('.') > 0) {
            names.add(state.substring(0, state.indexOf('.')));
        }
        return names;
    }

    private static void addScopeSeat(@Nullable String scope, List<String> names) {
        if (scope == null || SHARED_SCOPES.contains(scope)) return;
        int colon = scope.indexOf(':');
        names.add(colon < 0 ? scope : scope.substring(0, colon));
    }

    private static void validateStep(Step step, String label, List<String> errors) {
        List<String> verbs = step.verbs();
        if (verbs.size() != 1) {
            errors.add(label + ": needs exactly one of " + TestBedScript.VERBS + ", found " + verbs + ".");
            return;
        }
        switch (verbs.getFirst()) {
            case "press", "pressId" -> {
                requireSeat(step, label, errors);
                if (step.getIn() != null) validateScope(step.getIn(), label, errors);
                if (step.getPressId() != null && step.getPressId().length() > BUTTON_ID_LIMIT) {
                    errors.add(label + ": `pressId` is longer than Discord's " + BUTTON_ID_LIMIT + "-character limit.");
                }
            }
            case "do" -> validateAction(step, label, errors);
            case "wait" -> {
                if (step.getWait() < 0) errors.add(label + ": `wait` may not be negative.");
            }
            case "expect" -> validateExpect(step.getExpect(), label, errors);
            default -> {}
        }
    }

    private static void requireSeat(Step step, String label, List<String> errors) {
        if (step.getAs() == null || step.getAs().isBlank()) errors.add(label + ": needs `as` (a seat or `you`).");
        if (ALL_SEATS.equals(step.getAs())) errors.add(label + ": `as: all` only works with `do: hand`.");
    }

    private static void validateAction(Step step, String label, List<String> errors) {
        String action = step.getAction();
        if (!TestBedScript.ACTIONS.contains(action)) {
            errors.add(label + ": unknown action `" + action + "`; use " + TestBedScript.ACTIONS + ".");
            return;
        }
        switch (action) {
            case "startPhase", "runCron" -> requireValue(step.getValue(), "value", label, errors);
            case "setStored" -> {
                requireValue(step.getKey(), "key", label, errors);
                requireValue(step.getValue(), "value", label, errors);
                if (step.getKey() != null
                        && step.getValue() != null
                        && !(TestBedService.isSaveSafe(step.getKey()) && TestBedService.isSaveSafe(step.getValue()))) {
                    errors.add(label + ": `setStored` key and value may not contain `,`, `:` or line breaks.");
                }
            }
            case "removeStored" -> requireValue(step.getKey(), "key", label, errors);
            case "setActivePlayer" -> requireSeat(step, label, errors);
            case "actAs" -> requireValue(step.getAs(), "as", label, errors);
            case "hand" -> {
                requireValue(step.getAs(), "as", label, errors);
                if (step.getHand() == null) {
                    errors.add(label + ": `do: hand` needs a `hand` object.");
                } else {
                    if (step.getHand().hasIdentity()) {
                        errors.add(label + ": `hand` may not set faction, color, home, speaker or sc.");
                    }
                    TestBedPresetService.validateHandContents(step.getHand(), label, errors);
                }
            }
            default -> {}
        }
    }

    private static void requireValue(@Nullable String value, String field, String label, List<String> errors) {
        if (value == null || value.isBlank()) errors.add(label + ": needs `" + field + "`.");
    }

    private static void validateExpect(Expect expect, String label, List<String> errors) {
        int kinds = 0;
        if (expect.getState() != null) {
            kinds++;
            String problem = TestBedStateResolver.validatePath(expect.getState());
            if (problem != null) errors.add(label + ": " + problem + ".");
            if (expect.getEquals() == null
                    && expect.getContains().isEmpty()
                    && expect.getNotContains().isEmpty()) {
                errors.add(label + ": a `state` check needs `equals`, `contains` or `notContains`.");
            }
        }
        if (expect.getEphemeral() != null) kinds++;
        if (expect.getModal() != null) kinds++;
        if (expect.getIn() != null) {
            kinds++;
            validateScope(expect.getIn(), label, errors);
            boolean hasCheck = !expect.getContains().isEmpty()
                    || !expect.getNotContains().isEmpty()
                    || expect.getCount() != null
                    || expect.isNoFactionLeak();
            if (!hasCheck) errors.add(label + ": a message check needs contains, notContains, count or noFactionLeak.");
            if (expect.getCount() != null && expect.getContains().size() != 1) {
                errors.add(label + ": `count` needs exactly one `contains` text.");
            }
        }
        if (kinds != 1) errors.add(label + ": `expect` needs exactly one of in, state, ephemeral or modal.");
    }

    static void validateScope(String scope, String label, List<String> errors) {
        if (SHARED_SCOPES.contains(scope)) return;
        int colon = scope.indexOf(':');
        String seat = colon < 0 ? scope : scope.substring(0, colon);
        if (seat.isBlank() || ALL_SEATS.equals(seat)) errors.add(label + ": scope `" + scope + "` needs a seat.");
        if (colon >= 0 && !SEAT_SCOPES.contains(scope.substring(colon + 1))) {
            errors.add(label + ": unknown scope `" + scope + "`; use main, actions, gm, <seat>, <seat>:private,"
                    + " <seat>:cards-info or <seat>:combat.");
        }
    }
}
