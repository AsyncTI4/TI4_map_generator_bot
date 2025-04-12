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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.commands.CommandHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.model.ShipPositionModel.ShipPosition;
import ti4.model.UnitModel;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.image.FileUploadService;

public class TileGenerator {

    private static final int TILE_PADDING = 100;
    private static final Point TILE_POSITION_POINT = new Point(255, 295);
    private static final Point LABEL_POSITION_POINT = new Point(90, 295);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);
    private static final BasicStroke stroke7 = new BasicStroke(7.0f);
    private static final BasicStroke stroke8 = new BasicStroke(8.0f);
    private static final int TILE_EXTRA_WIDTH = 260;
    private static final int EXTRA_X = 100;
    private static final int TILE_WIDTH = 345;
    private static final int TILE_HEIGHT = 300;
    private static final int EXTRA_Y = 100;

    private final Game game;
    private final GenericInteractionCreateEvent event;
    private final boolean isFoWPrivate;
    private final Player fowPlayer;
    private final int context;
    private final String focusTile;
    private final DisplayType displayType;

    public TileGenerator(@NotNull Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        this(game, event, displayType, 0, "000", null);
    }

    public TileGenerator(@NotNull Game game, GenericInteractionCreateEvent event, @Nullable DisplayType displayType, int context, @NotNull String focusTile) {
        this(game, event, displayType, context, focusTile, null);
    }

    public TileGenerator(@NotNull Game game, GenericInteractionCreateEvent event, @Nullable DisplayType displayType, int context, @NotNull String focusTile, @Nullable Player fowPlayer) {
        this.game = game;
        this.event = event;
        this.displayType = displayType;
        this.context = context;
        this.focusTile = focusTile;
        this.isFoWPrivate = isFowModeActive();
        this.fowPlayer = fowPlayer != null ? fowPlayer
            : (event != null ? CommandHelper.getPlayerFromGame(game, event.getMember(), event.getUser().getId()) : null);
    }

    private boolean isFowModeActive() {
        return game.isFowMode() && event != null &&
            (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL) ||
                event instanceof UserOverridenGenericInteractionCreateEvent);
    }

    public FileUpload createFileUpload() {
        return FileUploadService.createFileUpload(createMainImage(), game.getName());
    }

    public BufferedImage createMainImage() {
        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        Set<String> systemsInRange = getTilesToShow(game, context, focusTile);
        Set<String> keysToRemove = new HashSet<>(tilesToDisplay.keySet());
        keysToRemove.removeAll(systemsInRange);
        for (String tile_ : keysToRemove) {
            tilesToDisplay.remove(tile_);
        }

        // Resolve fog of war vision limitations
        if (isFoWPrivate) {
            Set<String> tilesToShow = FoWHelper.fowFilter(game, fowPlayer);
            Set<String> keys = new HashSet<>(tilesToDisplay.keySet());
            keys.removeAll(tilesToShow);
            for (String key : keys) {
                tilesToDisplay.remove(key);
                if (fowPlayer != null) {
                    tilesToDisplay.put(key, fowPlayer.buildFogTile(key, fowPlayer));
                }
            }
        }

        int width = TILE_WIDTH + (TILE_EXTRA_WIDTH * 2 * context) + EXTRA_X;
        int height = TILE_HEIGHT * (2 * context + 1) + EXTRA_Y;
        var mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = mainImage.getGraphics();

        try {
            Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
            tileMap.remove(null);

            Set<String> tiles = tileMap.keySet();
            Set<String> tilesWithExtra = new HashSet<>(game.getAdjacentTileOverrides().values());
            tilesWithExtra.addAll(game.getBorderAnomalies().stream()
                .map(BorderAnomalyHolder::getTile)
                .collect(Collectors.toSet()));

            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Tile));
            tilesWithExtra.forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Extras));
            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Units));
            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.TileNumber));

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
        var isSpiral = tileShipPositions != null && tileShipPositions.isSpiral();

        switch (step) {
            case Setup -> {
            } // do nothing
            case Tile -> {
                BufferedImage image = ImageHelper.read(tile.getTilePath());
                tileGraphics.drawImage(image, TILE_PADDING, TILE_PADDING, null);

                // ADD ANOMALY BORDER IF HAS ANOMALY PRODUCING TOKENS OR UNITS
                if (tile.isAnomaly(game) && tileShipPositions != null) {
                    BufferedImage anomalyImage = ImageHelper.read(ResourceHelper.getInstance().getTileFile("tile_anomaly.png"));
                    switch (tileShipPositions.toString().toUpperCase()) {
                        case "TYPE09":
                        case "TYPE12":
                        case "TYPE15":
                            tileGraphics.drawImage(anomalyImage, TILE_PADDING + 36, TILE_PADDING + 43, null);
                            break;
                        default:
                            tileGraphics.drawImage(anomalyImage, TILE_PADDING, TILE_PADDING, null);
                    }
                }

                // ADD HEX BORDERS FOR CONTROL
                Player controllingPlayer = game.getPlayerThatControlsTile(tile);
                if (!game.getHexBorderStyle().equals("off") && controllingPlayer != null && !isSpiral) {
                    int sideNum = 0;
                    List<Integer> openSides = new ArrayList<>();
                    for (String adj : PositionMapper.getAdjacentTilePositions(tile.getPosition())) {
                        if (game.getPlayerThatControlsTile(adj) == controllingPlayer) {
                            openSides.add(sideNum);
                        }
                        sideNum++;
                    }
                    if (isFoWPrivate && this.fowPlayer == null) openSides.clear();
                    BufferedImage border = DrawingUtil.hexBorder(game.getHexBorderStyle(), Mapper.getColor(controllingPlayer.getColor()), openSides);
                    tileGraphics.drawImage(border, TILE_PADDING, TILE_PADDING, null);
                }

                setTextSize(tileGraphics);

                if (isFoWPrivate && tile.hasFog(fowPlayer)) {
                    BufferedImage frogOfWar = ImageHelper.read(tile.getFowTilePath(fowPlayer));
                    tileGraphics.drawImage(frogOfWar, TILE_PADDING, TILE_PADDING, null);
                    String label = tile.getFogLabel(fowPlayer);
                    if (label != null) {
                        //Rnd number label at the bottom
                        if (label.startsWith("Rnd")) {
                            int labelX = TILE_PADDING + LABEL_POSITION_POINT.x;
                            int labelY = TILE_PADDING + LABEL_POSITION_POINT.y;
                            DrawingUtil.superDrawString(tileGraphics, label, labelX, labelY, Color.WHITE, null, null, null, null);
                            //Any other custom label wordwrapped in the middle
                        } else {
                            int labelX = TILE_PADDING + (TILE_WIDTH / 2);
                            int labelY = TILE_PADDING + (TILE_HEIGHT / 2);
                            int lineHeight = tileGraphics.getFontMetrics().getHeight();
                            List<String> toDraw = DrawingUtil.layoutText((Graphics2D) tileGraphics, label, TILE_WIDTH - TILE_PADDING);
                            int deltaY = 0;
                            for (String line : toDraw) {
                                DrawingUtil.superDrawString(tileGraphics, line, labelX, labelY + deltaY, Color.WHITE, MapGenerator.HorizontalAlign.Center, null, null, null);
                                deltaY += lineHeight;
                            }
                        }
                    }
                }

                if (TileHelper.isDraftTile(tile.getTileModel())) {
                    String tileID = tile.getTileID();
                    String draftNum = tileID.replaceAll("[a-z]", "");
                    String draftColor = tileID.replaceAll("[0-9]", "").replaceAll("blank", "").toUpperCase();
                    Point draftNumPosition = new Point(85, 140);

                    if (tileID.endsWith("blank")) {
                        //tiny tile
                        String greenPath = ResourceHelper.getInstance().getTileFile("00_green.png");
                        BufferedImage green = ImageHelper.readScaled(greenPath, 73, 63);
                        tileGraphics.drawImage(green, TILE_PADDING + 49, TILE_PADDING + 119, null);
                    } else {
                        tileGraphics.setFont(Storage.getFont50());
                        int numX = TILE_PADDING + draftNumPosition.x;
                        int numY = TILE_PADDING + draftNumPosition.y;
                        DrawingUtil.superDrawString(tileGraphics, draftNum, numX, numY, Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Center, stroke8, Color.BLACK);
                    }
                    tileGraphics.setFont(Storage.getFont24());
                    int numX = TILE_PADDING + 172; //172 //320
                    int numY = TILE_PADDING + 228; //50  //161
                    DrawingUtil.superDrawString(tileGraphics, draftColor, numX, numY, Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Bottom, stroke6, Color.BLACK);
                }

                // pa_unitimage.png
                // add icons to wormholes for agendas
                boolean reconstruction = (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
                if ((ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban"))
                    && (Mapper.getWormholes(tile.getTileID()).contains(Constants.ALPHA) || Mapper.getWormholes(tile.getTileID()).contains(Constants.BETA))) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    drawOnWormhole(tile, tileGraphics, blockedWormholeImage, 40);
                }
                if (reconstruction
                    && (Mapper.getWormholes(tile.getTileID()).contains(Constants.ALPHA))) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                    drawOnWormhole(tile, tileGraphics, doubleWormholeImage, 0, "a");
                }
                if (reconstruction
                    && (Mapper.getWormholes(tile.getTileID()).contains(Constants.BETA))) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                    drawOnWormhole(tile, tileGraphics, doubleWormholeImage, 0, "b");
                }
                if ((ButtonHelper.isLawInPlay(game, "nexus") || ButtonHelper.isLawInPlay(game, "absol_nexus"))
                    && (tile.getTileID().equals("82b"))
                    && !(ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban")) // 
                ) {
                    // avoid doubling up, which is important when using the transparent symbol
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    drawOnWormhole(tile, tileGraphics, blockedWormholeImage, 40);
                }
                if ((ButtonHelper.isLawInPlay(game, "shared_research")) && tile.isNebula()) {
                    BufferedImage nebulaBypass = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_shared_research.png"));
                    if (isSpiral) {
                        switch (tile.getTileID()) {
                            case "51":
                                tileGraphics.drawImage(nebulaBypass, TILE_PADDING + 42, TILE_PADDING + 235, null);
                                break;
                            case "82b":
                                tileGraphics.drawImage(nebulaBypass, TILE_PADDING + 30, TILE_PADDING + 221, null);
                                break;
                            case "82a":
                                tileGraphics.drawImage(nebulaBypass, TILE_PADDING + 63, TILE_PADDING + 273, null);
                                break;
                            default:
                                tileGraphics.drawImage(nebulaBypass, TILE_PADDING + 99, TILE_PADDING + 294, null);
                        }
                    } else if (tile.isHomeSystem()) {
                        tileGraphics.drawImage(nebulaBypass, TILE_PADDING + 42, TILE_PADDING + 193, null);
                    } else {
                        tileGraphics.drawImage(nebulaBypass, TILE_PADDING + 80, TILE_PADDING + 236, null);
                    }
                }
            }
            case Extras -> {
                if (isFoWPrivate && tile.hasFog(fowPlayer))
                    return tileOutput;

                List<String> adj = game.getAdjacentTileOverrides(tile.getPosition());
                int direction = 0;
                for (String secondaryTile : adj) {
                    if (secondaryTile != null) {
                        addBorderDecoration(direction, secondaryTile, tileGraphics,
                            BorderAnomalyModel.BorderAnomalyType.ARROW);
                    }
                    direction++;
                }
                game.getBorderAnomalies().forEach(borderAnomalyHolder -> {
                    if (borderAnomalyHolder.getTile().equals(tile.getPosition())) {
                        addBorderDecoration(borderAnomalyHolder.getDirection(), null, tileGraphics, borderAnomalyHolder.getType());
                    }
                });
            }
            case Units -> {
                if (isFoWPrivate && tile.hasFog(fowPlayer))
                    return tileOutput;

                List<Rectangle> rectangles = new ArrayList<>();
                Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
                UnitHolder spaceUnitHolder = tile.getSpaceUnitHolder();
                if (spaceUnitHolder != null) {
                    addSleeperToken(tile, tileGraphics, spaceUnitHolder, TileGenerator::isValidCustodianToken, game);
                    addToken(tile, tileGraphics, spaceUnitHolder, game);
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
                        ignoredFs = ButtonHelper.checkFleetAndCapacity(player, game, tile, event, false, false)[3];
                        capacity = ButtonHelper.checkFleetAndCapacity(player, game, tile, event, false, false)[2];
                        capacityUsed = ButtonHelper.checkFleetAndCapacity(player, game, tile, event, false, false)[1];
                    }
                }
                for (UnitHolder unitHolder : unitHolders) {
                    addSleeperToken(tile, tileGraphics, unitHolder, TileGenerator::isValidToken, game);
                    addControl(tile, tileGraphics, unitHolder, rectangles);
                }
                if (game.isShowGears() && !game.isFowMode()) {
                    if (prodInSystem > 0) {
                        int textModifer = 0;
                        if (prodInSystem == 1) {
                            textModifer = 7;
                        }
                        if (prodInSystem > 9) {
                            textModifer = -5;
                        }
                        if (prodInSystem == 11) {
                            textModifer = 0;
                        }
                        List<String> problematicTiles = java.util.List.of("25", "26", "64"); // quann, lodor, atlas
                        BufferedImage gearImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTileFile("production_representation.png"), 64, 64);
                        int xMod;
                        int yMod = -290;
                        if (tile.getUnitHolders().size() != 4 || problematicTiles.contains(tile.getTileID())) {
                            xMod = -15;
                        } else {
                            xMod = -155;
                        }
                        tileGraphics.drawImage(gearImage, TILE_PADDING + TILE_POSITION_POINT.x + xMod - 29, TILE_PADDING + TILE_POSITION_POINT.y + yMod - 4, null);
                        tileGraphics.setFont(Storage.getFont35());
                        tileGraphics.drawString(prodInSystem + "", TILE_PADDING + TILE_POSITION_POINT.x + xMod + 15 + textModifer - 25, TILE_PADDING + TILE_POSITION_POINT.y + yMod + 40);
                    }

                    if (capacityUsed > 0 || capacity > 0 || ignoredFs > 0) {
                        int textModifer = 0;
                        if (capacity == 1) {
                            textModifer = 7;
                        }
                        if (capacity > 9) {
                            textModifer = -5;
                        }
                        if (capacity == 11) {
                            textModifer = 0;
                        }
                        List<String> problematicTiles = java.util.List.of("25", "26", "64"); // quann, lodor, atlas
                        BufferedImage carrierImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTileFile("capacity_representation.png"), 64, 21);

                        int xMod;
                        int yMod = -290;
                        if (tile.getUnitHolders().size() != 4 || problematicTiles.contains(tile.getTileID())) {
                            xMod = -15;
                        } else {
                            xMod = -155;
                        }
                        Graphics2D g2d = (Graphics2D) tileGraphics;
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        // Calculate water height  
                        double waterHeight;
                        if (capacity + ignoredFs > 0) {
                            waterHeight = 20.0 * capacityUsed / (capacity + ignoredFs);
                        } else {
                            waterHeight = 20.0 * Math.min(capacityUsed, 1.2);
                        }

                        // Draw brown box (3 sides)  

                        g2d.setStroke(new BasicStroke(6));

                        int gearX = TILE_PADDING + TILE_POSITION_POINT.x + xMod - 29;
                        int gearY = TILE_PADDING + TILE_POSITION_POINT.y + yMod + 5;

                        if (prodInSystem == 0) {
                            gearX = gearX - 27;
                            gearY = gearY - 55;
                        }

                        //g2d.setColor(new Color(128, 197, 222));  
                        //g2d.fillRect(gearX+43, gearY+64+18 -(int)(waterHeight), 25, (int)waterHeight);   
                        //g2d.setColor(new Color(122, 127, 128)); 

                        //g2d.drawLine(gearX+40, gearY+64, gearX+40, gearY+64+20);  

                        // Right side  
                        //g2d.drawLine(gearX+40+30, gearY+64, gearX+40+30, gearY+64+20);  

                        // Bottom side  
                        //g2d.drawLine(gearX+40, gearY+64+20, gearX+40+30, gearY+64+20);  
                        tileGraphics.drawImage(carrierImage, gearX + 24, gearY + 60, null);
                        g2d.setColor(Color.WHITE);
                        tileGraphics.setFont(Storage.getFont12());
                        String msg = capacityUsed + " / " + capacity;
                        if (ignoredFs > 0) {
                            msg = capacityUsed + " / " + (capacity + ignoredFs) + "*";
                        }
                        DrawingUtil.superDrawString(tileGraphics, msg, gearX + 39 + 17, gearY + 95, Color.WHITE, HorizontalAlign.Center, null, stroke4, Color.BLACK);

                    }
                }

                if (spaceUnitHolder != null) {
                    addCC(tile, tileGraphics, spaceUnitHolder);
                }
                int degree = 180;
                int degreeChange = 5;

                for (UnitHolder unitHolder : unitHolders) {
                    int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS
                        : Constants.RADIUS;
                    if (unitHolder != spaceUnitHolder) {
                        addPlanetToken(tile, tileGraphics, unitHolder, rectangles);
                    }
                    new UnitRenderGenerator(
                        game, displayType, tile, tileGraphics, rectangles, degree, degreeChange, unitHolder, radius, fowPlayer).render();
                }
            }
            case Distance -> {
                if (game.isFowMode()) {
                    break;
                }
                Integer distance = game.getTileDistances().get(tile.getPosition());
                if (distance == null) {
                    distance = 100;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }
                int x = TILE_PADDING + (isSpiral ? 36 : 0);
                int y = TILE_PADDING + (isSpiral ? 43 : 0);
                if (distance > 0) {
                    BufferedImage distanceColor = ImageHelper.read(ResourceHelper.getInstance().getTileFile(getColorFilterForDistance(distance)));
                    tileGraphics.drawImage(distanceColor, x, y, null);
                }
                if (distance < 11) {
                    tileGraphics.setFont(Storage.getFont110());
                    DrawingUtil.superDrawString(tileGraphics, distance.toString(), x + tileImage.getWidth() / 2, y + tileImage.getHeight() / 2, Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Center, stroke4, Color.BLACK);
                }
            }
            case Wormholes -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;

                if (!FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    x += (isSpiral ? 36 : 0);
                    y += (isSpiral ? 43 : 0);
                    int count = FoWHelper.getTileWHs(game, tile.getPosition()).size();
                    x += (count == 1 ? 76 : 30);
                    y += (count == 1 ? 52 : 26);
                    for (String wh : FoWHelper.getTileWHs(game, tile.getPosition())) {
                        String whFile = ResourceHelper.getInstance().getTokenFile("token_wh" + wh + ".png");
                        if (whFile == null) {
                            whFile = ResourceHelper.getInstance().getTokenFile("token_custom_eronous_wh" + wh + ".png");
                        }
                        if (whFile != null) {
                            BufferedImage bufferedImage = ImageHelper.readScaled(whFile, 3);
                            tileGraphics.drawImage(bufferedImage, x, y, null);
                        }
                        x += (count <= 1 ? 0 : 91 / (count - 1));
                        y += (count <= 1 ? 0 : 52 / (count - 1));
                    }
                }
            }
            case Anomalies -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;

                if (!tile.isAnomaly(game)) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    x += (isSpiral ? 36 : 0);
                    y += (isSpiral ? 43 : 0);
                    x += 33;
                    y += 101;
                    String chevronFile = ResourceHelper.getInstance().getTileFile("tile_anomaly_chevron.png");
                    BufferedImage bufferedImage = ImageHelper.read(chevronFile);
                    tileGraphics.drawImage(bufferedImage, x, y, null);
                }
            }
            case Aetherstream -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;
                boolean anomalyIsAdjacent = FoWHelper.isTileAdjacentToAnAnomaly(game, tile.getPosition(), null);

                if (!anomalyIsAdjacent) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    x += (isSpiral ? 36 : 0);
                    y += (isSpiral ? 43 : 0);
                    String batFile = ResourceHelper.getInstance().getGeneralFile("zobat" + (RandomHelper.isOneInX(4096) ? "_shiny" : "") + ".png");
                    BufferedImage bufferedImage = ImageHelper.read(batFile);
                    if (bufferedImage != null) {
                        x += (345 - bufferedImage.getWidth()) / 2;
                        y += (300 - bufferedImage.getHeight()) / 2;
                        tileGraphics.drawImage(bufferedImage, x, y, null);
                    }
                }
            }
            case Legendaries -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;
                boolean isLegendary = ButtonHelper.isTileLegendary(tile) || tile.isMecatol();

                if (!isLegendary) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else if (tile.isMecatol()) {
                    String councilFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
                    BufferedImage bufferedImage = ImageHelper.readScaled(councilFile, 2.0f);
                    if (bufferedImage == null) break;

                    int w = bufferedImage.getWidth();
                    int h = bufferedImage.getHeight();
                    int border = 3;
                    int padding = border + 2;
                    BufferedImage backgroundImage = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                    x += (345 - w) / 2;
                    y += (300 - w) / 2;

                    for (int i = -padding; i < w + padding; i++) {
                        for (int j = -padding; j < h + padding; j++) {
                            int bestAlpha = 0;
                            for (int p = -padding; p <= padding; p++) {
                                if (i + p < 0 || i + p >= w) continue;
                                for (int q = -padding; q <= padding; q++) {
                                    if (j + q < 0 || j + q >= h) continue;
                                    int alpha = new Color(bufferedImage.getRGB(i + p, j + q), true).getAlpha();
                                    if (p * p + q * q <= border * border) {
                                        bestAlpha = Math.max(bestAlpha, alpha);
                                    }
                                }
                            }
                            backgroundImage.setRGB(i + padding, j + padding, new Color(0, 0, 0, bestAlpha).getRGB());
                        }
                    }

                    tileGraphics.drawImage(backgroundImage, x - padding, y - padding, null);
                    tileGraphics.drawImage(bufferedImage, x, y, null);
                } else {
                    int number = 0;
                    for (Planet planet : tile.getPlanetUnitHolders()) {
                        number += (planet.isLegendary() ? 1 : 0);
                    }
                    x += (isSpiral ? 36 : 0);
                    y += (isSpiral ? 43 : 0);
                    String legendaryFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                    BufferedImage bufferedImage = ImageHelper.readScaled(legendaryFile, 0.5f);
                    if (number >= 2) {
                        bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(Math.sqrt(1.0f / number)));
                        x += (345 - bufferedImage.getWidth()) / 2;
                        y += (300 - bufferedImage.getHeight()) / 2;
                        for (int i = 0; i < number; i++) {
                            tileGraphics.drawImage(bufferedImage, x - 30 + i * 60 / (number - 1), y - 30 + i * 60 / (number - 1), null);
                        }
                    } else {
                        if (bufferedImage == null) break;
                        x += (345 - bufferedImage.getWidth()) / 2;
                        y += (300 - bufferedImage.getHeight()) / 2;
                        tileGraphics.drawImage(bufferedImage, x, y, null);
                    }
                }
            }
            case Empties -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;
                boolean isEmpty = tile.getPlanetUnitHolders().isEmpty();

                if (!isEmpty) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    x += (isSpiral ? 36 : 0);
                    y += (isSpiral ? 43 : 0);
                    x += 93;
                    y += 70;
                    tileGraphics.setColor(Color.RED);
                    tileGraphics.fillOval(x, y, 159, 159);
                    tileGraphics.setColor(Color.WHITE);
                    DrawingUtil.drawCenteredString(tileGraphics, "!", new Rectangle(x, y, 159, 159), Storage.getFont80());
                }
            }
            case SpaceCannon -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;
                String tilePos = tile.getPosition();
                HashMap<String, List<Integer>> pdsDice = new HashMap<>();

                for (Player player : game.getRealPlayers()) {
                    List<Integer> diceCount = new ArrayList<>();
                    List<Integer> diceCountMirveda = new ArrayList<>();
                    int mod = (game.playerHasLeaderUnlockedOrAlliance(player, "kolumecommander") ? 1 : 0);

                    if (player.hasAbility("starfall_gunnery")) {
                        for (int i = ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile); i > 0; i--) {
                            diceCount.add(8 - mod);
                        }
                    }

                    for (String adjTilePos : FoWHelper.getAdjacentTiles(game, tilePos, player, false, true)) {
                        Tile adjTile = game.getTileByPosition(adjTilePos);
                        if (adjTile == null) {
                            continue;
                        }
                        boolean sameTile = tilePos.equalsIgnoreCase(adjTilePos);
                        for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                            if (sameTile && Constants.MECATOLS.contains(unitHolder.getName())) {
                                if (player.controlsMecatol(false) && player.getTechs().contains("iihq")) {
                                    diceCount.add(5 - mod);
                                }
                            }
                            for (Map.Entry<Units.UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                                if (unitEntry.getValue() == 0) {
                                    continue;
                                }

                                Units.UnitKey unitKey = unitEntry.getKey();
                                if (game.getPlayerByColorID(unitKey.getColorID()).orElse(null) != player) {
                                    continue;
                                }

                                UnitModel model = player.getUnitFromUnitKey(unitKey);
                                if (model == null || (model.getId().equalsIgnoreCase("xxcha_mech")
                                    && ButtonHelper.isLawInPlay(game, "articles_war"))) {
                                    continue;
                                }
                                int tempMod = 0;
                                if (model.getId().equalsIgnoreCase("bentor_flagship")) {
                                    tempMod += player.getNumberOfBluePrints();
                                }
                                if (model.getDeepSpaceCannon() || sameTile) {
                                    for (int i = model.getSpaceCannonDieCount() * unitEntry.getValue(); i > 0; i--) {
                                        diceCount.add(model.getSpaceCannonHitsOn() - mod - tempMod);
                                    }
                                } else if (game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")) {
                                    diceCountMirveda.add(model.getSpaceCannonHitsOn() - mod - tempMod);
                                }
                            }
                            if (sameTile && player.getPlanets().contains(unitHolder.getName())) {
                                Planet planet = game.getPlanetsInfo().get(unitHolder.getName());
                                for (int i = planet.getSpaceCannonDieCount(); i > 0; i--) {
                                    diceCount.add(planet.getSpaceCannonHitsOn() - mod);
                                }
                            }
                        }
                    }

                    if (!diceCountMirveda.isEmpty()) {
                        Collections.sort(diceCountMirveda);
                        diceCount.add(diceCountMirveda.getFirst());
                    }

                    if (!diceCount.isEmpty()) {
                        Collections.sort(diceCount);
                        if (player.getTechs().contains("ps")) {
                            diceCount.addFirst(diceCount.getFirst());
                        }
                        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
                            diceCount.addFirst(diceCount.getFirst());
                        }
                        pdsDice.put(player.getUserID(), diceCount);
                    }
                }

                if (pdsDice.isEmpty()) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    x += (isSpiral ? 36 : 0);
                    y += (isSpiral ? 43 : 0);
                    float scale = pdsDice.size() >= 3 ? 6.0f / pdsDice.size() : 3.0f;

                    Font bigFont;
                    Font smallFont = switch (pdsDice.size()) {
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

                    x += (int) ((345 - 73 * scale) / 2);
                    y += (int) ((300 - pdsDice.size() * 48 * scale) / 2);
                    for (String playerID : pdsDice.keySet()) {
                        Player player = game.getPlayer(playerID);
                        int numberOfDice = pdsDice.get(playerID).size();
                        boolean rerolls = game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander");
                        float expectedHits;
                        if (rerolls) {
                            expectedHits = (100.0f * numberOfDice - pdsDice.get(playerID).stream().mapToInt(value -> (value - 1) * (value - 1)).sum()) / 100;
                        } else {
                            expectedHits = (11.0f * numberOfDice - pdsDice.get(playerID).stream().mapToInt(Integer::intValue).sum()) / 10;
                        }
                        if (DrawingUtil.getBlackWhiteFileSuffix(player.getColorID()).equals("_wht.png")) {
                            tileGraphics.setColor(Color.WHITE);
                        } else {
                            tileGraphics.setColor(Color.BLACK);
                        }
                        BufferedImage bufferedImage = ImageHelper.readScaled(Mapper.getCCPath(Mapper.getControlID(player.getColor())), scale);
                        DrawingUtil.drawControlToken(tileGraphics, bufferedImage, player, x, y, false, scale / 2);
                        DrawingUtil.drawCenteredString(tileGraphics, numberOfDice + (rerolls ? "*" : ""),
                            new Rectangle(Math.round(x + 6 * scale), Math.round(y + 12 * scale), Math.round(61 * scale / 2), Math.round(24 * scale * 2 / 3)),
                            bigFont);
                        DrawingUtil.drawCenteredString(tileGraphics, "(" + expectedHits + ")",
                            new Rectangle(Math.round(x + 6 * scale), Math.round(y + 12 * scale + 24 * scale * 2 / 3), Math.round(61 * scale / 2), Math.round(24 * scale / 3)),
                            smallFont);
                        if (numberOfDice >= 5) {
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(playerID).subList(0, numberOfDice / 3).stream().map(Object::toString).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(playerID).subList(numberOfDice / 3, 2 * numberOfDice / 3).stream().map(Object::toString).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale + 36 * scale / 3), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(playerID).subList(2 * numberOfDice / 3, numberOfDice).stream().map(Object::toString).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale + 36 * scale * 2 / 3), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                        } else if (numberOfDice >= 3) {
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(playerID).subList(0, numberOfDice / 2).stream().map(Object::toString).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 12 * scale), Math.round(73 * scale / 2), Math.round(24 * scale / 2)),
                                smallFont);
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(playerID).subList(numberOfDice / 2, numberOfDice).stream().map(Object::toString).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 12 * scale + 24 * scale / 2), Math.round(73 * scale / 2), Math.round(24 * scale / 2)),
                                smallFont);
                        } else {
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(playerID).stream().map(Object::toString).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), y, Math.round(73 * scale / 2), Math.round(48 * scale)),
                                smallFont);
                        }
                        y += (int) (48 * scale);
                    }
                }
            }
            case Traits -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }
                BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                tileGraphics.drawImage(fogging, TILE_PADDING, TILE_PADDING, null);

                for (Planet planet : tile.getPlanetUnitHolders()) {
                    String traitFile = "";
                    List<String> traits = planet.getPlanetType();
                    if (traits.isEmpty() && StringUtils.isNotBlank(planet.getOriginalPlanetType())) {
                        traits.add(planet.getOriginalPlanetType());
                    }

                    if (tile.isMecatol()) {
                        traitFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
                    } else if ("faction".equalsIgnoreCase(planet.getOriginalPlanetType())) {
                        traitFile = ResourceHelper.getInstance().getFactionFile(Mapper.getPlanet(planet.getName()).getFactionHomeworld() + ".png");
                    } else if (traits.size() == 1) {
                        String t = planet.getPlanetType().getFirst();
                        traitFile = ResourceHelper.getInstance().getGeneralFile(("" + t.charAt(0)).toUpperCase() + t.substring(1).toLowerCase() + ".png");
                    } else if (!traits.isEmpty()) {
                        String t = "";
                        t += traits.contains("cultural") ? "C" : "";
                        t += traits.contains("hazardous") ? "H" : "";
                        t += traits.contains("industrial") ? "I" : "";
                        if (t.equals("CHI")) {
                            traitFile = ResourceHelper.getInstance().getPlanetResource("pc_attribute_combo_CHI_big.png");
                        } else {
                            traitFile = ResourceHelper.getInstance().getPlanetResource("pc_attribute_combo_" + t + ".png");
                        }
                    }

                    if (!traitFile.isEmpty()) {
                        BufferedImage bufferedImage = ImageHelper.read(traitFile);
                        bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(9200.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                        int w = bufferedImage.getWidth();
                        int h = bufferedImage.getHeight();
                        int innerBorder = (tile.isMecatol() ? 3 : 2);
                        int outerBorder = innerBorder + (tile.isMecatol() ? -1 : 2);
                        int padding = outerBorder + (tile.isMecatol() ? 3 : 2);
                        BufferedImage backgroundInner = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                        BufferedImage backgroundOuter = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                        for (int i = -padding; i < w + padding; i++) {
                            for (int j = -padding; j < h + padding; j++) {
                                int bestInnerAlpha = 0;
                                int bestOuterAlpha = 0;
                                for (int p = -padding; p <= padding; p++) {
                                    if (i + p < 0 || i + p >= w) continue;
                                    for (int q = -padding; q <= padding; q++) {
                                        if (j + q < 0 || j + q >= h) continue;
                                        int alpha = new Color(bufferedImage.getRGB(i + p, j + q), true).getAlpha();
                                        if (p * p + q * q <= innerBorder * innerBorder) {
                                            bestInnerAlpha = Math.max(bestInnerAlpha, alpha);
                                        }
                                        if (p * p + q * q <= outerBorder * outerBorder) {
                                            bestOuterAlpha = Math.max(bestOuterAlpha, alpha);
                                        }
                                    }
                                }
                                backgroundInner.setRGB(i + padding, j + padding, new Color(0, 0, 0, bestInnerAlpha).getRGB());
                                backgroundOuter.setRGB(i + padding, j + padding, new Color(255, 255, 255, bestOuterAlpha).getRGB());
                            }
                        }

                        Point position = planet.getHolderCenterPosition();
                        if (planet.getName().equalsIgnoreCase("mirage") && (tile.getPlanetUnitHolders().size() == 3 + 1)) {
                            position = new Point(Constants.MIRAGE_TRIPLE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x,
                                Constants.MIRAGE_TRIPLE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
                        }
                        position = new Point(position.x - w / 2 + TILE_PADDING, position.y - h / 2 + TILE_PADDING);

                        tileGraphics.drawImage(backgroundOuter, position.x - padding, position.y - padding, null);
                        tileGraphics.drawImage(backgroundInner, position.x - padding, position.y - padding, null);
                        tileGraphics.drawImage(bufferedImage, position.x, position.y, null);
                    }
                }
            }
            case TechSkips -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int planets = tile.getPlanetUnitHolders().size();
                if (planets == 0) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, TILE_PADDING, TILE_PADDING, null);
                    break;
                }
                boolean anySkips = false;
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    Set<String> skips = planet.getTechSpecialities();
                    int number = skips.size();
                    if (number == 0) {
                        continue;
                    }
                    anySkips = true;
                    int count = 0;

                    for (String skip : skips) {
                        String skipFile = switch (skip.toLowerCase()) {
                            case "biotic" -> ResourceHelper.getInstance().getGeneralFile("Biotic light.png");
                            case "cybernetic" -> ResourceHelper.getInstance().getGeneralFile("Cybernetic light.png");
                            case "propulsion" -> ResourceHelper.getInstance().getGeneralFile("Propulsion_light.png");
                            case "warfare" -> ResourceHelper.getInstance().getGeneralFile("Warfare_light.png");
                            default -> ResourceHelper.getInstance().getGeneralFile("Generic_Technology.png");
                        };

                        BufferedImage bufferedImage = ImageHelper.read(skipFile);
                        bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(9200.0f / bufferedImage.getWidth() / bufferedImage.getHeight() / Math.sqrt(1.0f * number)));
                        int w = bufferedImage.getWidth();
                        int h = bufferedImage.getHeight();
                        int innerBorder = 3;
                        int outerBorder = innerBorder + 3;
                        int padding = outerBorder + 2;
                        BufferedImage backgroundInner = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                        BufferedImage backgroundOuter = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                        for (int i = -padding; i < w + padding; i++) {
                            for (int j = -padding; j < h + padding; j++) {
                                int bestInnerAlpha = 0;
                                int bestOuterAlpha = 0;
                                for (int p = -padding; p <= padding; p++) {
                                    if (i + p < 0 || i + p >= w) continue;
                                    for (int q = -padding; q <= padding; q++) {
                                        if (j + q < 0 || j + q >= h) continue;
                                        int alpha = new Color(bufferedImage.getRGB(i + p, j + q), true).getAlpha();
                                        if (p * p + q * q <= innerBorder * innerBorder) {
                                            bestInnerAlpha = Math.max(bestInnerAlpha, alpha);
                                        }
                                        if (p * p + q * q <= outerBorder * outerBorder) {
                                            bestOuterAlpha = Math.max(bestOuterAlpha, alpha);
                                        }
                                    }
                                }
                                backgroundInner.setRGB(i + padding, j + padding, new Color(0, 0, 0, bestInnerAlpha).getRGB());
                                backgroundOuter.setRGB(i + padding, j + padding, new Color(255, 255, 255, bestOuterAlpha).getRGB());
                            }
                        }

                        Point position = planet.getHolderCenterPosition();
                        if (planet.getName().equalsIgnoreCase("mirage") && (tile.getPlanetUnitHolders().size() == 3 + 1)) {
                            position = new Point(Constants.MIRAGE_TRIPLE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x,
                                Constants.MIRAGE_TRIPLE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
                        }
                        if (number > 1) {
                            position = new Point(position.x - 20 + count * 40 / (number - 1), position.y - 20 + count * 40 / (number - 1));
                        }
                        position = new Point(position.x - w / 2 + TILE_PADDING, position.y - h / 2 + TILE_PADDING);

                        tileGraphics.drawImage(backgroundOuter, position.x - padding, position.y - padding, null);
                        tileGraphics.drawImage(backgroundInner, position.x - padding, position.y - padding, null);
                        tileGraphics.drawImage(bufferedImage, position.x, position.y, null);
                        count++;
                    }
                }
                if (!anySkips) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, TILE_PADDING, TILE_PADDING, null);
                }
            }
            case Attachments -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getTileModel().isHyperlane()) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int planets = tile.getPlanetUnitHolders().size();
                if (planets == 0) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, TILE_PADDING, TILE_PADDING, null);
                    break;
                }
                boolean anyAttachments = false;
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    List<String> attachments = new ArrayList<>(planet.getAttachments());
                    int number = attachments.size();
                    if (number == 0) {
                        continue;
                    }
                    anyAttachments = true;

                    String chevronFile = ResourceHelper.getInstance().getGeneralFile("misc_chevrons_basic.png");
                    if (attachments.contains("attachment_tombofemphidia.png")) {
                        chevronFile = ResourceHelper.getInstance().getGeneralFile("misc_chevrons_toe.png");
                    }
                    BufferedImage bufferedImage = ImageHelper.read(chevronFile);
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(9200.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    int w = bufferedImage.getWidth();
                    int h = bufferedImage.getHeight();
                    int innerBorder = 3;
                    int outerBorder = innerBorder + 3;
                    int padding = outerBorder + 2;
                    BufferedImage backgroundInner = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                    BufferedImage backgroundOuter = new BufferedImage(w + 2 * padding, h + 2 * padding, bufferedImage.getType());
                    for (int i = -padding; i < w + padding; i++) {
                        for (int j = -padding; j < h + padding; j++) {
                            int bestInnerAlpha = 0;
                            int bestOuterAlpha = 0;
                            for (int p = -padding; p <= padding; p++) {
                                if (i + p < 0 || i + p >= w) continue;
                                for (int q = -padding; q <= padding; q++) {
                                    if (j + q < 0 || j + q >= h) continue;
                                    int alpha = new Color(bufferedImage.getRGB(i + p, j + q), true).getAlpha();
                                    if (p * p + q * q <= innerBorder * innerBorder) {
                                        bestInnerAlpha = Math.max(bestInnerAlpha, alpha);
                                    }
                                    if (p * p + q * q <= outerBorder * outerBorder) {
                                        bestOuterAlpha = Math.max(bestOuterAlpha, alpha);
                                    }
                                }
                            }
                            backgroundInner.setRGB(i + padding, j + padding, new Color(0, 0, 0, bestInnerAlpha).getRGB());
                            backgroundOuter.setRGB(i + padding, j + padding, new Color(255, 255, 255, bestOuterAlpha).getRGB());
                        }
                    }

                    Point position = planet.getHolderCenterPosition();
                    if (planet.getName().equalsIgnoreCase("mirage") && (tile.getPlanetUnitHolders().size() == 3 + 1)) {
                        position = new Point(Constants.MIRAGE_TRIPLE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x,
                            Constants.MIRAGE_TRIPLE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
                    }
                    position = new Point(position.x - w / 2 + TILE_PADDING, position.y - h / 2 + TILE_PADDING);
                    if (number == 1) {
                        tileGraphics.drawImage(backgroundOuter, position.x - padding, position.y - padding, null);
                        tileGraphics.drawImage(backgroundInner, position.x - padding, position.y - padding, null);
                        tileGraphics.drawImage(bufferedImage, position.x, position.y, null);
                    } else {
                        tileGraphics.drawImage(backgroundOuter, position.x - padding, position.y - padding - 36, null);
                        tileGraphics.drawImage(backgroundInner, position.x - padding, position.y - padding - 36, null);
                        tileGraphics.drawImage(bufferedImage, position.x, position.y - 36, null);
                        tileGraphics.setColor(Color.WHITE);
                        tileGraphics.fillOval(position.x + w / 2 - 40, position.y + h / 2 - 8, 80, 80);
                        tileGraphics.setColor(Color.BLACK);
                        tileGraphics.fillOval(position.x + w / 2 - 36, position.y + h / 2 - 8 + 4, 72, 72);
                        tileGraphics.setColor(Color.WHITE);
                        DrawingUtil.drawCenteredString(tileGraphics, "" + number,
                            new Rectangle(position.x + w / 2 - 40, position.y + h / 2 - 8, 80, 80),
                            Storage.getFont48());
                    }

                }
                if (!anyAttachments) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, TILE_PADDING, TILE_PADDING, null);
                }
            }
            case TileNumber -> {
                //Tile number as the last step to put it on top of everything else
                setTextSize(tileGraphics);
                int textX = TILE_PADDING + TILE_POSITION_POINT.x;
                int textY = TILE_PADDING + TILE_POSITION_POINT.y;
                DrawingUtil.superDrawString(tileGraphics, tile.getPosition(), textX, textY, Color.WHITE, MapGenerator.HorizontalAlign.Right, MapGenerator.VerticalAlign.Bottom, stroke7, Color.BLACK);
            }
        }
        return tileOutput;
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

    private static void addBorderDecoration(
        int direction,
        String secondaryTile,
        Graphics tileGraphics,
        BorderAnomalyModel.BorderAnomalyType decorationType
    ) {
        Graphics2D tileGraphics2d = (Graphics2D) tileGraphics;

        if (decorationType == null) {
            return;
        }
        BufferedImage borderDecorationImage;
        try {
            BufferedImage cached = ImageHelper.read(decorationType.getImageFilePath());
            borderDecorationImage = new BufferedImage(cached.getColorModel(), cached.copyData(null), cached.isAlphaPremultiplied(), null);
        } catch (Exception e) {
            BotLogger.error("Could not find border decoration image! Decoration was " + decorationType, e);
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

        if (decorationType == BorderAnomalyModel.BorderAnomalyType.ARROW) {
            int textOffsetX = 12;
            int textOffsetY = 40;
            Graphics2D arrow = borderDecorationImage.createGraphics();
            AffineTransform arrowTextTransform = arrow.getTransform();

            arrow.setFont(secondaryTile.length() > 3 ? Storage.getFont14() : Storage.getFont16());
            arrow.setColor(Color.BLACK);

            if (direction >= 2 && direction <= 4) { // all the south directions
                arrow.rotate(Math.toRadians(180), imageCenterX, imageCenterY);
                textOffsetY = 25;
            }
            arrow.drawString(secondaryTile, textOffsetX, textOffsetY);
            arrow.setTransform(arrowTextTransform);
        }

        tileGraphics2d.rotate(Math.toRadians((direction) * 60), centerX, centerY);
        if (decorationType == BorderAnomalyModel.BorderAnomalyType.ARROW)
            centerX -= 20;
        tileGraphics2d.drawImage(borderDecorationImage, null, centerX - imageCenterX, -imageCenterY);
        tileGraphics2d.setTransform(originalTileTransform);
    }

    private void addCC(Tile tile, Graphics tileGraphics, UnitHolder unitHolder) {
        int deltaX = 0;
        int deltaY = 0;
        for (String ccID : unitHolder.getCCList()) {
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
                continue;
            }
            BufferedImage image = null;
            try {
                image = ImageHelper.read(ccPath);

                Point centerPosition = unitHolder.getHolderCenterPosition();

                Player player = DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), ccID);
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

    private void addControl(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<Rectangle> rectangles) {
        List<String> controlList = new ArrayList<>(unitHolder.getControlList());
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String controlID : controlList) {
                if (controlID.contains(Constants.SLEEPER)) {
                    continue;
                }

                Player player = DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID);
                boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);

                boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
                Point position = unitTokenPosition.getPosition(controlID);
                if (isMirage) {
                    if (tile.getPlanetUnitHolders().size() == 3 + 1) {
                        position = Constants.MIRAGE_TRIPLE_POSITION;
                    } else if (position == null) {
                        position = Constants.MIRAGE_POSITION;
                    } else {
                        position.x += Constants.MIRAGE_POSITION.x;
                        position.y += Constants.MIRAGE_POSITION.y;
                    }
                }

                float scale = 1.0f;
                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                if (controlTokenImage == null)
                    continue;

                if (position != null) {
                    int imgX = TILE_PADDING + position.x;
                    int imgY = TILE_PADDING + position.y;
                    DrawingUtil.drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    if (player != null && player.isRealPlayer() && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                        BufferedImage exhaustedTokenImage = ImageHelper.readScaled(ResourceHelper.getResourceFromFolder("command_token/", "exhaustedControl.png"), scale);
                        DrawingUtil.drawControlToken(tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
                        rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    }
                } else {
                    int imgX = TILE_PADDING + centerPosition.x + xDelta;
                    int imgY = TILE_PADDING + centerPosition.y;
                    DrawingUtil.drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    if (player != null && player.isRealPlayer() && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                        BufferedImage exhaustedTokenImage = ImageHelper.readScaled(ResourceHelper.getResourceFromFolder("command_token/", "exhaustedControl"), scale);
                        DrawingUtil.drawControlToken(tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
                        rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    }
                    xDelta += 10;
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, controlList);
        }
    }

    private static void addSleeperToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Function<String, Boolean> isValid, Game game) {
        BufferedImage tokenImage;
        Point centerPosition = unitHolder.getHolderCenterPosition();
        if (unitHolder.getName().equalsIgnoreCase("mirage") && (tile.getPlanetUnitHolders().size() == 3 + 1)) {
            centerPosition = new Point(Constants.MIRAGE_TRIPLE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x,
                Constants.MIRAGE_TRIPLE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
        }
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
        if (game.isShowBubbles() && unitHolder instanceof Planet planetHolder && shouldPlanetHaveShield(unitHolder, game)) {
            String tokenPath = switch (StringUtils.defaultString(planetHolder.getContrastColor())) {
                case "orange" -> ResourceHelper.getInstance().getTokenFile("token_planetaryShield_orange.png");
                default -> ResourceHelper.getInstance().getTokenFile("token_planetaryShield.png");
            };
            float scale = 0.95f;
            List<String> smallLegendaries = List.of("mirage", "mallice", "mallicelocked", "eko", "domna");
            if (Mapper.getPlanet(unitHolder.getName()).getLegendaryAbilityText() != null
                && !smallLegendaries.contains(unitHolder.getName().toLowerCase())) {
                scale = 1.65f;
            }
            if (unitHolder.getName().equalsIgnoreCase("elysium")) {
                scale = 1.65f;
            }
            if (Constants.MECATOLS.contains(unitHolder.getName())) {
                scale = 1.9f;
            }
            tokenImage = ImageHelper.readScaled(tokenPath, scale);
            Point position = new Point(centerPosition.x - (tokenImage.getWidth() / 2),
                centerPosition.y - (tokenImage.getHeight() / 2));
            tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y, null);
        }
        boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
        for (String tokenID : tokenList) {
            if (isValid.apply(tokenID)) {
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Could not find token file for: " + tokenID);
                    continue;
                }
                float scale = 0.85f;
                List<String> smallLegendaries = List.of("mirage", "mallice", "mallicelocked", "eko", "domna");
                boolean isLegendary = Mapper.getPlanet(unitHolder.getName()).getLegendaryAbilityText() != null;
                if (tokenPath.contains(Constants.DMZ_LARGE)) {
                    scale = 0.3f;
                    if (isLegendary && !smallLegendaries.contains(unitHolder.getName().toLowerCase())) {
                        scale = 0.53f;
                    }
                    if (unitHolder.getName().equalsIgnoreCase("elysium")) {
                        scale = 0.50f;
                    }
                    if (Constants.MECATOLS.contains(unitHolder.getName())) {
                        scale = 0.61f;
                    }
                } else if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                    scale = 1.32f;
                    if (isLegendary && !smallLegendaries.contains(unitHolder.getName().toLowerCase())) {
                        scale = 2.33f;
                    }
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    scale = 0.5f; // didn't previous get changed for custodians
                }
                tokenImage = ImageHelper.readScaled(tokenPath, scale);
                if (tokenImage == null)
                    continue;
                Point position = new Point(centerPosition.x - (tokenImage.getWidth() / 2),
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
        Tile tile,
        Graphics tileGraphics,
        UnitHolder unitHolder,
        List<Rectangle> rectangles
    ) {
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
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String tokenID : tokenList) {
                if (isValidToken(tokenID) || isValidCustodianToken(tokenID)) {
                    continue;
                }
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.warning("Could not parse token file for: " + tokenID + " on tile: " + tile.getAutoCompleteName());
                    continue;
                }
                BufferedImage tokenImage = ImageHelper.read(tokenPath);
                if (tokenImage == null)
                    continue;

                if (tokenPath.contains(Constants.WORLD_DESTROYED) ||
                    tokenPath.contains(Constants.CONSULATE_TOKEN) ||
                    tokenPath.contains(Constants.GLEDGE_CORE) || tokenPath.contains("freepeople")) {
                    tileGraphics.drawImage(tokenImage,
                        TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                        TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
                } else if (tokenPath.contains(Constants.DMZ_LARGE)) {
                    float scale = 0.3f;
                    List<String> smallLegendaries = List.of("mirage", "mallice", "mallicelocked", "eko", "domna");
                    if (Mapper.getPlanet(unitHolder.getName()).getLegendaryAbilityText() != null
                        && !smallLegendaries.contains(unitHolder.getName().toLowerCase())) {
                        scale = 0.53f;
                    }
                    if (unitHolder.getName().equalsIgnoreCase("elysium")) {
                        scale = 0.50f;
                    }
                    if (Constants.MECATOLS.contains(unitHolder.getName())) {
                        scale = 0.61f;
                    }
                    tokenImage = ImageHelper.readScaled(tokenPath, scale);
                    tileGraphics.drawImage(tokenImage,
                        TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                        TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2) + 10, null);
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + 70, TILE_PADDING + 45, null);
                } else if (tokenPath.contains("custodiavigilia")) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + 140, TILE_PADDING + 185, null);
                } else {
                    Point position = unitTokenPosition.getPosition(tokenID);
                    boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
                    if (isMirage) {
                        if (tile.getPlanetUnitHolders().size() == 3 + 1) {
                            position.x += Constants.MIRAGE_TRIPLE_POSITION.x;
                            position.y += Constants.MIRAGE_TRIPLE_POSITION.y;
                        } else if (position == null) {
                            position = Constants.MIRAGE_POSITION;
                        } else {
                            position.x += Constants.MIRAGE_POSITION.x;
                            position.y += Constants.MIRAGE_POSITION.y;
                        }
                    }
                    if (position != null) {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y, null);
                        rectangles.add(new Rectangle(TILE_PADDING + position.x, TILE_PADDING + position.y,
                            tokenImage.getWidth(), tokenImage.getHeight()));
                    } else {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x + xDelta,
                            TILE_PADDING + centerPosition.y, null);
                        rectangles.add(new Rectangle(TILE_PADDING + centerPosition.x + xDelta,
                            TILE_PADDING + centerPosition.y, tokenImage.getWidth(), tokenImage.getHeight()));
                        xDelta += 10;
                    }
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, tokenList);
        }
    }

    private static boolean shouldPlanetHaveShield(UnitHolder unitHolder, Game game) {
        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(Constants.WORLD_DESTROYED))) {
            return false;
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
                if (!player.unitBelongsToPlayer(unitKey))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) {
                    continue;
                }
                if (unitModel.getPlanetaryShield()) {
                    return !unitModel.getBaseType().equalsIgnoreCase("mech") || !ButtonHelper.isLawInPlay(game, "articles_war");
                }
            }
        }
        return false;
    }

    private static boolean isValidToken(String tokenID) {
        return tokenID.contains(Constants.SLEEPER) ||
            tokenID.contains(Constants.DMZ_LARGE) ||
            tokenID.contains(Constants.WORLD_DESTROYED) ||
            tokenID.contains(Constants.GLEDGE_CORE) ||
            tokenID.contains(Constants.CUSTODIAN_TOKEN) ||
            tokenID.contains(Constants.CONSULATE_TOKEN);
    }

    private static boolean isValidCustodianToken(String tokenID) {
        return tokenID.contains(Constants.CUSTODIAN_TOKEN);
    }

    private static void oldFormatPlanetTokenAdd(
        Tile tile,
        Graphics tileGraphics,
        UnitHolder unitHolder,
        List<String> tokenList
    ) {
        int deltaY = 0;
        int offSet = 0;
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = centerPosition.x;
        int y = centerPosition.y - (tokenList.size() > 1 ? 35 : 0);
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                BotLogger.warning("Could not parse token file for: " + tokenID);
                continue;
            }
            BufferedImage image = ImageHelper.readScaled(tokenPath, 0.85f);
            if (image == null)
                continue;
            tileGraphics.drawImage(image, TILE_PADDING + x - (image.getWidth() / 2),
                TILE_PADDING + y + offSet + deltaY - (image.getHeight() / 2), null);
            y += image.getHeight();
        }
    }

    private static void addToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Game game) {
        Set<String> tokenList = unitHolder.getTokenList();
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = 0;
        int y = 0;
        int deltaX = 80;
        int deltaY = 0;

        float mirageDragRatio = 2.0f / 3;
        int mirageDragX = Math.round(((float) 345 / 8 + TILE_PADDING) * (1 - mirageDragRatio));
        int mirageDragY = Math.round(((float) (3 * 300) / 4 + TILE_PADDING) * (1 - mirageDragRatio));
        boolean hasMirage = tokenList.stream().anyMatch(tok -> tok.contains("mirage")) && (tile.getPlanetUnitHolders().size() != 3 + 1);
        List<Point> spaceTokenPositions = PositionMapper.getSpaceTokenPositions(tile.getTileID());
        if (spaceTokenPositions.isEmpty()) {
            x = centerPosition.x;
            y = centerPosition.y;
        }
        int index = 0;
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Could not parse token file for: " + tokenID);
                continue;
            }
            if (game.isCptiExploreMode() && tokenPath.toLowerCase().contains("token_frontier")) {
                tokenPath = tokenPath.replace("token_frontier", "token_frontier_cpti");
            }

            BufferedImage tokenImage = ImageHelper.read(tokenPath);
            if (tokenImage == null)
                return;

            if (tokenPath.contains(Constants.MIRAGE)) {
                if (tile.getPlanetUnitHolders().size() == 3 + 1) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + Constants.MIRAGE_TRIPLE_POSITION.x,
                        TILE_PADDING + Constants.MIRAGE_TRIPLE_POSITION.y, null);
                } else {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + Constants.MIRAGE_POSITION.x,
                        TILE_PADDING + Constants.MIRAGE_POSITION.y, null);
                }
            } else if (tokenPath.contains(Constants.SLEEPER)) {
                int sleeperX = TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2);
                int sleeperY = TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2);
                tileGraphics.drawImage(tokenImage, sleeperX, sleeperY, null);
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
                if (hasMirage) {
                    drawX += (tokenImage.getWidth() / 2);
                    drawY += (tokenImage.getHeight() / 2);
                    drawX = Math.round(mirageDragRatio * drawX) + mirageDragX;
                    drawY = Math.round(mirageDragRatio * drawY) + mirageDragY;
                    drawX -= (tokenImage.getWidth() / 2);
                    drawY -= (tokenImage.getHeight() / 2);
                }
                tileGraphics.drawImage(tokenImage, drawX, drawY, null);

                // add icons to wormholes for agendas
                boolean reconstruction = (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
                int offsetX = (tokenImage.getWidth() - 80) / 2;
                int offsetY = (tokenImage.getWidth() - 80) / 2;
                if ((ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban"))
                    && (tokenPath.toLowerCase().contains("alpha") || tokenPath.toLowerCase().contains("beta"))) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    tileGraphics.drawImage(blockedWormholeImage, drawX + offsetX + 40, drawY + offsetY + 40, null);
                }
                if (reconstruction && tokenPath.toLowerCase().contains("alpha")) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                    tileGraphics.drawImage(doubleWormholeImage, drawX + offsetX, drawY + offsetY, null);
                }
                if (reconstruction && tokenPath.toLowerCase().contains("beta")) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                    tileGraphics.drawImage(doubleWormholeImage, drawX + offsetX, drawY + offsetY, null);
                }
                if ((ButtonHelper.isLawInPlay(game, "nexus") || ButtonHelper.isLawInPlay(game, "absol_nexus"))
                    && (tile.getTileID().equals("82b"))
                    && !(ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban")) && (tokenPath.toLowerCase().contains("alpha") || tokenPath.toLowerCase().contains("beta"))) {
                    // avoid doubling up, which is important when using the transparent symbol
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    tileGraphics.drawImage(blockedWormholeImage, drawX + offsetX + 40, drawY + offsetY + 40, null);
                }
            }
        }
    }

    private static void drawOnWormhole(Tile tile, Graphics graphics, BufferedImage icon, int offset) {
        drawOnWormhole(tile, graphics, icon, offset, "ab");
    }

    private static void drawOnWormhole(Tile tile, Graphics graphics, BufferedImage icon, int offset, String types) {
        switch (tile.getTileID()) {
            case "82b": // wormhole nexus
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 95, TILE_PADDING + offset + 249, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 169, TILE_PADDING + offset + 273, null);
                break;
            case "c02": // Locke/Bentham
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 37, TILE_PADDING + offset + 158, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 223, TILE_PADDING + offset + 62, null);
                break;
            case "c10": // Kwon
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 182, TILE_PADDING + offset + 22, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 259, TILE_PADDING + offset + 241, null);
                break;
            case "c11": // Ethan
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 54, TILE_PADDING + offset + 138, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 159, TILE_PADDING + offset + 275, null);
                break;
            case "d119": // beta/nebula
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 94, TILE_PADDING + offset + 170, null);
                break;
            case "d123": // alpha/beta/supernova
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 22, TILE_PADDING + offset + 110, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 190, TILE_PADDING + offset + 206, null);
                break;
            case "er19": // alpha/beta/rift
            case "er119": // alpha/beta/nebula
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 60, TILE_PADDING + offset + 44, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 192, TILE_PADDING + offset + 184, null);
                break;
            case "er94": // Iynntani
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 157, TILE_PADDING + offset + 165, null);
                break;
            case "er95": // Kytos/Prymis
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 60, TILE_PADDING + offset + 155, null);
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 215, TILE_PADDING + offset + 61, null);
                break;
            case "m05": // Shanh
                if (types.contains("a")) graphics.drawImage(icon, TILE_PADDING + offset + 185, TILE_PADDING + offset + 180, null);
                break;
            case "m32": // Vespa/Apis
                if (types.contains("b")) graphics.drawImage(icon, TILE_PADDING + offset + 49, TILE_PADDING + offset + 147, null);
                break;
            default:
                Point wormholeLocation = TileHelper.getTileById(tile.getTileID()).getShipPositionsType().getWormholeLocation();
                if (wormholeLocation == null) {
                    graphics.drawImage(icon, TILE_PADDING + offset + 86, TILE_PADDING + 260, null);
                } else {
                    graphics.drawImage(icon, TILE_PADDING + offset + wormholeLocation.x, TILE_PADDING + offset + wormholeLocation.y, null);
                }
        }
    }
}
