package ti4.image.map;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.image.playerarea.PlayerAreaColumns;
import ti4.image.tile.TileGeometry;
import ti4.model.BoardPosition;
import ti4.model.PositionMapper;
import ti4.service.map.FractureService;
import ti4.service.option.FOWOptionService.FOWOption;

final class BoardLayout {

    static final int RING_MAX_COUNT = 8;
    static final int RING_MIN_COUNT = 3;
    static final int EXTRA_X = 300;
    static final int EXTRA_Y = 200;
    static final int PLAYER_AREAS_LEFT = 10;
    static final int MINIMUM_WIDTH_OF_PLAYER_AREA = 1000;
    static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");
    private static final int FRACTURE_BUMP = 400;
    private static final int EXPANDED_FRACTURE_BUMP = 600;

    private static final int PLAYER_STATS_HEIGHT = 650;
    private static final int CORNER_MARGIN = 20;
    private static final int CORNER_TOP = 60;

    private final Game game;
    private final FogView fog;
    private final int fractureYbump;

    @Nullable
    private final MapFrame mapFrame;

    private final FrameResolver.Framing framing;
    private final int mapWidth;
    private final int framedWidth;
    private final int mapHeight;
    private final List<SectorConnections.Connection> connections;
    private final int connectionStripTop;

    private BoardLayout(
            Game game,
            FogView fog,
            int fractureYbump,
            @Nullable MapFrame mapFrame,
            FrameResolver.Framing framing,
            int mapWidth,
            int framedWidth,
            int mapHeight,
            List<SectorConnections.Connection> connections,
            int connectionStripTop) {
        this.game = game;
        this.fog = fog;
        this.fractureYbump = fractureYbump;
        this.mapFrame = mapFrame;
        this.framing = framing;
        this.mapWidth = mapWidth;
        this.framedWidth = framedWidth;
        this.mapHeight = mapHeight;
        this.connections = connections;
        this.connectionStripTop = connectionStripTop;
    }

    static BoardLayout resolve(
            Game game,
            DisplayType displayType,
            DisplayType basicType,
            @Nullable String requestedSegment,
            FogView fog,
            MapSideStatLayout stats,
            int scoreTokenSpacing) {
        int fractureYbump = fractureYbump(game);
        int mapHeight = getMapHeight(game) + fractureYbump;

        FrameResolver.Framing framing =
                new FrameResolver(game, displayType, requestedSegment, fog, stats, fractureYbump).resolve();
        Rectangle frameBounds = framing.content();

        int mapWidth = Math.max(
                MINIMUM_WIDTH_OF_PLAYER_AREA,
                frameBounds == null
                        ? getMapWidth(game) + extraBoardsWidth(game)
                        : layoutWidthFor(game, frameBounds.width));
        MapFrame mapFrame = frameBounds == null
                ? null
                : MapFrame.fit(
                        frameBounds,
                        basicType == DisplayType.map ? mapOnlyMinimumWidth(game, scoreTokenSpacing) : mapWidth,
                        MapFrame.hexBounds(game, fog.homeSystemPosition(), fractureYbump, EXTRA_X, EXTRA_Y));
        int framedWidth = mapWidth;
        List<SectorConnections.Connection> connections = List.of();
        int connectionStripTop = 0;
        if (mapFrame != null) {
            mapHeight = mapFrame.height();
            framedWidth = mapFrame.width();
            connections = sectorConnections(game, fog, framing);
            connectionStripTop = mapHeight;
            mapHeight += ConnectionStripSection.height(connections, framedWidth);
        }
        return new BoardLayout(
                game,
                fog,
                fractureYbump,
                mapFrame,
                framing,
                mapWidth,
                framedWidth,
                mapHeight,
                connections,
                connectionStripTop);
    }

    private static List<SectorConnections.Connection> sectorConnections(
            Game game, FogView fog, FrameResolver.Framing framing) {
        if (!fog.isPrivate() || fog.viewer() == null || !game.getFowOption(FOWOption.MAP_CONNECTIONS)) {
            return List.of();
        }
        return SectorConnections.find(
                game, fog.viewer(), framing.shownSegment(), fog.visibleSystems(), framing.knownSystems());
    }

    @Nullable
    static String segmentLabel(
            Game game, FogView fog, @Nullable MapSegment shownSegment, Set<String> knownSystems, boolean framed) {
        if (shownSegment != null
                && (shownSegment.isDetached() || segmentsVisibleToViewer(game, fog, knownSystems) > 1)) {
            return shownSegment.displayName(game);
        }
        if (!framed) {
            return null;
        }
        boolean otherViews = segmentsVisibleToViewer(game, fog, knownSystems) > 0
                || MapSegment.all(game).stream().anyMatch(MapSegment::isDetached);
        return otherViews ? MapSegment.mainDisplayName(game) : null;
    }

    private static int segmentsVisibleToViewer(Game game, FogView fog, Set<String> knownSystems) {
        return fog.isPrivate()
                ? MapSegment.visibleFrom(game, knownSystems).size()
                : MapSegment.all(game).size();
    }

    @Nullable
    String segmentLabel() {
        return segmentLabel(game, fog, framing.shownSegment(), framing.knownSystems(), mapFrame != null);
    }

    int fractureYbump() {
        return fractureYbump;
    }

    static int fractureYbump(Game game) {
        int bump = FractureService.isFractureRegionOnMap(game) ? FRACTURE_BUMP : 0;
        return FractureService.isFractureExpandedRegionOnMap(game) ? bump + EXPANDED_FRACTURE_BUMP : bump;
    }

    @Nullable
    MapFrame mapFrame() {
        return mapFrame;
    }

    int mapWidth() {
        return mapWidth;
    }

    int framedWidth() {
        return framedWidth;
    }

    int mapHeight() {
        return mapHeight;
    }

    List<SectorConnections.Connection> connections() {
        return connections;
    }

    int connectionStripTop() {
        return connectionStripTop;
    }

    @Nullable
    MapSegment shownSegment() {
        return framing.shownSegment();
    }

    @Nullable
    String shownSegmentName() {
        MapSegment shownSegment = framing.shownSegment();
        return shownSegment == null ? null : shownSegment.name();
    }

    Set<String> pinnedCorners() {
        return framing.pinnedCorners();
    }

    Set<String> framedStatFactions() {
        return framing.framedStatFactions();
    }

    int frameContentWidth() {
        return framing.content() == null ? 0 : framing.content().width;
    }

    boolean isShowingSeparateFracture() {
        return framing.shownSegment() != null && framing.shownSegment().isDetached();
    }

    int frameOffsetX() {
        return mapFrame == null ? 0 : mapFrame.offsetX();
    }

    int frameOffsetY() {
        return mapFrame == null ? 0 : mapFrame.offsetY();
    }

    boolean isInShownRegion(@Nullable String position) {
        if (position == null || mapFrame == null || framing.pinnedCorners().contains(position)) {
            return true;
        }
        if (framing.segmentDrawPositions() != null) {
            return framing.segmentDrawPositions().contains(position);
        }
        return !MapSegment.isDetachedPosition(game, position);
    }

    @Nullable
    Point tileOrigin(String position, Point projected) {
        Point scaled = PositionMapper.getScaledTilePosition(game, position, projected.x, projected.y, fractureYbump);
        int tileX = scaled.x + EXTRA_X - TileGeometry.TILE_PADDING - frameOffsetX();
        int tileY = scaled.y + EXTRA_Y - TileGeometry.TILE_PADDING - frameOffsetY();
        if (mapFrame != null && framing.pinnedCorners().contains(position)) {
            return pinnedCornerTileOrigin(position);
        }
        if (isOutsideFrame(tileX, tileY)) {
            return null;
        }
        return new Point(tileX, tileY);
    }

    Point pinnedCornerTileOrigin(String corner) {
        Rectangle frameContent = framing.content();
        int contentLeft = Math.max(0, frameContent.x - mapFrame.offsetX());
        int contentRight = Math.min(mapFrame.width(), frameContent.x + frameContent.width - mapFrame.offsetX());
        boolean left = "tl".equalsIgnoreCase(corner) || "bl".equalsIgnoreCase(corner);
        boolean top = "tl".equalsIgnoreCase(corner) || "tr".equalsIgnoreCase(corner);
        int hexLeft = left ? contentLeft + CORNER_MARGIN : contentRight - CORNER_MARGIN - TileGeometry.TILE_WIDTH;
        int hexTop = top ? CORNER_TOP : mapFrame.height() - CORNER_MARGIN - TileGeometry.TILE_HEIGHT;
        return new Point(hexLeft - TileGeometry.TILE_PADDING, hexTop - TileGeometry.TILE_PADDING);
    }

    boolean isOutsideFrame(int tileX, int tileY) {
        if (mapFrame == null) {
            return false;
        }
        int hexLeft = tileX + TileGeometry.TILE_PADDING;
        int hexTop = tileY + TileGeometry.TILE_PADDING;
        return hexLeft + TileGeometry.TILE_WIDTH <= 0
                || hexTop + TileGeometry.TILE_HEIGHT <= 0
                || hexLeft >= mapFrame.width()
                || hexTop >= mapFrame.height();
    }

    @Nullable
    Rectangle hexBounds(String position) {
        return MapFrame.hexBounds(game, position, fractureYbump, EXTRA_X - frameOffsetX(), EXTRA_Y - frameOffsetY());
    }

    static int getRingCount(Game game) {
        return Math.clamp(PositionMapper.layoutRingCount(game), RING_MIN_COUNT, RING_MAX_COUNT);
    }

    static int getMapHeight(Game game) {
        return getMapHeight(getRingCount(game));
    }

    private static int getMapHeight(int ringCount) {
        return (ringCount + 1) * TileGeometry.TILE_HEIGHT * 2 + EXTRA_Y * 2;
    }

    private static int getMapPlayerCount(Game game) {
        return game.getRealPlayers().size() + game.getDummies().size();
    }

    // TODO: explain why this exists. Can we get rid of it?
    private static boolean hasExtraRow(Game game, int ringCount) {
        return !PlayerAreaColumns.isMegaGame(game)
                && (getMapHeight(ringCount) - EXTRA_Y) < (getMapPlayerCount(game) / 2 * PLAYER_STATS_HEIGHT + EXTRA_Y);
    }

    static int getMapWidth(Game game) {
        return getMapWidth(game, getRingCount(game));
    }

    static int getMapWidth(Game game, int rings) {
        int mapWidth = ringsWidth(rings);
        mapWidth += hasExtraRow(game, rings) ? EXTRA_X : 0;
        return mapWidth;
    }

    // TODO: a 3-ring map is drawn 1.5 rings thick (1.5 * 3 > 1 * 4); why?
    private static int ringsWidth(int rings) {
        float ringCount = rings;
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1;
        return (int) (ringCount * 520 + EXTRA_X * 2);
    }

    static int smallestRegularPlayerAreaWidth() {
        return ringsWidth(RING_MIN_COUNT) - PLAYER_AREAS_LEFT;
    }

    static int extraBoardsWidth(Game game) {
        int highestBoard = BoardPosition.boardsInUse(game).stream()
                .mapToInt(BoardPosition.BOARDS::indexOf)
                .max()
                .orElse(-1);
        return (highestBoard + 1) * PositionMapper.boardWidth(PositionMapper.boardLayoutRingCount(game));
    }

    static int getMaxObjectiveWidth(Game game) {
        return (getMapWidth(game) - ObjectivesSection.SPACING_BETWEEN_OBJECTIVE_TYPES * 4) / 3;
    }

    private static int layoutWidthFor(Game game, int frameContentWidth) {
        int contentWidth = Math.min(frameContentWidth, MapFrame.MAX_WIDTH);
        for (int rings = RING_MIN_COUNT; rings <= RING_MAX_COUNT; rings++) {
            int classicWidth = getMapWidth(game, rings);
            if (classicWidth >= contentWidth) {
                return classicWidth;
            }
        }
        return contentWidth;
    }

    private static int mapOnlyMinimumWidth(Game game, int scoreTokenSpacing) {
        return Math.max(
                Math.max(MINIMUM_WIDTH_OF_PLAYER_AREA, MapCanvasLayout.EXPEDITION_TRACKER_RIGHT_OFFSET),
                Math.max(ScoreTrackSection.width(game), ObjectivesSection.rowWidth(game, scoreTokenSpacing)));
    }
}
