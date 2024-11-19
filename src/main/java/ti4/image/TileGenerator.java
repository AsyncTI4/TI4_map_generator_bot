package ti4.image;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.commands2.CommandHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.model.BorderAnomalyModel;
import ti4.model.ShipPositionModel;
import ti4.model.UnitModel;
import ti4.service.fow.UserOverridenSlashCommandInteractionEvent;

public class TileGenerator {

    private static final int TILE_PADDING = 100;
    private static final Point TILE_POSITION_POINT = new Point(255, 295);
    private static final Point LABEL_POSITION_POINT = new Point(90, 295);
    private static final Point NUMBER_POSITION_POINT = new Point(40, 27);
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
    private final boolean allEyesOnMe;

    public TileGenerator(@NotNull Game game, @NotNull GenericInteractionCreateEvent event, DisplayType displayType) {
        this(game, event, displayType, 0, "000");
    }

    public TileGenerator(@NotNull Game game, @NotNull GenericInteractionCreateEvent event, @Nullable DisplayType displayType, int context, @NotNull String focusTile) {
        this.game = game;
        this.event = event;
        this.displayType = displayType;
        this.context = context;
        this.focusTile = focusTile;
        isFoWPrivate = isFowModeActive();
        fowPlayer = CommandHelper.getPlayerFromGame(game, event.getMember(), event.getUser().getId());
        allEyesOnMe = displayType != null && displayType.equals(DisplayType.googly);
    }

    private boolean isFowModeActive() {
        return game.isFowMode() && event != null &&
            (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL) ||
                event instanceof UserOverridenSlashCommandInteractionEvent);
    }

    public FileUpload createFileUpload() {
        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        Set<String> systemsInRange = getTilesToShow(game, context, focusTile);
        Set<String> keysToRemove = new HashSet<>(tilesToDisplay.keySet());
        keysToRemove.removeAll(systemsInRange);
        for (String tile_ : keysToRemove) {
            tilesToDisplay.remove(tile_);
        }

        // Resolve fog of war vision limitations
        if (game.isFowMode() && event != null && event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)) {
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
            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Tile));
            tilesWithExtra.forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Extras));
            tiles.stream().sorted().forEach(key -> addTile(graphics, tileMap.get(key), TileStep.Units));

            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            String timeStamp = getTimeStamp();
            graphics.drawString(game.getName() + " " + timeStamp, 0, 34);
        } catch (Exception e) {
            BotLogger.log(game.getName() + ": Could not save generated system info image");
        }

        FileUpload fileUpload = null;
        try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageHelper.writeCompressedFormat(mainImage, byteArrayOutputStream, "jpg", 1f);
            String imageName = game.getName() + "_" + getTimeStamp() + ".jpg";
            fileUpload = FileUpload.fromData(byteArrayOutputStream.toByteArray(), imageName);
        } catch (IOException e) {
            BotLogger.log("Failed to create FileUpload for tile.", e);
        }
        return fileUpload;
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
            BotLogger.log("Tile Error, when building map: " + tile.getTileID(), exception);
        }
    }

    public BufferedImage draw(Tile tile, TileStep step) {
        BufferedImage tileOutput = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics tileGraphics = tileOutput.createGraphics();
        var isSpiral = tile.getTileModel().getShipPositionsType() != null && tile.getTileModel().getShipPositionsType().isSpiral();
        switch (step) {
            case Setup -> {
            } // do nothing
            case Tile -> {
                BufferedImage image = ImageHelper.read(tile.getTilePath());
                tileGraphics.drawImage(image, TILE_PADDING, TILE_PADDING, null);

                // ADD ANOMALY BORDER IF HAS ANOMALY PRODUCING TOKENS OR UNITS
                ShipPositionModel.ShipPosition shipPositionsType = TileHelper.getTileById(tile.getTileID()).getShipPositionsType();
                if (tile.isAnomaly(game) && shipPositionsType != null) {
                    BufferedImage anomalyImage = ImageHelper.read(ResourceHelper.getInstance().getTileFile("tile_anomaly.png"));
                    switch (shipPositionsType.toString().toUpperCase()) {
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

                switch (game.getTextSize()) {
                    case "large" -> tileGraphics.setFont(Storage.getFont40());
                    case "medium" -> tileGraphics.setFont(Storage.getFont30());
                    case "tiny" -> tileGraphics.setFont(Storage.getFont12());
                    case null, default -> // "small"
                        tileGraphics.setFont(Storage.getFont20());
                }

                if (isFoWPrivate && tile.hasFog(fowPlayer)) {
                    BufferedImage frogOfWar = ImageHelper.read(tile.getFowTilePath(fowPlayer));
                    tileGraphics.drawImage(frogOfWar, TILE_PADDING, TILE_PADDING, null);
                    int labelX = TILE_PADDING + LABEL_POSITION_POINT.x;
                    int labelY = TILE_PADDING + LABEL_POSITION_POINT.y;
                    DrawingUtil.superDrawString(tileGraphics, tile.getFogLabel(fowPlayer), labelX, labelY, Color.WHITE, null, null, null, null);
                }

                int textX = TILE_PADDING + TILE_POSITION_POINT.x;
                int textY = TILE_PADDING + TILE_POSITION_POINT.y;
                DrawingUtil.superDrawString(tileGraphics, tile.getPosition(), textX, textY, Color.WHITE, MapGenerator.HorizontalAlign.Right, MapGenerator.VerticalAlign.Bottom, stroke7, Color.BLACK);

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
                    && !(ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban"))) // avoid doubling up, which is important when using the transparent symbol
                {
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
                UnitHolder spaceUnitHolder = unitHolders.stream()
                    .filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);

                if (spaceUnitHolder != null) {
                    addSleeperToken(tile, tileGraphics, spaceUnitHolder, TileGenerator::isValidCustodianToken, game);
                    addToken(tile, tileGraphics, spaceUnitHolder, game);
                    unitHolders.remove(spaceUnitHolder);
                    unitHolders.add(spaceUnitHolder);
                }
                int prodInSystem = 0;
                for (Player player : game.getRealPlayers()) {
                    prodInSystem = Math.max(prodInSystem, Helper.getProductionValue(player, game, tile, false));
                }
                for (UnitHolder unitHolder : unitHolders) {
                    addSleeperToken(tile, tileGraphics, unitHolder, TileGenerator::isValidToken, game);
                    addControl(tile, tileGraphics, unitHolder, rectangles);
                }
                if (prodInSystem > 0 && game.isShowGears() && !game.isFowMode()) {
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
                    addUnits(tile, tileGraphics, rectangles, degree, degreeChange, unitHolder, radius, fowPlayer);
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
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                    String batFile = ResourceHelper.getInstance().getGeneralFile("zobat" + (ThreadLocalRandom.current().nextInt(4096) == 0 ? "_shiny" : "") + ".png");
                    BufferedImage bufferedImage = ImageHelper.read(batFile);
                    x += (345 - bufferedImage.getWidth()) / 2;
                    y += (300 - bufferedImage.getHeight()) / 2;
                    tileGraphics.drawImage(bufferedImage, x, y, null);
                }
            }
            case Legendaries -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getRepresentation().contains("Hyperlane")) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;
                boolean isLegendary = ButtonHelper.isTileLegendary(tile, game) || tile.isMecatol();

                if (!isLegendary) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else if (tile.isMecatol()) {
                    String councilFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
                    BufferedImage bufferedImage = ImageHelper.readScaled(councilFile, 2.0f);
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
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                if (tile.getRepresentation().contains("Hyperlane")) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING;
                int y = TILE_PADDING;
                String tilePos = tile.getPosition();
                HashMap<Player, List<Integer>> pdsDice = new HashMap<>();

                for (Player player : game.getRealPlayers()) {
                    List<Integer> diceCount = new ArrayList<>();
                    List<Integer> diceCountMirveda = new ArrayList<>();
                    int mod = (game.playerHasLeaderUnlockedOrAlliance(player, "kolumecommander") ? 1 : 0);

                    if (player.hasAbility("starfall_gunnery")) {
                        for (int i = ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, game, tile); i > 0; i--) {
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
                        pdsDice.put(player, diceCount);
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

                    x += (345 - 73 * scale) / 2;
                    y += (300 - pdsDice.size() * 48 * scale) / 2;
                    for (Player player : pdsDice.keySet()) {
                        int numberOfDice = pdsDice.get(player).size();
                        boolean rerolls = game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander");
                        float expectedHits;
                        if (rerolls) {
                            expectedHits = (100.0f * numberOfDice - pdsDice.get(player).stream().mapToInt(value -> (value - 1) * (value - 1)).sum()) / 100;
                        } else {
                            expectedHits = (11.0f * numberOfDice - pdsDice.get(player).stream().mapToInt(Integer::intValue).sum()) / 10;
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
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(player).subList(0, numberOfDice / 3).stream().map(Object::toString).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(player).subList(numberOfDice / 3, 2 * numberOfDice / 3).stream().map(Object::toString).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale + 36 * scale / 3), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(player).subList(2 * numberOfDice / 3, numberOfDice).stream().map(Object::toString).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale + 36 * scale * 2 / 3), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                        } else if (numberOfDice >= 3) {
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(player).subList(0, numberOfDice / 2).stream().map(Object::toString).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 12 * scale), Math.round(73 * scale / 2), Math.round(24 * scale / 2)),
                                smallFont);
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(player).subList(numberOfDice / 2, numberOfDice).stream().map(Object::toString).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 12 * scale + 24 * scale / 2), Math.round(73 * scale / 2), Math.round(24 * scale / 2)),
                                smallFont);
                        } else {
                            DrawingUtil.drawCenteredString(tileGraphics, pdsDice.get(player).stream().map(Object::toString).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), y, Math.round(73 * scale / 2), Math.round(48 * scale)),
                                smallFont);
                        }
                        y += 48 * scale;
                    }
                }
            }
            case Traits -> {
                if (game.isFowMode()) {
                    break;
                }
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                    if (traits.isEmpty()) {
                        traits.add(planet.getOriginalPlanetType());
                    }

                    if (tile.isMecatol()) {
                        traitFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
                    } else if (planet.getOriginalPlanetType().equals("faction")) {
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
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                    List<String> skips = planet.getTechSpeciality();
                    if (!skips.contains(planet.getOriginalTechSpeciality())) {
                        skips.add(planet.getOriginalTechSpeciality());
                    }
                    skips.removeAll(Collections.singleton(null));
                    skips.removeAll(Collections.singleton(""));
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
                if (tile.getRepresentation().contains("Hyperlane")) {
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
                    attachments.removeAll(Collections.singleton(null));
                    attachments.removeAll(Collections.singleton(""));
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
        }
        return tileOutput;
    }

    private static String getColorFilterForDistance(int distance) {
        return "Distance" + distance + ".png";
    }

    private static void addBorderDecoration(int direction, String secondaryTile, Graphics tileGraphics,
        BorderAnomalyModel.BorderAnomalyType decorationType) {
        Graphics2D tileGraphics2d = (Graphics2D) tileGraphics;

        if (decorationType == null) {
            return;
        }
        BufferedImage borderDecorationImage;
        try {
            BufferedImage cached = ImageHelper.read(decorationType.getImageFilePath());
            borderDecorationImage = new BufferedImage(cached.getColorModel(), cached.copyData(null), cached.isAlphaPremultiplied(), null);
        } catch (Exception e) {
            BotLogger.log("Could not find border decoration image! Decoration was " + decorationType);
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
                boolean convertToGeneric = isFoWPrivate
                    && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);

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
                BotLogger.log("Could not addCC", ignored);
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

                boolean convertToGeneric = isFoWPrivate
                    && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);

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
                    rectangles.add(
                        new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    if (player != null && player.isRealPlayer() && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                        BufferedImage exhaustedTokenImage = ImageHelper.readScaled(ResourceHelper.getResourceFromFolder("command_token/", "exhaustedControl.png"), scale);
                        DrawingUtil.drawControlToken(tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
                        rectangles.add(
                            new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    }

                } else {
                    int imgX = TILE_PADDING + centerPosition.x + xDelta;
                    int imgY = TILE_PADDING + centerPosition.y;
                    DrawingUtil.drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(
                        new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    if (player != null && player.isRealPlayer() && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                        BufferedImage exhaustedTokenImage = ImageHelper.readScaled(ResourceHelper.getResourceFromFolder("command_token/", "exhaustedControl"), scale);
                        DrawingUtil.drawControlToken(tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
                        rectangles.add(
                            new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    }
                    xDelta += 10;
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, controlList);
        }
    }

    private static void addSleeperToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder,
        Function<String, Boolean> isValid, Game game) {
        BufferedImage tokenImage;
        Point centerPosition = unitHolder.getHolderCenterPosition();
        if (unitHolder.getName().equalsIgnoreCase("mirage") && (tile.getPlanetUnitHolders().size() == 3 + 1)) {
            centerPosition = new Point(Constants.MIRAGE_TRIPLE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x,
                Constants.MIRAGE_TRIPLE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
        }
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.remove(null);
        tokenList.sort((o1, o2) -> {
            if ((o1.contains(Constants.SLEEPER) || o2.contains(Constants.SLEEPER))) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        if (game.isShowBubbles() && unitHolder instanceof Planet planetHolder && shouldPlanetHaveShield(unitHolder, game)) {
            String tokenPath = switch (planetHolder.getContrastColor()) {
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
            position = new Point(position.x, position.y + 10);
            tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y - 10, null);
        }
        boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
        for (String tokenID : tokenList) {
            if (isValid.apply(tokenID)) {
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.log("Could not find token file for: " + tokenID);
                    continue;
                }
                float scale = 0.85f;
                if (tokenPath.contains(Constants.DMZ_LARGE)) {
                    scale = 0.3f;
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
                } else if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                    scale = 1.32f;
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

    private static void addPlanetToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder,
        List<Rectangle> rectangles) {
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
                    BotLogger.log(
                        "Could not parse token file for: " + tokenID + " on tile: " + tile.getAutoCompleteName());
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
        if (unitHolder.getTokenList().contains(Constants.WORLD_DESTROYED_PNG)) {
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

    private static void oldFormatPlanetTokenAdd(Tile tile, Graphics tileGraphics, UnitHolder unitHolder,
        List<String> tokenList) {
        int deltaY = 0;
        int offSet = 0;
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = centerPosition.x;
        int y = centerPosition.y - (tokenList.size() > 1 ? 35 : 0);
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                BotLogger.log("Could not parse token file for: " + tokenID);
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
                BotLogger.log("Could not parse token file for: " + tokenID);
                continue;
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
                tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                    TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
            } else {

                int drawX = TILE_PADDING + x;
                if (tokenPath.contains("mustache")) {
                    drawX = drawX - 120;
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
                    && !(ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban")) // avoid doubling up, which is important when using the transparent symbol
                    && (tokenPath.toLowerCase().contains("alpha") || tokenPath.toLowerCase().contains("beta"))) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    tileGraphics.drawImage(blockedWormholeImage, drawX + offsetX + 40, drawY + offsetY + 40, null);
                }
            }
        }
    }

    private String getUnitPath(UnitKey unit) {
        return allEyesOnMe ? ResourceHelper.getInstance().getUnitFile(unit, allEyesOnMe) : ResourceHelper.getInstance().getUnitFile(unit);
    }

    private void addUnits(Tile tile, Graphics tileGraphics, List<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius, Player fowPlayer) {
        BufferedImage unitImage;
        Map<Units.UnitKey, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        Map<Units.UnitKey, Integer> units = new LinkedHashMap<>();
        HashMap<String, Point> unitOffset = new HashMap<>();
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);
        if (isSpace && displayType == DisplayType.shipless) {
            return;
        }

        float mirageDragRatio = 2.0f / 3;
        int mirageDragX = Math.round(((float) 345 / 8 + TILE_PADDING) * (1 - mirageDragRatio));
        int mirageDragY = Math.round(((float) (3 * 300) / 4 + TILE_PADDING) * (1 - mirageDragRatio));
        boolean hasMirage = false;
        if (isSpace) {
            Set<String> tokenList = unitHolder.getTokenList();
            hasMirage = tokenList.stream().anyMatch(tok -> tok.contains("mirage"))
                && (tile.getPlanetUnitHolders().size() != 3 + 1);
        }

        boolean isCabalJail = "s11".equals(tile.getTileID());
        boolean isNekroJail = "s12".equals(tile.getTileID());
        boolean isYssarilJail = "s13".equals(tile.getTileID());

        boolean isJail = isCabalJail || isNekroJail || isYssarilJail;
        boolean showJail = fowPlayer == null
            || (isCabalJail && FoWHelper.canSeeStatsOfFaction(game, "cabal", fowPlayer))
            || (isNekroJail && FoWHelper.canSeeStatsOfFaction(game, "nekro", fowPlayer))
            || (isYssarilJail && FoWHelper.canSeeStatsOfFaction(game, "yssaril", fowPlayer));

        Point unitOffsetValue = game.isAllianceMode() ? PositionMapper.getAllianceUnitOffset()
            : PositionMapper.getUnitOffset();
        int spaceX = unitOffsetValue != null ? unitOffsetValue.x : 10;
        int spaceY = unitOffsetValue != null ? unitOffsetValue.y : -7;
        for (Map.Entry<Units.UnitKey, Integer> entry : tempUnits.entrySet()) {
            Units.UnitKey id = entry.getKey();
            if (id != null && id.getUnitType() == Units.UnitType.Mech) {
                units.put(id, entry.getValue());
            }
        }
        for (Units.UnitKey key : units.keySet()) {
            tempUnits.remove(key);
        }
        units.putAll(tempUnits);
        Map<Units.UnitKey, Integer> unitDamage = unitHolder.getUnitDamage();
        // float scaleOfUnit = 1.0f;
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            unitTokenPosition = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
        }
        BufferedImage dmgImage = ImageHelper.readScaled(Helper.getDamagePath(), 0.8f);

        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
        int multInf = 2;
        int multFF = 2;
        for (Map.Entry<Units.UnitKey, Integer> unitEntry : units.entrySet()) {
            Units.UnitKey unitKey = unitEntry.getKey();
            if (unitKey != null && !Mapper.isValidColor(unitKey.getColor())) {
                continue;
            }
            Integer unitCount = unitEntry.getValue();

            if (isJail && fowPlayer != null) {
                String colorID = Mapper.getColorID(fowPlayer.getColor());
                if (!showJail && !unitKey.getColorID().equals(colorID)) {
                    continue;
                }
            }

            Integer unitDamageCount = unitDamage.get(unitKey);

            Integer bulkUnitCount = null;
            Color groupUnitColor = switch (Mapper.getColor(unitKey.getColorID()).getTextColor()) {
                case "black" -> Color.BLACK;
                default -> Color.WHITE;
            };

            try {
                String unitPath = getUnitPath(unitKey);
                if (unitPath != null) {
                    if (unitKey.getUnitType() == Units.UnitType.Fighter) {
                        unitPath = unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
                        bulkUnitCount = unitCount;
                    } else if (unitKey.getUnitType() == Units.UnitType.Infantry) {
                        unitPath = unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
                        bulkUnitCount = unitCount;
                    }
                }
                if (game.getPlayerByColorID(unitKey.getColorID()).orElse(null) != null) {
                    Player p = game.getPlayerByColorID(unitKey.getColorID()).get();
                    if (unitKey.getUnitType() == Units.UnitType.Spacedock && p.ownsUnitSubstring("cabal_spacedock")) {
                        unitPath = unitPath.replace("sd", "csd");
                    }
                    if (unitKey.getUnitType() == Units.UnitType.Lady) {
                        unitPath = unitPath.replace("lady", "fs");
                    }
                    if (unitKey.getUnitType() == Units.UnitType.Cavalry) {
                        String name = "Memoria_1.png";
                        if (game.getPNOwner("cavalry") != null && game.getPNOwner("cavalry").hasTech("m2")) {
                            name = "Memoria_2.png";
                        }
                        unitPath = ResourceHelper.getInstance().getUnitFile(name);
                    }
                }

                unitImage = ImageHelper.read(unitPath);
                if (bulkUnitCount != null && bulkUnitCount > 9) {
                    unitImage = ImageHelper.readScaled(unitPath, 1.2f);
                }
            } catch (Exception e) {
                BotLogger.log("Could not parse unit file for: " + unitKey + " in game " + game.getName(), e);
                continue;
            }
            if (unitImage == null)
                continue;

            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;

            }

            BufferedImage spoopy = null;
            if ((unitKey.getUnitType() == Units.UnitType.Warsun) && (ThreadLocalRandom.current().nextInt(1000) == 0)) {
                String spoopyPath = ResourceHelper.getInstance().getSpoopyFile();
                spoopy = ImageHelper.read(spoopyPath);
            }

            if (unitKey.getUnitType() == Units.UnitType.Lady) {
                String name = "units_ds_ghemina_lady_wht.png";
                String spoopyPath = ResourceHelper.getInstance().getDecalFile(name);
                spoopy = ImageHelper.read(spoopyPath);
            }
            Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
            if (unitKey.getUnitType() == Units.UnitType.Flagship && player.ownsUnit("ghemina_flagship_lord")) {
                String name = "units_ds_ghemina_lord_wht.png";
                String spoopyPath = ResourceHelper.getInstance().getDecalFile(name);
                spoopy = ImageHelper.read(spoopyPath);
            }
            Point centerPosition = unitHolder.getHolderCenterPosition();
            // DRAW UNITS
            for (int i = 0; i < unitCount; i++) {
                String id = unitKey.asyncID();
                boolean fighterOrInfantry = Set.of(Units.UnitType.Infantry, Units.UnitType.Fighter).contains(unitKey.getUnitType());
                Point position = unitTokenPosition.getPosition(fighterOrInfantry ? "tkn_" + id : id);
                if (isSpace && position != null && !fighterOrInfantry) {
                    Point point = unitOffset.get(id);
                    if (point == null) {
                        point = new Point(0, 0);
                    }
                    position.x = position.x + point.x;
                    position.y = position.y + point.y;
                    point.x += spaceX;
                    point.y += spaceY;
                    unitOffset.put(id, point);
                }
                boolean searchPosition = true;
                int x = 0;
                int y = 0;
                int mult = 0;
                if (fighterOrInfantry && isSpace) {
                    if (unitKey.getUnitType() == Units.UnitType.Infantry) {
                        multInf--;
                        mult = multInf;
                    } else {
                        multFF--;
                        mult = multFF;
                    }
                    if (mult < 0) {
                        UnitTokenPosition unitTokenPosition2 = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
                        int x2 = (int) centerPosition.getX() - 19;
                        int y2 = (int) centerPosition.getY() - 15;
                        if (unitTokenPosition2 != null) {
                            Point position2 = unitTokenPosition2.getPosition(fighterOrInfantry ? "tkn_" + id : id);
                            x2 = (int) position2.getX();
                            y2 = (int) position2.getY();
                        }
                        position = new Point(x2 + 30 * (mult - 1), y2);
                    }
                }
                if (unitKey.getUnitType() == Units.UnitType.Infantry && position == null) {
                    UnitTokenPosition unitTokenPosition2 = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
                    if (unitTokenPosition2 == null) {
                        unitTokenPosition2 = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
                    }
                    int x2 = (int) centerPosition.getX() - 19;
                    int y2 = (int) centerPosition.getY() - 15;
                    if (unitTokenPosition2 != null) {
                        Point position2 = unitTokenPosition2.getPosition(fighterOrInfantry ? "tkn_" + id : id);
                        x2 = (int) position2.getX();
                        y2 = (int) position2.getY();
                    }
                    position = new Point(x2 - 33 * multInf, y2);
                    multInf = multInf + 1;
                }
                while (searchPosition && position == null) {
                    x = (int) (radius * Math.sin(degree));
                    y = (int) (radius * Math.cos(degree));
                    int possibleX = centerPosition.x + x - (unitImage.getWidth() / 2);
                    int possibleY = centerPosition.y + y - (unitImage.getHeight() / 2);
                    BufferedImage finalImage = unitImage;
                    if (rectangles.stream().noneMatch(rectangle -> rectangle.intersects(possibleX, possibleY,
                        finalImage.getWidth(), finalImage.getHeight()))) {
                        searchPosition = false;
                    } else if (degree > 360) {
                        searchPosition = false;
                        degree += 3;// To change degree if we did not find place, might be better placement then
                    }
                    degree += degreeChange;
                    if (!searchPosition) {
                        rectangles.add(
                            new Rectangle(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()));
                    }
                }

                int xOriginal = centerPosition.x + x;
                int yOriginal = centerPosition.y + y;
                int imageX = position != null ? position.x : xOriginal - (unitImage.getWidth() / 2);
                imageX += TILE_PADDING;
                int imageY = position != null ? position.y : yOriginal - (unitImage.getHeight() / 2);
                imageY += TILE_PADDING;
                if (isMirage) {
                    if (tile.getPlanetUnitHolders().size() == 3 + 1) {
                        imageX += Constants.MIRAGE_TRIPLE_POSITION.x;
                        imageY += Constants.MIRAGE_TRIPLE_POSITION.y;
                    } else {
                        imageX += Constants.MIRAGE_POSITION.x;
                        imageY += Constants.MIRAGE_POSITION.y;
                    }
                } else if (hasMirage) {
                    imageX += (unitImage.getWidth() / 2);
                    imageY += (unitImage.getHeight() / 2);
                    imageX = Math.round(mirageDragRatio * imageX) + mirageDragX + (fighterOrInfantry ? 60 : 0);
                    imageY = Math.round(mirageDragRatio * imageY) + mirageDragY;
                    imageX -= (unitImage.getWidth() / 2);
                    imageY -= (unitImage.getHeight() / 2);
                }

                tileGraphics.drawImage(unitImage, imageX, imageY, null);
                if (unitKey.getUnitType() == Units.UnitType.Mech && (ButtonHelper.isLawInPlay(game, "articles_war") || ButtonHelper.isLawInPlay(game, "absol_articleswar"))) {
                    BufferedImage mechTearImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_articles_of_war" + DrawingUtil.getBlackWhiteFileSuffix(unitKey.getColorID())));
                    tileGraphics.drawImage(mechTearImage, imageX, imageY, null);
                } else if (unitKey.getUnitType() == Units.UnitType.Warsun && ButtonHelper.isLawInPlay(game, "schematics")) {
                    BufferedImage wsCrackImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_publicize_weapon_schematics" + DrawingUtil.getBlackWhiteFileSuffix(unitKey.getColorID())));
                    tileGraphics.drawImage(wsCrackImage, imageX, imageY, null);
                }

                Optional<BufferedImage> decal = Optional.ofNullable(player)
                    .map(p -> p.getDecalFile(unitKey.asyncID()))
                    .map(decalFileName -> ImageHelper.read(ResourceHelper.getInstance().getDecalFile(decalFileName)));

                if (decal.isPresent() && !List.of(Units.UnitType.Fighter, Units.UnitType.Infantry).contains(unitKey.getUnitType())) {
                    tileGraphics.drawImage(decal.get(), imageX, imageY, null);
                }
                if (spoopy != null) {
                    tileGraphics.drawImage(spoopy, imageX, imageY, null);
                }

                // UNIT TAGS
                if (i == 0 && !(Units.UnitType.Infantry.equals(unitKey.getUnitType())) && game.isShowUnitTags()) { // DRAW TAG
                    UnitModel unitModel = game.getUnitFromUnitKey(unitKey);
                    if (player != null && unitModel != null && unitModel.getIsShip()) {
                        // TODO: Only paint the tag of the most expensive ship per player, or if no
                        // ships, the "bottom most" unit on a planet
                        String factionTag = player.getFactionModel().getShortTag();
                        BufferedImage plaquette = ImageHelper
                            .read(ResourceHelper.getInstance().getUnitFile("unittags_plaquette.png"));
                        Point plaquetteOffset = getUnitTagLocation(id);

                        tileGraphics.drawImage(plaquette, imageX + plaquetteOffset.x,
                            imageY + plaquetteOffset.y, null);
                        DrawingUtil.drawPlayerFactionIconImage(tileGraphics, player, imageX + plaquetteOffset.x,
                            imageY + plaquetteOffset.y, 32, 32);

                        tileGraphics.setColor(Color.WHITE);
                        DrawingUtil.drawCenteredString(tileGraphics, factionTag,
                            new Rectangle(imageX + plaquetteOffset.x + 25,
                                imageY + plaquetteOffset.y + 17, 40, 13),
                            Storage.getFont13());
                    }
                }
                if (bulkUnitCount != null) {
                    tileGraphics.setFont(Storage.getFont24());
                    tileGraphics.setColor(groupUnitColor);

                    int scaledNumberPositionX = NUMBER_POSITION_POINT.x;
                    int scaledNumberPositionY = NUMBER_POSITION_POINT.y;
                    if (bulkUnitCount > 9) {
                        tileGraphics.setFont(Storage.getFont28());
                        scaledNumberPositionX = scaledNumberPositionX + 5;
                        scaledNumberPositionY = scaledNumberPositionY + 5;
                    }
                    tileGraphics.drawString(Integer.toString(bulkUnitCount),
                        imageX + scaledNumberPositionX,
                        imageY + scaledNumberPositionY);
                }

                if (unitDamageCount != null && unitDamageCount > 0 && dmgImage != null) {
                    if (isSpace && position != null) {
                        position.x = position.x - 7;
                    }
                    int imageDmgX = position != null
                        ? position.x + (unitImage.getWidth() / 2) - (dmgImage.getWidth() / 2)
                        : xOriginal - (dmgImage.getWidth() / 2);
                    int imageDmgY = position != null
                        ? position.y + (unitImage.getHeight() / 2) - (dmgImage.getHeight() / 2)
                        : yOriginal - (dmgImage.getHeight() / 2);
                    if (isMirage) {
                        imageDmgX = imageX - TILE_PADDING;
                        imageDmgY = imageY - TILE_PADDING;
                    } else if (unitKey.getUnitType() == Units.UnitType.Mech) {
                        imageDmgX = position != null ? position.x : xOriginal - (dmgImage.getWidth());
                        imageDmgY = position != null ? position.y : yOriginal - (dmgImage.getHeight());

                    }
                    tileGraphics.drawImage(dmgImage, TILE_PADDING + imageDmgX, TILE_PADDING + imageDmgY, null);
                    unitDamageCount--;
                }
            }
        }
    }

    private static Point getUnitTagLocation(String unitID) {
        return switch (unitID) {
            case "ws" -> new Point(-10, 45); // War Sun
            case "fs", "lord", "lady", "tyrantslament", "cavalry" -> new Point(10, 55); // Flagship
            case "dn" -> new Point(10, 50); // Dreadnought
            case "ca" -> new Point(0, 40); // Cruiser
            case "cv" -> new Point(0, 40); // Carrier
            case "gf", "ff" -> new Point(-15, 12); // Infantry/Fighter
            case "dd" -> new Point(-10, 30); // Destroyer
            case "mf" -> new Point(-10, 20); // Mech
            case "pd" -> new Point(-10, 20); // PDS
            case "sd", "csd", "plenaryorbital" -> new Point(-10, 20); // Space Dock
            default -> new Point(0, 0);
        };
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
