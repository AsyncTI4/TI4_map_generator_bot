package ti4.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.ResourceHelper;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna.AeternaBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisAbilityHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PatternHelper;
import ti4.helpers.PdsCoverage;
import ti4.helpers.PdsCoverageHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.MapGenerator.VerticalAlign;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.BorderAnomalyModel;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.ShipPositionModel.ShipPosition;
import ti4.model.UnitModel;
import ti4.service.image.FileUploadService;
import ti4.service.map.CustomHyperlaneService;

public class TileGenerator {

    private static final int TILE_PADDING = 100;
    private static final Point TILE_POSITION_POINT = new Point(255, 295);
    // top-left of the fog-vision token, tucked inside the hex's bottom-left edge
    private static final Point FOW_VISION_TOKEN_POINT = new Point(75, 236);
    private static final Point LABEL_POSITION_POINT = new Point(90, 295);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);
    private static final BasicStroke stroke7 = new BasicStroke(7.0f);
    private static final BasicStroke stroke8 = new BasicStroke(8.0f);
    private static final int TILE_EXTRA_WIDTH = 260;
    private static final int EXTRA_X = 100;
    private static final int EXTRA_Y = 100;

    static final int TILE_WIDTH = 345;
    static final int TILE_HEIGHT = 300;
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Point SPIRAL_OFFSET = new Point(36, 43);
    private static final List<String> GEAR_PROBLEM_TILES = List.of("25", "26", "64");
    private static final List<String> WORMHOLE_TOKEN_MARKERS = List.of(
            "wormhole",
            "token_creuss",
            "token_crimsoncreuss",
            "token_custom_eronous_wh",
            "token_gamma",
            "token_ion",
            "token_wh");

    private final Game game;
    private final GenericInteractionCreateEvent event;
    private final boolean isFoWPrivate;
    private final Player fowPlayer;
    private final int context;
    private final String focusTile;
    private final DisplayType displayType;

    // Map of nucleus slice number to border color. This should correspond to the
    // colors used in the MapTemplateHelper.getTileFromTemplateTile for draft tiles, given a player number.
    // And note that the "red" in the MapTemplateHelper is always skipped.
    private static final Map<Integer, String> NUCLEUS_SLICE_COLORS = Map.of(
            1, "blue",
            2, "yellow",
            3, "green", // draft tile color: emerald
            4, "lavender",
            5, "petrol",
            6, "brown", // draft tile color: chocolate
            7, "ethereal",
            8, "forest");

    // Map to aggregate unit coordinates by faction from all UnitRenderGenerator
    // calls
    private final Map<String, Map<String, List<Point>>> unitCoordinatesByFaction = new HashMap<>();

    public TileGenerator(@NotNull Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        this(game, event, displayType, 0, "000", null);
    }

    public TileGenerator(
            @NotNull Game game,
            GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            int context,
            @NotNull String focusTile) {
        this(game, event, displayType, context, focusTile, null);
    }

    public TileGenerator(
            @NotNull Game game,
            GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            int context,
            @NotNull String focusTile,
            @Nullable Player fowPlayer) {
        this.game = game;
        this.event = event;
        this.displayType = displayType;
        this.context = context;
        this.focusTile = focusTile;
        isFoWPrivate = MapGenerator.isFowPrivateView(game, event);
        this.fowPlayer =
                fowPlayer != null ? fowPlayer : (event != null ? MapGenerator.resolveViewingPlayer(game, event) : null);
    }

    /**
     * Get the aggregated unit coordinates by faction from all UnitRenderGenerator
     * calls
     *
     * @return Map where key is faction/player identifier, secondary key is unit ID,
     *         and value is list of coordinates
     */
    public Map<String, Map<String, List<Point>>> getUnitCoordinatesByFaction() {
        return new HashMap<>(unitCoordinatesByFaction);
    }

    /**
     * Helper method to aggregate unit coordinates from a UnitRenderGenerator into
     * the main map
     *
     * @param unitRenderGenerator The UnitRenderGenerator to get coordinates from
     */
    private void aggregateUnitCoordinates(UnitRenderGenerator unitRenderGenerator) {
        Map<String, Map<String, List<Point>>> renderCoordinates = unitRenderGenerator.getUnitCoordinatesByFaction();
        if (renderCoordinates == null) {
            return;
        }
        renderCoordinates.forEach(
                (faction, unitMap) -> unitMap.forEach((unitId, coordinates) -> unitCoordinatesByFaction
                        .computeIfAbsent(faction, _ -> new HashMap<>())
                        .computeIfAbsent(unitId, _ -> new ArrayList<>())
                        .addAll(coordinates)));
    }

    public FileUpload createFileUpload() {
        return FileUploadService.createFileUpload(createMainImage(), game.getName());
    }

    public BufferedImage createMainImage() {
        Map<String, Tile> tileMap = new HashMap<>(game.getTileMap());
        tileMap.keySet().retainAll(getTilesToShow(game, context, focusTile));
        if (isFoWPrivate) {
            MapGenerator.replaceHiddenTilesWithFog(game, fowPlayer, tileMap);
        }

        int width = TILE_WIDTH + (TILE_EXTRA_WIDTH * 2 * context) + EXTRA_X;
        int height = TILE_HEIGHT * (2 * context + 1) + EXTRA_Y;
        var mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = mainImage.getGraphics();

        try {
            tileMap.remove(null);

            List<String> sortedTiles = tileMap.keySet().stream().sorted().toList();
            sortedTiles.forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Tile));
            MapGenerator.tilesWithExtras(game).forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Extras));
            sortedTiles.forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Units));
            sortedTiles.forEach(key -> addTile(graphics, tileMap.get(key), TileStep.TileNumber));

            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            String timeStamp = getTimeStamp();
            graphics.drawString(game.getName() + " " + timeStamp, 0, 34);
        } catch (Exception e) {
            BotLogger.error(game.getName() + ": Could not save generated system info image", e);
        }

        return mainImage;
    }

    private static Set<String> getTilesToShow(Game game, int context, String focusTile) {
        Set<String> tileSet = new HashSet<>(Collections.singleton(focusTile));
        Set<String> tilesToCheck = new HashSet<>(Collections.singleton(focusTile));
        for (int i = 0; i < context; i++) {
            Set<String> nextTiles = new HashSet<>();
            for (String tile : tilesToCheck) {
                Set<String> adj = FoWHelper.traverseAdjacencies(game, true, tile);
                for (String tile_ : adj) {
                    if (!tileSet.contains(tile_)) {
                        tileSet.add(tile_);
                        nextTiles.add(tile_);
                    }
                }
            }
            tilesToCheck = nextTiles;
        }
        return tileSet;
    }

    @NotNull
    private static String getTimeStamp() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd - HH.mm.ss.SSS");
        return ZonedDateTime.now(ZoneOffset.UTC).format(fmt);
    }

    private void addTile(Graphics graphics, Tile tile, TileStep step) {
        if (tile == null || tile.getTileID() == null) {
            return;
        }
        try {
            String position = tile.getPosition();
            Point positionPoint = PositionMapper.getTilePosition(position);
            if (positionPoint == null) {
                throw new Exception("Could not map tile to a position on the map: " + game.getName());
            }

            Point p = PositionMapper.getTilePosition(focusTile);
            int offsetY = p == null ? 0 : -1 * p.y + (EXTRA_Y / 2) + (context * TILE_HEIGHT);
            int offsetX = p == null ? 0 : -1 * p.x + (EXTRA_X / 2) + (context * TILE_EXTRA_WIDTH);
            int tileX = positionPoint.x + offsetX - TILE_PADDING;
            int tileY = positionPoint.y + offsetY - TILE_PADDING;

            BufferedImage tileImage = draw(tile, step);
            graphics.drawImage(tileImage, tileX, tileY, null);
        } catch (Exception exception) {
            BotLogger.error("Tile Error, when building map: " + tile.getTileID(), exception);
        }
    }

    public BufferedImage draw(Tile tile, TileStep step) {
        BufferedImage tileOutput = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics tileGraphics = tileOutput.createGraphics();
        ShipPosition tileShipPositions = tile.getTileModel().getShipPositionsType();
        boolean isSpiral = tileShipPositions != null && tileShipPositions.isSpiral();

        switch (step) {
            case Setup -> {}
            case Tile, TileUnannotated -> {
                drawBaseTileImage(tile, tileGraphics, tileShipPositions);
                if (step == TileStep.Tile) {
                    drawTileAnnotations(tile, tileGraphics, isSpiral);
                }
            }
            case Extras -> drawExtras(tile, tileGraphics);
            case Units -> drawUnits(tile, tileGraphics);
            case Distance -> drawDistance(tile, tileGraphics, isSpiral);
            case Wormholes -> {
                if (canDrawOverlay(tile)) drawWormholeOverlay(tile, tileGraphics, isSpiral);
            }
            case Anomalies -> {
                if (canDrawOverlay(tile)) drawAnomalyOverlay(tile, tileGraphics, isSpiral);
            }
            case Exile -> {
                if (canDrawOverlay(tile)) drawExileOverlay(tile, tileGraphics, isSpiral);
            }
            case Aetherstream -> {
                if (canDrawOverlay(tile)) drawAetherstreamOverlay(tile, tileGraphics, isSpiral);
            }
            case Legendaries -> {
                if (canDrawOverlay(tile)) drawLegendaryOverlay(tile, tileGraphics, isSpiral);
            }
            case Empties -> {
                if (canDrawOverlay(tile)) drawEmptyOverlay(tile, tileGraphics, isSpiral);
            }
            case SpaceCannon -> drawSpaceCannonOverlay(tile, tileGraphics, isSpiral);
            case Traits -> {
                if (canDrawOverlay(tile)) drawTraitOverlay(tile, tileGraphics);
            }
            case TechSkips -> {
                if (canDrawOverlay(tile)) drawTechSkipOverlay(tile, tileGraphics);
            }
            case Attachments -> {
                if (canDrawOverlay(tile)) drawAttachmentOverlay(tile, tileGraphics);
            }
            case TileNumber -> drawTileNumber(tile, tileGraphics);
            case Nucleus -> drawNucleus(tile, tileGraphics, tileShipPositions);
        }
        return tileOutput;
    }

    private void drawBaseTileImage(Tile tile, Graphics tileGraphics, @Nullable ShipPosition tileShipPositions) {
        tileGraphics.drawImage(baseTileImage(tile), TILE_PADDING, TILE_PADDING, null);
        if (tile.isAnomaly(game, null) && tileShipPositions != null) {
            BufferedImage anomalyImage = ImageHelper.read(ResourceHelper.getTileFile("tile_anomaly.png"));
            Point origin = overlayOrigin(tileShipPositions.isSpiral());
            tileGraphics.drawImage(anomalyImage, origin.x, origin.y, null);
        }
    }

    private BufferedImage baseTileImage(Tile tile) {
        if (CustomHyperlaneService.isCustomHyperlaneTile(tile)) {
            return HyperlaneTileGenerator.generateHyperlaneTile(tile, game);
        }
        if (game.isLiberationC4Mode() && "51".equals(tile.getTileID())) {
            return ImageHelper.read(ResourceHelper.getTileFile("51r_Creuss.png"));
        }
        if (game.isLiberationC4Mode() && "17".equals(tile.getTileID())) {
            return ImageHelper.read(ResourceHelper.getTileFile("17r_DeltaWH.png"));
        }
        return ImageHelper.read(tile.getTilePath());
    }

    private static Point overlayOrigin(boolean isSpiral) {
        return isSpiral
                ? new Point(TILE_PADDING + SPIRAL_OFFSET.x, TILE_PADDING + SPIRAL_OFFSET.y)
                : new Point(TILE_PADDING, TILE_PADDING);
    }

    private void drawTileAnnotations(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        drawControlBorder(tile, tileGraphics, isSpiral);
        setTextSize(tileGraphics);
        if (isFoWPrivate && tile.hasFog(fowPlayer)) {
            drawFogTile(tile, tileGraphics);
        }
        if (TileHelper.isDraftTile(tile.getTileModel())) {
            drawDraftLabels(tile, tileGraphics);
        }
        drawWormholeAgendaIcons(tile, tileGraphics);
        if (ButtonHelper.isLawInPlay(game, "shared_research") && tile.isNebula(game)) {
            drawSharedResearchIcon(tile, tileGraphics, isSpiral);
        }
    }

    private void drawControlBorder(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        Player controllingPlayer = game.getPlayerThatControlsTile(tile);
        if (("off".equals(game.getHexBorderStyle()) && displayType != DisplayType.unlocked)
                || controllingPlayer == null
                || isSpiral) {
            return;
        }
        int sideNum = 0;
        List<Integer> openSides = new ArrayList<>();
        for (String adj : PositionMapper.getAdjacentTilePositions(tile.getPosition())) {
            if (game.getPlayerThatControlsTile(adj) == controllingPlayer) {
                openSides.add(sideNum);
            }
            sideNum++;
        }
        if (isFoWPrivate && fowPlayer == null) openSides.clear();
        String hexBorderStyle = displayType == DisplayType.unlocked ? "solid" : game.getHexBorderStyle();
        BufferedImage border =
                DrawingUtil.hexBorder(hexBorderStyle, Mapper.getColor(controllingPlayer.getColor()), openSides);
        tileGraphics.drawImage(border, TILE_PADDING, TILE_PADDING, null);
    }

    private void drawFogTile(Tile tile, Graphics tileGraphics) {
        BufferedImage frogOfWar = ImageHelper.read(tile.getFowTilePath(fowPlayer));
        tileGraphics.drawImage(frogOfWar, TILE_PADDING, TILE_PADDING, null);
        String label = tile.getFogLabel(fowPlayer);
        if (label == null) {
            return;
        }
        if (label.startsWith("Rnd")) {
            int labelX = TILE_PADDING + LABEL_POSITION_POINT.x;
            int labelY = TILE_PADDING + LABEL_POSITION_POINT.y;
            DrawingUtil.superDrawString(tileGraphics, label, labelX, labelY, Color.WHITE, null, null, null, null);
            return;
        }
        int labelX = TILE_PADDING + (TILE_WIDTH / 2);
        int labelY = TILE_PADDING + (TILE_HEIGHT / 2);
        int lineHeight = tileGraphics.getFontMetrics().getHeight();
        List<String> toDraw = DrawingUtil.layoutText((Graphics2D) tileGraphics, label, TILE_WIDTH - TILE_PADDING);
        int deltaY = 0;
        for (String line : toDraw) {
            DrawingUtil.superDrawString(
                    tileGraphics, line, labelX, labelY + deltaY, Color.WHITE, HorizontalAlign.Center, null, null, null);
            deltaY += lineHeight;
        }
    }

    private static void drawDraftLabels(Tile tile, Graphics tileGraphics) {
        String tileID = tile.getTileID();
        String draftNum = PatternHelper.LOWERCASE_LETTER_PATTERN.matcher(tileID).replaceAll("");
        String draftColor = PatternHelper.BLANK_WORD_PATTERN
                .matcher(DIGIT_PATTERN.matcher(tileID).replaceAll(""))
                .replaceAll("")
                .toUpperCase();

        if (tileID.endsWith("blank")) {
            BufferedImage green = ImageHelper.readScaled(ResourceHelper.getTileFile("00_green.png"), 73, 63);
            tileGraphics.drawImage(green, TILE_PADDING + 49, TILE_PADDING + 119, null);
        } else {
            tileGraphics.setFont(Storage.getFont50());
            DrawingUtil.superDrawString(
                    tileGraphics,
                    draftNum,
                    TILE_PADDING + 85,
                    TILE_PADDING + 140,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    stroke8,
                    Color.BLACK);
        }
        tileGraphics.setFont(Storage.getFont24());
        DrawingUtil.superDrawString(
                tileGraphics,
                draftColor,
                TILE_PADDING + 172,
                TILE_PADDING + 228,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                stroke6,
                Color.BLACK);
    }

    private void drawWormholeAgendaIcons(Tile tile, Graphics tileGraphics) {
        boolean reconstruction = anyLawInPlay(game, "wormhole_recon", "absol_recon");
        boolean travelBan = anyLawInPlay(game, "travel_ban", "absol_travelban");
        Set<String> wormholes = Mapper.getWormholes(tile.getTileID());
        boolean hasAlpha = wormholes.contains(Constants.ALPHA);
        boolean hasBeta = wormholes.contains(Constants.BETA);
        if (travelBan && (hasAlpha || hasBeta)) {
            drawOnWormhole(tile, tileGraphics, blockedWormholeImage(reconstruction), 40, "ab", false);
        } else if (tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_SEVERED)) {
            drawOnWormhole(tile, tileGraphics, blockedWormholeImage(reconstruction), 40, "", true);
        }
        if (reconstruction && hasAlpha) {
            drawOnWormhole(tile, tileGraphics, reconstructionWormholeImage("token_whbeta.png"), 0, "a", false);
        }
        if (reconstruction && hasBeta) {
            drawOnWormhole(tile, tileGraphics, reconstructionWormholeImage("token_whalpha.png"), 0, "b", false);
        }
        if (anyLawInPlay(game, "nexus", "absol_nexus") && "82b".equals(tile.getTileID()) && !travelBan) {
            drawOnWormhole(tile, tileGraphics, blockedWormholeImage(reconstruction), 40, "ab", false);
        }
    }

    record CreussWormholesOnMap(boolean alpha, boolean beta, boolean gamma) {
        static CreussWormholesOnMap of(Game game) {
            String alphaID = Mapper.getTokenID("creussalpha");
            String betaID = Mapper.getTokenID("creussbeta");
            String gammaID = Mapper.getTokenID("creussgamma");
            boolean alphaOnMap = false;
            boolean betaOnMap = false;
            boolean gammaOnMap = false;
            for (Tile tile : game.getTileMap().values()) {
                Set<String> tileTokens = tile.getSpaceUnitHolder().getTokenList();
                alphaOnMap |= tileTokens.contains(alphaID);
                betaOnMap |= tileTokens.contains(betaID);
                gammaOnMap |= tileTokens.contains(gammaID);
            }
            return new CreussWormholesOnMap(alphaOnMap, betaOnMap, gammaOnMap);
        }

        boolean all() {
            return alpha && beta && gamma;
        }
    }

    static void drawWormholeTokenWithAgendaIcons(
            Graphics graphics, Game game, String tokenFile, @Nullable String counterpartTokenFile, int x, int y) {
        graphics.drawImage(ImageHelper.read(tokenFile), x, y, null);
        if (counterpartTokenFile == null) {
            return;
        }
        boolean reconstruction = anyLawInPlay(game, "wormhole_recon", "absol_recon");
        if (anyLawInPlay(game, "travel_ban", "absol_travelban")) {
            graphics.drawImage(blockedWormholeImage(reconstruction), x + 40, y + 40, null);
        }
        if (reconstruction) {
            graphics.drawImage(reconstructionWormholeImage(counterpartTokenFile), x, y, null);
        }
    }

    static boolean anyLawInPlay(Game game, String... lawIds) {
        for (String lawId : lawIds) {
            if (ButtonHelper.isLawInPlay(game, lawId)) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage blockedWormholeImage(boolean half) {
        return ImageHelper.read(
                ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (half ? "_half" : "") + ".png"));
    }

    private static BufferedImage reconstructionWormholeImage(String tokenFile) {
        return ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile(tokenFile), 40.0f / 65);
    }

    private static void drawSharedResearchIcon(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        BufferedImage nebulaBypass =
                ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_shared_research.png"));
        Point offset;
        if (isSpiral) {
            offset = switch (tile.getTileID()) {
                case "51" -> new Point(42, 235);
                case "82b" -> new Point(30, 221);
                case "82a" -> new Point(63, 273);
                default -> new Point(99, 294);
            };
        } else if (tile.isHomeSystem()) {
            offset = new Point(42, 193);
        } else {
            offset = new Point(80, 236);
        }
        tileGraphics.drawImage(nebulaBypass, TILE_PADDING + offset.x, TILE_PADDING + offset.y, null);
    }

    private void drawExtras(Tile tile, Graphics tileGraphics) {
        if (!isFoWPrivate || !tile.hasFog(fowPlayer)) {
            List<String> adj = game.getAdjacentTileOverrides(tile.getPosition());
            int direction = 0;
            for (String secondaryTile : adj) {
                if (secondaryTile != null) {
                    addBorderDecoration(direction, secondaryTile, tileGraphics, getAdjacencyArrowImagePath());
                }
                direction++;
            }
        }

        if (game.getBorderAnomalies().isEmpty()) {
            return;
        }
        List<String> orderedAdjacentPositions = PositionMapper.getAdjacentTilePositions(tile.getPosition());
        game.getBorderAnomalies().forEach(borderAnomalyHolder -> {
            BorderAnomalyModel anomalyModel = borderAnomalyHolder.getModel();
            if (anomalyModel != null
                    && borderAnomalyHolder.getTile().equals(tile.getPosition())
                    && (!isFoWPrivate
                            || !tile.hasFog(fowPlayer)
                            || FoWHelper.getTilePositionsToShow(game, fowPlayer)
                                    .contains(orderedAdjacentPositions.get(borderAnomalyHolder.getDirection())))) {
                addBorderDecoration(
                        borderAnomalyHolder.getDirection(), null, tileGraphics, anomalyModel.getImageFilePath());
            }
        });
    }

    private void drawUnits(Tile tile, Graphics tileGraphics) {
        if (isFoWPrivate && tile.hasFog(fowPlayer)) return;

        List<Rectangle> rectangles = new ArrayList<>();
        Collection<UnitHolder> unitHolders =
                new ArrayList<>(tile.getUnitHolders().values());
        UnitHolder spaceUnitHolder = tile.getSpaceUnitHolder();
        if (spaceUnitHolder != null) {
            addSleeperToken(tile, tileGraphics, spaceUnitHolder, TileGenerator::isValidCustodianToken, game);
            drawTokensOnTile(tile, tileGraphics, spaceUnitHolder, game);
            unitHolders.remove(spaceUnitHolder);
            unitHolders.add(spaceUnitHolder);
        }

        int prodInSystem = 0;
        int capacity = 0;
        int capacityUsed = 0;
        int ignoredFs = 0;
        for (Player player : game.getRealPlayers()) {
            prodInSystem = Math.max(prodInSystem, Helper.getProductionValue(player, game, tile, false));
            if (capacity == 0 && capacityUsed == 0) {
                int[] capNCap = ButtonHelper.checkFleetAndCapacity(player, game, tile, false, false);
                capacityUsed = capNCap[1];
                capacity = capNCap[2];
                ignoredFs = capNCap[3];
            }
        }
        for (UnitHolder unitHolder : unitHolders) {
            addSleeperToken(tile, tileGraphics, unitHolder, TileGenerator::isValidToken, game);
            addControl(tile, tileGraphics, unitHolder, rectangles);
        }
        if (game.isShowGears() && !game.isFowMode()) {
            int xMod = tile.getUnitHolders().size() != 4 || GEAR_PROBLEM_TILES.contains(tile.getTileID()) ? -15 : -155;
            if (prodInSystem > 0) {
                drawProductionGear(tileGraphics, prodInSystem, xMod);
            }
            if (capacityUsed > 0 || capacity > 0 || ignoredFs > 0) {
                drawCapacity(tileGraphics, prodInSystem, capacity, capacityUsed, ignoredFs, xMod);
            }
        }

        if (spaceUnitHolder != null) {
            addCC(tile, tileGraphics, spaceUnitHolder);
        }
        for (UnitHolder unitHolder : unitHolders) {
            int radius = Constants.SPACE.equals(unitHolder.getName()) ? Constants.SPACE_RADIUS : Constants.RADIUS;
            if (unitHolder != spaceUnitHolder) {
                addPlanetToken(tile, tileGraphics, unitHolder, rectangles);
            }
            UnitRenderGenerator unitRenderGenerator = new UnitRenderGenerator(
                    game, displayType, tile, tileGraphics, rectangles, 180, 5, unitHolder, radius, fowPlayer);
            unitRenderGenerator.render();
            aggregateUnitCoordinates(unitRenderGenerator);
        }
    }

    private static void drawProductionGear(Graphics tileGraphics, int prodInSystem, int xMod) {
        int textModifier = prodInSystem == 1 ? 7 : (prodInSystem > 9 && prodInSystem != 11 ? -5 : 0);
        int yMod = -290;
        BufferedImage gearImage =
                ImageHelper.readScaled(ResourceHelper.getTileFile("production_representation.png"), 64, 64);
        tileGraphics.drawImage(
                gearImage,
                TILE_PADDING + TILE_POSITION_POINT.x + xMod - 29,
                TILE_PADDING + TILE_POSITION_POINT.y + yMod - 4,
                null);
        tileGraphics.setFont(Storage.getFont35());
        tileGraphics.drawString(
                prodInSystem + "",
                TILE_PADDING + TILE_POSITION_POINT.x + xMod + 15 + textModifier - 25,
                TILE_PADDING + TILE_POSITION_POINT.y + yMod + 40);
    }

    private static void drawCapacity(
            Graphics tileGraphics, int prodInSystem, int capacity, int capacityUsed, int ignoredFs, int xMod) {
        BufferedImage carrierImage =
                ImageHelper.readScaled(ResourceHelper.getTileFile("capacity_representation.png"), 64, 21);
        Graphics2D g2d = (Graphics2D) tileGraphics;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(6));

        int gearX = TILE_PADDING + TILE_POSITION_POINT.x + xMod - 29;
        int gearY = TILE_PADDING + TILE_POSITION_POINT.y - 290 + 5;
        if (prodInSystem == 0) {
            gearX -= 27;
            gearY -= 55;
        }

        tileGraphics.drawImage(carrierImage, gearX + 24, gearY + 60, null);
        g2d.setColor(Color.WHITE);
        tileGraphics.setFont(Storage.getFont12());
        String msg =
                ignoredFs > 0 ? capacityUsed + " / " + (capacity + ignoredFs) + "*" : capacityUsed + " / " + capacity;
        DrawingUtil.superDrawString(
                tileGraphics,
                msg,
                gearX + 39 + 17,
                gearY + 95,
                Color.WHITE,
                HorizontalAlign.Center,
                null,
                stroke4,
                Color.BLACK);
    }

    private void drawDistance(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        if (game.isFowMode()) {
            return;
        }
        Integer distance = game.getTileDistances().get(tile.getPosition());
        if (distance == null) {
            distance = 100;
        }

        BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
        if (tileImage == null) {
            return;
        }
        Point origin = overlayOrigin(isSpiral);
        if (distance > 0) {
            BufferedImage distanceColor =
                    ImageHelper.read(ResourceHelper.getTileFile(getColorFilterForDistance(distance)));
            tileGraphics.drawImage(distanceColor, origin.x, origin.y, null);
        }
        if (distance < 11) {
            tileGraphics.setFont(Storage.getFont110());
            DrawingUtil.superDrawString(
                    tileGraphics,
                    distance.toString(),
                    origin.x + tileImage.getWidth() / 2,
                    origin.y + tileImage.getHeight() / 2,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    stroke4,
                    Color.BLACK);
        }
    }

    private boolean canDrawOverlay(Tile tile) {
        return !game.isFowMode() && !tile.getTileModel().isHyperlane() && ImageHelper.read(tile.getTilePath()) != null;
    }

    private static void drawFogOverlay(Tile tile, Graphics tileGraphics) {
        tileGraphics.drawImage(ImageHelper.read(tile.getFowTilePath(null)), TILE_PADDING, TILE_PADDING, null);
    }

    private static void drawCenteredInTile(Graphics tileGraphics, BufferedImage image, Point origin) {
        tileGraphics.drawImage(
                image, origin.x + (345 - image.getWidth()) / 2, origin.y + (300 - image.getHeight()) / 2, null);
    }

    private void drawWormholeOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        if (!FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        Point origin = overlayOrigin(isSpiral);
        int count = FoWHelper.getTileWHs(game, tile.getPosition()).size();
        int x = origin.x + (count == 1 ? 76 : 30);
        int y = origin.y + (count == 1 ? 52 : 26);
        for (String wh : FoWHelper.getTileWHs(game, tile.getPosition())) {
            String whFile = ResourceHelper.getInstance().getTokenFile("token_wh" + wh + ".png");
            if (whFile == null) {
                whFile = ResourceHelper.getInstance().getTokenFile("token_custom_eronous_wh" + wh + ".png");
            }
            if (whFile != null) {
                tileGraphics.drawImage(ImageHelper.readScaled(whFile, 3), x, y, null);
            }
            x += (count <= 1 ? 0 : 91 / (count - 1));
            y += (count <= 1 ? 0 : 52 / (count - 1));
        }
    }

    private void drawAnomalyOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        if (!tile.isAnomaly(game, null)) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        Point origin = overlayOrigin(isSpiral);
        BufferedImage chevron = ImageHelper.read(ResourceHelper.getTileFile("tile_anomaly_chevron.png"));
        tileGraphics.drawImage(chevron, origin.x + 33, origin.y + 101, null);
    }

    private void drawExileOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        boolean inRangeOfExile = game.getRealPlayers().stream()
                .anyMatch(p -> FoWHelper.isTileInExileRange(game, tile, p)
                        || FoWHelper.isTileInUpgradedExileRange(game, tile, p));
        if (!inRangeOfExile) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        String breachFile = ResourceHelper.getInstance().getTokenFile("token_breachActive.png");
        BufferedImage breachImage = ImageHelper.readScaled(breachFile, 2.0f);
        if (breachImage != null) {
            drawCenteredInTile(tileGraphics, breachImage, overlayOrigin(isSpiral));
        }
    }

    private void drawAetherstreamOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        if (!FoWHelper.isTileAdjacentToAnAnomaly(game, tile.getPosition(), null)) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        String batFile = ResourceHelper.getInstance()
                .getGeneralFile("zobat" + (RandomHelper.isOneInX(4096) ? "_shiny" : "") + ".png");
        BufferedImage batImage = ImageHelper.read(batFile);
        if (batImage != null) {
            drawCenteredInTile(tileGraphics, batImage, overlayOrigin(isSpiral));
        }
    }

    private void drawLegendaryOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        if (tile.isMecatol(game)) {
            drawCouncilIcon(tileGraphics);
            return;
        }
        if (!ButtonHelper.isTileLegendary(tile)) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        int number = 0;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            number += (planet.isLegendary() ? 1 : 0);
        }
        Point origin = overlayOrigin(isSpiral);
        String legendaryFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
        BufferedImage legendaryImage = ImageHelper.readScaled(legendaryFile, 0.5f);
        if (number < 2) {
            if (legendaryImage != null) {
                drawCenteredInTile(tileGraphics, legendaryImage, origin);
            }
            return;
        }
        legendaryImage = ImageHelper.scale(legendaryImage, (float) Math.sqrt(Math.sqrt(1.0f / number)));
        int x = origin.x + (345 - legendaryImage.getWidth()) / 2;
        int y = origin.y + (300 - legendaryImage.getHeight()) / 2;
        for (int i = 0; i < number; i++) {
            tileGraphics.drawImage(
                    legendaryImage, x - 30 + i * 60 / (number - 1), y - 30 + i * 60 / (number - 1), null);
        }
    }

    private static void drawCouncilIcon(Graphics tileGraphics) {
        String councilFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
        BufferedImage councilImage = ImageHelper.readScaled(councilFile, 2.0f);
        if (councilImage == null) return;

        int padding = 5;
        int x = TILE_PADDING + (345 - councilImage.getWidth()) / 2;
        int y = TILE_PADDING + (300 - councilImage.getWidth()) / 2;
        tileGraphics.drawImage(halo(councilImage, padding, 3, Color.BLACK), x - padding, y - padding, null);
        tileGraphics.drawImage(councilImage, x, y, null);
    }

    private static BufferedImage halo(BufferedImage image, int padding, int radius, Color color) {
        return DrawingUtil.halo(image, padding, radius, color, image.getType());
    }

    private static void drawWithOutline(
            Graphics tileGraphics, BufferedImage image, int x, int y, int innerBorder, int outerBorder, int padding) {
        tileGraphics.drawImage(halo(image, padding, outerBorder, Color.WHITE), x - padding, y - padding, null);
        tileGraphics.drawImage(halo(image, padding, innerBorder, Color.BLACK), x - padding, y - padding, null);
        tileGraphics.drawImage(image, x, y, null);
    }

    private static BufferedImage scaleToIconArea(BufferedImage image, double divisor) {
        return ImageHelper.scale(image, (float) Math.sqrt(9200.0f / image.getWidth() / image.getHeight() / divisor));
    }

    private void drawEmptyOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        if (!tile.getPlanetUnitHolders().isEmpty()) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        Point origin = overlayOrigin(isSpiral);
        int x = origin.x + 93;
        int y = origin.y + 70;
        tileGraphics.setColor(Color.RED);
        tileGraphics.fillOval(x, y, 159, 159);
        tileGraphics.setColor(Color.WHITE);
        DrawingUtil.drawCenteredString(tileGraphics, "!", new Rectangle(x, y, 159, 159), Storage.getFont80());
    }

    private void drawSpaceCannonOverlay(Tile tile, Graphics tileGraphics, boolean isSpiral) {
        Map<String, PdsCoverage> pdsCoverageMap = PdsCoverageHelper.calculatePdsCoverage(game, tile);
        if (ImageHelper.read(tile.getTilePath()) == null) {
            return;
        }

        Map<String, List<Integer>> pdsDice = new HashMap<>();
        Map<String, PdsCoverage> pdsCoverageByFaction = new HashMap<>();
        if (pdsCoverageMap != null) {
            for (Player player : game.getRealPlayers()) {
                PdsCoverage coverage = pdsCoverageMap.get(player.getFaction());
                if (coverage != null) {
                    pdsDice.put(player.getUserID(), coverage.getDiceValues());
                    pdsCoverageByFaction.put(player.getFaction(), coverage);
                }
            }
        }

        if (pdsDice.isEmpty()) {
            drawFogOverlay(tile, tileGraphics);
            return;
        }
        float scale = pdsDice.size() >= 3 ? 6.0f / pdsDice.size() : 3.0f;
        Font bigFont;
        Font smallFont =
                switch (pdsDice.size()) {
                    case 1, 2 -> {
                        bigFont = Storage.getFont64();
                        yield Storage.getFont32();
                    }
                    case 3 -> {
                        bigFont = Storage.getFont40();
                        yield Storage.getFont20();
                    }
                    case 4 -> {
                        bigFont = Storage.getFont32();
                        yield Storage.getFont16();
                    }
                    case 5 -> {
                        bigFont = Storage.getFont24();
                        yield Storage.getFont12();
                    }
                    default -> {
                        bigFont = Storage.getFont16();
                        yield Storage.getFont8();
                    }
                };

        Point origin = overlayOrigin(isSpiral);
        int x = origin.x + (int) ((345 - 73 * scale) / 2);
        int y = origin.y + (int) ((300 - pdsDice.size() * 48 * scale) / 2);
        for (Map.Entry<String, List<Integer>> entry : pdsDice.entrySet()) {
            Player player = game.getPlayer(entry.getKey());
            PdsCoverage coverage = pdsCoverageByFaction.get(player.getFaction());

            int numberOfDice = coverage.getCount();
            if ("_wht.png".equals(DrawingUtil.getBlackWhiteFileSuffix(player.getColorID()))) {
                tileGraphics.setColor(Color.WHITE);
            } else {
                tileGraphics.setColor(Color.BLACK);
            }
            BufferedImage controlImage =
                    ImageHelper.readScaled(Mapper.getCCPath(Mapper.getControlID(player.getColor())), scale);
            DrawingUtil.drawControlToken(tileGraphics, controlImage, player, x, y, false, scale / 2);
            DrawingUtil.drawCenteredString(
                    tileGraphics,
                    numberOfDice + (coverage.isHasRerolls() ? "*" : ""),
                    new Rectangle(
                            Math.round(x + 6 * scale),
                            Math.round(y + 12 * scale),
                            Math.round(61 * scale / 2),
                            Math.round(24 * scale * 2 / 3)),
                    bigFont);
            DrawingUtil.drawCenteredString(
                    tileGraphics,
                    "(" + coverage.getExpected() + ")",
                    new Rectangle(
                            Math.round(x + 6 * scale),
                            Math.round(y + 12 * scale + 24 * scale * 2 / 3),
                            Math.round(61 * scale / 2),
                            Math.round(24 * scale / 3)),
                    smallFont);
            drawDiceValues(tileGraphics, entry.getValue(), numberOfDice, x, y, scale, smallFont);
            y += (int) (48 * scale);
        }
    }

    private static void drawDiceValues(
            Graphics tileGraphics, List<Integer> dice, int numberOfDice, int x, int y, float scale, Font font) {
        if (numberOfDice < 3) {
            DrawingUtil.drawCenteredString(
                    tileGraphics,
                    joinDice(dice),
                    new Rectangle(
                            Math.round(x + 73 * scale / 2), y, Math.round(73 * scale / 2), Math.round(48 * scale)),
                    font);
            return;
        }
        int rows = numberOfDice >= 5 ? 3 : 2;
        int top = numberOfDice >= 5 ? 6 : 12;
        int span = numberOfDice >= 5 ? 36 : 24;
        for (int row = 0; row < rows; row++) {
            List<Integer> rowDice = dice.subList(row * numberOfDice / rows, (row + 1) * numberOfDice / rows);
            DrawingUtil.drawCenteredString(
                    tileGraphics,
                    joinDice(rowDice) + (row < rows - 1 ? "," : ""),
                    new Rectangle(
                            Math.round(x + 73 * scale / 2),
                            Math.round(y + top * scale + row * span * scale / rows),
                            Math.round(73 * scale / 2),
                            Math.round(span * scale / rows)),
                    font);
        }
    }

    private static String joinDice(List<Integer> dice) {
        return dice.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    private void drawTraitOverlay(Tile tile, Graphics tileGraphics) {
        drawFogOverlay(tile, tileGraphics);
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            if (!(uh instanceof Planet planet)) {
                continue;
            }
            String traitFile = traitIconFile(tile, planet);
            if (traitFile == null || traitFile.isEmpty()) {
                continue;
            }
            BufferedImage traitImage = scaleToIconArea(ImageHelper.read(traitFile), 1.0);
            boolean mecatol = tile.isMecatol(game);
            int innerBorder = mecatol ? 3 : 2;
            int outerBorder = innerBorder + (mecatol ? -1 : 2);
            int padding = outerBorder + (mecatol ? 3 : 2);
            Point position = planet.getHolderCenterPosition(tile);
            drawWithOutline(
                    tileGraphics,
                    traitImage,
                    position.x - traitImage.getWidth() / 2 + TILE_PADDING,
                    position.y - traitImage.getHeight() / 2 + TILE_PADDING,
                    innerBorder,
                    outerBorder,
                    padding);
        }
    }

    private String traitIconFile(Tile tile, Planet planet) {
        List<String> traits = planet.getPlanetType();
        if (traits.isEmpty() && StringUtils.isNotBlank(planet.getOriginalPlanetType())) {
            traits.add(planet.getOriginalPlanetType());
        }
        if (tile.isMecatol(game)) {
            return ResourceHelper.getInstance().getFactionFile("agenda.png");
        }
        if ("faction".equalsIgnoreCase(planet.getOriginalPlanetType())) {
            return ResourceHelper.getInstance()
                    .getFactionFile(Mapper.getPlanet(planet.getName()).getFactionHomeworld() + ".png");
        }
        if (traits.size() == 1) {
            String t = planet.getPlanetType().getFirst();
            return ResourceHelper.getInstance()
                    .getGeneralFile(
                            ("" + t.charAt(0)).toUpperCase() + t.substring(1).toLowerCase() + ".png");
        }
        if (traits.isEmpty()) {
            return "";
        }
        String t = (traits.contains("cultural") ? "C" : "")
                + (traits.contains("hazardous") ? "H" : "")
                + (traits.contains("industrial") ? "I" : "");
        return ResourceHelper.getInstance()
                .getPlanetResource("pc_attribute_combo_" + ("CHI".equals(t) ? "CHI_big" : t) + ".png");
    }

    private static void drawTechSkipOverlay(Tile tile, Graphics tileGraphics) {
        boolean anySkips = false;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            List<String> skips = planet.getTechSpecialities();
            int number = skips.size();
            if (number == 0) {
                continue;
            }
            anySkips = true;
            int count = 0;
            for (String skip : skips) {
                BufferedImage skipImage =
                        scaleToIconArea(ImageHelper.read(techSkipIconFile(skip)), Math.sqrt(1.0f * number));
                Point position = planet.getHolderCenterPosition(tile);
                if (number > 1) {
                    int offset = -20 + count * 40 / (number - 1);
                    position.translate(offset, offset);
                }
                drawWithOutline(
                        tileGraphics,
                        skipImage,
                        position.x - skipImage.getWidth() / 2 + TILE_PADDING,
                        position.y - skipImage.getHeight() / 2 + TILE_PADDING,
                        3,
                        6,
                        8);
                count++;
            }
        }
        if (!anySkips) {
            drawFogOverlay(tile, tileGraphics);
        }
    }

    static String techSkipIconFile(String skip) {
        return ResourceHelper.getInstance()
                .getGeneralFile(
                        switch (skip.toLowerCase()) {
                            case "biotic" -> "Biotic light.png";
                            case "cybernetic" -> "Cybernetic light.png";
                            case "propulsion" -> "Propulsion_light.png";
                            case "warfare" -> "Warfare_light.png";
                            default -> "Generic_Technology.png";
                        });
    }

    private static void drawAttachmentOverlay(Tile tile, Graphics tileGraphics) {
        boolean anyAttachments = false;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            List<String> attachments = new ArrayList<>(planet.getAttachments());
            int number = attachments.size();
            if (number == 0) {
                continue;
            }
            anyAttachments = true;

            String chevronFile = ResourceHelper.getInstance()
                    .getGeneralFile(
                            attachments.contains(Constants.TOMB_OF_EMPHIDIA_PNG)
                                    ? "misc_chevrons_toe.png"
                                    : "misc_chevrons_basic.png");
            BufferedImage chevronImage = scaleToIconArea(ImageHelper.read(chevronFile), 1.0);
            int w = chevronImage.getWidth();
            int h = chevronImage.getHeight();
            Point center = planet.getHolderCenterPosition(tile);
            int x = center.x - w / 2 + TILE_PADDING;
            int y = center.y - h / 2 + TILE_PADDING;
            if (number == 1) {
                drawWithOutline(tileGraphics, chevronImage, x, y, 3, 6, 8);
                continue;
            }
            drawWithOutline(tileGraphics, chevronImage, x, y - 36, 3, 6, 8);
            tileGraphics.setColor(Color.WHITE);
            tileGraphics.fillOval(x + w / 2 - 40, y + h / 2 - 8, 80, 80);
            tileGraphics.setColor(Color.BLACK);
            tileGraphics.fillOval(x + w / 2 - 36, y + h / 2 - 8 + 4, 72, 72);
            tileGraphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(
                    tileGraphics,
                    "" + number,
                    new Rectangle(x + w / 2 - 40, y + h / 2 - 8, 80, 80),
                    Storage.getFont48());
        }
        if (!anyAttachments) {
            drawFogOverlay(tile, tileGraphics);
        }
    }

    private void drawTileNumber(Tile tile, Graphics tileGraphics) {
        setTextSize(tileGraphics);
        DrawingUtil.superDrawString(
                tileGraphics,
                tile.getPosition(),
                TILE_PADDING + TILE_POSITION_POINT.x,
                TILE_PADDING + TILE_POSITION_POINT.y,
                Color.WHITE,
                HorizontalAlign.Right,
                VerticalAlign.Bottom,
                stroke7,
                Color.BLACK);
    }

    private void drawNucleus(Tile tile, Graphics tileGraphics, @Nullable ShipPosition tileShipPositions) {
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = mapTemplateId != null ? Mapper.getMapTemplate(mapTemplateId) : null;

        if (mapTemplate == null || !mapTemplate.isNucleusTemplate()) {
            throw new IllegalStateException("Cannot generate Nucleus display without a Nucleus map template");
        }

        MapTemplateTile mapTile = mapTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPos().equals(tile.getPosition()))
                .findFirst()
                .orElse(null);
        if (mapTile == null) {
            throw new IllegalStateException(
                    "Cannot find tile " + tile.getPosition() + " in Nucleus map template " + mapTemplate.getAlias());
        }

        drawBaseTileImage(tile, tileGraphics, tileShipPositions);

        List<Integer> nucleusNumbers = mapTile.getNucleusNumbers();
        boolean inNucleus = nucleusNumbers != null && !nucleusNumbers.isEmpty();
        if (inNucleus) {
            Map<Integer, List<Integer>> nucleusNumberToSides = new HashMap<>();
            for (int i = 0; i < 6; ++i) {
                nucleusNumberToSides
                        .computeIfAbsent(nucleusNumbers.get(i % nucleusNumbers.size()), _ -> new ArrayList<>())
                        .add(i);
            }
            for (Map.Entry<Integer, List<Integer>> entry : nucleusNumberToSides.entrySet()) {
                List<Integer> openSides = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
                openSides.removeAll(entry.getValue());
                drawNucleusBorder(tileGraphics, entry.getKey(), openSides, 2.5f);
            }
        }

        if (!tile.getTileModel().isHyperlane() && !inNucleus) {
            drawFogOverlay(tile, tileGraphics);
        }

        if (mapTile.getHome() != null && mapTile.getHome() && mapTile.getPlayerNumber() != null) {
            drawNucleusBorder(tileGraphics, mapTile.getPlayerNumber(), List.of(), 4.0f);
        }
    }

    private static void drawNucleusBorder(
            Graphics tileGraphics, Integer nucleusNumber, List<Integer> openSides, float thickness) {
        String color = NUCLEUS_SLICE_COLORS.getOrDefault(nucleusNumber, "black");
        BufferedImage border = DrawingUtil.hexBorder("solid", Mapper.getColor(color), openSides, thickness);
        tileGraphics.drawImage(border, TILE_PADDING, TILE_PADDING, null);
    }

    private void setTextSize(Graphics tileGraphics) {
        switch (game.getTextSize()) {
            case "large" -> tileGraphics.setFont(Storage.getFont40());
            case "medium" -> tileGraphics.setFont(Storage.getFont30());
            case "tiny" -> tileGraphics.setFont(Storage.getFont12());
            case null, default -> // "small"
                tileGraphics.setFont(Storage.getFont20());
        }
    }

    private static String getColorFilterForDistance(int distance) {
        return "Distance" + distance + ".png";
    }

    private static String getAdjacencyArrowImagePath() {
        return ResourceHelper.getResourceFromFolder("borders/", "adjacency_arrow.png");
    }

    private static void addBorderDecoration(
            int direction, String secondaryTile, Graphics tileGraphics, String decorationImagePath) {
        Graphics2D tileGraphics2d = (Graphics2D) tileGraphics;

        if (decorationImagePath == null) {
            return;
        }
        boolean isAdjacencyArrow = secondaryTile != null;
        BufferedImage borderDecorationImage;
        try {
            BufferedImage cached = ImageHelper.read(decorationImagePath);
            borderDecorationImage = new BufferedImage(
                    cached.getColorModel(), cached.copyData(null), cached.isAlphaPremultiplied(), null);
        } catch (Exception e) {
            BotLogger.error("Could not find border decoration image! Decoration was " + decorationImagePath, e);
            return;
        }

        int imageCenterX = borderDecorationImage.getWidth() / 2;
        int imageCenterY = borderDecorationImage.getHeight() / 2;

        AffineTransform originalTileTransform = tileGraphics2d.getTransform();
        // Translate the graphics so that a rectangle drawn at 0,0 with same size as the
        // tile (345x299) is centered
        tileGraphics2d.translate(100, 100);
        int centerX = 173;
        int centerY = 150;

        if (isAdjacencyArrow) {
            int textOffsetX = 12;
            int textOffsetY = 40;
            Graphics2D arrow = borderDecorationImage.createGraphics();
            AffineTransform arrowTextTransform = arrow.getTransform();

            arrow.setFont(secondaryTile.length() > 3 ? Storage.getFont14() : Storage.getFont16());
            arrow.setColor(Color.BLACK);

            if (direction >= 2 && direction <= 4) { // all the south directions
                arrow.rotate(3.141_592_653_589_793, imageCenterX, imageCenterY);
                textOffsetY = 25;
            }
            arrow.drawString(secondaryTile, textOffsetX, textOffsetY);
            arrow.setTransform(arrowTextTransform);
        }

        tileGraphics2d.rotate(Math.toRadians((direction) * 60), centerX, centerY);
        if (isAdjacencyArrow) centerX -= 20;
        tileGraphics2d.drawImage(borderDecorationImage, null, centerX - imageCenterX, -imageCenterY);
        tileGraphics2d.setTransform(originalTileTransform);
    }

    private void addCC(Tile tile, Graphics tileGraphics, UnitHolder unitHolder) {
        int deltaX = 0;
        int deltaY = 0;
        for (String ccID : unitHolder.getCcList()) {
            String ccPath = Tile.getCCPath(ccID);
            if (ccPath == null) {
                continue;
            }
            BufferedImage image = null;
            try {
                image = ImageHelper.read(ccPath);

                Point centerPosition = unitHolder.getHolderCenterPosition(tile);

                Player player =
                        DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), ccID);
                boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);

                boolean generateImage = true;
                if (ccID.startsWith("sweep")) {
                    if (player != fowPlayer) {
                        generateImage = false;
                    }
                }

                if (generateImage) {
                    int imgX = TILE_PADDING + 10 + deltaX;
                    int imgY = TILE_PADDING + centerPosition.y - 40 + deltaY;
                    DrawingUtil.drawCCOfPlayer(tileGraphics, ccID, imgX, imgY, 1, player, convertToGeneric);
                }
            } catch (Exception ignored) {
                BotLogger.error("Could not addCC", ignored);
            }

            if (image != null) {
                deltaX += image.getWidth() / 5;
                deltaY += image.getHeight() / 4;
            }
        }
    }

    public static Point offsetTokenPositionForTokenPlanets(Point position, UnitHolder planet, Tile tile) {
        if (position == null) return null;
        if (Constants.TOKEN_PLANETS.contains(planet.getName())) {
            Point centerPosition = planet.getHolderCenterPosition(tile);
            int radius = 55;
            if (planet instanceof Planet p) {
                radius = (int) p.getRadius();
            }
            position = new Point(position);
            position.translate(centerPosition.x - radius, centerPosition.y - radius);
        }
        return position;
    }

    private void addControl(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<Rectangle> rectangles) {
        List<String> controlList = new ArrayList<>(unitHolder.getControlList());
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition(tile);
            int xDelta = 0;
            for (String controlID : controlList) {
                if (controlID.contains(Constants.SLEEPER)) {
                    continue;
                }

                Player player =
                        DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID);
                boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);

                Point position =
                        offsetTokenPositionForTokenPlanets(unitTokenPosition.getPosition(controlID), unitHolder, tile);

                float scale = 1.0f;
                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                if (controlTokenImage == null) continue;

                int imgX;
                int imgY;
                if (position != null) {
                    imgX = TILE_PADDING + position.x;
                    imgY = TILE_PADDING + position.y;
                } else {
                    imgX = TILE_PADDING + centerPosition.x + xDelta;
                    imgY = TILE_PADDING + centerPosition.y;
                    xDelta += 10;
                }
                DrawingUtil.drawControlToken(
                        tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                if (player != null
                        && player.isRealPlayer()
                        && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                    BufferedImage exhaustedTokenImage = ImageHelper.readScaled(
                            ResourceHelper.getResourceFromFolder("command_token/", "exhaustedControl.png"), scale);
                    DrawingUtil.drawControlToken(
                            tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(
                            new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                }
            }

        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, controlList);
        }
    }

    private static void addSleeperToken(
            Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Function<String, Boolean> isValid, Game game) {
        BufferedImage tokenImage;
        Point centerPosition = unitHolder.getHolderCenterPosition(tile);
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.remove(null);
        tokenList.sort((o1, o2) -> {
            if (o1.contains(Constants.SLEEPER) || o2.contains(Constants.SLEEPER)) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        if (game.isShowBubbles()
                && unitHolder instanceof Planet planetHolder
                && shouldPlanetHaveShield(unitHolder, game)) {
            String tokenPath =
                    switch (StringUtils.defaultString(planetHolder.getContrastColor())) {
                        case "orange" -> ResourceHelper.getInstance().getTokenFile("token_planetaryShield_orange.png");
                        case "test" -> ResourceHelper.getInstance().getTokenFile("token_planetaryShield_test.png");
                        default -> ResourceHelper.getInstance().getTokenFile("token_planetaryShield.png");
                    };
            float scale = planetHolder.getRadius() / 58.0f;
            tokenImage = ImageHelper.readScaled(tokenPath, scale);
            Point position = new Point(
                    centerPosition.x - (tokenImage.getWidth() / 2), centerPosition.y - (tokenImage.getHeight() / 2));
            tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y, null);
            if ("99".equals(tile.getTileID())) {
                tokenImage = ImageHelper.readScaled(tokenPath, scale * 0.2526f);
                tileGraphics.drawImage(
                        tokenImage, TILE_PADDING + position.x + 172, TILE_PADDING + position.y + 148, null);
                tokenImage = ImageHelper.readScaled(tokenPath, scale * 0.1421f);
                tileGraphics.drawImage(
                        tokenImage, TILE_PADDING + position.x - 19, TILE_PADDING + position.y + 13, null);
                tokenImage = ImageHelper.readScaled(tokenPath, scale * 0.0842f);
                tileGraphics.drawImage(
                        tokenImage, TILE_PADDING + position.x - 7, TILE_PADDING + position.y + 195, null);
            }
        }
        boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
        for (String tokenID : tokenList) {
            if (isValid.apply(tokenID)) {
                String tokenPath = Tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.warning(new LogOrigin(game), "Could not find token file for: " + tokenID);
                    continue;
                }
                float scale = 0.85f;
                if (tokenID.contains(Constants.DMZ_LARGE)) {
                    if (unitHolder instanceof Planet planetHolder) {
                        scale = planetHolder.getRadius() / 65.0f;
                    }
                    scale *= 0.35f;
                } else if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                    if (unitHolder instanceof Planet planetHolder) {
                        scale = planetHolder.getRadius() / 65.0f;
                    }
                    if (tile.isFracture()) {
                        tokenPath = Tile.getTokenPath("token_worlddestroyed_frac.png");
                    }
                    scale *= 1.55f;
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    scale = 0.5f; // didn't previous get changed for custodians
                }

                tokenImage = ImageHelper.readScaled(tokenPath, scale);
                if (tokenImage == null) continue;
                Point position = new Point(
                        centerPosition.x - (tokenImage.getWidth() / 2),
                        centerPosition.y - (tokenImage.getHeight() / 2));
                if (tokenID.contains(Constants.CUSTODIAN_TOKEN)) {
                    position = new Point(125, 115); // 70, 45
                } else if (tokenID.contains(Constants.SLEEPER) && containsDMZ) {
                    position = new Point(position.x + 10, position.y + 10);
                } else if (tokenID.contains(Constants.WORLD_DESTROYED)) {
                    position = new Point(position.x + 4, position.y + 13);
                } else if (tokenID.contains(Constants.DMZ_LARGE)) {
                    position = new Point(position.x, position.y + 10);
                }
                tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y - 10, null);
            }
        }
    }

    private static void addPlanetToken(
            Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<Rectangle> rectangles) {
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.sort((o1, o2) -> {
            if ((o1.contains("nanoforge") || o1.contains("titanspn"))) {
                return -1;
            } else if ((o2.contains("nanoforge") || o2.contains("titanspn"))) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition(tile);
            int xDelta = 0;
            for (String tokenID : tokenList) {
                if (isValidToken(tokenID) || isValidCustodianToken(tokenID)) {
                    continue;
                }
                String tokenPath = Tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.warning(
                            "Could not parse token file for: " + tokenID + " on tile: " + tile.getAutoCompleteName());
                    continue;
                }
                BufferedImage tokenImage = ImageHelper.read(tokenPath);
                if (tokenImage == null) continue;

                if (tokenPath.contains(Constants.WORLD_DESTROYED)
                        || tokenPath.contains(Constants.CONSULATE_TOKEN)
                        || tokenPath.contains(Constants.GLEDGE_CORE)
                        || tokenPath.contains("freepeople")) {
                    tileGraphics.drawImage(
                            tokenImage,
                            TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                            TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2),
                            null);
                } else if (tokenPath.contains("token_theodisi_kairnexpedition")) {
                    tokenImage = ImageHelper.readScaled(tokenPath, 0.5f);
                    int tokenX = TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2);
                    int tokenY = TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2);
                    tileGraphics.drawImage(tokenImage, tokenX, tokenY, null);
                    rectangles.add(new Rectangle(tokenX, tokenY, tokenImage.getWidth(), tokenImage.getHeight()));
                } else if (tokenPath.contains(Constants.DMZ_LARGE)) {
                    float scale = 0.3f;
                    if (unitHolder instanceof Planet planetHolder) {
                        scale = planetHolder.getRadius() / 184.0f;
                    }
                    tokenImage = ImageHelper.readScaled(tokenPath, scale);
                    tileGraphics.drawImage(
                            tokenImage,
                            TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                            TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2) + 10,
                            null);
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + 70, TILE_PADDING + 45, null);
                } else if (tokenPath.contains("custodiavigilia")) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + 140, TILE_PADDING + 185, null);
                } else {
                    Point position = offsetTokenPositionForTokenPlanets(
                            unitTokenPosition.getPosition(tokenID), unitHolder, tile);
                    int tokenX;
                    int tokenY;
                    if (position != null) {
                        tokenX = TILE_PADDING + position.x;
                        tokenY = TILE_PADDING + position.y;
                    } else {
                        tokenX = TILE_PADDING + centerPosition.x + xDelta;
                        tokenY = TILE_PADDING + centerPosition.y;
                        xDelta += 10;
                    }
                    tileGraphics.drawImage(tokenImage, tokenX, tokenY, null);
                    rectangles.add(new Rectangle(tokenX, tokenY, tokenImage.getWidth(), tokenImage.getHeight()));
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, tokenList);
        }
    }

    public static boolean shouldPlanetHaveShield(UnitHolder unitHolder, Game game) {
        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(Constants.WORLD_DESTROYED))) {
            return false;
        }
        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains("superweapon_mors"))) {
            return true;
        }
        if (unitHolder instanceof Planet planet
                && game.getRealPlayers().stream()
                        .anyMatch(player -> AeternaBreakthroughHandler.hasTwilightDefenseSystem(player, planet))) {
            return true;
        }
        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("synthesis") && player.getReadiedPlanets().contains(unitHolder.getName())) {
                return true;
            }
        }

        Map<Units.UnitKey, Integer> units = unitHolder.getUnits();

        if (ButtonHelper.isLawInPlay(game, "conventions")) {
            String planet = unitHolder.getName();
            if (ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural")) {
                return true;
            }
        }
        Map<Units.UnitKey, Integer> planetUnits = new HashMap<>(units);
        for (Player player : game.getRealPlayers()) {
            for (Map.Entry<Units.UnitKey, Integer> unitEntry : planetUnits.entrySet()) {
                Units.UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) {
                    continue;
                }
                if (unitModel.getPlanetaryShield()) {
                    return !"mech".equalsIgnoreCase(unitModel.getBaseType())
                            || !ButtonHelper.isLawInPlay(game, "articles_war");
                }
            }
        }
        return false;
    }

    private static boolean isValidToken(String tokenID) {
        return tokenID.contains(Constants.SLEEPER)
                || tokenID.contains(Constants.DMZ_LARGE)
                || tokenID.contains(Constants.WORLD_DESTROYED)
                || tokenID.contains(Constants.GLEDGE_CORE)
                || tokenID.contains(Constants.CUSTODIAN_TOKEN)
                || tokenID.contains(Constants.CONSULATE_TOKEN);
    }

    private static boolean isValidCustodianToken(String tokenID) {
        return tokenID.contains(Constants.CUSTODIAN_TOKEN);
    }

    private static void oldFormatPlanetTokenAdd(
            Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<String> tokenList) {
        Point centerPosition = unitHolder.getHolderCenterPosition(tile);
        int x = centerPosition.x;
        int y = centerPosition.y - (tokenList.size() > 1 ? 35 : 0);
        for (String tokenID : tokenList) {
            String tokenPath = Tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                BotLogger.warning("Could not parse token file for: " + tokenID);
                continue;
            }
            BufferedImage image = ImageHelper.readScaled(tokenPath, 0.85f);
            if (image == null) continue;
            tileGraphics.drawImage(
                    image, TILE_PADDING + x - (image.getWidth() / 2), TILE_PADDING + y - (image.getHeight() / 2), null);
            y += image.getHeight();
        }
    }

    private static Comparator<String> sortTokensForDisplay() {
        Function<String, Boolean> sortTokenPlanet = tok -> !Constants.TOKEN_PLANETS.contains(Mapper.getTokenKey(tok));
        return Comparator.comparing(sortTokenPlanet);
    }

    private static boolean isWormholeToken(String tokenPath) {
        String lowerPath = tokenPath.toLowerCase();
        return WORMHOLE_TOKEN_MARKERS.stream().anyMatch(lowerPath::contains);
    }

    private static void drawTokensOnTile(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Game game) {
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.sort(sortTokensForDisplay());

        Point centerPosition = unitHolder.getHolderCenterPosition(tile);
        int x = 0;
        int y = 0;
        int deltaX = 80;
        int deltaY = 0;

        float mirageDragRatio = 2.0f / 3;
        int mirageDragX = Math.round(((float) 345 / 8 + TILE_PADDING) * (1 - mirageDragRatio));
        int mirageDragY = Math.round(((float) (3 * 300) / 4 + TILE_PADDING) * (1 - mirageDragRatio));
        boolean hasTripleTokenPlanet =
                tokenList.stream().anyMatch(tok -> Constants.TOKEN_PLANETS.contains(Mapper.getTokenKey(tok)))
                        && (tile.getPlanetUnitHolders().size() >= 4);
        List<Point> spaceTokenPositions = PositionMapper.getSpaceTokenPositions(tile.getTileID());
        if (spaceTokenPositions.isEmpty()) {
            x = centerPosition.x;
            y = centerPosition.y;
        }
        int index = 0;
        for (String tokenID : tokenList) {
            String tokenPath = Tile.getTokenPath(tokenID);
            if (game.isLiberationC4Mode()) {
                tokenPath = tokenPath.replace("token_creuss", "token_crimsoncreuss");
                tokenPath = tokenPath.replace("token_crimsoncreussepsilon", "token_creussepsilon");
            }
            String tokenName = Mapper.getTokenKey(tokenID);

            if (tokenPath == null) {
                BotLogger.warning(new LogOrigin(game), "Could not parse token file for: " + tokenID);
                continue;
            }
            if (game.isCptiExploreMode() && tokenPath.toLowerCase().contains("token_frontier")) {
                tokenPath = tokenPath.replace("token_frontier", "token_frontier_cpti");
            }

            BufferedImage tokenImage = ImageHelper.read(tokenPath);
            if (tokenImage == null) return;

            boolean isTokenPlanet = Constants.TOKEN_PLANETS.contains(tokenName);
            if (isTokenPlanet) {
                Point tokenCenter = Helper.getTokenPlanetCenterOfImage(tile, tokenName);
                int tokenX = tokenCenter.x - (tokenImage.getWidth() / 2);
                int tokenY = tokenCenter.y - (tokenImage.getHeight() / 2);
                tileGraphics.drawImage(tokenImage, TILE_PADDING + tokenX, TILE_PADDING + tokenY, null);
            } else if (tokenPath.contains(Constants.SLEEPER)) {
                int sleeperX = TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2);
                int sleeperY = TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2);
                tileGraphics.drawImage(tokenImage, sleeperX, sleeperY, null);
            } else if (Mapper.isFowVisionToken(tokenID)) {
                // fixed marker: doesn't take a space-token slot, so other tokens keep their positions
                tileGraphics.drawImage(
                        tokenImage,
                        TILE_PADDING + FOW_VISION_TOKEN_POINT.x,
                        TILE_PADDING + FOW_VISION_TOKEN_POINT.y,
                        null);
            } else {

                int drawX = TILE_PADDING + x;
                if (tokenPath.contains("mustache")) {
                    drawX -= 120;
                }
                int drawY = TILE_PADDING + y;
                if (spaceTokenPositions.size() > index) {
                    Point point = spaceTokenPositions.get(index);
                    drawX += point.x;
                    drawY += point.y;
                    index++;
                } else {
                    drawX += deltaX;
                    drawY += deltaY;
                    deltaX += 30;
                    deltaY += 30;
                }
                if (hasTripleTokenPlanet) {
                    drawX += (tokenImage.getWidth() / 2);
                    drawY += (tokenImage.getHeight() / 2);
                    drawX = Math.round(mirageDragRatio * drawX) + mirageDragX;
                    drawY = Math.round(mirageDragRatio * drawY) + mirageDragY;
                    drawX -= (tokenImage.getWidth() / 2);
                    drawY -= (tokenImage.getHeight() / 2);
                }
                tileGraphics.drawImage(tokenImage, drawX, drawY, null);
                drawTheodisiTokenCount(tileGraphics, tokenPath, game, tile, drawX, drawY, tokenImage);

                drawWormholeTokenAgendaIcons(tileGraphics, tokenPath, game, tile, drawX, drawY, tokenImage);
            }
        }
    }

    private static void drawWormholeTokenAgendaIcons(
            Graphics tileGraphics,
            String tokenPath,
            Game game,
            Tile tile,
            int drawX,
            int drawY,
            BufferedImage tokenImage) {
        boolean reconstruction = anyLawInPlay(game, "wormhole_recon", "absol_recon");
        boolean travelBan = anyLawInPlay(game, "travel_ban", "absol_travelban");
        String lowerPath = tokenPath.toLowerCase();
        boolean isAlpha = lowerPath.contains("alpha");
        boolean isBeta = lowerPath.contains("beta");
        int offset = (tokenImage.getWidth() - 80) / 2;
        int iconX = drawX + offset;
        int iconY = drawY + offset;
        if (travelBan && (isAlpha || isBeta)) {
            tileGraphics.drawImage(blockedWormholeImage(reconstruction), iconX + 40, iconY + 40, null);
        } else if (tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_SEVERED)
                && isWormholeToken(tokenPath)) {
            tileGraphics.drawImage(
                    blockedWormholeImage(reconstruction && (isAlpha || isBeta)), iconX + 40, iconY + 40, null);
        }
        if (reconstruction && isAlpha) {
            tileGraphics.drawImage(reconstructionWormholeImage("token_whbeta.png"), iconX, iconY, null);
        }
        if (reconstruction && isBeta) {
            tileGraphics.drawImage(reconstructionWormholeImage("token_whalpha.png"), iconX, iconY, null);
        }
        if (anyLawInPlay(game, "nexus", "absol_nexus")
                && "82b".equals(tile.getTileID())
                && !travelBan
                && (isAlpha || isBeta)) {
            tileGraphics.drawImage(blockedWormholeImage(reconstruction), iconX + 40, iconY + 40, null);
        }
    }

    private static void drawTheodisiTokenCount(
            Graphics tileGraphics,
            String tokenPath,
            Game game,
            Tile tile,
            int drawX,
            int drawY,
            BufferedImage tokenImage) {
        int count = tokenPath.contains("token_theodisi_mine")
                ? XytherisAbilityHandler.getStingOfTheHiveMineCount(game, tile)
                : tokenPath.contains("token_theodisi_oblivionreflection")
                        ? OblivionAbilityHandler.getReflectionCount(game, tile)
                        : 0;
        if (count < 2) {
            return;
        }
        tileGraphics.setFont(Storage.getFont24());
        DrawingUtil.superDrawString(
                tileGraphics,
                Integer.toString(count),
                drawX + tokenImage.getWidth() / 2,
                drawY + tokenImage.getHeight() / 2,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                stroke4,
                Color.BLACK);
    }

    private record WormholeSpot(char type, int x, int y) {}

    private static final Map<String, List<WormholeSpot>> WORMHOLE_SPOTS = Map.ofEntries(
            Map.entry("17", List.of(new WormholeSpot('d', 187, 206))),
            Map.entry("51", List.of(new WormholeSpot('d', 239, 251))),
            Map.entry("82a", List.of(new WormholeSpot('g', 169, 273))),
            Map.entry(
                    "82b",
                    List.of(
                            new WormholeSpot('a', 95, 249),
                            new WormholeSpot('b', 169, 273),
                            new WormholeSpot('g', 243, 249))),
            Map.entry("94", List.of(new WormholeSpot('e', 45, 152))),
            Map.entry("118", List.of(new WormholeSpot('e', 250, 236))),
            Map.entry("c02", List.of(new WormholeSpot('a', 37, 158), new WormholeSpot('b', 223, 62))),
            Map.entry("c10", List.of(new WormholeSpot('a', 182, 22), new WormholeSpot('b', 259, 241))),
            Map.entry("c11", List.of(new WormholeSpot('a', 54, 138), new WormholeSpot('b', 159, 275))),
            Map.entry("d119", List.of(new WormholeSpot('b', 94, 170))),
            Map.entry("d123", List.of(new WormholeSpot('a', 22, 110), new WormholeSpot('b', 190, 206))),
            Map.entry("er19", List.of(new WormholeSpot('a', 60, 44), new WormholeSpot('b', 192, 184))),
            Map.entry("er119", List.of(new WormholeSpot('a', 60, 44), new WormholeSpot('b', 192, 184))),
            Map.entry("er94", List.of(new WormholeSpot('b', 157, 165))),
            Map.entry("er95", List.of(new WormholeSpot('a', 60, 155), new WormholeSpot('b', 215, 61))),
            Map.entry("m05", List.of(new WormholeSpot('a', 185, 180))),
            Map.entry("m32", List.of(new WormholeSpot('b', 49, 147))));

    private static void drawOnWormhole(
            Tile tile, Graphics graphics, BufferedImage icon, int offset, String types, boolean all) {
        List<WormholeSpot> spots = WORMHOLE_SPOTS.get(tile.getTileID());
        if (spots != null) {
            for (WormholeSpot spot : spots) {
                if (all || types.indexOf(spot.type()) >= 0) {
                    graphics.drawImage(icon, TILE_PADDING + offset + spot.x(), TILE_PADDING + offset + spot.y(), null);
                }
            }
            return;
        }
        Point wormholeLocation =
                TileHelper.getTileById(tile.getTileID()).getShipPositionsType().getWormholeLocation();
        if (wormholeLocation == null) {
            graphics.drawImage(icon, TILE_PADDING + offset + 86, TILE_PADDING + 260, null);
        } else {
            graphics.drawImage(
                    icon, TILE_PADDING + offset + wormholeLocation.x, TILE_PADDING + offset + wormholeLocation.y, null);
        }
    }
}
