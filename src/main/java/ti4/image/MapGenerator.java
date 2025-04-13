package ti4.image;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.commands.CommandHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.WebHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.BorderAnomalyHolder;
import ti4.model.ColorModel;
import ti4.model.EventModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetModel;
import ti4.model.StrategyCardModel;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.image.FileUploadService;
import ti4.settings.GlobalSettings;
import ti4.website.WebsiteOverlay;

public class MapGenerator implements AutoCloseable {

    private static final int RING_MAX_COUNT = 8;
    private static final int RING_MIN_COUNT = 3;
    private static final int PLAYER_STATS_HEIGHT = 650; // + 34 per teammate + 34 if line is long
    private static final int TILE_PADDING = 100;
    private static final int EXTRA_X = 300; // padding at left/right of map
    private static final int EXTRA_Y = 200; // padding at top/bottom of map
    private static final int SPACING_BETWEEN_OBJECTIVE_TYPES = 10;
    private static final int HORIZONTAL_TILE_SPACING = 260;
    private static final int VERTICAL_TILE_SPACING = 160;
    private static final int SPACE_FOR_TILE_HEIGHT = 300; // space to calculate tile image height with
    private static final int TILE_HEIGHT = 299; // typical height of a tile image
    private static final int SPACE_FOR_TILE_WIDTH = 350; // space to calculate tile image width with
    private static final int TILE_WIDTH = 345; // typical width of a tile image
    private static final int MINIMUM_WIDTH_OF_PLAYER_AREA = 1000;
    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke3 = new BasicStroke(3.0f);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke5 = new BasicStroke(5.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);

    private final Graphics graphics;
    private final BufferedImage mainImage;
    private byte[] mainImageBytes;
    private final GenericInteractionCreateEvent event;
    private final int scoreTokenSpacing;
    private final Game game;
    private final DisplayType displayType;
    private final DisplayType displayTypeBasic;
    private final boolean debug;
    private final int width;
    private final int height;
    private final int heightForGameInfo;

    private final List<WebsiteOverlay> websiteOverlays = new ArrayList<>();
    private final int mapWidth;
    private int minX = -1;
    private int minY = -1;
    private int maxX = -1;
    private int maxY = -1;
    private boolean isFoWPrivate;
    private Player fowPlayer;
    private StopWatch debugAbsoluteStartTime;
    private StopWatch debugTileTime;
    private StopWatch debugImageGraphicsTime;
    private StopWatch debugDrawTime;
    private StopWatch debugDiscordTime;
    private StopWatch debugWebsiteTime;

    MapGenerator(Game game, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event) {
        debug = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);
        if (debug) debugAbsoluteStartTime = StopWatch.createStarted();

        this.game = game;
        this.displayType = defaultIfNull(displayType);
        this.event = event;

        String controlID = Mapper.getControlID("red");
        BufferedImage bufferedImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), 0.45f);
        if (bufferedImage != null)
            scoreTokenSpacing = bufferedImage.getWidth() + 6;
        else
            scoreTokenSpacing = 30;

        int stage1PublicObjCount = game.getRevealedPublicObjectives().keySet().stream()
            .filter(Mapper.getPublicObjectivesStage1()::containsKey).toList().size();
        int stage2PublicObjCount = game.getRevealedPublicObjectives().keySet().stream()
            .filter(Mapper.getPublicObjectivesStage2()::containsKey).toList().size();
        int otherObjCount = game.getRevealedPublicObjectives().size() - stage1PublicObjCount - stage2PublicObjCount;
        stage1PublicObjCount = game.getPublicObjectives1Peakable().size() + stage1PublicObjCount;
        stage2PublicObjCount = game.getPublicObjectives2Peakable().size() + stage2PublicObjCount;

        int mostObjectivesInAColumn = Math.max(Math.max(stage1PublicObjCount, stage2PublicObjCount), otherObjCount);
        int heightOfObjectivesSection = Math.max((mostObjectivesInAColumn - 5) * 43, 0);

        int playerCountForMap = game.getRealPlayers().size() + game.getDummies().size();
        int heightOfPlayerAreasSection = getHeightOfPlayerAreasSection(game, playerCountForMap, heightOfObjectivesSection);

        int mapHeight = getMapHeight(game);
        mapWidth = Math.max(MINIMUM_WIDTH_OF_PLAYER_AREA, getMapWidth(game));
        switch (this.displayType) {
            case stats:
                heightForGameInfo = 40;
                height = heightOfPlayerAreasSection;
                displayTypeBasic = DisplayType.stats;
                width = mapWidth;
                break;
            case map:
            case wormholes:
            case anomalies:
            case legendaries:
            case empties:
            case aetherstream:
            case spacecannon:
            case traits:
            case techskips:
            case attachments:
            case shipless:
                heightForGameInfo = mapHeight;
                height = mapHeight + SPACE_FOR_TILE_HEIGHT * 2;
                displayTypeBasic = DisplayType.map;
                width = mapWidth;
                break;
            case landscape:
                heightForGameInfo = 40;
                height = Math.max(heightOfPlayerAreasSection, mapHeight);
                displayTypeBasic = DisplayType.all;
                width = mapWidth + 4 * 520 + EXTRA_X * 2;
                break;
            case googly:
            default:
                heightForGameInfo = mapHeight;
                height = mapHeight + heightOfPlayerAreasSection;
                displayTypeBasic = DisplayType.all;
                width = mapWidth;
        }
        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
    }

    private static int getHeightOfPlayerAreasSection(Game game, int playerCountForMap, int objectivesY) {
        final int typicalPlayerAreaHeight = 340;
        final int unrealPlayerHeight = 35;
        int playerY = playerCountForMap * typicalPlayerAreaHeight;
        int unrealPlayers = game.getNotRealPlayers().size();
        playerY += unrealPlayers * unrealPlayerHeight;
        for (Player player : game.getPlayers().values()) {
            if (player.isEliminated()) {
                playerY -= 190;
            } else if (player.getSecretsScored().size() >= 4) {
                playerY += (player.getSecretsScored().size() - 4) * 43 + 23;
            }
            playerY += (player.getTeamMateIDs().size() - 1) * unrealPlayerHeight;
        }
        final int columnsOfLaws = 2;
        final int lawHeight = 115;
        int lawsY = (game.getLaws().size() / columnsOfLaws + 1) * lawHeight;
        lawsY += (game.getEventsInEffect().size() / columnsOfLaws + 1) * lawHeight;
        return playerY + lawsY + objectivesY + EXTRA_Y * 3;
    }

    private DisplayType defaultIfNull(DisplayType displayType) {
        if (game.getDisplayTypeForced() != null) {
            return game.getDisplayTypeForced();
        }
        if (displayType == null) {
            return DisplayType.all;
        }
        return displayType;
    }

    FileUpload createFileUpload() {
        if (debug) debugDiscordTime = StopWatch.createStarted();
        AsyncTI4DiscordBot.jda.getPresence().setActivity(Activity.playing(game.getName()));
        game.incrementMapImageGenerationCount();
        FileUpload fileUpload = FileUploadService.createFileUpload(mainImageBytes, game.getName());
        if (debug) debugDiscordTime.stop();
        if (debug && !WebHelper.sendingToWeb())
            FileUploadService.saveLocalPng(mainImage, "MapDebug");
        return fileUpload;
    }

    void uploadToWebsite() {
        if (debug) debugWebsiteTime = StopWatch.createStarted();
        sendToWebsite();
        if (debug) debugWebsiteTime.stop();
    }

    void draw() {
        if (debug) debugDrawTime = StopWatch.createStarted();
        drawGame();
        if (debug) debugDrawTime.stop();
    }

    private void setupTilesForDisplayTypeAllAndMap(Map<String, Tile> tilesToDisplay) {
        if (displayTypeBasic != DisplayType.all && displayTypeBasic != DisplayType.map) {
            return;
        }
        Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
        // Show Grey Setup Tiles
        if (game.isShowMapSetup() || tilesToDisplay.isEmpty()) {
            int ringCount = game.getRingCount();
            ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);
            minX = 10000;
            minY = 10000;
            maxX = -1;
            maxY = -1;
            for (String position : PositionMapper.getTilePositions()) {
                String tileRing = "0";
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
        Set<String> tiles = tileMap.keySet();
        Set<String> tilesWithExtra = new HashSet<>(game.getAdjacentTileOverrides().values());
        tilesWithExtra.addAll(game.getBorderAnomalies().stream()
            .map(BorderAnomalyHolder::getTile)
            .collect(Collectors.toSet()));

        tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Tile));
        tilesWithExtra.forEach(key -> addTile(tileMap.get(key), TileStep.Extras));
        tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Units));
        if (!game.getTileDistances().isEmpty()) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Distance));
            game.setTileDistances(new HashMap<>()); // clear distances after consuming them
        }
        if (displayType == DisplayType.wormholes) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Wormholes));
        } else if (displayType == DisplayType.anomalies) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Anomalies));
        } else if (displayType == DisplayType.aetherstream) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Aetherstream));
        } else if (displayType == DisplayType.legendaries) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Legendaries));
        } else if (displayType == DisplayType.empties) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Empties));
        } else if (displayType == DisplayType.spacecannon) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.SpaceCannon));
        } else if (displayType == DisplayType.traits) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Traits));
        } else if (displayType == DisplayType.techskips) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.TechSkips));
        } else if (displayType == DisplayType.attachments) {
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Attachments));
        }
        tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.TileNumber));
    }

    private void setupFow(Map<String, Tile> tilesToDisplay) {
        if (!isFowModeActive()) {
            return;
        }
        isFoWPrivate = true;
        // IMPORTANT NOTE : This method used to be local and was refactored to extract
        // any references to tilesToDisplay
        fowPlayer = CommandHelper.getPlayerFromGame(game, event.getMember(), event.getUser().getId());

        Set<String> tilesToShow = FoWHelper.fowFilter(game, fowPlayer);
        Set<String> keys = new HashSet<>(tilesToDisplay.keySet());
        keys.removeAll(tilesToShow);
        for (String key : keys) {
            tilesToDisplay.remove(key);
            if (fowPlayer != null) {
                Tile fogTile = fowPlayer.buildFogTile(key, fowPlayer);
                if (fogTile != null) {
                    tilesToDisplay.put(key, fogTile);
                }
            }
        }
        //Check custom fog labeled tiles without actual tile
        Set<String> labelWithoutTile = new HashSet<>(fowPlayer.getFogLabels().keySet());
        labelWithoutTile.removeAll(tilesToDisplay.keySet());
        for (String position : labelWithoutTile) {
            tilesToDisplay.put(position, fowPlayer.buildFogTile(position, fowPlayer));
        }
    }

    private boolean isFowModeActive() {
        return game.isFowMode() && event != null &&
            (FoWHelper.isPrivateGame(game, event) || event instanceof UserOverridenGenericInteractionCreateEvent);
    }

    public boolean shouldConvertToGeneric(Player player) {
        return isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
    }

    private void logDebug() {
        if (!debug) return;
        debugAbsoluteStartTime.stop();

        StringBuilder sb = new StringBuilder();

        String totalTimeStr = DateTimeHelper.getTimeRepresentationNanoSeconds(debugAbsoluteStartTime.getNanoTime());
        String totalLine = String.format("%-34s%s", "Total time (" + game.getName() + "):", totalTimeStr);
        sb.append(totalLine);

        sb.append(debugString("  Draw time:", 36, debugDrawTime, debugAbsoluteStartTime));
        sb.append(debugString("    Tile time (of Draw Time):", 38, debugTileTime, debugDrawTime));
        sb.append(debugString("    Graphics time (of Draw Time):", 38, debugImageGraphicsTime, debugDrawTime));
        sb.append(debugString("  Discord time:", 36, debugDiscordTime, debugAbsoluteStartTime));
        sb.append(debugString("  Website time:", 36, debugWebsiteTime, debugAbsoluteStartTime));
        sb.append("\n");

        String message = "```\nDEBUG - GenerateMap Timing:\n" + sb + "\n```";
        MessageHelper.sendMessageToEventServerBotLogChannel(event, message);
    }

    private static String debugString(String name, int padRight, StopWatch subStopWatch, StopWatch totalStopWatch) {
        if (subStopWatch == null || totalStopWatch == null) {
            return "";
        }
        long subTime = subStopWatch.getNanoTime();
        long totalTime = totalStopWatch.getNanoTime();
        double percentage = ((double) subTime / totalTime) * 100.0;
        String timeStr = DateTimeHelper.getTimeRepresentationNanoSeconds(subTime);
        return String.format("\n%-" + padRight + "s%s (%2.2f%%)", name, timeStr, percentage);
    }

    private void sendToWebsite() {
        String testing = System.getenv("TESTING");
        if (testing == null && displayTypeBasic == DisplayType.all && !isFoWPrivate) {
            WebHelper.putMap(game.getName(), mainImageBytes, false, null);
            WebHelper.putData(game.getName(), game);
            WebHelper.putOverlays(game.getID(), websiteOverlays);
        } else if (isFoWPrivate) {
            Player player = CommandHelper.getPlayerFromGame(game, event.getMember(), event.getUser().getId());
            WebHelper.putMap(game.getName(), mainImageBytes, true, player);
        }
    }

    private void drawGame() {
        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        setupFow(tilesToDisplay);

        if (debug) debugTileTime = StopWatch.createStarted();
        setupTilesForDisplayTypeAllAndMap(tilesToDisplay);
        if (debug) debugTileTime.stop();

        if (debug) debugImageGraphicsTime = StopWatch.createStarted();
        drawImage();
        mainImageBytes = ImageHelper.writeJpg(mainImage);
        if (debug) debugImageGraphicsTime.stop();
    }

    private void drawImage() {
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        String timeStamp = DateTimeHelper.getFormattedTimestamp();
        graphics.drawString(game.getName() + " " + game.getCreationDate() + " - " + timeStamp, 0, 34);
        int landscapeShift = (displayType == DisplayType.landscape ? mapWidth : 0);
        int y = heightForGameInfo + 60;
        int x = landscapeShift + 10;
        Point coord;

        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // GAME MODES
        int deltaY = -150;
        if (game.isCompetitiveTIGLGame()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_TIGL.png");
            TIGLRank rank = game.getMinimumTIGLRankAtGameStart();
            if (rank != null) {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.superDrawString(graphics, rank.getShortName(), x + deltaX + 50, y + deltaY + 75, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Top, stroke2, Color.BLACK);
                graphics.setFont(Storage.getFont32());
            }
            deltaX += 100;
        }
        if (game.isAbsolMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_Absol.png");
            addWebsiteOverlay("Absol", null, x + deltaX, y + deltaY, 90, 90);
            deltaX += 100;
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

        // GAME FUN NAME
        deltaY = 35;
        graphics.setFont(Storage.getFont50());
        graphics.setColor(Color.WHITE);
        graphics.drawString(game.getCustomName(), landscapeShift, y);
        deltaX = graphics.getFontMetrics().stringWidth(game.getCustomName());

        // STRATEGY CARDS
        coord = drawStrategyCards(new Point(x, y));
        coord.translate(100, 0);

        // ROUND
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        String roundString = "ROUND: " + game.getRound();
        int roundLen = graphics.getFontMetrics().stringWidth(roundString);
        if (coord.x > mapWidth - roundLen - 100 * game.getRealPlayers().size()) {
            coord = new Point(landscapeShift + 20, coord.y + 100);
        }
        graphics.drawString(roundString, coord.x, coord.y);

        // CARD DECKS
        drawCardDecks(Math.max(x + deltaX, coord.x), y - 75);

        // TURN ORDER
        coord = drawTurnOrderTracker(coord.x + roundLen + 100 + landscapeShift, coord.y);

        y = coord.y + 30;
        x = 10 + landscapeShift;
        int tempY = y;
        y = drawObjectives(y + 180);
        y = laws(y);
        y = events(y);
        tempY = drawScoreTrack(tempY + 20);
        if (displayTypeBasic != DisplayType.stats) {
            playerInfo(game);
        }

        if (displayTypeBasic == DisplayType.all || displayTypeBasic == DisplayType.stats) {
            Point topLeft = new Point(x, y);
            new PlayerAreaGenerator(
                graphics,
                game,
                isFoWPrivate,
                fowPlayer,
                websiteOverlays,
                mapWidth,
                scoreTokenSpacing).drawAllPlayerAreas(topLeft);
        }
    }

    @Override
    public void close() {
        mainImage.flush();
        graphics.dispose();
        logDebug();
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
        Left, Center, Right
    }

    public enum VerticalAlign {
        Top, Center, Bottom
    }

    private int drawScoreTrack(int y) {
        int landscapeShift = (displayType == DisplayType.landscape ? mapWidth : 0);
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke5);
        graphics.setFont(Storage.getFont50());
        int boxHeight = 140;
        int boxWidth = 150;
        int boxBuffer = -1;
        if (game.getVp() > 14) {
            boxWidth = 120;
        }
        for (int i = 0; i <= game.getVp(); i++) {
            graphics.setColor(Color.WHITE);
            Rectangle rect = new Rectangle(i * boxWidth + landscapeShift, y, boxWidth, boxHeight);
            DrawingUtil.drawCenteredString(g2, Integer.toString(i), rect, Storage.getFont50());
            g2.setColor(Color.RED);
            g2.drawRect(i * boxWidth + landscapeShift, y, boxWidth, boxHeight);
        }

        List<Player> players = new ArrayList<>(game.getRealPlayers());
        if (isFoWPrivate) {
            Collections.shuffle(players);
        }

        int row = 0;
        int col = 0;
        int playerCount = players.size();
        int rowCount = (int) Math.max(2, Math.ceil(Math.sqrt(1.0 + playerCount) + 0.1));
        List<List<Player>> playerChunks = ListUtils.partition(players, rowCount);
        int colCount = (int) Math.max(2, Math.ceil(1.0 * playerCount / rowCount));
        int availableSpacePerColumn = (boxWidth - boxBuffer * 2) / colCount;
        int availableSpacePerRow = (boxHeight - boxBuffer * 2) / rowCount;
        float scale = 0.7f;
        for (List<Player> playerChunk : playerChunks) {
            for (Player player : playerChunk) {
                try {
                    boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                    String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());

                    BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                    int tokenWidth = controlTokenImage == null ? 51 : controlTokenImage.getWidth(); // 51
                    int tokenHeight = controlTokenImage == null ? 33 : controlTokenImage.getHeight(); // 33
                    int centreHorizontally = Math.max(0, (availableSpacePerColumn - tokenWidth) / 2);
                    int centreVertically = Math.max(0, (availableSpacePerRow - tokenHeight) / 2);

                    int vpCount = player.getTotalVictoryPoints();
                    int tokenX = vpCount * boxWidth + Math.min(boxBuffer + (availableSpacePerColumn * col) + centreHorizontally, boxWidth - tokenWidth - boxBuffer) + landscapeShift;
                    int tokenY = y + boxBuffer + (availableSpacePerRow * row) + centreVertically;
                    DrawingUtil.drawControlToken(graphics, controlTokenImage, DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID), tokenX, tokenY, convertToGeneric, scale);
                } catch (Exception e) {
                    // nothing
                    BotLogger.error("Could not display player: " + player.getUserName(), e);
                }
                row++;
            }
            row = 0;
            col++;
        }
        y += 180;
        return y;
    }

    private Point drawStrategyCards(Point coord) {
        int x = coord.x;
        int y = coord.y;
        boolean convertToGenericSC = isFoWPrivate;
        int deltaY = y + 80;
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        Collection<Player> players = game.getPlayers().values();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player : players) {
            scPicked.addAll(player.getSCs());
        }
        Map<Integer, Boolean> scPlayed = game.getScPlayed();

        for (Map.Entry<Integer, Integer> scTGs : scTradeGoods.entrySet()) {
            Integer sc = scTGs.getKey();
            if (sc == 0) {
                continue;
            }
            StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
            graphics.setFont(Storage.getFont64());
            int textWidth = graphics.getFontMetrics().stringWidth(Integer.toString(sc));

            if (!convertToGenericSC && !scPicked.contains(sc)) {
                graphics.setColor(ColorUtil.getSCColor(sc, game));
                if (!game.getStoredValue("exhaustedSC" + sc).isEmpty()) {
                    graphics.setColor(Color.GRAY);
                }
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, deltaY);
                // graphics.drawRect(x, y + 24, textWidth, 64); // debug
                addWebsiteOverlay(scModel, x, y + 24, textWidth, 60);
                Integer tg = scTGs.getValue();
                if (tg > 0) {
                    graphics.setFont(Storage.getFont24());
                    graphics.setColor(Color.WHITE);
                    String tgMsg = "TG:" + tg;
                    int tgMsgTextWidth = graphics.getFontMetrics().stringWidth(tgMsg);
                    graphics.drawString(tgMsg, x + textWidth / 2 - tgMsgTextWidth / 2, deltaY + 30);
                    textWidth = Math.max(textWidth, tgMsgTextWidth);
                }
            }
            if (convertToGenericSC && scPlayed.getOrDefault(sc, false)) {
                graphics.setColor(Color.GRAY);
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, deltaY);
            }
            x += textWidth + 25;

            // Drop down a level if there are a lot of SC cards
            if (x > (displayType == DisplayType.landscape ? mapWidth + 4 * 520 + EXTRA_X * 2 : mapWidth) - 100) {
                x = 20 + (displayType == DisplayType.landscape ? mapWidth : 0);
                deltaY += 100;
            }
        }

        return new Point(x, deltaY);
    }

    private Point drawTurnOrderTracker(int x, int y) {
        boolean convertToGenericSC = isFoWPrivate;
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
                if (player.isPassed() || player.getSCs().isEmpty())
                    continue;
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
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getSecretObjectiveDeckSize()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getSecretObjectiveDeckSize() + "/" + game.getSecretObjectiveFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Secret Objective Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_action.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getActionCards().size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getActionCards().size() + "/" + game.getActionCardFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Action Card Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_cultural.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getExploreDeck("cultural").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getCulturalExploreDeckSize() + "/" + game.getCulturalExploreFullDeckSize() + " in the deck \n" + game.getCulturalExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Cultural Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_industrial.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getExploreDeck("industrial").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getIndustrialExploreDeckSize() + "/" + game.getIndustrialExploreFullDeckSize() + " in the deck \n" + game.getIndustrialExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Industrial Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_hazardous.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getExploreDeck("hazardous").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getHazardousExploreDeckSize() + "/" + game.getHazardousExploreFullDeckSize() + " in the deck \n" + game.getHazardousExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Hazardous Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_frontier.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getExploreDeck("frontier").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getFrontierExploreDeckSize() + "/" + game.getFrontierExploreFullDeckSize() + " in the deck \n" + game.getFrontierExploreDiscardSize() + " cards in the discard pile";
        addWebsiteOverlay("Frontier Explore Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_relic.jpg", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getRelicDeckSize()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getRelicDeckSize() + "/" + game.getRelicFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Relic Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        drawPAImageScaled(x, y, "cardback_agenda.png", cardWidth, cardHeight);
        DrawingUtil.superDrawString(graphics, Integer.toString(game.getAgendaDeckSize()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
        overlayText = game.getAgendaDeckSize() + "/" + game.getAgendaFullDeckSize() + " cards in the deck";
        addWebsiteOverlay("Agenda Deck", overlayText, x, y, cardWidth, cardHeight);
        x += horSpacing;

        return x;
    }

    private int tileRing(String pos) {
        if (pos.replaceAll("\\d", "").isEmpty())
            return Integer.parseInt(pos) / 100;
        return 100;
    }

    private List<String> findThreeNearbyStatTiles(Game game, Player player, Set<String> taken) {
        boolean fow = isFoWPrivate;
        boolean randomizeLocation = false;
        if (fow && player != fowPlayer) {
            if (FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)) {
                if (!FoWHelper.hasHomeSystemInView(player, fowPlayer)) {
                    // if we can see a players stats, but we cannot see their home system - move
                    // their stats somewhere random
                    randomizeLocation = true;
                }
            }
        }

        String anchor = player.getPlayerStatsAnchorPosition();
        if (anchor == null) anchor = player.getHomeSystemPosition();
        if (anchor == null) return null;
        if (randomizeLocation) anchor = "000"; // just stick them on 000

        Set<String> validPositions = PositionMapper.getTilePositions().stream()
            .filter(pos -> tileRing(pos) <= (game.getRingCount() + 1))
            .filter(pos -> game.getTileByPosition(pos) == null)
            .filter(pos -> taken == null || !taken.contains(pos))
            .collect(Collectors.toSet());

        Point anchorRaw = PositionMapper.getTilePosition(anchor);
        if (anchorRaw == null) return null;
        Point anchorPt = getTilePosition(anchor, anchorRaw.x, anchorRaw.y);

        // BEGIN ALGORITHM
        // 1. Make a Priority Queue sorting on distance
        // 2. Take tiles from the PQ until we have a contiguous selection of 3 adj tiles
        // 3. Use those tiles :)

        // 1.
        boolean rand = randomizeLocation;
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(pos -> {
            Point positionPoint = PositionMapper.getTilePosition(pos);
            if (positionPoint == null) return 100000000f;
            int ring = tileRing(pos);
            Point realPosition = getTilePosition(pos, positionPoint.x, positionPoint.y);
            double distance = realPosition.distance(anchorPt);
            distance = rand ? ThreadLocalRandom.current().nextInt(0, 200) : distance + ring * 75;
            return distance;
        }));
        pq.addAll(validPositions);

        // 2. Take tiles from the PQ until we have 3 adj
        // - - N*logN * 6
        List<String> closestTiles = new ArrayList<>();
        Map<String, Integer> numAdj = new HashMap<>();
        String next;
        while ((next = pq.poll()) != null) {
            if (closestTiles.contains(next)) continue;
            closestTiles.add(next);
            numAdj.put(next, 0);

            for (String pos : PositionMapper.getAdjacentTilePositions(next)) {
                if (numAdj.containsKey(pos)) {
                    numAdj.put(pos, numAdj.get(pos) + 1);
                    numAdj.put(next, numAdj.get(next) + 1);
                }
            }
            for (String pos : closestTiles) {
                if (numAdj.get(pos) == 2) {
                    List<String> adjOut = PositionMapper.getAdjacentTilePositions(pos);
                    List<String> output = new ArrayList<>();
                    output.add(pos);
                    output.addAll(CollectionUtils.intersection(adjOut, closestTiles));
                    return output;
                }
            }
        }
        return null;
    }

    private void playerInfo(Game game) {
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);

        // Do some stuff for FoW
        boolean fow = isFoWPrivate;
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        List<Player> statOrder = new ArrayList<>(game.getRealPlayers());
        if (fow) {
            Collections.shuffle(players);
            statOrder.clear();
            // always build fowplayer's stat location first
            statOrder.add(fowPlayer);
            // then build the stats of players we can see home systems
            players.stream().filter(player -> FoWHelper.hasHomeSystemInView(player, fowPlayer)).forEach(statOrder::add);
            // then build the stats of everyone else
            players.stream().filter(player -> FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer))
                .forEach(statOrder::add);
        }

        int ringCount = Math.max(Math.min(game.getRingCount(), RING_MAX_COUNT), RING_MIN_COUNT);

        // highlightValidStatTiles(game);
        boolean useNewSystem = true;
        Set<String> statTilesInUse = new HashSet<>();
        Map<String, List<String>> playerStatTiles = new LinkedHashMap<>();
        for (Player p : statOrder) {
            if (p == null || p.getFaction() == null || playerStatTiles.containsKey(p.getFaction())) continue;
            // if we can't see stats anyway, skip this player
            if (fow && !FoWHelper.canSeeStatsOfPlayer(game, p, fowPlayer)) continue;

            List<String> myStatTiles = findThreeNearbyStatTiles(game, p, statTilesInUse);
            if (myStatTiles == null) {
                useNewSystem = false;
                break;
            }
            statTilesInUse.addAll(myStatTiles);
            playerStatTiles.put(p.getFaction(), myStatTiles);
        }

        for (Player player : statOrder) {
            if (player.getFaction() == null || !player.isRealPlayer()) {
                continue;
            }
            if (useNewSystem) {
                List<String> tiles = playerStatTiles.get(player.getFaction());
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
        boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
        if (convertToGeneric) {
            return;
        }

        // Get the map positions for each of the "stat tiles"
        if (statTiles == null || statTiles.size() < 3) return;
        Map<String, Point> points = new HashMap<>();
        for (String pos : statTiles) {
            Point p = PositionMapper.getTilePosition(pos);
            if (p == null) return;
            p = getTilePosition(pos, p.x, p.y);
            p.translate(EXTRA_X, EXTRA_Y);
            points.put(pos, p);
        }
        Point statTileMid = points.get(statTiles.get(0));
        Point statTile1 = points.get(statTiles.get(1));
        Point statTile2 = points.get(statTiles.get(2));

        int dir1 = 0, dir2 = 0, j = 0;
        for (String p : PositionMapper.getAdjacentTilePositions(statTiles.get(0))) {
            if (p.equals(statTiles.get(1)))
                dir1 = j;
            if (p.equals(statTiles.get(2)))
                dir2 = j;
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
            for (int i = 0; i < 6; i++)
                if (statTiles.contains(adjPos.get(i)))
                    adjDir.add(i);
            BufferedImage hex = DrawingUtil.hexBorder(game.getHexBorderStyle(), playerColor, adjDir);
            graphics.drawImage(tint, p.x, p.y, null);
            graphics.drawImage(hex, p.x, p.y, null);
        }

        Point miscTile; // To be used for speaker and other stuff
        Point point;
        HorizontalAlign center = HorizontalAlign.Center;
        VerticalAlign bottom = VerticalAlign.Bottom;
        { // PAINT FACTION ICON
            point = PositionMapper.getPlayerStats("factionicon");
            int size = 275;
            point.translate(statTileMid.x - (size / 2), statTileMid.y - (size / 2));
            DrawingUtil.drawPlayerFactionIconImageUnderlay(graphics, player, point.x, point.y, size, size);
            DrawingUtil.drawPlayerFactionIconImageOpaque(graphics, player, point.x, point.y, size, size, 0.40f);
        }

        { // PAINT USERNAME
            graphics.setFont(Storage.getFont32());
            String userName = player.getUserName();
            point = PositionMapper.getPlayerStats("newuserName");
            if (!game.hideUserNames()) {
                String name = userName.substring(0, Math.min(userName.length(), 15));
                DrawingUtil.superDrawString(
                    graphics, name, statTileMid.x + point.x, statTileMid.y + point.y,
                    Color.WHITE, center, null, stroke5, Color.BLACK);
            }
        }

        { // PAINT FACTION NAME ~AND~ COLOR STROTERS
            graphics.setFont(Storage.getFont32());
            point = PositionMapper.getPlayerStats("newfaction");
            point.translate(statTileMid.x, statTileMid.y);
            String factionText = player.getFaction();
            if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
                factionText = player.getDisplayName();
            }
            factionText = StringUtils.capitalize(factionText);
            DrawingUtil.superDrawString(graphics, factionText, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
        }

        { // PAINT VICTORY POINTS
            graphics.setFont(Storage.getFont32());
            String vpCount = "VP: " + player.getTotalVictoryPoints() + " / " + game.getVp();
            point = PositionMapper.getPlayerStats("newvp");
            point.translate(statTileMid.x, statTileMid.y);
            DrawingUtil.superDrawString(graphics, vpCount, point.x, point.y, Color.WHITE, center, null, stroke5,
                Color.BLACK);
        }

        { // PAINT SO ICONS
            List<String> soToPoList = game.getSoToPoList();
            int unscoredSOs = player.getSecrets().size();
            int scoredSOs = (int) player.getSecretsScored().keySet().stream().filter(so -> !soToPoList.contains(so)).count();
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

        { // PAINT SC#s
            graphics.setFont(Storage.getFont80());
            int scsize = 96;
            List<Integer> playerSCs = new ArrayList<>(player.getSCs());
            if (player.hasTheZeroToken())
                playerSCs.add(0);
            Collections.sort(playerSCs);

            point = PositionMapper.getPlayerStats("newsc");
            point.translate(statTileMid.x, statTileMid.y);
            point.translate(-1 * (scsize / 2) * (playerSCs.size() - 1), -1 * (scsize / 2));

            for (int sc : playerSCs) {
                StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                if (sc == 0) {
                    drawPAImageScaled(point.x, point.y, "pa_telepathic.png", scsize);
                    point.translate(scsize, 0);
                } else {
                    int fontYoffset = (scsize / 2) + 25;
                    DrawingUtil.superDrawString(
                        graphics, Integer.toString(sc), point.x, point.y + fontYoffset,
                        ColorUtil.getSCColor(sc, game), center, bottom, stroke6, Color.BLACK);
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

        { // PAINT CCs
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

            DrawingUtil.drawCCOfPlayer(
                graphics, ccID, point.x, point.y, player.getTacticalCC(), player, false,
                rightAlign);
            drawFleetCCOfPlayer(graphics, fleetCCID, point.x, point.y + 65, player, rightAlign);
            DrawingUtil.drawCCOfPlayer(
                graphics, ccID, point.x, point.y + 130, player.getStrategicCC(), player, false,
                rightAlign);

            // Additional FS
            int additionalFleetSupply = 0;
            String addFS = "";
            if (player.hasAbility("edict")) additionalFleetSupply += player.getMahactCC().size();
            if (player.hasAbility("armada")) additionalFleetSupply += 2;
            if (additionalFleetSupply > 0) addFS = "*";

            // Draw Numbers
            HorizontalAlign align = HorizontalAlign.Left;
            List<String> reps = Arrays.asList(player.getCCRepresentation().split("/"));
            graphics.setFont(Storage.getFont28());
            point.translate(rightAlign ? 58 : -3, 32);
            String fleetCCs = player.getFleetCC() + additionalFleetSupply + addFS;
            DrawingUtil.superDrawString(graphics, reps.get(0), point.x, point.y, Color.WHITE, align, null, stroke4, Color.BLACK);
            DrawingUtil.superDrawString(graphics, fleetCCs, point.x, point.y + 65, Color.WHITE, align, null, stroke4, Color.BLACK);
            DrawingUtil.superDrawString(graphics, reps.get(2), point.x, point.y + 130, Color.WHITE, align, null, stroke4, Color.BLACK);
        }

        int offBoardHighlighting = 0;
        { // PAINT OFF BOARD HIGHLIGHTING
            if (displayType == DisplayType.legendaries) {
                boolean hasNanoForge = player.hasRelic("nanoforge") || player.hasRelic("absol_nanoforge");
                for (String planet : player.getPlanets()) {
                    PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                    offBoardHighlighting += (custodiaVigilia.getLegendaryAbilityName() != null && game.getTileFromPlanet(planet) == null) ? 1 : 0;
                }
                if (offBoardHighlighting >= 1) {
                    String legendaryFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                    BufferedImage bufferedImage = ImageHelper.read(legendaryFile);
                    if (offBoardHighlighting + (hasNanoForge ? 1 : 0) >= 2) {
                        bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / (offBoardHighlighting + (hasNanoForge ? 1 : 0)) / bufferedImage.getWidth() / bufferedImage.getHeight()));
                        for (int i = 0; i < offBoardHighlighting; i++) {
                            graphics.drawImage(bufferedImage,
                                miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1),
                                miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1) + (player.isSpeaker() ? 30 : 0),
                                null);
                        }
                    } else {
                        graphics.drawImage(bufferedImage,
                            miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                            null);
                    }
                }
                if (hasNanoForge) {
                    String relicFile = ResourceHelper.getInstance().getGeneralFile("Relic.png");
                    BufferedImage bufferedImage = ImageHelper.read(relicFile);
                    if (offBoardHighlighting >= 1) {
                        bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / (offBoardHighlighting + 1) / bufferedImage.getWidth() / bufferedImage.getHeight()));
                        graphics.drawImage(bufferedImage,
                            miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 + 30,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + 30 + (player.isSpeaker() ? 30 : 0),
                            null);
                    } else {
                        graphics.drawImage(bufferedImage,
                            miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                            null);
                    }
                    offBoardHighlighting++;
                }
            } else if (displayType == DisplayType.empties) {
                boolean hasStellar = player.hasRelic("stellarconverter") || player.hasRelic("absol_stellarconverter");
                String relicFile = ResourceHelper.getInstance().getGeneralFile("Relic.png");
                boolean hasHero = player.hasLeaderUnlocked("muaathero") || player.hasLeaderUnlocked("zelianhero");
                String heroFile = ResourceHelper.getResourceFromFolder("emojis/leaders/", "Hero.png");
                if (player.hasLeaderUnlocked("muaathero")) {
                    heroFile = ResourceHelper.getResourceFromFolder("emojis/leaders/pok/Emoji Farm 4/", "MuaatHero.png");
                }
                BufferedImage bufferedImage;
                if (hasStellar && hasHero) {
                    bufferedImage = ImageHelper.read(relicFile);
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(17000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 - 30,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30
                            + (player.isSpeaker() ? 30 : 0),
                        null);
                    bufferedImage = ImageHelper.read(heroFile);
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(17000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 + 30,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + 30
                            + (player.isSpeaker() ? 30 : 0),
                        null);
                    offBoardHighlighting += 2;
                } else if (hasStellar) {
                    bufferedImage = ImageHelper.read(relicFile);
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                            + (player.isSpeaker() ? 30 : 0),
                        null);
                    offBoardHighlighting++;
                } else if (hasHero) {
                    bufferedImage = ImageHelper.read(heroFile);
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                            + (player.isSpeaker() ? 30 : 0),
                        null);
                    offBoardHighlighting++;
                }
            } else if (displayType == DisplayType.wormholes && player.getFaction().equalsIgnoreCase("ghost")) {
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
                int x = miscTile.x + (TILE_WIDTH - 80) / 2;
                x += (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 30 : 0);
                int y = miscTile.y + (SPACE_FOR_TILE_HEIGHT - 80) / 2 + (player.isSpeaker() ? 30 : 0);
                boolean reconstruction = (ButtonHelper.isLawInPlay(game, "wormhole_recon")
                    || ButtonHelper.isLawInPlay(game, "absol_recon"));
                boolean travelBan = ButtonHelper.isLawInPlay(game, "travel_ban")
                    || ButtonHelper.isLawInPlay(game, "absol_travelban");

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
                        BufferedImage blockedWormholeImage = ImageHelper.read(
                            ResourceHelper.getInstance()
                                .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                        graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                    }
                    if (reconstruction) {
                        BufferedImage doubleWormholeImage = ImageHelper
                            .readScaled(ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                        graphics.drawImage(doubleWormholeImage, x, y, null);
                    }
                    x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
                }
                if (!alphaOnMap) {
                    String tokenFile = Mapper.getTokenPath(alphaID);
                    BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                    graphics.drawImage(bufferedImage, x, y, null);
                    if (travelBan) {
                        BufferedImage blockedWormholeImage = ImageHelper.read(
                            ResourceHelper.getInstance()
                                .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                        graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                    }
                    if (reconstruction) {
                        BufferedImage doubleWormholeImage = ImageHelper
                            .readScaled(ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                        graphics.drawImage(doubleWormholeImage, x, y, null);
                    }
                    x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
                }
            } else if (displayType == DisplayType.anomalies && player.ownsUnitSubstring("cabal_spacedock")) {
                UnitKey unitKey = Mapper.getUnitKey("sd", player.getColor());
                UnitKey unitKeyCabal = Mapper.getUnitKey("csd", player.getColor());
                int unitNum = player.getUnitCap("sd") + player.getUnitCap("csd");
                unitNum = (unitNum == 0 ? PositionMapper.getReinforcementsPosition("sd").getPositionCount("sd") : unitNum);
                for (Tile tile2 : game.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                        unitNum -= unitHolder.getUnits().getOrDefault(unitKey, 0);
                        unitNum -= unitHolder.getUnits().getOrDefault(unitKeyCabal, 0);
                    }
                }
                if (unitNum > 0) {
                    int x = miscTile.x + (TILE_WIDTH - 95) / 2;
                    x += (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 30 : 0);
                    int y = miscTile.y + (SPACE_FOR_TILE_HEIGHT - 95) / 2 + (player.isSpeaker() ? 30 : 0);
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
                            traitFile = ResourceHelper.getInstance().getGeneralFile(
                                ("" + t.charAt(0)).toUpperCase() + t.substring(1).toLowerCase() + ".png");
                        } else if (!traits.isEmpty()) {
                            String t = "";
                            t += traits.contains("cultural") ? "C" : "";
                            t += traits.contains("hazardous") ? "H" : "";
                            t += traits.contains("industrial") ? "I" : "";
                            if (t.equals("CHI")) {
                                traitFile = ResourceHelper.getInstance()
                                    .getPlanetResource("pc_attribute_combo_CHI_big.png");
                            } else {
                                traitFile = ResourceHelper.getInstance()
                                    .getPlanetResource("pc_attribute_combo_" + t + ".png");
                            }
                        }
                        traitFiles.add(traitFile);
                        offBoardHighlighting++;
                    }
                }
                if (offBoardHighlighting >= 2) {
                    for (int i = 0; i < offBoardHighlighting; i++) {
                        BufferedImage bufferedImage = ImageHelper.read(traitFiles.get(i));
                        bufferedImage = ImageHelper.scale(
                            bufferedImage, (float) Math.sqrt(
                                24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                        graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 - 30
                                + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30
                                + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                            null);
                    }
                } else if (offBoardHighlighting == 1) {
                    BufferedImage bufferedImage = ImageHelper.read(traitFiles.getFirst());
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                            + (player.isSpeaker() ? 30 : 0),
                        null);
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
                        bufferedImage = ImageHelper.scale(
                            bufferedImage, (float) Math.sqrt(
                                24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                        graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30
                                + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30
                                + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                            null);
                    }
                } else if (offBoardHighlighting == 1) {
                    BufferedImage bufferedImage = ImageHelper.read(techFiles.getFirst());
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
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
                    for (String planet : attachFiles.keySet()) {
                        BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                        bufferedImage = ImageHelper.scale(
                            bufferedImage, (float) Math.sqrt(
                                24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                        graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30
                                + (player.isSpeaker() ? 30 : 0),
                            null);
                        if (attachCount.get(planet) > 1) {
                            graphics.setColor(Color.WHITE);
                            graphics.fillOval(
                                miscTile.x + (345 - 80) / 2 - 30,
                                miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 - 30 + (player.isSpeaker() ? 30 : 0),
                                80, 80);
                            graphics.setColor(Color.BLACK);
                            graphics.fillOval(
                                miscTile.x + (345 - 72) / 2 - 30,
                                miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 - 30 + (player.isSpeaker() ? 30 : 0) + 4,
                                72, 72);
                            graphics.setColor(Color.WHITE);
                            DrawingUtil.drawCenteredString(
                                graphics, "" + attachCount.get(planet),
                                new Rectangle(
                                    miscTile.x + (345 - 80) / 2 - 30,
                                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 - 30
                                        + (player.isSpeaker() ? 30 : 0),
                                    80, 80),
                                Storage.getFont48());
                        }
                    }
                } else if (offBoardHighlighting == 1) {
                    String planet = attachFiles.keySet().iterator().next();

                    BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                    bufferedImage = ImageHelper.scale(
                        bufferedImage,
                        (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                            + (player.isSpeaker() ? 30 : 0),
                        null);
                    if (attachCount.get(planet) > 1) {
                        graphics.setColor(Color.WHITE);
                        graphics.fillOval(
                            miscTile.x + (345 - 80) / 2,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                            80, 80);
                        graphics.setColor(Color.BLACK);
                        graphics.fillOval(
                            miscTile.x + (345 - 72) / 2,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0) + 4,
                            72, 72);
                        graphics.setColor(Color.WHITE);
                        DrawingUtil.drawCenteredString(
                            graphics, "" + attachCount.get(planet),
                            new Rectangle(
                                miscTile.x + (345 - 80) / 2,
                                miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                                80, 80),
                            Storage.getFont48());
                    }
                }
            }
        }

        { // PAINT SPEAKER
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
        }

        { // PAINT PASSED/ACTIVE/AFK
            String activePlayerID = game.getActivePlayerID();
            String phase = game.getPhaseOfGame();
            if (player.isPassed()) {
                point = PositionMapper.getPlayerStats("newpassed");
                point.translate(miscTile.x, miscTile.y);
                DrawingUtil.superDrawString(
                    graphics, "PASSED", point.x, point.y, ColorUtil.PassedColor, center, null, stroke4,
                    Color.BLACK);
            } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                point = PositionMapper.getPlayerStats("newpassed");
                point.translate(miscTile.x, miscTile.y);
                DrawingUtil.superDrawString(
                    graphics, "ACTIVE", point.x, point.y, ColorUtil.ActiveColor, center, null, stroke4,
                    Color.BLACK);
            }
            if (player.isAFK()) {
                point = PositionMapper.getPlayerStats("newafk");
                point.translate(miscTile.x, miscTile.y);
                DrawingUtil.superDrawString(
                    graphics, "AFK", point.x, point.y, Color.gray, center, null, stroke4,
                    Color.BLACK);
            }
            graphics.setColor(Color.WHITE);
        }
    }

    private void paintPlayerInfoOld(Game game, Player player, int ringCount) {
        int deltaX = 0, deltaSplitX = 0;
        int deltaY = 0, deltaSplitY = 0;

        String playerStatsAnchor = player.getPlayerStatsAnchorPosition();
        if (playerStatsAnchor != null) {
            // String anchorProjectedOnOutsideRing =
            // PositionMapper.getEquivalentPositionAtRing(ringCount, playerStatsAnchor);
            Point anchorProjectedPoint = PositionMapper.getTilePosition(playerStatsAnchor);
            if (anchorProjectedPoint != null) {
                Point playerStatsAnchorPoint = getTilePosition(
                    playerStatsAnchor, anchorProjectedPoint.x,
                    anchorProjectedPoint.y);
                Integer anchorLocationIndex = PositionMapper
                    .getRingSideNumberOfTileID(player.getPlayerStatsAnchorPosition());
                anchorLocationIndex = anchorLocationIndex == null ? 0 : anchorLocationIndex - 1;
                boolean isCorner = playerStatsAnchor
                    .equals(PositionMapper.getTileIDAtCornerPositionOfRing(ringCount, anchorLocationIndex + 1));
                if (anchorLocationIndex == 0 && isCorner) { // North Corner
                    deltaX = playerStatsAnchorPoint.x + EXTRA_X + 80;
                    deltaY = playerStatsAnchorPoint.y - 80;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 0) { // North East
                    deltaX = playerStatsAnchorPoint.x + EXTRA_X + SPACE_FOR_TILE_HEIGHT;
                    deltaY = playerStatsAnchorPoint.y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 1) { // East
                    deltaX = playerStatsAnchorPoint.x + 360 + EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + EXTRA_Y;
                } else if (anchorLocationIndex == 2 && isCorner) { // South East Corner
                    deltaX = playerStatsAnchorPoint.x + 360 + EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + EXTRA_Y;
                } else if (anchorLocationIndex == 2) { // South East
                    deltaX = playerStatsAnchorPoint.x + 360 + EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + EXTRA_Y + 100;
                } else if (anchorLocationIndex == 3 && isCorner) { // South Corner
                    deltaX = playerStatsAnchorPoint.x + EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + 360 + EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 3) { // South West
                    deltaX = playerStatsAnchorPoint.x;
                    deltaY = playerStatsAnchorPoint.y + 250 + EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 4) { // West
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + EXTRA_Y;
                } else if (anchorLocationIndex == 5 && isCorner) { // North West Corner
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + EXTRA_Y;
                } else if (anchorLocationIndex == 5) { // North West
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y - 100;
                    deltaSplitX = 200;
                } else
                    return;
            } else
                return;
        } else
            return;

        String userName = player.getUserName();

        boolean convertToGeneric = isFoWPrivate
            && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
        if (convertToGeneric) {
            return;
        }

        // PAINT USERNAME
        Point point = PositionMapper.getPlayerStats(Constants.STATS_USERNAME);
        if (!game.hideUserNames()) {
            graphics.drawString(
                userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX,
                point.y + deltaY);
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
            scText = game.getSCNumberIfNaaluInPlay(player, scText);
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
            graphics, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player,
            false, false);
        drawFleetCCOfPlayer(graphics, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player, false);
        DrawingUtil.drawCCOfPlayer(
            graphics, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(),
            player, false, false);

        // PAINT SPEAKER
        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                graphics.drawImage(
                    bufferedImage, point.x + deltaX + deltaSplitX + negativeDelta,
                    point.y + deltaY - deltaSplitY, null);
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
        Graphics graphics, String ccID, int x, int y, Player player,
        boolean rightAlign
    ) {
        String ccPath = Mapper.getCCPath(ccID);
        int ccCount = player.getFleetCC();
        boolean hasArmada = player.hasAbility("armada");
        List<String> mahactCC = player.getMahactCC();
        boolean hasMahactCCs = !player.getMahactCC().isEmpty() && player.hasAbility("edict");

        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            BufferedImage blankCC = ImageHelper.read(Mapper.getCCPath("command_blank.png"));
            int delta = rightAlign ? -20 : 20;

            // DRAW TWO ARMADA TOKENS
            if (hasArmada) {
                BufferedImage armadaLowerCCImage = ImageHelper
                    .read(Mapper.getCCPath(Mapper.getCCID(player.getColor())));
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
            BotLogger.error(new BotLogger.LogMessageOrigin(player), "Ignored exception during map generation", e);
        }
    }

    private int drawObjectives(int y) {
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke3);
        graphics.setFont(Storage.getFont26());

        int top = y;
        int x = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
        int maxY = y;

        // Objective 1
        List<Objective> objectives = Objective.retrievePublic1(game);
        int maxTextWidth = ObjectiveBox.getMaxTextWidth(game, graphics, objectives);
        int boxWidth = ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);

        for (Objective objective : objectives) {
            ObjectiveBox box = new ObjectiveBox(x, y, boxWidth, maxTextWidth, scoreTokenSpacing);
            box.Display(game, graphics, this, objective);
            y += ObjectiveBox.getVerticalSpacing();
        }

        // Objective 2
        x += boxWidth + SPACING_BETWEEN_OBJECTIVE_TYPES;
        maxY = Math.max(y, maxY);
        y = top;

        objectives = Objective.retrievePublic2(game);
        maxTextWidth = ObjectiveBox.getMaxTextWidth(game, graphics, objectives);
        boxWidth = ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);
        for (Objective objective : objectives) {
            ObjectiveBox box = new ObjectiveBox(x, y, boxWidth, maxTextWidth, scoreTokenSpacing);
            box.Display(game, graphics, this, objective);
            y += ObjectiveBox.getVerticalSpacing();
        }

        // Custom
        x += boxWidth + SPACING_BETWEEN_OBJECTIVE_TYPES;
        maxY = Math.max(y, maxY);
        y = top;

        objectives = Objective.retrieveCustom(game);
        maxTextWidth = ObjectiveBox.getMaxTextWidth(game, graphics, objectives);
        boxWidth = ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);
        for (Objective objective : objectives) {
            ObjectiveBox box = new ObjectiveBox(x, y, boxWidth, maxTextWidth, scoreTokenSpacing);
            box.Display(game, graphics, this, objective);
            y += ObjectiveBox.getVerticalSpacing();
        }

        return maxY + 15;
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
            if (optionalText != null && !optionalText.isEmpty()
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

                if (optionalText == null || optionalText.isEmpty()
                    || game.getPlayerFromColorOrFaction(optionalText) == null) {
                    paintAgendaIcon(y, x);
                } else if ("0".equals(agendaType)) {
                    Player electedPlayer = null;
                    boolean convertToGeneric = false;
                    for (Player player : game.getPlayers().values()) {
                        if (optionalText.equals(player.getFaction()) || optionalText.equals(player.getColor())) {
                            if (isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)) {
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
                if (!game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue()).isEmpty()) {
                    int tokenDeltaY = 0;
                    int count = 0;
                    for (String debtToken : game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue())
                        .split("_")) {

                        boolean hideFactionIcon = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(
                            game,
                            game.getPlayerFromColorOrFaction(debtToken), fowPlayer);
                        String controlID = hideFactionIcon ? Mapper.getControlID("gray")
                            : Mapper.getControlID(debtToken);
                        if (controlID.contains("null")) {
                            continue;
                        }
                        float scale = 0.80f;
                        BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                        DrawingUtil.drawControlToken(
                            graphics, controlTokenImage,
                            game.getPlayerFromColorOrFaction(debtToken), x + (count / 3) * 55,
                            y + tokenDeltaY - (count / 3) * 90,
                            hideFactionIcon, scale);
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
                y += 112;
                x -= lawWidth;
            }
        }
        return secondColumn ? y + 115 : y + 3;
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

            int x = positionPoint.x;
            int y = positionPoint.y;

            if (!"tl".equalsIgnoreCase(position) &&
                !"tr".equalsIgnoreCase(position) &&
                !"bl".equalsIgnoreCase(position) &&
                !"br".equalsIgnoreCase(position)) {
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }

            positionPoint = getTilePosition(position, x, y);
            int tileX = positionPoint.x + EXTRA_X - TILE_PADDING;
            int tileY = positionPoint.y + EXTRA_Y - TILE_PADDING;

            BufferedImage tileImage = new TileGenerator(game, event, displayType).draw(tile, step);
            graphics.drawImage(tileImage, tileX, tileY, null);
        } catch (Exception exception) {
            BotLogger.error(
                "Tile Error, when building map `" + game.getName() + "`, tile: " + tile.getTileID(),
                exception);
        }
    }

    private Point getTilePosition(String position, int x, int y) {
        int ringCount = game.getRingCount();
        ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);
        if (ringCount == RING_MIN_COUNT) {
            x += HORIZONTAL_TILE_SPACING;
        }
        if (ringCount < RING_MAX_COUNT) {
            int lower = RING_MAX_COUNT - ringCount;

            if ("tl".equalsIgnoreCase(position)) {
                y -= 150;
            } else if ("bl".equalsIgnoreCase(position)) {
                y -= lower * SPACE_FOR_TILE_HEIGHT * 2 - 150;
            } else if ("tr".equalsIgnoreCase(position)) {
                x -= lower * HORIZONTAL_TILE_SPACING * 2;
                y -= 150;
            } else if ("br".equalsIgnoreCase(position)) {
                x -= lower * HORIZONTAL_TILE_SPACING * 2;
                y -= lower * SPACE_FOR_TILE_HEIGHT * 2 - 150;
            } else {
                x -= lower * HORIZONTAL_TILE_SPACING;
                y -= lower * SPACE_FOR_TILE_HEIGHT;
            }
            return new Point(x, y);
        }
        return new Point(x, y);
    }

    public static int getRingCount(Game game) {
        return Math.max(Math.min(game.getRingCount(), RING_MAX_COUNT), RING_MIN_COUNT);
    }

    private static int getMapHeight(Game game) {
        int topMost = PositionMapper.getTopMostTileOffsetInGame(game);
        int bottomMost = PositionMapper.getBottomMostTileOffsetInGame(game);
        int topToBottomDistance = bottomMost - topMost;
        // return topToBottomDistance + SPACE_FOR_TILE_HEIGHT * 2 + EXTRA_Y * 2;
        return (getRingCount(game) + 1) * SPACE_FOR_TILE_HEIGHT * 2 + EXTRA_Y * 2;
    }

    private static int getMapPlayerCount(Game game) {
        return game.getRealPlayers().size() + game.getDummies().size();
    }

    private static boolean hasExtraRow(Game game) { // TODO: explain why this exists. Can we get rid of it?
        return (getMapHeight(game) - EXTRA_Y) < (getMapPlayerCount(game) / 2 * PLAYER_STATS_HEIGHT + EXTRA_Y);
    }

    private static int getMapWidth(Game game) {
        float ringCount = getRingCount(game);
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1; // make it thick if it's a 3-ring? why? player areas?
        int leftMost = PositionMapper.getLeftMostTileOffsetInGame(game);
        int rightMost = PositionMapper.getRightMostTileOffsetInGame(game);
        int leftToRightDistance = rightMost - leftMost;
        // int mapWidth = (int) (leftToRightDistance + EXTRA_X * 2);
        int mapWidth = (int) (ringCount * 520 + EXTRA_X * 2);
        mapWidth += hasExtraRow(game) ? EXTRA_X : 0;
        return mapWidth;
    }

    protected static int getMaxObjectiveWidth(Game game) {
        return (MapGenerator.getMapWidth(game) - MapGenerator.SPACING_BETWEEN_OBJECTIVE_TYPES * 4) / 3;
    }

    // The first parameter is the scale factor (contrast), the second is the offset
    // (brightness)
    private static BufferedImage makeGrayscale(BufferedImage image) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorConvertOp op = new ColorConvertOp(cs, null);
        return op.filter(image, null);
    }

    void addWebsiteOverlay(String overlayTitle, String overlayText, int x, int y, int width, int height) {
        addWebsiteOverlay(websiteOverlays, overlayTitle, overlayText, x, y, width, height);
    }

    void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        addWebsiteOverlay(websiteOverlays, dataModel, x, y, width, height);
    }

    public static void addWebsiteOverlay(
        List<WebsiteOverlay> overlays, ModelInterface dataModel, int x, int y,
        int width, int height
    ) {
        overlays.add(new WebsiteOverlay(dataModel, List.of(x, y, width, height)));
    }

    public static void addWebsiteOverlay(
        List<WebsiteOverlay> overlays, String title, String text, int x, int y, int w,
        int h
    ) {
        overlays.add(new WebsiteOverlay(title, text, List.of(x, y, w, h)));
    }

    String getGameName() {
        return game.getName();
    }
}
