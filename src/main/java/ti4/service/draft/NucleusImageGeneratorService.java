package ti4.service.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DisplayType;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileGenerator;
import ti4.image.TileStep;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.logging.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.image.FileUploadService;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class NucleusImageGeneratorService {

    private static final int RING_MAX_COUNT = 8;
    private static final int RING_MIN_COUNT = 3;
    private static final int TILE_PADDING = 100;
    private static final int EXTRA_X = 0; // padding at left/right of map
    private static final int EXTRA_Y = 0; // padding at top/bottom of map
    private static final int SPACE_FOR_TILE_HEIGHT = 300; // space to calculate tile image height with
    private static final BasicStroke innerStroke = new BasicStroke(4.0f);
    private static final BasicStroke outlineStroke = new BasicStroke(9.0f);

    public FileUpload tryGenerateImage(DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {

        Game game = draftManager.getGame();
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (!mapTemplate.isNucleusTemplate() || draftManager.getDraftable(SeatDraftable.TYPE) == null) {
            return null;
        }

        if (restrictChoiceKeys != null) {
            // This COULD absolutely support limited choice keys. Any tile not present in the restricted set
            // could just be replaced with gray setup tiles. You'd skip printing any additional info about those slices.
            // If restrictChoiceKeys is set, you probably also just render the default map template preview tiles for
            // all non-Nucleus tiles, to avoid leaking info about other draftables.
            throw new IllegalArgumentException("Nucleus image generation does not support incomplete information.");
        }

        BufferedImage nucleusImage = generateImage(draftManager);
        if (nucleusImage == null) {
            return null;
        }
        FileUpload fileUpload = FileUploadService.createFileUpload(nucleusImage, uniqueKey);

        return fileUpload;
    }

    private BufferedImage generateImage(DraftManager draftManager) {
        int width = getMapWidth(draftManager.getGame());
        int height = getMapHeight(draftManager.getGame());
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        Game game = draftManager.getGame();
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);

        Map<String, Tile> tileMap = getRelevantTiles(draftManager);
        Map<String, MiltyDraftSlice> seatPosToNucleusTiles = getSeatToNucleusTiles(mapTemplate, tileMap);
        Map<String, String> seatPosToUserName = getSeatToPlayer(mapTemplate, draftManager);

        Map<String, Point> tileImagePoints = new HashMap<>();

        // Add all on-board game tiles to the image
        for (Tile tile : tileMap.values()) {
            try {
                Point tilePos = getTilePosition(game, tile.getPosition());
                TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);
                BufferedImage tileImage = tileGenerator.draw(tile, TileStep.Nucleus);
                graphicsMain.drawImage(tileImage, tilePos.x, tilePos.y, null);
                tileImagePoints.put(tile.getPosition(), tilePos);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile IMG " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (Tile tile : tileMap.values()) {
            try {
                Point tilePos = tileImagePoints.get(tile.getPosition());
                if (seatPosToNucleusTiles.containsKey(tile.getPosition())) {
                    MiltyDraftSlice pseudoSlice = seatPosToNucleusTiles.get(tile.getPosition());
                    drawSliceStats(graphicsMain, tilePos, game, pseudoSlice);
                }
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile STATS " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (Tile tile : tileMap.values()) {
            try {
                Point tilePos = tileImagePoints.get(tile.getPosition());
                if (seatPosToUserName.containsKey(tile.getPosition())) {
                    drawPlayerInfo(graphicsMain, tilePos, seatPosToUserName.get(tile.getPosition()));
                }
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile PLAYER " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }

        return mainImage;
    }

    private Map<String, String> getSeatToPlayer(MapTemplateModel mapTemplateModel, DraftManager draftManager) {
        Map<String, String> seatNumToPlayerInfo = new HashMap<>();

        for (Map.Entry<String, PlayerDraftState> playerState :
                draftManager.getPlayerStates().entrySet()) {
            List<DraftChoice> seatChoices = playerState.getValue().getPicks().get(SeatDraftable.TYPE);
            if (seatChoices == null || seatChoices.isEmpty()) {
                continue;
            }
            DraftChoice seatChoice = seatChoices.get(0);
            Integer seatNum = Integer.parseInt(seatChoice.getChoiceKey().substring("seat".length()));
            MapTemplateTile homeTile = mapTemplateModel.getTemplateTiles().stream()
                    .filter(t -> t.getHome() != null
                            && t.getHome()
                            && t.getPlayerNumber() != null
                            && t.getPlayerNumber() == seatNum)
                    .findFirst()
                    .orElse(null);
            if (homeTile == null) {
                continue;
            }
            String playerUserName =
                    draftManager.getGame().getPlayer(playerState.getKey()).getUserName();
            seatNumToPlayerInfo.put(homeTile.getPos(), playerUserName);
        }

        return seatNumToPlayerInfo;
    }

    private void drawSliceStats(Graphics graphics, Point positionPoint, Game game, MiltyDraftSlice pseudoSlice) {
        if (pseudoSlice == null) {
            return;
        }

        Point base = new Point(positionPoint.x + TILE_PADDING, positionPoint.y + TILE_PADDING + 20);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(
                graphics,
                pseudoSlice.getName(),
                base.x + 172,
                base.y + 50,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);

        List<TI4Emoji> whs = new ArrayList<>();
        List<TI4Emoji> legendary = new ArrayList<>();
        List<TI4Emoji> blueSkips = new ArrayList<>();
        List<TI4Emoji> greenSkips = new ArrayList<>();
        List<TI4Emoji> yellowSkips = new ArrayList<>();
        List<TI4Emoji> redSkips = new ArrayList<>();
        for (MiltyDraftTile tile : pseudoSlice.getTiles()) {
            if (tile.isHasAlphaWH()) whs.add(MiscEmojis.WHalpha);
            if (tile.isHasBetaWH()) whs.add(MiscEmojis.WHbeta);
            if (tile.isHasOtherWH()) whs.add(MiscEmojis.WHgamma);
            if (tile.isLegendary()) legendary.add(MiscEmojis.LegendaryPlanet);

            for (UnitHolder uh : tile.getTile().getPlanetUnitHolders()) {
                if (uh instanceof Planet p) {
                    for (String spec : p.getTechSpecialities()) {
                        switch (spec) {
                            case "propulsion" -> blueSkips.add(TechEmojis.PropulsionTech);
                            case "biotic" -> greenSkips.add(TechEmojis.BioticTech);
                            case "cybernetic" -> yellowSkips.add(TechEmojis.CyberneticTech);
                            case "warfare" -> redSkips.add(TechEmojis.WarfareTech);
                        }
                    }
                }
            }
        }
        List<TI4Emoji> featureEmojis = new ArrayList<>();
        featureEmojis.addAll(yellowSkips);
        featureEmojis.addAll(blueSkips);
        featureEmojis.addAll(greenSkips);
        featureEmojis.addAll(redSkips);
        featureEmojis.addAll(whs);
        featureEmojis.addAll(legendary);

        List<Point> featurePoints = Arrays.asList(
                new Point(83, 3), new Point(220, 3),
                new Point(60, 43), new Point(243, 43),
                new Point(37, 83), new Point(266, 83),
                new Point(14, 123), new Point(289, 123));

        int resources = pseudoSlice.getTotalRes();
        int influence = pseudoSlice.getTotalInf();
        String totalsString = resources + "/" + influence;

        int resourcesMilty = pseudoSlice.getOptimalRes();
        int influenceMilty = pseudoSlice.getOptimalInf();
        int flexMilty = pseudoSlice.getOptimalFlex();
        String optimalString = "(" + resourcesMilty + "/" + influenceMilty + "+" + flexMilty + ")";

        ((Graphics2D) graphics).setStroke(innerStroke);

        int index = 0;
        graphics.setColor(Color.black);
        for (TI4Emoji feature : featureEmojis) {
            Point fPoint = featurePoints.get(index);
            BufferedImage featureImage = getEmojiImage(feature);
            featureImage.getGraphics();
            graphics.setColor(Color.black);
            graphics.fillRoundRect(fPoint.x + base.x, fPoint.y + base.y, 40, 40, 40, 40);

            graphics.drawImage(featureImage, fPoint.x + base.x, fPoint.y + base.y, null);
            index++;
            if (index >= featurePoints.size()) break;
        }

        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont50());
        DrawingUtil.superDrawString(
                graphics,
                totalsString,
                base.x + 172,
                base.y + 110,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);
        DrawingUtil.superDrawString(
                graphics,
                optimalString,
                base.x + 172,
                base.y + 165,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);
    }

    private void drawPlayerInfo(Graphics graphics, Point positionPoint, String playerUserName) {
        if (playerUserName == null || playerUserName.isBlank()) {
            return;
        }

        Point base = new Point(positionPoint.x + TILE_PADDING, positionPoint.y + TILE_PADDING + 20);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont50());

        DrawingUtil.superDrawString(
                graphics,
                playerUserName,
                base.x + 172,
                base.y + 230,
                Color.red,
                hCenter,
                null,
                outlineStroke,
                Color.black);
    }

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

    private Map<String, MiltyDraftSlice> getSeatToNucleusTiles(
            MapTemplateModel mapTemplate, Map<String, Tile> gameTileMap) {
        Map<String, MiltyDraftSlice> seatToNucleusTiles = new HashMap<>();
        for (int i = 1; i <= mapTemplate.getNucleusSliceCount(); ++i) {
            int playerNum = i;
            MapTemplateTile sliceSeat = mapTemplate.getTemplateTiles().stream()
                    .filter(t -> t.getHome() != null
                            && t.getHome()
                            && t.getPlayerNumber() != null
                            && t.getPlayerNumber() == playerNum)
                    .findFirst()
                    .orElse(null);
            String sliceSeatPos = sliceSeat.getPos();
            List<MiltyDraftTile> nucleusSliceTiles = mapTemplate.getTemplateTiles().stream()
                    .filter(t -> {
                        List<Integer> nucleusNumbers = t.getNucleusNumbers();
                        return nucleusNumbers != null && nucleusNumbers.contains(playerNum);
                    })
                    .map(tile -> DraftTileManager.findTile(
                            gameTileMap.get(tile.getPos()).getTileID()))
                    .toList();
            MiltyDraftSlice pseudoSlice = new MiltyDraftSlice();
            pseudoSlice.setName("" + i);
            pseudoSlice.setTiles(new ArrayList<>(nucleusSliceTiles));

            seatToNucleusTiles.put(sliceSeatPos, pseudoSlice);
        }
        return seatToNucleusTiles;
    }

    /**
     * Gives the number of rings of the map
     *
     * @param game
     * @return between 3 and 8 (bounds based on constants)
     */
    private static int getRingCount(Game game) {
        return Math.max(Math.min(game.getRingCount(), RING_MAX_COUNT), RING_MIN_COUNT);
    }

    /**
     * Gives the height of the map part of the image
     *
     * @param game
     * @return space for the (number of rings + 1) + 2 * EXTRA_Y
     */
    private static int getMapHeight(Game game) {
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
    private static int getMapWidth(Game game) {
        float ringCount = getRingCount(game);
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1;
        int mapWidth = (int) (ringCount * 520 + EXTRA_X * 2);
        // mapWidth += hasExtraRow(game) ? EXTRA_X : 0;
        return mapWidth;
    }

    private static BufferedImage getEmojiImage(TI4Emoji emoji) {
        return ImageHelper.readEmojiImageScaled(emoji, 40);
    }
}
