package ti4.service.testbed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.CardPick;
import ti4.model.TestBedPreset.Seat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class TestBedPresetService {

    public static final String PRESET_FOLDER = "testbed";
    public static final String LOCAL_FOLDER = PRESET_FOLDER + "/local";
    public static final String HOME_UNIT_LOCATION = "home";
    public static final List<String> DEFAULT_HOME_POSITIONS = List.of("301", "304", "307", "310", "313", "316");
    public static final String DEFAULT_MAP_STRING = "{18} 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36"
            + " 0 37 38 0 39 40 0 41 42 0 43 44 0 45 46 0 47 48";

    private static final int MAX_SEATS = 8;
    private static final Set<String> LEADER_TYPES = Set.of("agent", "commander", "hero", "envoy");
    public static final List<String> BREAKTHROUGH_STATES = List.of("unlocked", "exhausted");
    private static final Pattern CCS_PATTERN = Pattern.compile("\\d+/\\d+/\\d+");
    private static final Set<String> EMPTY_MAP_TILES = Set.of("0", "-1");

    private static final JsonMapper STRICT_MAPPER = JsonMapperManager.basic()
            .rebuild()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static TestBedPreset parse(String json) {
        return parse(json, TestBedPreset.class);
    }

    static <T> T parse(String json, Class<T> type) {
        return STRICT_MAPPER.readValue(json, type);
    }

    static String toJson(Object value) {
        return STRICT_MAPPER.writeValueAsString(value);
    }

    private static volatile Map<String, TestBedPreset> presets;

    public static Map<String, TestBedPreset> loadPresets() {
        Map<String, TestBedPreset> cached = presets;
        if (cached == null) {
            cached = Collections.unmodifiableMap(readPresets(allPresetFiles()));
            presets = cached;
        }
        return cached;
    }

    static Map<String, TestBedPreset> readPresets(List<Path> files) {
        Map<String, TestBedPreset> read = new TreeMap<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString().replaceFirst("\\.json$", "");
            try {
                TestBedPreset preset = parse(Files.readString(file));
                read.put(preset.getName() == null ? fileName : preset.getName(), preset);
            } catch (IOException | JacksonException e) {
                BotLogger.error("Could not read test bed preset " + file, e);
            }
        }
        return read;
    }

    public static void clearCache() {
        presets = null;
    }

    static List<Path> allPresetFiles() {
        List<Path> files = new ArrayList<>(shippedPresetFiles());
        files.addAll(localPresetFiles());
        return files;
    }

    static List<Path> localPresetFiles() {
        return jsonFilesIn(LOCAL_FOLDER + "/presets");
    }

    @Nullable
    public static TestBedPreset getPreset(String name) {
        return loadPresets().get(name);
    }

    static List<Path> shippedPresetFiles() {
        return jsonFilesIn(PRESET_FOLDER);
    }

    static List<Path> jsonFilesIn(String dataFolder) {
        Path folder = Path.of(ResourceHelper.getDataFolder(dataFolder));
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            BotLogger.error("Could not list test bed files in " + folder, e);
            return List.of();
        }
    }

    public static Seat withDefaults(Seat seat, @Nullable Seat defaults) {
        if (defaults == null) return seat;
        Seat merged = new Seat();
        merged.setFaction(seat.getFaction());
        merged.setColor(seat.getColor());
        merged.setHome(seat.getHome());
        merged.setSpeaker(seat.isSpeaker());
        merged.setSc(seat.getSc());
        merged.setAcs(firstNonNull(seat.getAcs(), defaults.getAcs()));
        merged.setSos(firstNonNull(seat.getSos(), defaults.getSos()));
        merged.setRelics(firstNonNull(seat.getRelics(), defaults.getRelics()));
        merged.setTechs(firstNonNull(seat.getTechs(), defaults.getTechs()));
        merged.setTg(firstNonNull(seat.getTg(), defaults.getTg()));
        merged.setCommodities(firstNonNull(seat.getCommodities(), defaults.getCommodities()));
        merged.setCcs(firstNonNull(seat.getCcs(), defaults.getCcs()));
        merged.setLeaders(firstNonNull(seat.getLeaders(), defaults.getLeaders()));
        merged.setUnits(seat.getUnits().isEmpty() ? defaults.getUnits() : seat.getUnits());
        merged.setPlanets(firstNonNull(seat.getPlanets(), defaults.getPlanets()));
        merged.setPns(firstNonNull(seat.getPns(), defaults.getPns()));
        merged.setScoredObjectives(firstNonNull(seat.getScoredObjectives(), defaults.getScoredObjectives()));
        merged.setFragments(firstNonNull(seat.getFragments(), defaults.getFragments()));
        merged.setBreakthrough(firstNonNull(seat.getBreakthrough(), defaults.getBreakthrough()));
        return merged;
    }

    private static <T> T firstNonNull(@Nullable T value, @Nullable T fallback) {
        return value != null ? value : fallback;
    }

    public static List<String> validate(TestBedPreset preset) {
        List<String> errors = new ArrayList<>();
        List<Seat> seats = preset.allSeats();
        if (seats.isEmpty()) errors.add("The preset has no seats (`you` or `seats`).");
        if (seats.size() > MAX_SEATS) errors.add("At most " + MAX_SEATS + " seats are supported.");
        if (!TestBedPreset.START_PHASES.contains(preset.getStart())) {
            errors.add("`start` must be one of " + TestBedPreset.START_PHASES + ", not `" + preset.getStart() + "`.");
        }
        if (preset.getDefaults() != null && preset.getDefaults().hasIdentity()) {
            errors.add("`defaults` may not set faction, color, home, speaker or sc.");
        }
        validateMapString(preset.getMapString(), errors);
        for (String position : preset.getCombat()) {
            if (!PositionMapper.isTilePositionValid(position)) {
                errors.add("Unknown `combat` position `" + position + "`.");
            }
        }
        if (!preset.getCombat().isEmpty() && !"action".equals(preset.getStart())) {
            errors.add("`combat` needs `start` to be `action`.");
        }
        validateSeatIdentities(seats, errors);
        validateGameState(preset, errors);
        TestBedScriptService.validateShortcuts(
                preset.getShortcuts(), TestBedScriptService.knownSeatNames(preset), errors);
        validateHandContents(preset.getDefaults(), "defaults", errors);
        for (int i = 0; i < seats.size(); i++) {
            validateHandContents(seats.get(i), seatLabel(preset, i), errors);
        }
        return errors;
    }

    private static String seatLabel(TestBedPreset preset, int index) {
        if (preset.getYou() != null) return index == 0 ? "you" : "seat " + index;
        return "seat " + (index + 1);
    }

    private static void validateMapString(@Nullable String mapString, List<String> errors) {
        if (mapString == null) return;
        for (String token : mapString.replaceAll("[{},]", " ").trim().split("\\s+")) {
            String tileId = token.toLowerCase();
            if (!EMPTY_MAP_TILES.contains(tileId) && !TileHelper.isValidTile(tileId)) {
                errors.add("Unknown tile `" + token + "` in `mapString`.");
            }
        }
    }

    private static void validateSeatIdentities(List<Seat> seats, List<String> errors) {
        Set<String> factions = new HashSet<>();
        Set<String> colors = new HashSet<>();
        Set<String> homes = new HashSet<>();
        Set<Integer> scs = new HashSet<>();
        int speakers = 0;
        int seatsWithoutHome = 0;
        for (Seat seat : seats) {
            if (!seat.hasRandomFaction()) {
                if (!Mapper.isValidFaction(seat.getFaction()))
                    errors.add("Unknown faction `" + seat.getFaction() + "`.");
                if (!factions.add(seat.getFaction())) errors.add("Faction `" + seat.getFaction() + "` is used twice.");
            }
            if (seat.getColor() != null) {
                if (!Mapper.isValidColor(seat.getColor())) errors.add("Unknown color `" + seat.getColor() + "`.");
                if (!colors.add(seat.getColor())) errors.add("Color `" + seat.getColor() + "` is used twice.");
            }
            if (seat.getHome() == null) {
                seatsWithoutHome++;
            } else {
                if (!PositionMapper.isTilePositionValid(seat.getHome())) {
                    errors.add("Unknown home position `" + seat.getHome() + "`.");
                }
                if (!homes.add(seat.getHome())) errors.add("Home position `" + seat.getHome() + "` is used twice.");
            }
            if (seat.getSc() != null) {
                if (seat.getSc() < 1 || seat.getSc() > 8)
                    errors.add("Strategy card `" + seat.getSc() + "` is not 1-8.");
                if (!scs.add(seat.getSc())) errors.add("Strategy card `" + seat.getSc() + "` is picked twice.");
            }
            if (seat.isSpeaker()) speakers++;
        }
        if (speakers > 1) errors.add("More than one seat is the speaker.");
        long freeDefaultHomes = DEFAULT_HOME_POSITIONS.stream()
                .filter(home -> !homes.contains(home))
                .count();
        if (seatsWithoutHome > freeDefaultHomes) {
            errors.add("Too many seats without a `home`: only " + DEFAULT_HOME_POSITIONS + " are used as defaults.");
        }
    }

    static void validateHandContents(@Nullable Seat seat, String label, List<String> errors) {
        if (seat == null) return;
        validateCardIds(seat.getAcs(), Mapper::isValidActionCard, label, "action card", errors);
        validateCardIds(seat.getSos(), Mapper::isValidSecretObjective, label, "secret objective", errors);
        validateCardIds(seat.getRelics(), Mapper::isValidRelic, label, "relic", errors);
        validateIds(seat.getTechs(), Mapper::isValidTech, label, "technology", errors);
        validateIds(
                seat.getPlanets(),
                planet -> Mapper.isValidPlanet(AliasHandler.resolvePlanet(planet.toLowerCase())),
                label,
                "planet",
                errors);
        if (seat.getCcs() != null && !CCS_PATTERN.matcher(seat.getCcs()).matches()) {
            errors.add(label + ": `ccs` must look like `3/3/2` (tactic/fleet/strategy).");
        }
        for (String location : seat.getUnits().keySet()) {
            if (!HOME_UNIT_LOCATION.equalsIgnoreCase(location) && !PositionMapper.isTilePositionValid(location)) {
                errors.add(label + ": unknown unit position `" + location + "` (use a tile position or `home`).");
            }
        }
        if (seat.getLeaders() != null) {
            Predicate<String> isLeader = leader -> LEADER_TYPES.contains(leader) || Mapper.isValidLeader(leader);
            validateIds(seat.getLeaders().getUnlock(), isLeader, label, "leader", errors);
            validateIds(seat.getLeaders().getExhaust(), isLeader, label, "leader", errors);
        }
        validateIds(seat.getPns(), TestBedPresetService::isPromissoryNoteEntry, label, "promissory note", errors);
        validateIds(seat.getScoredObjectives(), Mapper::isValidPublicObjective, label, "public objective", errors);
        validateIds(seat.getFragments(), Mapper::isValidExplore, label, "relic fragment", errors);
        if (seat.getBreakthrough() != null && !BREAKTHROUGH_STATES.contains(seat.getBreakthrough())) {
            errors.add(label + ": `breakthrough` must be one of " + BREAKTHROUGH_STATES + ".");
        }
    }

    private static boolean isPromissoryNoteEntry(String entry) {
        int colon = entry.indexOf(':');
        if (colon < 0) return Mapper.isValidPromissoryNote(entry);
        return colon > 0 && colon < entry.length() - 1;
    }

    private static void validateGameState(TestBedPreset preset, List<String> errors) {
        validateIds(
                preset.getRevealedObjectives(), Mapper::isValidPublicObjective, "preset", "public objective", errors);
        validateIds(
                preset.getLaws(),
                law -> Mapper.isValidAgenda(StringUtils.substringBefore(law, ":")),
                "preset",
                "law",
                errors);
        for (Map.Entry<String, List<String>> entry : preset.getTokens().entrySet()) {
            String where = entry.getKey();
            if (!PositionMapper.isTilePositionValid(where)
                    && !Mapper.isValidPlanet(AliasHandler.resolvePlanet(where.toLowerCase()))) {
                errors.add("preset: `tokens` target `" + where + "` is neither a tile position nor a planet.");
            }
            validateIds(entry.getValue(), TestBedPresetService::isKnownToken, "preset", "token", errors);
        }
        for (Seat seat : preset.allSeats()) {
            if (seat.getScoredObjectives() == null) continue;
            for (String objective : seat.getScoredObjectives()) {
                if (!preset.getRevealedObjectives().contains(objective)) {
                    errors.add("Objective `" + objective + "` is scored but not in `revealedObjectives`.");
                }
            }
        }
    }

    static boolean isKnownToken(String token) {
        return Mapper.getAttachmentImagePath(token) != null
                || Mapper.getTokenID(AliasHandler.resolveToken(token)) != null;
    }

    private static void validateCardIds(
            @Nullable CardPick pick, Predicate<String> isValid, String label, String kind, List<String> errors) {
        if (pick == null) return;
        if (pick.random() < 0) errors.add(label + ": negative " + kind + " count.");
        validateIds(pick.ids(), isValid, label, kind, errors);
    }

    private static void validateIds(
            @Nullable List<String> ids, Predicate<String> isValid, String label, String kind, List<String> errors) {
        if (ids == null) return;
        for (String id : ids) {
            if (!isValid.test(id)) errors.add(label + ": unknown " + kind + " `" + id + "`.");
        }
    }
}
