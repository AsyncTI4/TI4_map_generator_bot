package ti4.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.service.option.FOWOptionService.FOWOption;

public record MapSegment(
        String name,
        String centre,
        int radius,
        Kind kind,
        @Nullable Set<String> fixedPositions) {

    public enum Kind {
        CIRCLE,
        CLUSTER,
        AUTO,
        FRACTURE,
        BOARD
    }

    public static final int MAX_RADIUS = 9;
    public static final int MAX_SEGMENTS = 20;
    public static final int MAX_GAP = 2;
    public static final String MAIN = "main";
    public static final String FRACTURE = "fracture";
    private static final String STORAGE_KEY = "fowMapSegments";
    private static final String DEFAULT_KEY = "fowMapSegmentDefault";
    private static final String AUTO_KEY = "fowMapAutoSectors";
    private static final String GAP_KEY = "fowMapSegmentGap";
    private static final String PIN_KEY = "fowMapSectorNames";
    private static final String CLUSTER_TOKEN = "c";
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9-]{1,20}");
    private static final Pattern BOARD_NAME_PATTERN = Pattern.compile("board-[a-g]");
    private static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");
    private static final List<String> FRACTURE_POSITIONS =
            IntStream.rangeClosed(1, 25).mapToObj(i -> "frac" + i).toList();

    public MapSegment(String name, String centre, int radius) {
        this(name, centre, radius, Kind.CIRCLE, null);
    }

    public static MapSegment cluster(String name, String centre, int radiusCap) {
        return new MapSegment(name, centre, radiusCap, Kind.CLUSTER, null);
    }

    public record Dormant(String name, @Nullable String mergedInto) {}

    private record Pin(String name, Set<String> positions) {}

    private static List<Pin> pins(Game game) {
        String stored = game.getStoredValue(PIN_KEY);
        if (StringUtils.isBlank(stored)) {
            return List.of();
        }
        return Arrays.stream(stored.split(";"))
                .map(MapSegment::parsePin)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<Pin> parsePin(String entry) {
        String name = StringUtils.substringBefore(entry, "=");
        Set<String> positions = Arrays.stream(
                        StringUtils.substringAfter(entry, "=").split(","))
                .map(String::trim)
                .filter(position -> PositionMapper.getTilePosition(position) != null)
                .collect(Collectors.toSet());
        if (!isValidName(name) || isReservedName(name) || positions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Pin(name, positions));
    }

    private static void savePins(Game game, List<Pin> pins) {
        game.setStoredValue(
                PIN_KEY,
                pins.stream()
                        .map(pin -> pin.name() + "="
                                + pin.positions().stream()
                                        .sorted(MapFrame.POSITION_ORDER)
                                        .collect(Collectors.joining(",")))
                        .collect(Collectors.joining(";")));
    }

    public static boolean isValidName(@Nullable String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isReservedName(String name) {
        return MAIN.equals(name)
                || FRACTURE.equals(name)
                || BOARD_NAME_PATTERN.matcher(name).matches();
    }

    // TODO: clusters are recomputed on every call (several per render); cache per game keyed on occupied positions
    public static List<MapSegment> all(Game game) {
        List<MapSegment> segments = new ArrayList<>();
        for (MapSegment segment : stored(game)) {
            segments.add(segment.resolve(game));
        }
        if (isAutoSectors(game)) {
            segments.addAll(autoSectors(game, segments));
        }
        segments.addAll(boardSegments(game, segments));
        fractureSegment(game).ifPresent(segments::add);
        return segments;
    }

    public static List<MapSegment> stored(Game game) {
        String stored = game.getStoredValue(STORAGE_KEY);
        if (StringUtils.isBlank(stored)) {
            return List.of();
        }
        return Arrays.stream(stored.split(";"))
                .map(MapSegment::parse)
                .flatMap(Optional::stream)
                .toList();
    }

    public static boolean isAutoSectors(Game game) {
        return Boolean.parseBoolean(game.getStoredValue(AUTO_KEY));
    }

    public static void setAutoSectors(Game game, boolean enabled) {
        game.setStoredValue(AUTO_KEY, enabled ? "true" : "");
    }

    public static int gap(Game game) {
        try {
            return Math.clamp(Integer.parseInt(game.getStoredValue(GAP_KEY)), 0, MAX_GAP);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void setGap(Game game, int gap) {
        game.setStoredValue(GAP_KEY, gap == 0 ? "" : String.valueOf(Math.clamp(gap, 0, MAX_GAP)));
    }

    public static boolean isFractureSeparate(Game game) {
        return fractureSegment(game).isPresent();
    }

    public static boolean isFracturePosition(String position) {
        return position.startsWith("frac");
    }

    public static boolean isDetachedPosition(Game game, String position) {
        if (isFracturePosition(position)) {
            return isFractureSeparate(game);
        }
        return game.isFowMode() && BoardPosition.isBoardPosition(position);
    }

    private static List<MapSegment> boardSegments(Game game, List<MapSegment> named) {
        if (!game.isFowMode()) {
            return List.of();
        }
        Map<Character, Set<String>> positionsByBoard = new TreeMap<>();
        for (String position : game.getTileMap().keySet()) {
            boolean covered =
                    named.stream().anyMatch(segment -> segment.positions().contains(position));
            BoardPosition.parse(position)
                    .filter(board -> !covered)
                    .ifPresent(board -> positionsByBoard
                            .computeIfAbsent(board.board(), key -> new HashSet<>())
                            .add(position));
        }
        return positionsByBoard.entrySet().stream()
                .map(entry -> new MapSegment(
                        BoardPosition.defaultSegmentName(entry.getKey()),
                        entry.getKey() + "000",
                        0,
                        Kind.BOARD,
                        entry.getValue()))
                .toList();
    }

    private static Optional<MapSegment> fractureSegment(Game game) {
        if (!game.isFowMode() || !game.getFowOption(FOWOption.FRACTURE_SEPARATE_MAP) && !isAutoSectors(game)) {
            return Optional.empty();
        }
        Set<String> placed = FRACTURE_POSITIONS.stream()
                .filter(position -> game.getTileByPosition(position) != null)
                .collect(Collectors.toSet());
        return placed.isEmpty()
                ? Optional.empty()
                : Optional.of(new MapSegment(FRACTURE, "frac1", 0, Kind.FRACTURE, placed));
    }

    private static List<MapSegment> autoSectors(Game game, List<MapSegment> named) {
        Set<String> takenNames = new HashSet<>(Set.of(MAIN, FRACTURE));
        named.forEach(segment -> takenNames.add(segment.name()));
        List<Pin> pins = pins(game);
        pins.forEach(pin -> takenNames.add(pin.name()));
        List<Pin> usablePins = pins.stream()
                .filter(pin ->
                        named.stream().noneMatch(segment -> segment.name().equals(pin.name())))
                .toList();
        List<Set<String>> clusters = uncoveredClusters(game, named);
        Map<Integer, Pin> pinByCluster = assignPins(clusters, usablePins);
        List<MapSegment> sectors = new ArrayList<>();
        for (int index = 0; index < clusters.size(); index++) {
            Set<String> cluster = clusters.get(index);
            Pin pin = pinByCluster.get(index);
            String name = pin != null ? pin.name() : autoSectorName(game, cluster, takenNames);
            takenNames.add(name);
            sectors.add(new MapSegment(name, "", 0, Kind.AUTO, cluster));
        }
        return sectors;
    }

    private static List<Set<String>> uncoveredClusters(Game game, List<MapSegment> named) {
        Set<String> placed = placedGridPositions(game);
        return MapFrame.clusters(placed, gap(game) + 1, adjacencyLinks(game, placed)).stream()
                .filter(cluster ->
                        named.stream().allMatch(segment -> Collections.disjoint(segment.positions(), cluster)))
                .toList();
    }

    private static Map<Integer, Pin> assignPins(List<Set<String>> clusters, List<Pin> pins) {
        record Match(int pin, int cluster, long overlap) {}
        List<Match> matches = new ArrayList<>();
        for (int pin = 0; pin < pins.size(); pin++) {
            for (int cluster = 0; cluster < clusters.size(); cluster++) {
                long overlap = overlap(pins.get(pin).positions(), clusters.get(cluster));
                if (overlap > 0) {
                    matches.add(new Match(pin, cluster, overlap));
                }
            }
        }
        matches.sort(Comparator.comparingLong(Match::overlap).reversed().thenComparingInt(Match::pin));
        Map<Integer, Pin> pinByCluster = new HashMap<>();
        Set<Integer> usedPins = new HashSet<>();
        for (Match match : matches) {
            if (!pinByCluster.containsKey(match.cluster()) && usedPins.add(match.pin())) {
                pinByCluster.put(match.cluster(), pins.get(match.pin()));
            }
        }
        return pinByCluster;
    }

    private static long overlap(Set<String> a, Set<String> b) {
        return a.stream().filter(b::contains).count();
    }

    private static String autoSectorName(Game game, Set<String> cluster, Set<String> takenNames) {
        String anchor = cluster.stream().min(MapFrame.POSITION_ORDER).orElse("");
        List<String> words = SectorNames.NAMES;
        int start = Math.floorMod((game.getName() + ":" + anchor).hashCode(), words.size());
        for (int offset = 0; offset < words.size(); offset++) {
            String word = words.get((start + offset) % words.size());
            if (!takenNames.contains(word)) {
                return word;
            }
        }
        String base = words.get(start);
        int suffix = 2;
        while (takenNames.contains(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private static Set<String> placedGridPositions(Game game) {
        return game.getTileMap().keySet().stream()
                .filter(position -> !isFracturePosition(position))
                .filter(position -> !CORNER_POSITIONS.contains(position.toLowerCase()))
                .filter(position -> PositionMapper.getTilePosition(position) != null)
                .collect(Collectors.toSet());
    }

    static Map<String, Set<String>> adjacencyLinks(Game game, Set<String> placed) {
        Map<String, Set<String>> links = new HashMap<>();
        game.getCustomAdjacentTiles().forEach((from, targets) -> targets.forEach(to -> link(links, placed, from, to)));
        game.getAdjacentTileOverrides().forEach((side, to) -> link(links, placed, side.getLeft(), to));
        return links;
    }

    private static void link(Map<String, Set<String>> links, Set<String> placed, String from, String to) {
        if (!placed.contains(from)
                || !placed.contains(to)
                || BoardPosition.boardOf(from) != BoardPosition.boardOf(to)) {
            return;
        }
        links.computeIfAbsent(from, key -> new HashSet<>()).add(to);
        links.computeIfAbsent(to, key -> new HashSet<>()).add(from);
    }

    private MapSegment resolve(Game game) {
        if (kind != Kind.CLUSTER) {
            return this;
        }
        Set<String> placed = placedGridPositions(game);
        if (!placed.contains(centre)) {
            return new MapSegment(name, centre, radius, kind, Set.of(centre));
        }
        Set<String> cluster =
                new HashSet<>(MapFrame.cluster(placed, centre, gap(game) + 1, adjacencyLinks(game, placed)));
        if (radius > 0) {
            cluster.retainAll(MapFrame.positionsWithin(centre, radius));
        }
        return new MapSegment(name, centre, radius, kind, cluster);
    }

    public static Optional<MapSegment> find(Game game, String name) {
        return all(game).stream().filter(segment -> segment.name().equals(name)).findFirst();
    }

    public static void put(Game game, MapSegment segment) {
        List<MapSegment> segments = new ArrayList<>(stored(game));
        segments.removeIf(existing -> existing.name().equals(segment.name()));
        segments.add(segment);
        save(game, segments);
    }

    public static boolean remove(Game game, String name) {
        List<MapSegment> segments = new ArrayList<>(stored(game));
        boolean removed = segments.removeIf(existing -> existing.name().equals(name));
        save(game, segments);
        List<Pin> pins = new ArrayList<>(pins(game));
        removed |= pins.removeIf(pin -> pin.name().equals(name));
        savePins(game, pins);
        if (name.equals(game.getStoredValue(DEFAULT_KEY))) {
            game.removeStoredValue(DEFAULT_KEY);
        }
        return removed;
    }

    @Nullable
    public static String rename(Game game, String from, String to) {
        if (!isValidName(to)) {
            return "Segment names use lowercase letters, digits and `-`, up to 20 characters.";
        }
        if (isReservedName(from) || isReservedName(to)) {
            return "`" + MAIN + "` and `" + FRACTURE + "` are built in and cannot be renamed or reused.";
        }
        List<MapSegment> live = all(game);
        if (from.equals(to) || live.stream().anyMatch(segment -> segment.name().equals(to))) {
            return "There is already a segment called `" + to + "`.";
        }
        Optional<MapSegment> current =
                live.stream().filter(segment -> segment.name().equals(from)).findFirst();
        List<Pin> pins = new ArrayList<>(pins(game));
        boolean pinned = pins.stream().anyMatch(pin -> pin.name().equals(from));
        if (current.isEmpty() && !pinned) {
            return "No segment called `" + from + "`.";
        }
        boolean storedSegment = current.isPresent() && current.get().kind() != Kind.AUTO;
        if (!storedSegment && !pinned && pins.size() >= MAX_SEGMENTS) {
            return "This game already has the maximum of " + MAX_SEGMENTS + " renamed sectors.";
        }
        pins.removeIf(pin -> pin.name().equals(to));
        if (storedSegment) {
            renameStored(game, from, to);
        } else {
            Set<String> positions = current.isPresent()
                    ? current.get().positions()
                    : pins.stream()
                            .filter(pin -> pin.name().equals(from))
                            .findFirst()
                            .orElseThrow()
                            .positions();
            pins.replaceAll(pin -> pin.name().equals(from) ? new Pin(to, positions) : pin);
            if (!pinned) {
                pins.add(new Pin(to, positions));
            }
        }
        savePins(game, pins);
        if (from.equals(game.getStoredValue(DEFAULT_KEY))) {
            setDefault(game, to);
        }
        return null;
    }

    public static List<Dormant> dormantNames(Game game) {
        if (!isAutoSectors(game)) {
            return List.of();
        }
        List<MapSegment> live = all(game);
        return pins(game).stream()
                .filter(pin -> live.stream().noneMatch(segment -> segment.name().equals(pin.name())))
                .map(pin -> new Dormant(pin.name(), largestOverlap(live, pin.positions())))
                .toList();
    }

    @Nullable
    private static String largestOverlap(List<MapSegment> segments, Set<String> positions) {
        return segments.stream()
                .filter(segment -> overlap(segment.positions(), positions) > 0)
                .max(Comparator.comparingLong(segment -> overlap(segment.positions(), positions)))
                .map(MapSegment::name)
                .orElse(null);
    }

    private static void renameStored(Game game, String from, String to) {
        save(
                game,
                stored(game).stream()
                        .map(segment -> segment.name().equals(from)
                                ? new MapSegment(to, segment.centre(), segment.radius(), segment.kind(), null)
                                : segment)
                        .toList());
    }

    public static Optional<MapSegment> defaultSegment(Game game) {
        return find(game, game.getStoredValue(DEFAULT_KEY));
    }

    public static void setDefault(Game game, String name) {
        game.setStoredValue(DEFAULT_KEY, name);
    }

    public static void clearDefault(Game game) {
        game.removeStoredValue(DEFAULT_KEY);
    }

    public static List<MapSegment> visibleTo(Game game, @Nullable Player viewer) {
        if (viewer == null) {
            return all(game);
        }
        return visibleFrom(game, knownPositions(game, viewer));
    }

    static List<MapSegment> visibleFrom(Game game, Collection<String> knownPositions) {
        return all(game).stream()
                .filter(segment -> !Collections.disjoint(segment.positions(), knownPositions))
                .toList();
    }

    public static Set<String> knownPositions(Game game, Player player) {
        Set<String> known = new HashSet<>(FoWHelper.getTilePositionsToShow(game, player));
        new HashMap<>(player.getFogTiles()).forEach((position, tileId) -> {
            if (!"0b".equals(tileId)
                    || StringUtils.isNotBlank(player.getFogLabels().get(position))) {
                known.add(position);
            }
        });
        return known;
    }

    public static Set<String> positionsAround(String centre, int radius) {
        return MapFrame.positionsWithin(centre, radius);
    }

    public boolean isFracture() {
        return kind == Kind.FRACTURE;
    }

    public boolean isDetached() {
        return kind == Kind.FRACTURE || kind == Kind.BOARD;
    }

    public String displayName(Game game) {
        Set<Character> boards = positions().stream().map(BoardPosition::boardOf).collect(Collectors.toSet());
        if (boards.size() != 1) {
            return name;
        }
        char board = boards.iterator().next();
        if (board == BoardPosition.MAIN_BOARD) {
            return GalaxyNames.isMultiGalaxy(game) ? mainDisplayName(game) + " / " + name : name;
        }
        String galaxy = GalaxyNames.name(game, GalaxyNames.idOf(board));
        return kind == Kind.BOARD ? galaxy : galaxy + " / " + name;
    }

    public static String mainDisplayName(Game game) {
        return GalaxyNames.isMultiGalaxy(game) ? GalaxyNames.name(game, GalaxyNames.MAIN_ID) : MAIN;
    }

    public static boolean mainMapVisibleTo(Game game, @Nullable Player viewer) {
        Set<String> uncovered = uncoveredMainPositions(game);
        if (viewer == null) {
            return !uncovered.isEmpty();
        }
        return !Collections.disjoint(uncovered, knownPositions(game, viewer));
    }

    public static boolean isOnUncoveredMainMap(Game game, String position) {
        return uncoveredMainPositions(game).contains(position);
    }

    static Set<String> uncoveredMainPositions(Game game) {
        return uncoveredMainPositions(game, all(game));
    }

    static Set<String> uncoveredMainPositions(Game game, List<MapSegment> segments) {
        return game.getTileMap().keySet().stream()
                .filter(position -> !isDetachedPosition(game, position))
                .filter(position -> !CORNER_POSITIONS.contains(position.toLowerCase()))
                .filter(position -> segments.stream()
                        .noneMatch(segment -> segment.positions().contains(position)))
                .collect(Collectors.toSet());
    }

    public boolean isDerivedFromMap() {
        return kind == Kind.CLUSTER || kind == Kind.AUTO;
    }

    public Set<String> positions() {
        return fixedPositions != null ? fixedPositions : MapFrame.positionsWithin(centre, radius);
    }

    public String describe() {
        return switch (kind) {
            case FRACTURE -> "`" + name + "`: the Fracture (built in, from the FoW option)";
            case BOARD ->
                "`" + name + "`: extra board " + Character.toUpperCase(centre.charAt(0)) + " ("
                        + positions().size() + " systems)";
            case AUTO -> "`" + name + "`: automatic sector (" + positions().size() + " systems)";
            case CLUSTER ->
                "`" + name + "`: cluster around " + centre + (radius > 0 ? ", at most radius " + radius : "")
                        + (fixedPositions == null ? "" : " (" + fixedPositions.size() + " systems)");
            case CIRCLE -> "`" + name + "`: centre " + centre + ", radius " + radius;
        };
    }

    private static void save(Game game, List<MapSegment> segments) {
        game.setStoredValue(
                STORAGE_KEY, segments.stream().map(MapSegment::format).collect(Collectors.joining(";")));
    }

    private String format() {
        if (kind == Kind.CLUSTER) {
            return name + "=" + centre + ":" + CLUSTER_TOKEN + (radius > 0 ? radius : "");
        }
        return name + "=" + centre + ":" + radius;
    }

    private static Optional<MapSegment> parse(String entry) {
        String name = StringUtils.substringBefore(entry, "=");
        String centre = StringUtils.substringBetween(entry, "=", ":");
        String extent = StringUtils.substringAfterLast(entry, ":").trim();
        if (!isValidName(name)
                || isReservedName(name)
                || centre == null
                || PositionMapper.getTilePosition(centre) == null) {
            return Optional.empty();
        }
        boolean cluster = extent.startsWith(CLUSTER_TOKEN);
        String radiusText = cluster ? extent.substring(CLUSTER_TOKEN.length()) : extent;
        if (cluster && radiusText.isEmpty()) {
            return Optional.of(cluster(name, centre, 0));
        }
        try {
            int radius = Integer.parseInt(radiusText);
            if (radius < 0 || radius > MAX_RADIUS) {
                return Optional.empty();
            }
            return Optional.of(cluster ? cluster(name, centre, radius) : new MapSegment(name, centre, radius));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
