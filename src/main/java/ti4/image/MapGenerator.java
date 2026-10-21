package ti4.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.game.Expeditions;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.RenderRandom;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units.UnitKey;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.AgendaModel;
import ti4.model.BorderAnomalyHolder;
import ti4.model.ColorModel;
import ti4.model.EventModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetModel;
import ti4.model.StrategyCardModel;
import ti4.service.map.FractureService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.website.model.WebsiteOverlay;

public class MapGenerator implements AutoCloseable {

    private static final int SC_LABEL_SPACING = 25;
    private static final Color GHOST_RING = new Color(255, 255, 255, 128);
    private static final Color GHOST_NUMBER = new Color(255, 255, 255, 150);
    private static final Color GHOST_NUMBER_OUTLINE = new Color(0, 0, 0, 120);
    private static final String EASTER_EGG_NAME = "A wild brainchild";
    private static final int EASTER_EGG_ODDS = 200;
    private static final Color EASTER_EGG_TEXT = new Color(255, 200, 0);
    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke3 = new BasicStroke(3.0f);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke5 = new BasicStroke(5.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);
    private static final int WEBP_MAX_DIMENSION = 16_383;
    private static final ColorConvertOp GRAYSCALE_CONVERT_OP =
            new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

    private final RenderContext ctx;
    private final Graphics2D graphics;
    private final BufferedImage mainImage;
    private final GenericInteractionCreateEvent event;
    private final int scoreTokenSpacing;
    private final Game game;
    private final DisplayType displayType;
    private final DisplayType displayTypeBasic;
    private final RenderTimings timings;
    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final FogView fog;
    private final BoardLayout board;
    private final WebsiteOverlays websiteOverlays;
    private final int mapWidth;
    private final int playerAreasWidth;
    private final int stripWidth;

    MapGenerator(Game game, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event) {
        this(game, displayType, event, null);
    }

    MapGenerator(
            Game game,
            @Nullable DisplayType displayType,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable String requestedSegment) {
        this(
                RenderContext.build(new MapRenderRequest(game, displayType, event, requestedSegment)),
                RenderTimings.disabled());
    }

    MapGenerator(RenderContext ctx, RenderTimings timings) {
        this.ctx = ctx;
        this.timings = timings;
        game = ctx.game();
        event = ctx.event();
        displayTypeBasic = ctx.basicType();
        this.displayType = ctx.displayType();
        scoreTokenSpacing = ctx.scoreTokenSpacing();
        fog = ctx.fog();
        board = ctx.board();
        websiteOverlays = ctx.overlays();
        MapCanvasLayout canvas = ctx.canvas();
        width = canvas.width();
        height = canvas.height();
        heightForGameInfo = canvas.heightForGameInfo();
        mapWidth = canvas.mapWidth();
        playerAreasWidth = canvas.playerAreasWidth();
        stripWidth = canvas.stripWidth();

        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        graphics = mainImage.createGraphics();
    }

    int imageWidth() {
        return width;
    }

    int imageHeight() {
        return height;
    }

    @Nullable
    String shownSegmentName() {
        return board.shownSegmentName();
    }

    Set<String> pinnedCorners() {
        return board.pinnedCorners();
    }

    int frameContentWidth() {
        return board.frameContentWidth();
    }

    Point pinnedCornerTileOrigin(String corner) {
        return board.pinnedCornerTileOrigin(corner);
    }

    boolean isInShownRegion(@Nullable String position) {
        return board.isInShownRegion(position);
    }

    private void setupTilesForDisplayTypeAllAndMap(Map<String, Tile> tilesToDisplay) {
        if (displayTypeBasic != DisplayType.all && displayTypeBasic != DisplayType.map) {
            return;
        }
        Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
        tileMap.keySet().removeIf(position -> !isInShownRegion(position));
        boolean showFracture = FractureService.isFractureRegionOnMap(game);
        // Show Grey Setup Tiles
        if (game.isShowMapSetup() || tilesToDisplay.isEmpty()) {
            int ringCount = game.getRingCount();
            ringCount = Math.clamp(ringCount, BoardLayout.RING_MIN_COUNT, BoardLayout.RING_MAX_COUNT);
            for (String position : PositionMapper.getTilePositions()) {
                String tileRing = "0";
                if (position.startsWith("frac") && !showFracture) continue;
                if (position.length() == 3) {
                    tileRing = position.substring(0, 1);
                } else if (position.length() == 4) {
                    tileRing = position.substring(0, 2);
                }
                int tileRingNumber = -1;
                try {
                    tileRingNumber = Integer.parseInt(tileRing);
                } catch (Exception e) {
                    BotLogger.error("Hitting an error", e);
                }

                Tile setupTile = null;
                if (tileRingNumber > -1 && tileRingNumber <= ringCount && !tileMap.containsKey(position)) {
                    setupTile = new Tile("0gray", position);
                } else if (tileRingNumber > -1 && tileRingNumber <= ringCount + 1 && !tileMap.containsKey(position)) {
                    setupTile = new Tile("0border", position);
                }
                if (setupTile != null) {
                    addTile(setupTile, TileStep.Tile);
                    addTile(setupTile, TileStep.TileNumber);
                }
            }
        }

        tileMap.remove(null);
        List<String> sortedTiles = tileMap.keySet().stream().sorted().toList();
        Set<String> tilesWithExtra =
                new HashSet<>(game.getAdjacentTileOverrides().values());
        tilesWithExtra.addAll(game.getBorderAnomalies().stream()
                .map(BorderAnomalyHolder::getTile)
                .collect(Collectors.toSet()));

        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Tile));
        drawGhostHexes(tileMap.keySet());
        tilesWithExtra.forEach(key -> addTile(tileMap.get(key), TileStep.Extras));
        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Units));
        if (!game.getTileDistances().isEmpty()) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Distance));
            game.setTileDistances(new HashMap<>()); // clear distances after consuming them
        }
        if (displayType == DisplayType.wormholes) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Wormholes));
        } else if (displayType == DisplayType.anomalies) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Anomalies));
        } else if (displayType == DisplayType.aetherstream) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Aetherstream));
        } else if (displayType == DisplayType.exile) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Exile));
        } else if (displayType == DisplayType.legendaries) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Legendaries));
        } else if (displayType == DisplayType.empties) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Empties));
        } else if (displayType == DisplayType.spacecannon) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.SpaceCannon));
        } else if (displayType == DisplayType.traits) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Traits));
        } else if (displayType == DisplayType.techskips) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.TechSkips));
        } else if (displayType == DisplayType.attachments) {
            sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.Attachments));
        }
        sortedTiles.forEach(key -> addTile(tileMap.get(key), TileStep.TileNumber));
    }

    private void drawMapTilesInsideFrame() {
        Shape previousClip = graphics.getClip();
        if (board.mapFrame() != null) {
            graphics.setClip(0, 0, width, board.mapFrame().height());
        }
        setupTilesForDisplayTypeAllAndMap(fog.tiles());
        graphics.setClip(previousClip);
    }

    BufferedImage drawCanvas() {
        paintCanvas();
        return mainImage;
    }

    List<WebsiteOverlay> overlays() {
        return websiteOverlays.asList();
    }

    private void paintCanvas() {
        timings.startTiles();
        drawMapTilesInsideFrame();
        ConnectionStripSection.draw(ctx, graphics);
        timings.stopTiles();

        timings.startGraphics();
        drawImage();
    }

    static boolean isEasterEggRoll(int roll) {
        return roll == 0;
    }

    private void drawSegmentLabel() {
        String label = board.segmentLabel();
        if (label == null) {
            return;
        }
        if (isSectorTitle(label) && isEasterEggRoll(RenderRandom.nextInt("segmentTitle.easterEgg", EASTER_EGG_ODDS))) {
            String text = "Map: " + EASTER_EGG_NAME + "!";
            Font big = Storage.getFont80();
            Font font = graphics.getFontMetrics(big).stringWidth(text) < width - 40 ? big : Storage.getFont64();
            drawSegmentTitle(text, font, EASTER_EGG_TEXT, stroke4, Color.BLACK);
        } else {
            drawSegmentTitle("Map: " + label, Storage.getFont64(), Color.WHITE, stroke4, Color.BLACK);
        }
        graphics.setFont(Storage.getFont32());
    }

    private boolean isSectorTitle(String label) {
        MapSegment shownSegment = board.shownSegment();
        return shownSegment != null && !shownSegment.isFracture() && label.equals(shownSegment.displayName(game));
    }

    private void drawSegmentTitle(String text, Font font, Color color, Stroke stroke, Color outline) {
        graphics.setFont(font);
        DrawingUtil.superDrawString(
                graphics, text, width / 2, 110, color, HorizontalAlign.Center, VerticalAlign.Center, stroke, outline);
    }

    private void drawGhostHexes(Set<String> drawnPositions) {
        for (String position : ghostFrontierPositions()) {
            Rectangle hex = MapFrame.hexBounds(
                    game,
                    position,
                    board.fractureYbump(),
                    BoardLayout.EXTRA_X - board.frameOffsetX(),
                    BoardLayout.EXTRA_Y - board.frameOffsetY());
            if (hex != null
                    && !board.isOutsideFrame(hex.x - BoardLayout.TILE_PADDING, hex.y - BoardLayout.TILE_PADDING)) {
                drawGhostHex(position, hex, !drawnPositions.contains(position));
            }
        }
    }

    private Set<String> ghostFrontierPositions() {
        if (!fog.isPrivate()
                || fog.viewer() == null
                || !fog.viewer().isFogGhostHexes()
                || !game.getFowOption(FOWOption.GHOST_HEXES)) {
            return Set.of();
        }
        Set<String> known = fog.seenPositions();
        List<String> sources = known.stream()
                .filter(position -> board.isInShownRegion(position)
                        && !board.pinnedCorners().contains(position))
                .toList();
        return frontier(sources, known);
    }

    static Set<String> frontier(Collection<String> sources, Set<String> known) {
        Set<String> frontier = new HashSet<>();
        for (String source : sources) {
            for (String neighbour : PositionMapper.getAdjacentTilePositions(source)) {
                if (!known.contains(neighbour) && PositionMapper.isTilePositionValid(neighbour)) {
                    frontier.add(neighbour);
                }
            }
        }
        return frontier;
    }

    private void drawGhostHex(String position, Rectangle hex, boolean withNumber) {
        Graphics2D g2 = (Graphics2D) graphics;
        Stroke previousStroke = g2.getStroke();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(GHOST_RING);
        g2.setStroke(stroke3);
        g2.drawPolygon(MapFrame.hexPolygon(hex));
        g2.setStroke(previousStroke);
        if (withNumber) {
            g2.setFont(Storage.getFont40());
            DrawingUtil.superDrawString(
                    g2,
                    position,
                    (int) hex.getCenterX(),
                    (int) hex.getCenterY(),
                    GHOST_NUMBER,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    stroke3,
                    GHOST_NUMBER_OUTLINE);
        }
    }

    private void drawImage() {
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        String timeStamp = DateTimeHelper.getFormattedTimestamp();
        graphics.drawString(game.getName() + " " + game.getCreationDate() + " - " + timeStamp, 0, 34);
        drawSegmentLabel();
        int landscapeShift = (displayType == DisplayType.landscape ? mapWidth : 0);
        int y = heightForGameInfo + 60;
        int x = landscapeShift + BoardLayout.PLAYER_AREAS_LEFT;
        Point coord;

        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // GAME MODES
        int deltaY = -150;
        if (game.isCompetitiveTIGLGame()) {
            drawGeneralImage(
                    x + deltaX,
                    y + deltaY,
                    TIGLHelper.isFracturedTIGLGame(game) ? "GameMode_TIGL_Fractured.png" : "GameMode_TIGL.png");
            TIGLRank rank = game.getMinimumTIGLRankAtGameStart();
            if (rank != null) {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.superDrawString(
                        graphics,
                        rank.getShortName(),
                        x + deltaX + 50,
                        y + deltaY + 75,
                        Color.WHITE,
                        HorizontalAlign.Center,
                        VerticalAlign.Top,
                        stroke2,
                        Color.BLACK);
                graphics.setFont(Storage.getFont32());
            }
            deltaX += 100;
        }
        if (game.isAbsolMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_Absol.png");
            addWebsiteOverlay("Absol", null, x + deltaX, y + deltaY, 90, 90);
            deltaX += 100;
        }
        if (game.isNoSwapMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "NoSwap.png");
            addWebsiteOverlay("No Support Swap", null, x + deltaX, y + deltaY, 180, 90);
            deltaX += 190;
        }
        if (game.isMiltyModMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_MiltyMod.png");
            addWebsiteOverlay("MiltyMod", null, x + deltaX, y + deltaY, 90, 90);
            deltaX += 100;
        }
        if (game.isDiscordantStarsMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_DiscordantStars.png");
            addWebsiteOverlay("Discordant Stars", null, x + deltaX, y + deltaY, 90, 90);
        }

        deltaY = 35;
        y += 40; // needed for ghost HS on br

        String server;
        if (game.getGuild() == null) {
            server = "asyncti_icon_nothing";
        } else {
            switch (game.getGuild().getId()) {
                case "943410040369479690" -> server = "asyncti_icon_hub";
                case "1176104225932058694" -> server = "asyncti_icon_warsuntzu";
                case "1145823841227112598" -> server = "asyncti_icon_dreadnot";
                case "1250131684393881610" -> server = "asyncti_icon_tommerhawk";
                case "1312882116597518416" -> server = "asyncti_icon_dudersdomain";
                case "1090910555327434774" -> server = "asyncti_icon_stroterarea";
                case "1209956332380229672" -> server = "asyncti_icon_fighterclub";
                case "1378702133297414164" -> server = "asyncti_icon_whatsupdock";
                case "1410728648817770526" -> server = "asyncti_icon_shipflag";
                case "1434181175944941649" -> server = "asyncti_icon_pdstrians";
                case "1434180793139204198" -> server = "asyncti_icon_greatcarrierreef";
                case "1434632452097446040" -> server = "asyncti_icon_tournaments";
                case "1458845770672377989" -> server = "asyncti_icon_planetaryducksystem";
                case "1458844879709929532" -> server = "asyncti_icon_stroatymcstroatface";
                case "1458845518393246032" -> server = "asyncti_icon_dannelscampground";
                // fog of war?
                // megagames?
                default -> server = "asyncti_icon_unknown";
            }
        }
        String serverPath = ResourceHelper.getResourceFromFolder("server_icons/", server + ".png");
        int serverSize = 96;
        BufferedImage serverImage = ImageHelper.readScaled(serverPath, serverSize, serverSize);
        graphics.drawImage(serverImage, x, y - 3 * serverSize / 4, null);

        // GAME FUN NAME
        graphics.setFont(Storage.getFont50());
        graphics.setColor(Color.WHITE);
        graphics.drawString(game.getCustomName(), landscapeShift + 4 * serverSize / 3, y);
        deltaX = graphics.getFontMetrics().stringWidth(game.getCustomName()) + 5 * serverSize / 3;

        // STRATEGY CARDS
        coord = drawStrategyCards(new Point(x, y));
        coord.translate(100, 0);

        // ROUND
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        String roundString = "ROUND: " + game.getRound();
        int roundLen = graphics.getFontMetrics().stringWidth(roundString);
        if (coord.x > stripWidth - roundLen - 100 * game.getRealPlayers().size()) {
            coord = new Point(landscapeShift + 20, coord.y + 100);
        }
        graphics.drawString(roundString, coord.x, coord.y);

        // CARD DECKS
        drawCardDecks(Math.max(x + deltaX, coord.x), y - 75);

        // TURN ORDER
        coord = drawTurnOrderTracker(coord.x + roundLen + 100 + landscapeShift, coord.y);

        drawExpeditionTracker(stripWidth - MapCanvasLayout.EXPEDITION_TRACKER_RIGHT_OFFSET, coord.y - 265);

        y = coord.y + 30;
        x = 10 + landscapeShift;
        int tempY = y;
        tempY = ScoreTrackSection.draw(ctx, graphics, landscapeShift, tempY + 20);
        if (PriorityTrackSection.isShown(game)) {
            tempY = PriorityTrackSection.draw(ctx, graphics, landscapeShift, tempY);
        }
        y = ObjectivesSection.draw(ctx, graphics, landscapeShift, tempY);
        y = laws(y);
        y = events(y);
        if (MapSideStatsSection.drawsPlayerInfo(game, displayType, fog) && !board.isShowingSeparateFracture()) {
            playerInfo(game);
        }

        if (displayTypeBasic == DisplayType.all || displayTypeBasic == DisplayType.stats) {
            Point topLeft = new Point(x, y);
            new PlayerAreaGenerator(
                            graphics,
                            game,
                            fog.isPrivate(),
                            fog.viewer(),
                            websiteOverlays,
                            playerAreasWidth,
                            scoreTokenSpacing,
                            ctx.neighbours())
                    .drawAllPlayerAreas(topLeft);
        }
    }

    @Override
    public void close() {
        graphics.dispose();
    }

    private void drawGeneralImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getGeneralFile(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.error("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int size) {
        PlayerAreaGenerator.drawPAImageScaled(graphics, x, y, resourceName, size, size);
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int width, int height) {
        PlayerAreaGenerator.drawPAImageScaled(graphics, x, y, resourceName, width, height);
    }

    public enum HorizontalAlign {
        Left,
        Center,
        Right
    }

    public enum VerticalAlign {
        Top,
        Center,
        Bottom
    }

    private record StrategyCardLabel(int sc, int tradeGoods, int width) {}

    private Point drawStrategyCards(Point coord) {
        List<StrategyCardLabel> labels = strategyCardLabels();
        int rowStart = coord.x;
        int maxRowWidth = strategyCardRowRightLimit() - rowStart + SC_LABEL_SPACING;
        List<List<StrategyCardLabel>> rows = PlayerAreaGenerator.isMegaGame(game)
                ? RowWrap.balancedRows(labels, label -> label.width() + SC_LABEL_SPACING, 0, maxRowWidth)
                : oneStripOfStrategyCards(labels, rowStart);

        int x = rowStart;
        int deltaY = coord.y + 80;
        for (int row = 0; row < rows.size(); row++) {
            if (row > 0) {
                x = 20 + (displayType == DisplayType.landscape ? mapWidth : 0);
                deltaY += 100;
            }
            for (StrategyCardLabel label : rows.get(row)) {
                drawStrategyCardLabel(label, x, coord.y, deltaY);
                x += label.width() + SC_LABEL_SPACING;
            }
        }
        return new Point(x, deltaY);
    }

    private List<List<StrategyCardLabel>> oneStripOfStrategyCards(List<StrategyCardLabel> labels, int rowStart) {
        boolean landscape = displayType == DisplayType.landscape;
        int rightLimit = (landscape ? mapWidth + 4 * 520 + BoardLayout.EXTRA_X * 2 : stripWidth) - 100;
        List<List<StrategyCardLabel>> rows = new ArrayList<>();
        rows.add(new ArrayList<>());
        int x = rowStart;
        for (StrategyCardLabel label : labels) {
            rows.getLast().add(label);
            x += label.width() + SC_LABEL_SPACING;
            if (x > rightLimit) {
                x = 20 + (landscape ? mapWidth : 0);
                rows.add(new ArrayList<>());
            }
        }
        return rows;
    }

    private List<StrategyCardLabel> strategyCardLabels() {
        Set<Integer> scPicked = new HashSet<>();
        for (Player player : game.getPlayers().values()) {
            scPicked.addAll(player.getSCs());
        }
        List<StrategyCardLabel> labels = new ArrayList<>();
        for (Map.Entry<Integer, Integer> scTGs : game.getScTradeGoods().entrySet()) {
            int sc = scTGs.getKey();
            if (sc == 0) {
                continue;
            }
            int tradeGoods = fog.isPrivate() || scPicked.contains(sc) ? 0 : scTGs.getValue();
            graphics.setFont(Storage.getFont64());
            int width = graphics.getFontMetrics().stringWidth(Integer.toString(sc));
            if (tradeGoods > 0) {
                graphics.setFont(Storage.getFont24());
                width = Math.max(width, graphics.getFontMetrics().stringWidth("TG:" + tradeGoods));
            }
            labels.add(new StrategyCardLabel(sc, tradeGoods, width));
        }
        return labels;
    }

    private int strategyCardRowRightLimit() {
        if (displayType == DisplayType.landscape) {
            return mapWidth + 4 * 520 + BoardLayout.EXTRA_X * 2 - 100;
        }
        if (showsExpeditionTracker()) {
            return stripWidth - MapCanvasLayout.EXPEDITION_TRACKER_RIGHT_OFFSET - SC_LABEL_SPACING;
        }
        return stripWidth - 100;
    }

    private void drawStrategyCardLabel(StrategyCardLabel label, int x, int y, int baseline) {
        int sc = label.sc();
        String scText = Integer.toString(sc);
        graphics.setFont(Storage.getFont64());
        int textWidth = graphics.getFontMetrics().stringWidth(scText);
        boolean scPicked = game.getPlayers().values().stream()
                .anyMatch(player -> player.getSCs().contains(sc));

        if (!fog.isPrivate() && !scPicked) {
            graphics.setColor(ColorUtil.getSCColor(sc, game));
            if (!game.getStoredValue("exhaustedSC" + sc).isEmpty()) {
                graphics.setColor(Color.GRAY);
            }
            graphics.drawString(scText, x, baseline);
            addWebsiteOverlay(game.getStrategyCardModelByInitiative(sc).orElse(null), x, y + 24, textWidth, 60);
            if (label.tradeGoods() > 0) {
                graphics.setFont(Storage.getFont24());
                graphics.setColor(Color.WHITE);
                String tgMsg = "TG:" + label.tradeGoods();
                int tgMsgTextWidth = graphics.getFontMetrics().stringWidth(tgMsg);
                graphics.drawString(tgMsg, x + textWidth / 2 - tgMsgTextWidth / 2, baseline + 30);
            }
        }
        if (fog.isPrivate() && game.getScPlayed().getOrDefault(sc, false)) {
            graphics.setColor(Color.GRAY);
            graphics.setFont(Storage.getFont64());
            graphics.drawString(scText, x, baseline);
        }
    }

    private Point drawTurnOrderTracker(int x, int y) {
        boolean convertToGenericSC = fog.isPrivate();
        String activePlayerUserID = game.getActivePlayerID();
        if (!convertToGenericSC && activePlayerUserID != null && "action".equals(game.getPhaseOfGame())) {
            graphics.setFont(Storage.getFont20());
            graphics.setColor(ColorUtil.ActiveColor);
            graphics.drawString("ACTIVE", x + 10, y + 35);
            graphics.setFont(Storage.getFont16());
            graphics.setColor(Color.LIGHT_GRAY);
            graphics.drawString("NEXT UP", x + 112, y + 34);

            Player activePlayer = game.getPlayer(activePlayerUserID);
            List<Player> allPlayers = new ArrayList<>(game.getRealPlayers());
            allPlayers.sort(Player.comparingInitiative());

            int rotationDistance = allPlayers.size() - allPlayers.indexOf(activePlayer);
            Collections.rotate(allPlayers, rotationDistance);
            for (Player player : allPlayers) {
                if (player.isPassed() || player.getSCs().isEmpty()) continue;
                String faction = player.getFaction();
                if (faction != null) {
                    BufferedImage bufferedImage = DrawingUtil.getPlayerFactionIconImage(player);
                    if (bufferedImage != null) {
                        graphics.drawImage(bufferedImage, x, y - 70, null);
                        if (!player.hasCustomFactionEmoji()) {
                            addWebsiteOverlay(player.getFactionModel(), x + 10, y - 60, 75, 75);
                        }
                        x += 100;
                    }
                }
            }
            x += 100;
            for (Player player : game.getPassedPlayers()) {
                String faction = player.getFaction();
                if (faction != null) {
                    BufferedImage bufferedImage = DrawingUtil.getPlayerFactionIconImage(player);
                    if (bufferedImage != null) {
                        bufferedImage = makeGrayscale(bufferedImage);
                        graphics.drawImage(bufferedImage, x, y - 70, null);
                        graphics.setColor(Color.RED);
                        graphics.drawString("PASSED", x + 10, y + 34);
                        x += 100;
                    }
                }
            }
        }
        return new Point(x, y);
    }

    private boolean showsExpeditionTracker() {
        return game.isThundersEdge()
                && game.getExpeditions().getRemainingExpeditionCount() > 0
                && game.getTileFromPlanet("thundersedge") == null
                && !game.isTwilightsFallMode();
    }

    private void drawExpeditionTracker(int x, int y) {
        if (!showsExpeditionTracker()) return;
        Expeditions exp = game.getExpeditions();

        drawGeneralImage(x, y, "Expeditions.png");
        if (exp.getTradeGoods() != null) {
            Player p = game.getPlayerFromColorOrFaction(exp.getTradeGoods());
            DrawingUtil.getAndDrawControlToken(graphics, p, x + 47, y + 101, fog.isPrivate(), 1.0f);
        }
        if (exp.getFiveRes() != null) {
            Player p = game.getPlayerFromColorOrFaction(exp.getFiveRes());
            DrawingUtil.getAndDrawControlToken(graphics, p, x + 114, y + 5, fog.isPrivate(), 1.0f);
        }
        if (exp.getActionCards() != null) {
            Player p = game.getPlayerFromColorOrFaction(exp.getActionCards());
            DrawingUtil.getAndDrawControlToken(graphics, p, x + 182, y + 101, fog.isPrivate(), 1.0f);
        }
        if (exp.getTechSkip() != null) {
            Player p = game.getPlayerFromColorOrFaction(exp.getTechSkip());
            DrawingUtil.getAndDrawControlToken(graphics, p, x + 47, y + 150, fog.isPrivate(), 1.0f);
        }
        if (exp.getSecret() != null) {
            Player p = game.getPlayerFromColorOrFaction(exp.getSecret());
            DrawingUtil.getAndDrawControlToken(graphics, p, x + 114, y + 243, fog.isPrivate(), 1.0f);
        }
        if (exp.getFiveInf() != null) {
            Player p = game.getPlayerFromColorOrFaction(exp.getFiveInf());
            DrawingUtil.getAndDrawControlToken(graphics, p, x + 182, y + 150, fog.isPrivate(), 1.0f);
        }
    }

    private int drawCardDecks(int x, int y) {
        if (game.isFowMode()) return x;

        int cardWidth = 60;
        int cardHeight = 90;
        int horSpacing = cardWidth + 15;
        int textY = y + cardHeight - 10;
        Stroke outline = stroke2;
        String overlayText;

        graphics.setFont(Storage.getFont24());

        drawPAImageScaled(x, y, "cardback_secret.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getSecretObjectiveDeckSize()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText =
                game.getSecretObjectiveDeckSize() + "/" + game.getSecretObjectiveFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Secret Objective Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(
                x,
                y,
                game.isTwilightsFallMode() ? "cardback_action_tf.jpg" : "cardback_action.jpg",
                cardWidth,
                cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getActionCards().size()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getActionCards().size() + "/" + game.getActionCardFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Action Card Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_cultural.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getExploreDeck("cultural").size()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getCulturalExploreDeckSize() + "/" + game.getCulturalExploreFullDeckSize()
                + " in the deck \n" + game.getCulturalExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Cultural Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_industrial.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getExploreDeck("industrial").size()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getIndustrialExploreDeckSize() + "/" + game.getIndustrialExploreFullDeckSize()
                + " in the deck \n" + game.getIndustrialExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Industrial Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_hazardous.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getExploreDeck("hazardous").size()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getHazardousExploreDeckSize() + "/" + game.getHazardousExploreFullDeckSize()
                + " in the deck \n" + game.getHazardousExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Hazardous Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_frontier.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getExploreDeck("frontier").size()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getFrontierExploreDeckSize() + "/" + game.getFrontierExploreFullDeckSize()
                + " in the deck \n" + game.getFrontierExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Frontier Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_relic.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getRelicDeckSize()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getRelicDeckSize() + "/" + game.getRelicFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Relic Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(
                x, y, game.isTwilightsFallMode() ? "cardback_edict.jpg" : "cardback_agenda.png", cardWidth, cardHeight);
        DrawingUtil.superDrawString(
                graphics,
                Integer.toString(game.getAgendaDeckSize()),
                x + cardWidth / 2,
                textY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                outline,
                Color.BLACK);
        overlayText = game.getAgendaDeckSize() + "/" + game.getAgendaFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Agenda Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        if (game.isTwilightsFallMode()) {
            int cardCount, fullDeck;

            cardCount = game.getAbilitySpliceDeck(false).size();
            fullDeck = Mapper.getDeck(game.getAbilitySpliceDeckID()).getCardCount();
            drawPAImageScaled(x, y, "cardback_tf_ability.jpg", cardWidth, cardHeight);
            DrawingUtil.superDrawString(
                    graphics,
                    Integer.toString(cardCount),
                    x + cardWidth / 2,
                    textY,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Bottom,
                    outline,
                    Color.BLACK);
            overlayText = cardCount + "/" + fullDeck + " cards in the deck";
            addWebsiteOverlay("Ability Splice Deck", overlayText, x, y, cardWidth, cardHeight);
            x += horSpacing;

            cardCount = game.getUnitSpliceDeck(false).size();
            fullDeck = Mapper.getDeck(game.getUnitSpliceDeckID()).getCardCount();
            drawPAImageScaled(x, y, "cardback_unit_upgrade.jpg", cardWidth, cardHeight);
            DrawingUtil.superDrawString(
                    graphics,
                    Integer.toString(cardCount),
                    x + cardWidth / 2,
                    textY,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Bottom,
                    outline,
                    Color.BLACK);
            overlayText = cardCount + "/" + fullDeck + " cards in the deck";
            addWebsiteOverlay("Unit Upgrade Splice Deck", overlayText, x, y, cardWidth, cardHeight);
            x += horSpacing;

            cardCount = game.getGenomeSpliceDeck(false).size();
            fullDeck = Mapper.getDeck(game.getGenomeSpliceDeckID()).getCardCount();
            drawPAImageScaled(x, y, "cardback_genome.jpg", cardWidth, cardHeight);
            DrawingUtil.superDrawString(
                    graphics,
                    Integer.toString(cardCount),
                    x + cardWidth / 2,
                    textY,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Bottom,
                    outline,
                    Color.BLACK);
            overlayText = cardCount + "/" + fullDeck + " cards in the deck";
            addWebsiteOverlay("Genome Splice Deck", overlayText, x, y, cardWidth, cardHeight);
            x += horSpacing;

            cardCount = game.getParadigmSpliceDeck(false).size();
            fullDeck = Mapper.getDeck(game.getParadigmSpliceDeckID()).getCardCount();
            drawPAImageScaled(x, y, "cardback_paradigm.jpg", cardWidth, cardHeight);
            DrawingUtil.superDrawString(
                    graphics,
                    Integer.toString(cardCount),
                    x + cardWidth / 2,
                    textY,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Bottom,
                    outline,
                    Color.BLACK);
            overlayText = cardCount + "/" + fullDeck + " cards in the deck";
            addWebsiteOverlay("Paradigm Deck", overlayText, x, y, cardWidth, cardHeight);
            x += horSpacing;

            if (game.getTyrantUserID().isEmpty()) {
                String tyrantFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID(Constants.TYRANT));
                if (tyrantFile != null) {
                    BufferedImage bufferedImage = ImageHelper.read(tyrantFile);
                    graphics.drawImage(bufferedImage, x + 15, y - 13, null);
                }
                x += 200;
            }
        }

        return x;
    }

    private void playerInfo(Game game) {
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);

        int ringCount = Math.clamp(game.getRingCount(), BoardLayout.RING_MIN_COUNT, BoardLayout.RING_MAX_COUNT);
        MapSideStatLayout.Layout layout = ctx.statLayout().get();
        for (Player player : layout.statOrder()) {
            if (player.getFaction() == null || !player.isRealPlayer()) {
                continue;
            }
            if (layout.useNewSystem()) {
                if (board.shownSegment() != null && !board.framedStatFactions().contains(player.getFaction())) {
                    continue;
                }
                List<String> tiles = layout.playerStatTiles().get(player.getFaction());
                paintPlayerInfo(game, player, tiles);
            } else {
                paintPlayerInfoOld(game, player, ringCount);
            }
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        PlayerAreaGenerator.drawPAImage(graphics, x, y, resourceName);
    }

    private void paintPlayerInfo(Game game, Player player, List<String> statTiles) {
        boolean convertToGeneric = fog.shouldConvertToGeneric(player);
        if (convertToGeneric) {
            return;
        }

        // Get the map positions for each of the "stat tiles"
        if (statTiles == null || statTiles.size() < 3) return;
        Map<String, Point> points = new HashMap<>();
        for (String pos : statTiles) {
            Point p = PositionMapper.getTilePosition(pos);
            if (p == null) return;
            p = PositionMapper.getScaledTilePosition(game, pos, p.x, p.y, board.fractureYbump());
            p.translate(BoardLayout.EXTRA_X - board.frameOffsetX(), BoardLayout.EXTRA_Y - board.frameOffsetY());
            points.put(pos, p);
        }
        Point statTileMid = points.get(statTiles.get(0));
        Point statTile1 = points.get(statTiles.get(1));
        Point statTile2 = points.get(statTiles.get(2));

        int dir1 = 0, dir2 = 0, j = 0;
        for (String p : PositionMapper.getAdjacentTilePositions(statTiles.get(0))) {
            if (p.equals(statTiles.get(1))) dir1 = j;
            if (p.equals(statTiles.get(2))) dir2 = j;
            j++;
        }

        ColorModel playerColor = Mapper.getColor(player.getColor());
        float bgAlpha = 0.10f;
        Color bgColor = player.isActivePlayer() ? Color.green : (player.isPassed() ? Color.red : Color.BLACK);
        BufferedImage tint = DrawingUtil.tintedBackground(bgColor, bgAlpha);
        for (String pos : statTiles) {
            Point p = points.get(pos);
            List<Integer> adjDir = new ArrayList<>();
            List<String> adjPos = PositionMapper.getAdjacentTilePositions(pos);
            for (int i = 0; i < 6; i++) if (statTiles.contains(adjPos.get(i))) adjDir.add(i);
            BufferedImage hex = DrawingUtil.hexBorder(game.getHexBorderStyle(), playerColor, adjDir);
            graphics.drawImage(tint, p.x, p.y, null);
            graphics.drawImage(hex, p.x, p.y, null);
        }

        drawFactionIcon(player, statTileMid);
        HorizontalAlign center = HorizontalAlign.Center;
        drawUsername(game, player, statTileMid, center);
        drawFactionNameAndColorStroters(player, statTileMid, center);
        drawVictoryPoints(game, player, statTileMid, center);
        drawSecretObjectiveIcons(game, player, statTileMid);
        VerticalAlign bottom = VerticalAlign.Bottom;
        drawStrategyCardNumbers(game, player, statTileMid, center, bottom);
        // To be used for speaker and other stuff
        Point miscTile = drawCommandCounters(player, dir1, statTile1, statTile2, dir2);
        int offBoardHighlighting = drawOffBoardHighlighting(game, player, miscTile);
        drawSpeaker(player, miscTile, offBoardHighlighting);
        drawPassedActiveOrAfk(game, player, miscTile, center);
    }

    private void drawFactionIcon(Player player, Point statTileMid) {
        Point point;
        point = PositionMapper.getPlayerStats("factionicon");
        int size = 275;
        point.translate(statTileMid.x - (size / 2), statTileMid.y - (size / 2));
        DrawingUtil.drawPlayerFactionIconImageUnderlay(graphics, player, point.x, point.y, size, size);
        DrawingUtil.drawPlayerFactionIconImageOpaque(graphics, player, point.x, point.y, size, size, 0.40f);
    }

    private void drawUsername(Game game, Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        graphics.setFont(Storage.getFont32());
        String userName = player.getUserName();
        point = PositionMapper.getPlayerStats("newuserName");
        if (!game.hideUserNames()) {
            String name = userName.substring(0, Math.min(userName.length(), 15));
            DrawingUtil.superDrawString(
                    graphics,
                    name,
                    statTileMid.x + point.x,
                    statTileMid.y + point.y,
                    Color.WHITE,
                    center,
                    null,
                    stroke5,
                    Color.BLACK);
        }
    }

    private void drawFactionNameAndColorStroters(Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        graphics.setFont(Storage.getFont32());
        point = PositionMapper.getPlayerStats("newfaction");
        point.translate(statTileMid.x, statTileMid.y);
        String factionText = player.getFactionModel() == null
                ? StringUtils.capitalize(player.getFaction())
                : player.getFactionModel().getShortName();
        if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
            factionText = player.getDisplayName();
        }
        DrawingUtil.superDrawString(
                graphics, factionText, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
    }

    private void drawVictoryPoints(Game game, Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        graphics.setFont(Storage.getFont32());
        String vpCount = "VP: " + player.getTotalVictoryPoints() + " / " + game.getVp();
        point = PositionMapper.getPlayerStats("newvp");
        point.translate(statTileMid.x, statTileMid.y);
        DrawingUtil.superDrawString(
                graphics, vpCount, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
    }

    private void drawSecretObjectiveIcons(Game game, Player player, Point statTileMid) {
        Point point;
        List<String> soToPoList = game.getSoToPoList();
        int unscoredSOs = player.getSecrets().size();
        int scoredSOs = (int) player.getSecretsScored().keySet().stream()
                .filter(so -> !soToPoList.contains(so))
                .count();
        int secretsEmpty = player.getMaxSOCount() - unscoredSOs - scoredSOs;
        int soOffset = (15 + 35 * player.getMaxSOCount()) / 2 - 50;

        String soHand = "pa_so-icon_hand.png";
        String soScored = "pa_so-icon_scored.png";
        String soEmpty = "pa_so-icon_empty.png";
        point = PositionMapper.getPlayerStats("newso");
        for (int i = 0; i < secretsEmpty; i++) {
            drawPAImage((point.x + statTileMid.x + soOffset), point.y + statTileMid.y, soEmpty);
            soOffset -= 35;
        }
        for (int i = 0; i < unscoredSOs; i++) {
            drawPAImage((point.x + statTileMid.x + soOffset), point.y + statTileMid.y, soHand);
            soOffset -= 35;
        }
        for (int i = 0; i < scoredSOs; i++) {
            drawPAImage((point.x + statTileMid.x + soOffset), point.y + statTileMid.y, soScored);
            soOffset -= 35;
        }
    }

    private int drawOffBoardHighlighting(Game game, Player player, Point miscTile) {
        int offBoardHighlighting = 0;
        if (displayType == DisplayType.legendaries) {
            boolean hasNanoForge = player.hasRelic("nanoforge") || player.hasRelic("absol_nanoforge");
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                offBoardHighlighting +=
                        (custodiaVigilia.getLegendaryAbilityName() != null && game.getTileFromPlanet(planet) == null)
                                ? 1
                                : 0;
            }
            if (offBoardHighlighting >= 1) {
                String legendaryFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                BufferedImage bufferedImage = ImageHelper.read(legendaryFile);
                if (offBoardHighlighting + (hasNanoForge ? 1 : 0) >= 2) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24_000.0f
                            / (offBoardHighlighting + (hasNanoForge ? 1 : 0))
                            / bufferedImage.getWidth()
                            / bufferedImage.getHeight()));
                    for (int i = 0; i < offBoardHighlighting; i++) {
                        graphics.drawImage(
                                bufferedImage,
                                miscTile.x
                                        + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2
                                        - 30
                                        + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1),
                                miscTile.y
                                        + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                        - 30
                                        + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1)
                                        + (player.isSpeaker() ? 30 : 0),
                                null);
                    }
                } else {
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            }
            if (hasNanoForge) {
                String relicFile = ResourceHelper.getInstance().getGeneralFile("Relic.png");
                BufferedImage bufferedImage = ImageHelper.read(relicFile);
                if (offBoardHighlighting >= 1) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24_000.0f
                            / (offBoardHighlighting + 1)
                            / bufferedImage.getWidth()
                            / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2 + 30,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + 30
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                } else {
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
                offBoardHighlighting++;
            }
        } else if (displayType == DisplayType.wormholes && "ghost".equalsIgnoreCase(player.getFaction())) {
            boolean alphaOnMap = false;
            boolean betaOnMap = false;
            boolean gammaOnMap = false;
            String alphaID = Mapper.getTokenID("creussalpha");
            String betaID = Mapper.getTokenID("creussbeta");
            String gammaID = Mapper.getTokenID("creussgamma");
            for (Tile tile2 : game.getTileMap().values()) {
                Set<String> tileTokens = tile2.getUnitHolders().get("space").getTokenList();
                alphaOnMap |= tileTokens.contains(alphaID);
                betaOnMap |= tileTokens.contains(betaID);
                gammaOnMap |= tileTokens.contains(gammaID);
            }

            offBoardHighlighting = (alphaOnMap ? 0 : 1) + (betaOnMap ? 0 : 1) + (gammaOnMap ? 0 : 1);
            int x = miscTile.x + (BoardLayout.TILE_WIDTH - 80) / 2;
            x += (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 30 : 0);
            int y = miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 80) / 2 + (player.isSpeaker() ? 30 : 0);
            boolean reconstruction =
                    (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
            boolean travelBan =
                    ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");

            if (!gammaOnMap) {
                String tokenFile = Mapper.getTokenPath(gammaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
            if (!betaOnMap) {
                String tokenFile = Mapper.getTokenPath(betaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                if (travelBan) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance()
                            .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                }
                if (reconstruction) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(
                            ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                    graphics.drawImage(doubleWormholeImage, x, y, null);
                }
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
            if (!alphaOnMap) {
                String tokenFile = Mapper.getTokenPath(alphaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                if (travelBan) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance()
                            .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                }
                if (reconstruction) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(
                            ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                    graphics.drawImage(doubleWormholeImage, x, y, null);
                }
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
        } else if (displayType == DisplayType.anomalies
                && (player.ownsUnitSubstring("cabal_spacedock") || player.hasTech("tf-dimensionaltear"))) {
            UnitKey unitKey = Mapper.getUnitKey("sd", player.getColor());
            int unitNum = player.getUnitCap("sd") + player.getUnitCap("csd");
            unitNum = (unitNum == 0
                    ? PositionMapper.getReinforcementsPosition("sd").getPositionCount("sd")
                    : unitNum);
            for (Tile tile2 : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                    unitNum -= unitHolder.getUnitCount(unitKey);
                }
            }
            if (unitNum > 0) {
                int x = miscTile.x + (BoardLayout.TILE_WIDTH - 95) / 2;
                x += (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 30 : 0);
                int y = miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 95) / 2 + (player.isSpeaker() ? 30 : 0);
                String tokenFile = Mapper.getTokenPath("token_gravityrift.png");
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                for (int i = 0; i < unitNum; i++) {
                    graphics.drawImage(bufferedImage, x, y, null);
                    x -= (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 60 : 0);
                }
                offBoardHighlighting += unitNum;
            }
        } else if (displayType == DisplayType.traits) {
            List<String> traitFiles = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    String traitFile = "";
                    List<String> traits = planetReal.getPlanetType();

                    if ("faction".equalsIgnoreCase(planetReal.getOriginalPlanetType()) && traits.isEmpty()) {
                        if (custodiaVigilia.getFactionHomeworld() == null) {
                            traitFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                        } else {
                            traitFile = ResourceHelper.getInstance()
                                    .getFactionFile(custodiaVigilia.getFactionHomeworld() + ".png");
                        }
                    } else if (traits.size() == 1) {
                        String t = planetReal.getPlanetType().getFirst();
                        traitFile = ResourceHelper.getInstance()
                                .getGeneralFile(("" + t.charAt(0)).toUpperCase()
                                        + t.substring(1).toLowerCase() + ".png");
                    } else if (!traits.isEmpty()) {
                        String t = "";
                        t += traits.contains("cultural") ? "C" : "";
                        t += traits.contains("hazardous") ? "H" : "";
                        t += traits.contains("industrial") ? "I" : "";
                        if ("CHI".equals(t)) {
                            traitFile =
                                    ResourceHelper.getInstance().getPlanetResource("pc_attribute_combo_CHI_big.png");
                        } else {
                            traitFile =
                                    ResourceHelper.getInstance().getPlanetResource("pc_attribute_combo_" + t + ".png");
                        }
                    }
                    traitFiles.add(traitFile);
                    offBoardHighlighting++;
                }
            }
            if (offBoardHighlighting >= 2) {
                for (int i = 0; i < offBoardHighlighting; i++) {
                    BufferedImage bufferedImage = ImageHelper.read(traitFiles.get(i));
                    if (bufferedImage == null) {
                        BotLogger.error("Could not load trait file " + traitFiles.get(i));
                        continue;
                    }
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                            24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x
                                    + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1)
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(traitFiles.getFirst());
                if (bufferedImage != null) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float)
                            Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            }
        } else if (displayType == DisplayType.techskips) {
            List<String> techFiles = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    List<String> skips = planetReal.getTechSpeciality();
                    skips.removeAll(Collections.singleton(null));
                    skips.removeAll(Collections.singleton(""));
                    if (skips.isEmpty()) {
                        continue;
                    }
                    for (String skip : skips) {
                        switch (skip.toLowerCase()) {
                            case "biotic":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Biotic light.png"));
                                break;
                            case "cybernetic":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Cybernetic light.png"));
                                break;
                            case "propulsion":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Propulsion_light.png"));
                                break;
                            case "warfare":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Warfare_light.png"));
                                break;
                            default:
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Generic_Technology.png"));
                        }
                        offBoardHighlighting++;
                    }
                }
            }
            if (offBoardHighlighting >= 2) {
                for (int i = 0; i < offBoardHighlighting; i++) {
                    BufferedImage bufferedImage = ImageHelper.read(techFiles.get(i));
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                            24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x
                                    + (345 - bufferedImage.getWidth()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1)
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(techFiles.getFirst());
                bufferedImage = ImageHelper.scale(bufferedImage, (float)
                        Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
        } else if (displayType == DisplayType.attachments) {
            Map<String, String> attachFiles = new HashMap<>();
            Map<String, Integer> attachCount = new HashMap<>();
            for (String planet : player.getPlanets()) {
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    List<String> attach = new ArrayList<>(planetReal.getAttachments());
                    attach.removeAll(Collections.singleton(null));
                    attach.removeAll(Collections.singleton(""));
                    if (attach.isEmpty()) {
                        continue;
                    }
                    attachFiles.put(planet, ResourceHelper.getInstance().getGeneralFile("misc_chevrons_basic.png"));
                    if (attach.contains("attachment_tombofemphidia.png")) {
                        attachFiles.put(planet, ResourceHelper.getInstance().getGeneralFile("misc_chevrons_toe.png"));
                    }
                    attachCount.put(planet, attach.size());
                    offBoardHighlighting++;
                }
            }
            if (offBoardHighlighting >= 2) {
                for (Map.Entry<String, String> entry : attachFiles.entrySet()) {
                    String planet = entry.getKey();
                    BufferedImage bufferedImage = ImageHelper.read(entry.getValue());
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                            24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                    if (attachCount.get(planet) > 1) {
                        graphics.setColor(Color.WHITE);
                        graphics.fillOval(
                                miscTile.x + (345 - 80) / 2 - 30,
                                miscTile.y
                                        + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                        - 30
                                        + (player.isSpeaker() ? 30 : 0),
                                80,
                                80);
                        graphics.setColor(Color.BLACK);
                        graphics.fillOval(
                                miscTile.x + (345 - 72) / 2 - 30,
                                miscTile.y
                                        + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                        - 30
                                        + (player.isSpeaker() ? 30 : 0)
                                        + 4,
                                72,
                                72);
                        graphics.setColor(Color.WHITE);
                        DrawingUtil.drawCenteredString(
                                graphics,
                                "" + attachCount.get(planet),
                                new Rectangle(
                                        miscTile.x + (345 - 80) / 2 - 30,
                                        miscTile.y
                                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                                - 30
                                                + (player.isSpeaker() ? 30 : 0),
                                        80,
                                        80),
                                Storage.getFont48());
                    }
                }
            } else if (offBoardHighlighting == 1) {
                String planet = attachFiles.keySet().iterator().next();

                BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                bufferedImage = ImageHelper.scale(bufferedImage, (float)
                        Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
                if (attachCount.get(planet) > 1) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillOval(
                            miscTile.x + (345 - 80) / 2,
                            miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                            80,
                            80);
                    graphics.setColor(Color.BLACK);
                    graphics.fillOval(
                            miscTile.x + (345 - 72) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                    + (player.isSpeaker() ? 30 : 0)
                                    + 4,
                            72,
                            72);
                    graphics.setColor(Color.WHITE);
                    DrawingUtil.drawCenteredString(
                            graphics,
                            "" + attachCount.get(planet),
                            new Rectangle(
                                    miscTile.x + (345 - 80) / 2,
                                    miscTile.y
                                            + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                            + (player.isSpeaker() ? 30 : 0),
                                    80,
                                    80),
                            Storage.getFont48());
                }
            }
        }
        return offBoardHighlighting;
    }

    private Point drawCommandCounters(Player player, int dir1, Point statTile1, Point statTile2, int dir2) {
        Point point;
        Point miscTile;
        graphics.setFont(Storage.getFont32());
        String ccID = Mapper.getCCID(player.getColor());
        String fleetCCID = Mapper.getFleetCCID(player.getColor());
        point = PositionMapper.getPlayerStats("newcc");
        int dir = dir1;
        if (dir1 == 1 || dir1 == 2 || dir1 == 4 || dir1 == 5) {
            point.translate(statTile1.x, statTile1.y);
            miscTile = statTile2;
        } else {
            dir = dir2;
            point.translate(statTile2.x, statTile2.y);
            miscTile = statTile1;
        }
        boolean rightAlign = false;
        switch (dir) {
            case 0, 1, 2, 3 -> point.translate(60, 45); // centered vertically, coming from the left side
            case 4, 5 -> {
                point.translate(210, 45); // centered vertically
                rightAlign = true; // coming from the left side
            }
        }

        DrawingUtil.drawCCOfPlayer(graphics, ccID, point.x, point.y, player.getTacticalCC(), player, false, rightAlign);
        drawFleetCCOfPlayer(graphics, fleetCCID, point.x, point.y + 65, player, rightAlign);
        DrawingUtil.drawCCOfPlayer(
                graphics, ccID, point.x, point.y + 130, player.getStrategicCC(), player, false, rightAlign);

        // Additional FS
        int additionalFleetSupply = 0;
        String addFS = "";

        if (player.hasAbility("edict") || player.hasAbility("edict_y")) {
            additionalFleetSupply += player.getMahactCC().size();
        }
        if (player.hasAbility("armada")) additionalFleetSupply += 2;
        if (additionalFleetSupply > 0) addFS = "*";

        // Draw Numbers
        HorizontalAlign align = HorizontalAlign.Left;
        List<String> reps = Arrays.asList(player.getCCRepresentation().split("/"));
        graphics.setFont(Storage.getFont28());
        point.translate(rightAlign ? 58 : -3, 32);
        String fleetCCs = player.getFleetCC() + additionalFleetSupply + addFS;
        DrawingUtil.superDrawString(
                graphics, reps.get(0), point.x, point.y, Color.WHITE, align, null, stroke4, Color.BLACK);
        DrawingUtil.superDrawString(
                graphics, fleetCCs, point.x, point.y + 65, Color.WHITE, align, null, stroke4, Color.BLACK);
        DrawingUtil.superDrawString(
                graphics, reps.get(2), point.x, point.y + 130, Color.WHITE, align, null, stroke4, Color.BLACK);
        return miscTile;
    }

    private void drawStrategyCardNumbers(
            Game game, Player player, Point statTileMid, HorizontalAlign center, VerticalAlign bottom) {
        Point point;
        graphics.setFont(Storage.getFont80());
        int scsize = 96;
        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        if (player.hasTheZeroToken()) playerSCs.add(0);
        if (player.hasAbility("patience")) {
            playerSCs.add(9);
        }
        Collections.sort(playerSCs);

        point = PositionMapper.getPlayerStats("newsc");
        point.translate(statTileMid.x, statTileMid.y);
        point.translate(-1 * (scsize / 2) * (playerSCs.size() - 1), -1 * (scsize / 2));

        for (int sc : playerSCs) {
            StrategyCardModel scModel =
                    game.getStrategyCardModelByInitiative(sc).orElse(null);
            if (sc == 0) {
                drawPAImageScaled(point.x, point.y, "pa_telepathic.png", scsize);
                point.translate(scsize, 0);
            } else {
                int fontYoffset = (scsize / 2) + 25;
                DrawingUtil.superDrawString(
                        graphics,
                        Integer.toString(sc),
                        point.x,
                        point.y + fontYoffset,
                        ColorUtil.getSCColor(sc, game),
                        center,
                        bottom,
                        stroke6,
                        Color.BLACK);
                if (scModel != null) {
                    addWebsiteOverlay(scModel, point.x - 20, point.y + 20, 40, 50);
                    // graphics.drawRect(point.x - 20, point.y + 20, 40, 50); //debug
                }
                point.translate(scsize, 0);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont32());
    }

    private void drawPassedActiveOrAfk(Game game, Player player, Point miscTile, HorizontalAlign center) {
        Point point;
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = PositionMapper.getPlayerStats("newpassed");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    graphics, "PASSED", point.x, point.y, ColorUtil.PassedColor, center, null, stroke4, Color.BLACK);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = PositionMapper.getPlayerStats("newpassed");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    graphics, "ACTIVE", point.x, point.y, ColorUtil.ActiveColor, center, null, stroke4, Color.BLACK);
        }
        if (player.isAFK()) {
            point = PositionMapper.getPlayerStats("newafk");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    graphics, "AFK", point.x, point.y, Color.gray, center, null, stroke4, Color.BLACK);
        }
        graphics.setColor(Color.WHITE);
    }

    private void drawSpeaker(Player player, Point miscTile, int offBoardHighlighting) {
        Point point;
        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
            BufferedImage img = ImageHelper.read(speakerFile);
            if (img != null) {
                point = PositionMapper.getPlayerStats("newspeaker");
                point.translate(miscTile.x - (img.getWidth() / 2), miscTile.y - (img.getHeight() / 2));
                graphics.drawImage(img, point.x, point.y - (offBoardHighlighting > 0 ? 30 : 0), null);
            }
        }
        if (player.isTyrant()) {
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID("tyrant"));
            BufferedImage img = ImageHelper.read(speakerFile);
            if (img != null) {
                point = PositionMapper.getPlayerStats("newspeaker");
                point.translate(miscTile.x - (img.getWidth() / 2), miscTile.y - (img.getHeight() / 2));
                graphics.drawImage(img, point.x, point.y - (offBoardHighlighting > 0 ? 30 : 0), null);
            }
        }
    }

    private void paintPlayerInfoOld(Game game, Player player, int ringCount) {
        int deltaX, deltaSplitX = 0;
        int deltaY, deltaSplitY = 0;

        String playerStatsAnchor = player.getPlayerStatsAnchorPosition();
        if (playerStatsAnchor != null) {
            Point anchorProjectedPoint = PositionMapper.getTilePosition(playerStatsAnchor);
            if (anchorProjectedPoint != null) {
                Point playerStatsAnchorPoint = PositionMapper.getScaledTilePosition(
                        game, playerStatsAnchor, anchorProjectedPoint.x, anchorProjectedPoint.y, board.fractureYbump());
                playerStatsAnchorPoint.translate(-board.frameOffsetX(), -board.frameOffsetY());
                Integer anchorLocationIndex =
                        PositionMapper.getRingSideNumberOfTileID(player.getPlayerStatsAnchorPosition());
                anchorLocationIndex = anchorLocationIndex == null ? 0 : anchorLocationIndex - 1;
                boolean isCorner = playerStatsAnchor.equals(
                        PositionMapper.getTileIDAtCornerPositionOfRing(ringCount, anchorLocationIndex + 1));
                if (anchorLocationIndex == 0 && isCorner) { // North Corner
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X + 80;
                    deltaY = playerStatsAnchorPoint.y - 80;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 0) { // North East
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X + BoardLayout.SPACE_FOR_TILE_HEIGHT;
                    deltaY = playerStatsAnchorPoint.y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 1) { // East
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 2 && isCorner) { // South East Corner
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 2) { // South East
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y + 100;
                } else if (anchorLocationIndex == 3 && isCorner) { // South Corner
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + 360 + BoardLayout.EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 3) { // South West
                    deltaX = playerStatsAnchorPoint.x;
                    deltaY = playerStatsAnchorPoint.y + 250 + BoardLayout.EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 4) { // West
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 5 && isCorner) { // North West Corner
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 5) { // North West
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y - 100;
                    deltaSplitX = 200;
                } else return;
            } else return;
        } else return;

        String userName = player.getUserName();

        boolean convertToGeneric = fog.shouldConvertToGeneric(player);
        if (convertToGeneric) {
            return;
        }

        // PAINT USERNAME
        Point point = PositionMapper.getPlayerStats(Constants.STATS_USERNAME);
        if (!game.hideUserNames()) {
            graphics.drawString(
                    userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX, point.y + deltaY);
        }

        // PAINT FACTION
        point = PositionMapper.getPlayerStats(Constants.STATS_FACTION);
        String factionText = player.getFaction();
        if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
            factionText = player.getDisplayName();
        }
        graphics.drawString(StringUtils.capitalize(factionText), point.x + deltaX, point.y + deltaY);

        // PAINT COLOR
        point = PositionMapper.getPlayerStats(Constants.STATS_COLOR);
        graphics.drawString(player.getColor(), point.x + deltaX, point.y + deltaY);

        // PAIN VICTORY POINTS
        int vpCount = player.getTotalVictoryPoints();
        point = PositionMapper.getPlayerStats(Constants.STATS_VP);
        graphics.drawString("VP: " + vpCount, point.x + deltaX, point.y + deltaY);

        // PAINT SO ICONS
        int totalSecrets = player.getSecrets().size();
        Set<String> soSet = player.getSecretsScored().keySet();
        int soOffset = 0;
        String soHand = "pa_so-icon_hand.png";
        String soScored = "pa_so-icon_scored.png";
        point = PositionMapper.getPlayerStats(Constants.STATS_SO);
        for (int i = 0; i < totalSecrets; i++) {
            drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soHand);
            soOffset += 25;
        }
        List<String> soToPoList = game.getSoToPoList();
        for (String soID : soSet) {
            if (!soToPoList.contains(soID)) {
                drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soScored);
                soOffset += 25;
            }
        }

        // PAINT SC#
        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        Collections.sort(playerSCs);
        int count = 0;
        for (int sc : playerSCs) {
            String scText = sc == 0 ? " " : Integer.toString(sc);
            scText = Game.getSCNumberIfNaaluInPlay(player, scText);
            graphics.setColor(ColorUtil.getSCColor(sc, game));
            graphics.setFont(Storage.getFont64());
            point = PositionMapper.getPlayerStats(Constants.STATS_SC);
            if (sc != 0) {
                graphics.drawString(scText, point.x + deltaX + 64 * count, point.y + deltaY);
            }
            count++;
        }

        // PAINT CCs
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont32());
        String ccID = Mapper.getCCID(player.getColor());
        String fleetCCID = Mapper.getFleetCCID(player.getColor());
        point = PositionMapper.getPlayerStats(Constants.STATS_CC);
        int x = point.x + deltaX;
        int y = point.y + deltaY;
        if (deltaSplitX != 0) {
            deltaSplitY = point.y;
        }

        DrawingUtil.drawCCOfPlayer(
                graphics, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player, false, false);
        drawFleetCCOfPlayer(graphics, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player, false);
        DrawingUtil.drawCCOfPlayer(
                graphics, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), player, false, false);

        // PAINT SPEAKER
        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                graphics.drawImage(
                        bufferedImage,
                        point.x + deltaX + deltaSplitX + negativeDelta,
                        point.y + deltaY - deltaSplitY,
                        null);
                graphics.setColor(Color.WHITE);
            }
        }
        if (player.isTyrant()) {
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID("tyrant"));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                graphics.drawImage(
                        bufferedImage,
                        point.x + deltaX + deltaSplitX + negativeDelta,
                        point.y + deltaY - deltaSplitY,
                        null);
                graphics.setColor(Color.WHITE);
            }
        }
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(ColorUtil.PassedColor);
            graphics.drawString("PASSED", point.x + deltaX, point.y + deltaY);
            graphics.setColor(Color.WHITE);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(ColorUtil.ActiveColor);
            graphics.drawString("ACTIVE", point.x + deltaX + 4, point.y + deltaY);

            if (player.isAFK()) {
                graphics.setColor(Color.GRAY);
                graphics.drawString("(AFK)", point.x + deltaX + 124, point.y + deltaY);
            }
            graphics.setColor(Color.WHITE);
        } else if (player.isAFK()) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(Color.GRAY);
            graphics.drawString("AFK", point.x + deltaX + 4, point.y + deltaY);
            graphics.setColor(Color.WHITE);
        }
    }

    private static void drawFleetCCOfPlayer(Graphics graphics, String ccID, int x, int y, Player player) {
        drawFleetCCOfPlayer(graphics, ccID, x, y, player, true);
    }

    private static void drawFleetCCOfPlayer(
            Graphics graphics, String ccID, int x, int y, Player player, boolean rightAlign) {
        String ccPath = Mapper.getCCPath(ccID);
        int ccCount = player.getFleetCC();
        boolean hasArmada = player.hasAbility("armada");
        List<String> mahactCC = player.getMahactCC();
        boolean hasMahactCCs =
                !player.getMahactCC().isEmpty() && (player.hasAbility("edict") || player.hasAbility("edict_y"));

        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            BufferedImage blankCC = ImageHelper.read(Mapper.getCCPath("command_blank.png"));
            int delta = rightAlign ? -20 : 20;

            // DRAW TWO ARMADA TOKENS
            if (hasArmada) {
                BufferedImage armadaLowerCCImage =
                        ImageHelper.read(Mapper.getCCPath(Mapper.getCCID(player.getColor())));
                BufferedImage armadaCCImage = ImageHelper.read(Mapper.getCCPath("fleet_armada.png"));
                for (int i = 0; i < 2; i++) {
                    graphics.drawImage(armadaLowerCCImage, x, y, null);
                    graphics.drawImage(armadaCCImage, x, y, null);
                    x += delta;
                }
                x += delta;
            }

            if (ccCount == 0 && !hasArmada && !hasMahactCCs) {
                ccCount = 1;
                ccImage = blankCC;
            }

            // DRAW FLEET TOKENS
            for (int i = 0; i < ccCount; i++) {
                graphics.drawImage(ccImage, x, y, null);
                x += delta;
            }

            if (hasMahactCCs) {
                if (hasArmada || ccCount >= 1) {
                    x += delta;
                }
                for (String ccColor : mahactCC) {
                    String fleetCCID = Mapper.getCCPath(Mapper.getFleetCCID(ccColor));
                    BufferedImage ccImageExtra = ImageHelper.readScaled(fleetCCID, 1.0f);
                    graphics.drawImage(ccImageExtra, x, y, null);
                    x += delta;
                }
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "Ignored exception during map generation", e);
        }
    }

    private int laws(int y) {
        if (displayTypeBasic == DisplayType.map) {
            return y;
        }
        int x = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
        int lawWidth = 1178 + 8;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke3);

        Map<String, Integer> laws = game.getLaws();
        Map<String, String> lawsInfo = game.getLawsInfo();
        boolean secondColumn = false;
        for (Map.Entry<String, Integer> lawEntry : laws.entrySet()) {
            String lawID = lawEntry.getKey();
            String lawNumberID = "(" + lawEntry.getValue() + ") ";
            String optionalText = lawsInfo.get(lawID);
            graphics.setFont(Storage.getFont35());
            graphics.setColor(ColorUtil.LawColor);
            AgendaModel agendaModel = Mapper.getAgenda(lawID);

            graphics.drawRect(x, y, 1178, 110);
            if (agendaModel != null) {
                addWebsiteOverlay(agendaModel, x, y, 1178, 110);
            }
            String agendaTitle = Mapper.getAgendaTitle(lawID);
            if (agendaTitle == null) {
                agendaTitle = Mapper.getAgendaJustNames().get(lawID);
            }
            if (agendaTitle == null) {
                BotLogger.error("Unable to find law with id '" + lawID + "' during map generation. Skipping.");
                continue;
            }
            if (optionalText != null
                    && !optionalText.isEmpty()
                    && game.getPlayerFromColorOrFaction(optionalText) == null) {
                agendaTitle += "   [" + optionalText + "]";
            }
            graphics.drawString(agendaTitle, x + 95, y + 33);
            graphics.setFont(Storage.getFont26());
            graphics.setColor(Color.WHITE);
            String agendaText = Mapper.getAgendaText(lawID);
            if (agendaText == null) {
                agendaText = Mapper.getAgendaForOnly(lawID);
            }
            agendaText = lawNumberID + agendaText;
            int width = g2.getFontMetrics().stringWidth(agendaText);

            int index = 0;
            int agendaTextLength = agendaText.length();
            while (width > 1076) {
                index++;
                String substringText = agendaText.substring(0, agendaTextLength - index);
                width = g2.getFontMetrics().stringWidth(substringText);
            }
            if (index > 0) {
                while (index < agendaTextLength && agendaText.charAt(agendaTextLength - index) != ' ') {
                    index++;
                }
                graphics.drawString(agendaText.substring(0, agendaTextLength - index), x + 95, y + 70);
                graphics.drawString(agendaText.substring(agendaTextLength - index), x + 95, y + 96);
            } else {
                graphics.drawString(agendaText, x + 95, y + 70);
            }
            try {
                String agendaType = Mapper.getAgendaType(lawID);

                if (optionalText == null
                        || optionalText.isEmpty()
                        || game.getPlayerFromColorOrFaction(optionalText) == null) {
                    paintAgendaIcon(y, x);
                } else if ("0".equals(agendaType)) {
                    Player electedPlayer = null;
                    boolean convertToGeneric = false;
                    for (Player player : game.getPlayers().values()) {
                        if (optionalText.equals(player.getFaction()) || optionalText.equals(player.getColor())) {
                            if (fog.shouldConvertToGeneric(player)) {
                                convertToGeneric = true;
                            }
                            electedPlayer = player;
                            break;
                        }
                    }
                    if (convertToGeneric || electedPlayer == null) {
                        paintAgendaIcon(y, x);
                    } else {
                        DrawingUtil.drawPlayerFactionIconImage(graphics, electedPlayer, x + 2, y + 2, 95, 95);
                    }
                }
                if (!game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue())
                        .isEmpty()) {
                    int tokenDeltaY = 0;
                    int count = 0;
                    for (String debtToken : game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue())
                            .split("_")) {

                        boolean hideFactionIcon = fog.isPrivate()
                                && !FoWHelper.canSeeStatsOfPlayer(
                                        game, game.getPlayerFromColorOrFaction(debtToken), fog.viewer());
                        String controlID =
                                hideFactionIcon ? Mapper.getControlID("gray") : Mapper.getControlID(debtToken);
                        if (controlID.contains("null")) {
                            continue;
                        }
                        float scale = 0.80f;
                        BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                        DrawingUtil.drawControlToken(
                                graphics,
                                controlTokenImage,
                                game.getPlayerFromColorOrFaction(debtToken),
                                x + (count / 3) * 55,
                                y + tokenDeltaY - (count / 3) * 90,
                                hideFactionIcon,
                                scale);
                        tokenDeltaY += 30;
                        count += 1;
                    }
                }

            } catch (Exception e) {
                BotLogger.error("Could not paint agenda icon", e);
            }

            if (!secondColumn) {
                secondColumn = true;
                x += lawWidth;
            } else {
                secondColumn = false;
                y += 118;
                x -= lawWidth;
            }
        }
        return y + 3 + (secondColumn ? 118 : 0);
    }

    private int events(int y) {
        int x = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke3);

        Map<String, Integer> events = game.getEventsInEffect();
        boolean secondColumn = false;
        for (Map.Entry<String, Integer> event : events.entrySet()) {
            String eventID = event.getKey();
            String eventNumberID = "(" + event.getValue() + ") ";

            graphics.setFont(Storage.getFont35());
            graphics.setColor(Color.BLUE);
            graphics.drawRect(x, y, 1178, 110);

            EventModel eventModel = Mapper.getEvent(eventID);

            graphics.setColor(Color.WHITE);
            graphics.drawString(eventModel.getName(), x + 95, y + 30);
            graphics.setFont(Storage.getFont26());

            String eventText = eventModel.getMapText();
            eventText = eventNumberID + eventText;
            int width = g2.getFontMetrics().stringWidth(eventText);

            int index = 0;
            int textLength = eventText.length();
            while (width > 1076) {
                index++;
                String substringText = eventText.substring(0, textLength - index);
                width = g2.getFontMetrics().stringWidth(substringText);
            }
            if (index > 0) {
                graphics.drawString(eventText.substring(0, textLength - index), x + 95, y + 70);
                graphics.drawString(eventText.substring(textLength - index), x + 95, y + 96);
            } else {
                graphics.drawString(eventText, x + 95, y + 70);
            }
            try {
                paintEventIcon(y, x);
            } catch (Exception e) {
                BotLogger.error("Could not paint event icon", e);
            }

            if (!secondColumn) {
                secondColumn = true;
                x += 1178 + 8;
            } else {
                secondColumn = false;
                y += 112;
                x = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
            }
        }
        return secondColumn ? y + 115 : y + 3;
    }

    private void paintAgendaIcon(int y, int x) {
        String factionFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
        if (factionFile != null) {
            BufferedImage bufferedImage = ImageHelper.read(factionFile);
            graphics.drawImage(bufferedImage, x + 2, y + 2, null);
        }
    }

    private void paintEventIcon(int y, int x) {
        String factionFile = ResourceHelper.getInstance().getFactionFile("event.png");
        if (factionFile != null) {
            BufferedImage bufferedImage = ImageHelper.read(factionFile);
            graphics.drawImage(bufferedImage, x + 2, y + 2, null);
        }
    }

    private void addTile(Tile tile, TileStep step) {
        if (tile == null || tile.getTileID() == null) {
            return;
        }
        try {
            String position = tile.getPosition();
            Point positionPoint = PositionMapper.getTilePosition(position);
            if (positionPoint == null) {
                if ("-1".equalsIgnoreCase(tile.getTileID())) {
                    return;
                }
                throw new Exception("Could not map tile to a position on the map: " + game.getName());
            }

            Point origin = board.tileOrigin(position, positionPoint);
            if (origin == null) {
                return;
            }

            TileGenerator tileGenerator = new TileGenerator(game, event, displayType);
            BufferedImage tileImage = tileGenerator.draw(tile, step);
            graphics.drawImage(tileImage, origin.x, origin.y, null);
        } catch (Exception exception) {
            BotLogger.error(
                    "Tile Error, when building map `" + game.getName() + "`, tile: " + tile.getTileID(), exception);
        }
    }

    // The first parameter is the scale factor (contrast), the second is the offset
    // (brightness)
    private static BufferedImage makeGrayscale(BufferedImage image) {
        return GRAYSCALE_CONVERT_OP.filter(image, null);
    }

    private void addWebsiteOverlay(String overlayTitle, String overlayText, int x, int y, int width, int height) {
        websiteOverlays.add(overlayTitle, overlayText, x, y, width, height);
    }

    private void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        websiteOverlays.add(dataModel, x, y, width, height);
    }

    String getGameName() {
        return game.getName();
    }
}
