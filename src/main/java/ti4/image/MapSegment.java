package ti4.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        FRACTURE
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
    private static final String CLUSTER_TOKEN = "c";
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9-]{1,20}");
    private static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");
    private static final List<String> FRACTURE_POSITIONS =
            IntStream.rangeClosed(1, 25).mapToObj(i -> "frac" + i).toList();

    public MapSegment(String name, String centre, int radius) {
        this(name, centre, radius, Kind.CIRCLE, null);
    }

    public static MapSegment cluster(String name, String centre, int radiusCap) {
        return new MapSegment(name, centre, radiusCap, Kind.CLUSTER, null);
    }

    public static boolean isValidName(@Nullable String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isReservedName(String name) {
        return MAIN.equals(name) || FRACTURE.equals(name);
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

    private static Optional<MapSegment> fractureSegment(Game game) {
        if (!game.isFowMode() || !game.getFowOption(FOWOption.FRACTURE_SEPARATE_MAP)) {
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
        List<MapSegment> sectors = new ArrayList<>();
        Set<String> takenNames = new HashSet<>(Set.of(MAIN, FRACTURE));
        named.forEach(segment -> takenNames.add(segment.name()));
        for (Set<String> cluster : MapFrame.clusters(placedGridPositions(game), gap(game) + 1)) {
            boolean coveredByNamedSegment =
                    named.stream().anyMatch(segment -> !Collections.disjoint(segment.positions(), cluster));
            if (!coveredByNamedSegment) {
                String name = autoSectorName(game, cluster, takenNames);
                takenNames.add(name);
                sectors.add(new MapSegment(name, "", 0, Kind.AUTO, cluster));
            }
        }
        return sectors;
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

    private MapSegment resolve(Game game) {
        if (kind != Kind.CLUSTER) {
            return this;
        }
        Set<String> placed = placedGridPositions(game);
        if (!placed.contains(centre)) {
            return new MapSegment(name, centre, radius, kind, Set.of(centre));
        }
        Set<String> cluster = new HashSet<>(MapFrame.cluster(placed, centre, gap(game) + 1));
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
        if (name.equals(game.getStoredValue(DEFAULT_KEY))) {
            game.removeStoredValue(DEFAULT_KEY);
        }
        return removed;
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

    static Set<String> knownPositions(Game game, Player player) {
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

    public boolean isDerivedFromMap() {
        return kind == Kind.CLUSTER || kind == Kind.AUTO;
    }

    public Set<String> positions() {
        return fixedPositions != null ? fixedPositions : MapFrame.positionsWithin(centre, radius);
    }

    public String describe() {
        return switch (kind) {
            case FRACTURE -> "`" + name + "`: the Fracture (built in, from the FoW option)";
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
