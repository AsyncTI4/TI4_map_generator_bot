package ti4.image.map;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.tile.TileGeometry;
import ti4.model.BoardPosition;
import ti4.model.MapPositionMapper;
import ti4.service.option.FOWOptionService.FOWOption;

final class FrameResolver {

    record Framing(
            @Nullable Rectangle content,
            @Nullable MapSegment shownSegment,
            @Nullable Set<String> segmentDrawPositions,
            Set<String> knownSystems,
            Set<String> framedStatFactions,
            Set<String> pinnedCorners) {}

    private static final int CORNER_MARGIN = 20;
    private static final int SEGMENT_LABEL_SPACE = 150;

    private final Game game;
    private final DisplayType displayType;

    @Nullable
    private final String requestedSegment;

    private final FogView fog;
    private final MapSideStatLayout stats;
    private final int fractureYbump;

    @Nullable
    private MapSegment shownSegment;

    @Nullable
    private Set<String> segmentDrawPositions;

    private Set<String> knownSystems = Set.of();
    private final Set<String> framedStatFactions = new HashSet<>();
    private Set<String> pinnedCorners = Set.of();

    FrameResolver(
            Game game,
            DisplayType displayType,
            @Nullable String requestedSegment,
            FogView fog,
            MapSideStatLayout stats,
            int fractureYbump) {
        this.game = game;
        this.displayType = displayType;
        this.requestedSegment = requestedSegment;
        this.fog = fog;
        this.stats = stats;
        this.fractureYbump = fractureYbump;
    }

    Framing resolve() {
        Rectangle frameBounds = computeFrameBounds();
        if (frameBounds != null && BoardLayout.segmentLabel(game, fog, shownSegment, knownSystems, true) != null) {
            frameBounds.y -= SEGMENT_LABEL_SPACE;
            frameBounds.height += SEGMENT_LABEL_SPACE;
        }
        if (frameBounds != null) {
            pinnedCorners = cornersToPin();
            makeRoomForPinnedCorners(frameBounds);
        }
        return new Framing(
                frameBounds, shownSegment, segmentDrawPositions, knownSystems, framedStatFactions, pinnedCorners);
    }

    @Nullable
    private Rectangle computeFrameBounds() {
        if (!game.isFowMode()
                || game.getFowOption(FOWOption.CLASSIC_MAP_LAYOUT)
                || displayType == DisplayType.landscape
                || displayType == DisplayType.stats) {
            return null;
        }
        knownSystems = fog.seenPositions();
        Set<String> known = new HashSet<>(knownSystems);
        if (MapSideStatsSection.drawsPlayerInfo(game, displayType, fog)) {
            known.addAll(stats.tilePositions());
        }
        Set<String> positions = segmentPositions(known);
        if (positions == null) {
            positions = positionsWithinCap(withoutDetachedMaps(known));
        }
        return MapFrame.bounds(game, positions, fractureYbump, BoardLayout.EXTRA_X, BoardLayout.EXTRA_Y);
    }

    private Set<String> cornersToPin() {
        if (isShowingSeparateFracture()) {
            return Set.of();
        }
        Set<String> corners = new HashSet<>();
        for (Map.Entry<String, Tile> entry : fog.tiles().entrySet()) {
            String position = entry.getKey();
            if (BoardLayout.CORNER_POSITIONS.contains(position.toLowerCase())
                    && !fog.isUnseenBlankFogTile(entry.getValue())) {
                corners.add(position);
            }
        }
        return corners;
    }

    private boolean isShowingSeparateFracture() {
        return shownSegment != null && shownSegment.isDetached();
    }

    private void makeRoomForPinnedCorners(Rectangle bounds) {
        int column = TileGeometry.TILE_WIDTH + CORNER_MARGIN;
        if (isPinned("tl") || isPinned("bl")) {
            bounds.x -= column;
            bounds.width += column;
        }
        if (isPinned("tr") || isPinned("br")) {
            bounds.width += column;
        }
    }

    private boolean isPinned(String corner) {
        return pinnedCorners.stream().anyMatch(corner::equalsIgnoreCase);
    }

    private Set<String> withoutDetachedMaps(Set<String> positions) {
        Set<String> mainMap = new HashSet<>(positions);
        mainMap.removeIf(position -> MapSegment.isDetachedPosition(game, position));
        return mainMap;
    }

    @Nullable
    private Set<String> segmentPositions(Set<String> known) {
        MapSegment segment = selectSegment(knownSystems);
        shownSegment = segment;
        if (segment == null) {
            return null;
        }
        Set<String> inSegment = segment.positions();
        if (!fog.isPrivate()) {
            segmentDrawPositions = inSegment;
            return withNearbyStatTiles(inSegment);
        }
        Set<String> knownInSegment = new HashSet<>(known);
        knownInSegment.retainAll(inSegment);
        if (knownInSegment.isEmpty() && segment.isDerivedFromMap()) {
            return null;
        }
        if (segment.isDerivedFromMap()) {
            segmentDrawPositions = knownInSegment;
            return withNearbyStatTiles(knownInSegment);
        }
        segmentDrawPositions = inSegment;
        return withNearbyStatTiles(knownInSegment.isEmpty() ? inSegment : knownInSegment);
    }

    private Set<String> withNearbyStatTiles(Set<String> positions) {
        if (!MapSideStatsSection.drawsPlayerInfo(game, displayType, fog)) {
            return positions;
        }
        Set<String> withStats = new HashSet<>(positions);
        MapSideStatLayout.Layout layout = stats.get();
        if (!layout.useNewSystem()) {
            return withStats;
        }
        for (Map.Entry<String, List<String>> statBlock :
                layout.playerStatTiles().entrySet()) {
            boolean touchesSegment = statBlock.getValue().stream()
                    .flatMap(statTile -> MapPositionMapper.getAdjacentTilePositions(statTile).stream())
                    .anyMatch(positions::contains);
            if (touchesSegment) {
                withStats.addAll(statBlock.getValue());
                framedStatFactions.add(statBlock.getKey());
            }
        }
        return withStats;
    }

    @Nullable
    private MapSegment selectSegment(Set<String> known) {
        if (MapSegment.MAIN.equals(requestedSegment) && knowsGalaxySystems(known)) {
            return null;
        }
        List<MapSegment> visibleWithFracture =
                fog.isPrivate() ? MapSegment.visibleFrom(game, known) : MapSegment.all(game);
        if (requestedSegment != null) {
            Optional<MapSegment> requested = visibleWithFracture.stream()
                    .filter(segment -> segment.name().equals(requestedSegment))
                    .findFirst();
            if (requested.isPresent()) {
                return requested.get();
            }
        }
        List<MapSegment> visible = visibleWithFracture.stream()
                .filter(segment -> !segment.isDetached())
                .toList();
        if (visible.isEmpty()) {
            return knowsGalaxySystems(known) ? null : detachedViewFor(visibleWithFracture);
        }
        Optional<MapSegment> defaultSegment = MapSegment.defaultSegment(game).filter(visible::contains);
        if (!fog.isPrivate()) {
            return defaultSegment.orElse(visible.getFirst());
        }
        String home = fog.homeSystemPosition();
        if (requestedSegment == null
                && home != null
                && known.contains(home)
                && MapSegment.isOnUncoveredMainMap(game, home)) {
            return null;
        }
        return visible.stream()
                .filter(segment -> home != null && segment.positions().contains(home))
                .findFirst()
                .or(() -> defaultSegment)
                .orElseGet(() -> visible.stream()
                        .max(Comparator.comparingLong(segment -> segment.positions().stream()
                                .filter(known::contains)
                                .count()))
                        .orElseThrow());
    }

    @Nullable
    private MapSegment detachedViewFor(List<MapSegment> segments) {
        String home = fog.homeSystemPosition();
        List<MapSegment> detached =
                segments.stream().filter(MapSegment::isDetached).toList();
        return detached.stream()
                .filter(segment -> home != null && segment.positions().contains(home))
                .findFirst()
                .orElse(detached.isEmpty() ? null : detached.getFirst());
    }

    private static boolean knowsGalaxySystems(Set<String> known) {
        return known.stream()
                .anyMatch(position -> !MapSegment.isFracturePosition(position)
                        && !BoardPosition.isBoardPosition(position)
                        && !BoardLayout.CORNER_POSITIONS.contains(position.toLowerCase()));
    }

    private Set<String> positionsWithinCap(Set<String> known) {
        Rectangle bounds = MapFrame.bounds(game, known, fractureYbump, BoardLayout.EXTRA_X, BoardLayout.EXTRA_Y);
        if (bounds == null || MapFrame.fitsCap(bounds)) {
            return known;
        }
        int reach = MapSegment.gap(game) + 1;
        Map<String, Set<String>> links = MapSegment.adjacencyLinks(game, known);
        String home = fog.homeSystemPosition();
        if (home != null && known.contains(home)) {
            return MapFrame.cluster(known, home, reach, links);
        }
        return MapFrame.largestCluster(known, reach, links);
    }
}
