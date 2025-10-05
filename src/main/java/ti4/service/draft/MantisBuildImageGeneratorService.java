package ti4.service.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DisplayType;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileGenerator;
import ti4.image.TileStep;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.logging.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.service.image.FileUploadService;

@UtilityClass
public class MantisBuildImageGeneratorService {

    private static final int RING_MAX_COUNT = 8;
    private static final int RING_MIN_COUNT = 3;
    private static final int TILE_PADDING = 100;
    private static final int EXTRA_X = 0; // padding at left/right of map
    private static final int EXTRA_Y = 0; // padding at top/bottom of map
    private static final int SPACE_FOR_TILE_HEIGHT = 300; // space to calculate tile image height with
    // private static final BasicStroke innerStroke = new BasicStroke(4.0f);
    private static final BasicStroke outlineStroke = new BasicStroke(9.0f);

    private static final String PENDING_TILE_POS = "br"; // bottom right corner

    /**
     * Draw a simple image of the current map, and if optionally a tile which has been drawn
     * for a player to place.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param uniqueKey A unique key to include in the file name.
     * @param currentPositions A list of tile positions to highlight on the map as available for the next tile.
     * @param pendingTileId A Tile ID to draw separately as pending placement by the current player.
     * @return A FileUpload containing the image, or null if the image could not be generated.
     */
    public FileUpload tryGenerateImage(
            DraftManager draftManager, String uniqueKey, List<String> currentPositions, String pendingTileId) {

        Game game = draftManager.getGame();
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (mapTemplate == null) {
            return null;
        }

        currentPositions = currentPositions == null ? List.of() : currentPositions;
        BufferedImage mapImage = generateImage(draftManager, mapTemplate, currentPositions, pendingTileId);
        if (mapImage == null) {
            return null;
        }
        FileUpload fileUpload = FileUploadService.createFileUpload(mapImage, uniqueKey);

        return fileUpload;
    }

    private BufferedImage generateImage(
            DraftManager draftManager,
            MapTemplateModel mapTemplate,
            List<String> currentPositions,
            String pendingTileId) {
        int width = getMapWidth(draftManager.getGame());
        int height = getMapHeight(draftManager.getGame());
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        Game game = draftManager.getGame();

        Map<String, Tile> tileMap = getRelevantTiles(draftManager);
        // Map<String, MiltyDraftSlice> seatPosToNucleusTiles = getSeatToNucleusTiles(mapTemplate, tileMap);
        // Map<String, String> seatPosToUserName = getSeatToPlayer(mapTemplate, draftManager);

        TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);
        Map<String, Point> tileImagePoints = new HashMap<>();

        // Add all on-board game tiles to the image
        for (Tile tile : tileMap.values()) {
            try {
                Point tilePos = getTilePosition(game, tile.getPosition());
                BufferedImage tileImage = tileGenerator.draw(tile, TileStep.Tile);
                graphicsMain.drawImage(tileImage, tilePos.x, tilePos.y, null);
                tileImagePoints.put(tile.getPosition(), tilePos);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile IMG " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (String tilePos : currentPositions) {
            try {
                Tile tile = tileMap.get(tilePos);
                if (tile == null) {
                    BotLogger.warning("MantisBuildImageGeneratorService: Could not find tile at position " + tilePos
                            + " to highlight.");
                    continue;
                }
                Point tilePoint = tileImagePoints.get(tile.getPosition());
                drawAvailability(graphicsMain, tilePoint, game);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile AVAILABILITY at " + tilePos, e);
                return null;
            }
        }
        if (pendingTileId != null) {
            try {
                Tile tile = tileMap.get(PENDING_TILE_POS);
                if (tile != null) {
                    BotLogger.warning(
                            "MantisBuildImageGeneratorService: There is already a tile at the pending tile position "
                                    + PENDING_TILE_POS + "; cannot draw pending tile.");
                    return mainImage;
                }
                tile = new Tile(pendingTileId, PENDING_TILE_POS);
                Point tilePoint = getTilePosition(game, PENDING_TILE_POS);
                drawPendingTile(graphicsMain, tilePoint, game, tile);

                // TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);
                // BufferedImage tileImage = tileGenerator.draw(tile, TileStep.Tile);
                // graphicsMain.drawImage(tileImage, tilePoint.x, tilePoint.y, null);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile OPTION " + pendingTileId + " at " + PENDING_TILE_POS, e);
                return null;
            }
        }

        // for (Tile tile : tileMap.values()) {
        //     try {
        //         Point tilePoint = tileImagePoints.get(tile.getPosition());
        //         if (seatPosToUserName.containsKey(tile.getPosition())) {
        //             drawPlayerInfo(graphicsMain, tilePoint, seatPosToUserName.get(tile.getPosition()));
        //         }
        //     } catch (Exception e) {
        //         BotLogger.error("Failed to draw tile PLAYER " + tile.getTileID() + " at " + tile.getPosition(), e);
        //         return null;
        //     }
        // }

        return mainImage;
    }

    // private Map<String, String> getSeatToPlayer(MapTemplateModel mapTemplateModel, DraftManager draftManager) {
    //     Map<String, String> seatNumToPlayerInfo = new HashMap<>();

    //     for (Map.Entry<String, PlayerDraftState> playerState :
    //             draftManager.getPlayerStates().entrySet()) {
    //         List<DraftChoice> seatChoices = playerState.getValue().getPicks().get(SeatDraftable.TYPE);
    //         if (seatChoices == null || seatChoices.isEmpty()) {
    //             continue;
    //         }
    //         DraftChoice seatChoice = seatChoices.get(0);
    //         Integer seatNum = SeatDraftable.getSeatNumberFromChoiceKey(seatChoice.getChoiceKey());
    //         MapTemplateTile homeTile = getSeatTileForPlayer(mapTemplateModel, seatNum);
    //         if (homeTile == null) {
    //             continue;
    //         }
    //         String playerUserName =
    //                 draftManager.getGame().getPlayer(playerState.getKey()).getUserName();
    //         seatNumToPlayerInfo.put(homeTile.getPos(), playerUserName);
    //     }

    //     return seatNumToPlayerInfo;
    // }

    private void drawAvailability(Graphics graphics, Point positionPoint, Game game) {
        Point base = new Point(positionPoint.x + TILE_PADDING, positionPoint.y + TILE_PADDING + 20);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(
                graphics, "PLACE?", base.x + 172, base.y + 150, Color.white, hCenter, null, outlineStroke, Color.black);

        // List<TI4Emoji> whs = new ArrayList<>();
        // List<TI4Emoji> legendary = new ArrayList<>();
        // List<TI4Emoji> blueSkips = new ArrayList<>();
        // List<TI4Emoji> greenSkips = new ArrayList<>();
        // List<TI4Emoji> yellowSkips = new ArrayList<>();
        // List<TI4Emoji> redSkips = new ArrayList<>();
        // for (MiltyDraftTile tile : pseudoSlice.getTiles()) {
        //     if (tile.isHasAlphaWH()) whs.add(MiscEmojis.WHalpha);
        //     if (tile.isHasBetaWH()) whs.add(MiscEmojis.WHbeta);
        //     if (tile.isHasOtherWH()) whs.add(MiscEmojis.WHgamma);
        //     if (tile.isLegendary()) legendary.add(MiscEmojis.LegendaryPlanet);

        //     for (UnitHolder uh : tile.getTile().getPlanetUnitHolders()) {
        //         if (uh instanceof Planet p) {
        //             for (String spec : p.getTechSpecialities()) {
        //                 switch (spec) {
        //                     case "propulsion" -> blueSkips.add(TechEmojis.PropulsionTech);
        //                     case "biotic" -> greenSkips.add(TechEmojis.BioticTech);
        //                     case "cybernetic" -> yellowSkips.add(TechEmojis.CyberneticTech);
        //                     case "warfare" -> redSkips.add(TechEmojis.WarfareTech);
        //                 }
        //             }
        //         }
        //     }
        // }
        // List<TI4Emoji> featureEmojis = new ArrayList<>();
        // featureEmojis.addAll(yellowSkips);
        // featureEmojis.addAll(blueSkips);
        // featureEmojis.addAll(greenSkips);
        // featureEmojis.addAll(redSkips);
        // featureEmojis.addAll(whs);
        // featureEmojis.addAll(legendary);

        // List<Point> featurePoints = Arrays.asList(
        //         new Point(83, 3), new Point(220, 3),
        //         new Point(60, 43), new Point(243, 43),
        //         new Point(37, 83), new Point(266, 83),
        //         new Point(14, 123), new Point(289, 123));

        // int resources = pseudoSlice.getTotalRes();
        // int influence = pseudoSlice.getTotalInf();
        // String totalsString = resources + "/" + influence;

        // int resourcesMilty = pseudoSlice.getOptimalRes();
        // int influenceMilty = pseudoSlice.getOptimalInf();
        // int flexMilty = pseudoSlice.getOptimalFlex();
        // String optimalString = "(" + resourcesMilty + "/" + influenceMilty + "+" + flexMilty + ")";

        // ((Graphics2D) graphics).setStroke(innerStroke);

        // int index = 0;
        // graphics.setColor(Color.black);
        // for (TI4Emoji feature : featureEmojis) {
        //     Point fPoint = featurePoints.get(index);
        //     BufferedImage featureImage = getEmojiImage(feature);
        //     featureImage.getGraphics();
        //     graphics.setColor(Color.black);
        //     graphics.fillRoundRect(fPoint.x + base.x, fPoint.y + base.y, 40, 40, 40, 40);

        //     graphics.drawImage(featureImage, fPoint.x + base.x, fPoint.y + base.y, null);
        //     index++;
        //     if (index >= featurePoints.size()) break;
        // }

        // graphics.setColor(Color.white);
        // graphics.setFont(Storage.getFont50());
        // DrawingUtil.superDrawString(
        //         graphics,
        //         totalsString,
        //         base.x + 172,
        //         base.y + 110,
        //         Color.white,
        //         hCenter,
        //         null,
        //         outlineStroke,
        //         Color.black);
        // DrawingUtil.superDrawString(
        //         graphics,
        //         optimalString,
        //         base.x + 172,
        //         base.y + 165,
        //         Color.white,
        //         hCenter,
        //         null,
        //         outlineStroke,
        //         Color.black);
    }

    private void drawPendingTile(Graphics graphics, Point positionPoint, Game game, Tile tile) {
        Point base = new Point(positionPoint.x + TILE_PADDING, positionPoint.y + TILE_PADDING + 20);

        Point tilePoint = getTilePosition(game, PENDING_TILE_POS);
        TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);
        BufferedImage tileImage = tileGenerator.draw(tile, TileStep.Tile);
        graphics.drawImage(tileImage, tilePoint.x, tilePoint.y, null);
        // TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(
                graphics,
                "CURRENT",
                base.x + 172,
                base.y - 150,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);
    }

    // private void drawPlayerInfo(Graphics graphics, Point positionPoint, String playerUserName) {
    //     if (playerUserName == null || playerUserName.isBlank()) {
    //         return;
    //     }

    //     Point base = new Point(positionPoint.x + TILE_PADDING, positionPoint.y + TILE_PADDING + 20);

    //     HorizontalAlign hCenter = HorizontalAlign.Center;
    //     graphics.setColor(Color.white);
    //     graphics.setFont(Storage.getFont50());

    //     DrawingUtil.superDrawString(
    //             graphics,
    //             playerUserName,
    //             base.x + 172,
    //             base.y + 230,
    //             Color.red,
    //             hCenter,
    //             null,
    //             outlineStroke,
    //             Color.black);
    // }

    private Point getTilePosition(Game game, String position) {
        Point positionPoint = PositionMapper.getTilePosition(position);
        if (positionPoint == null) {
            return null;
        }

        int x = positionPoint.x;
        int y = positionPoint.y;

        positionPoint = PositionMapper.getScaledTilePosition(game, position, x, y);
        int tileX = positionPoint.x + EXTRA_X - TILE_PADDING;
        int tileY = positionPoint.y + EXTRA_Y - TILE_PADDING;

        return new Point(tileX, tileY);
    }

    private Map<String, Tile> getRelevantTiles(DraftManager draftManager) {
        Game game = draftManager.getGame();
        Map<String, Tile> tileMap = new HashMap<>(game.getTileMap());
        tileMap.remove("tl");
        tileMap.remove("tr");
        tileMap.remove("bl");
        tileMap.remove("br");

        tileMap.entrySet().removeIf(e -> e.getValue() == null || e.getValue().getTileID() == null);
        tileMap.entrySet()
                .removeIf(e -> PositionMapper.getTilePosition(e.getValue().getPosition()) == null);

        return tileMap;
    }

    // private Map<String, MiltyDraftSlice> getSeatToNucleusTiles(
    //         MapTemplateModel mapTemplate, Map<String, Tile> gameTileMap) {
    //     Map<String, MiltyDraftSlice> seatToNucleusTiles = new HashMap<>();
    //     for (int i = 1; i <= mapTemplate.getNucleusSliceCount(); ++i) {
    //         int playerNum = i;

    //         MapTemplateTile sliceSeat = getSeatTileForPlayer(mapTemplate, playerNum);
    //         String sliceSeatPos = sliceSeat.getPos();

    //         Predicate<MapTemplateTile> nucleusSliceFilter =
    //                 t -> t.getNucleusNumbers() != null && t.getNucleusNumbers().contains(playerNum);
    //         List<MiltyDraftTile> nucleusSliceTiles = mapTemplate.getTemplateTiles().stream()
    //                 .filter(nucleusSliceFilter)
    //                 .map(MapTemplateTile::getPos)
    //                 .map(pos -> gameTileMap.get(pos).getTileID())
    //                 .map(DraftTileManager::findTile)
    //                 .toList();
    //         MiltyDraftSlice pseudoSlice = new MiltyDraftSlice();
    //         pseudoSlice.setName("" + i);
    //         pseudoSlice.setTiles(new ArrayList<>(nucleusSliceTiles));

    //         seatToNucleusTiles.put(sliceSeatPos, pseudoSlice);
    //     }
    //     return seatToNucleusTiles;
    // }

    // private MapTemplateTile getSeatTileForPlayer(MapTemplateModel mapTemplateModel, int playerNumber) {
    //     Predicate<MapTemplateTile> seatFilter = t -> t.getHome() != null
    //             && t.getHome()
    //             && t.getPlayerNumber() != null
    //             && t.getPlayerNumber() == playerNumber;
    //     return mapTemplateModel.getTemplateTiles().stream()
    //             .filter(seatFilter)
    //             .findFirst()
    //             .orElse(null);
    // }

    /**
     * Gives the number of rings of the map
     *
     * @param game
     * @return between 3 and 8 (bounds based on constants)
     */
    private int getRingCount(Game game) {
        return Math.max(Math.min(game.getRingCount(), RING_MAX_COUNT), RING_MIN_COUNT);
    }

    /**
     * Gives the height of the map part of the image
     *
     * @param game
     * @return space for the (number of rings + 1) + 2 * EXTRA_Y
     */
    private int getMapHeight(Game game) {
        return (getRingCount(game) + 1) * SPACE_FOR_TILE_HEIGHT * 2 + EXTRA_Y * 2;
    }

    /**
     * Gives the width of the map part of the image
     * TO DO:
     * - fix the "ringCount == minRingCount" ternary (see comment)
     * - some variables are never used...
     *
     * @param game
     * @return space for ring count + 2 * EXTRA_X + potential EXTRA_X
     */
    private int getMapWidth(Game game) {
        float ringCount = getRingCount(game);
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1;
        int mapWidth = (int) (ringCount * 520 + EXTRA_X * 2);
        // mapWidth += hasExtraRow(game) ? EXTRA_X : 0;
        return mapWidth;
    }

    // private BufferedImage getEmojiImage(TI4Emoji emoji) {
    //     return ImageHelper.readEmojiImageScaled(emoji, 40);
    // }
}
