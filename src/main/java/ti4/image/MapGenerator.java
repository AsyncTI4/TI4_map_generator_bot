package ti4.image;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
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
import java.util.Map.Entry;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.commands2.CommandHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CalendarHelper;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.WebHelper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.AgendaModel;
import ti4.model.BorderAnomalyHolder;
import ti4.model.ColorModel;
import ti4.model.EventModel;
import ti4.model.ExploreModel;
import ti4.model.LeaderModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.fow.UserOverridenSlashCommandInteractionEvent;
import ti4.service.image.FileUploadService;
import ti4.service.user.AFKService;
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
    private static final Color EliminatedColor = new Color(150, 0, 24); // Carmine
    private static final Color ActiveColor = new Color(80, 200, 120); // Emerald
    private static final Color PassedColor = new Color(220, 20, 60); // Crimson
    private static final Color Stage1RevealedColor = new Color(230, 126, 34);
    private static final Color LawColor = new Color(228, 255, 0);
    private static final Color TradeGoodColor = new Color(241, 176, 0);

    private final Graphics graphics;
    private final BufferedImage mainImage;
    private final GenericInteractionCreateEvent event;
    private final int scoreTokenSpacing;
    private final Game game;
    private final DisplayType displayType;
    private final DisplayType displayTypeBasic;
    private final boolean debug;
    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final boolean allEyesOnMe;

    private final List<WebsiteOverlay> websiteOverlays = new ArrayList<>();
    private int mapWidth;
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
        allEyesOnMe = this.displayType.equals(DisplayType.googly);
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
            } else if (player.getSecretsScored().size() == 4) {
                playerY += 23;
            } else if (player.getSecretsScored().size() > 4) {
                playerY += (player.getSecretsScored().size() - 4) * 43 + 23;
            }
            playerY += (player.getTeamMateIDs().size() - 1) * unrealPlayerHeight;
        }
        final int columnsOfLaws = 2;
        final int lawHeight = 115;
        int lawsY = (game.getLaws().size() / columnsOfLaws + 1) * lawHeight;
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
        FileUpload fileUpload = FileUploadService.createFileUpload(mainImage, 0.25f, game.getName());
        if (debug) debugDiscordTime.stop();
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
                    BotLogger.log("Hitting an error");
                }

                if (tileRingNumber > -1 && tileRingNumber <= ringCount && !tileMap.containsKey(position)) {
                    addTile(new Tile("0gray", position), TileStep.Tile);
                }
                if (tileRingNumber > -1 && tileRingNumber <= ringCount + 1 && !tileMap.containsKey(position)) {
                    addTile(new Tile("0border", position), TileStep.Tile);
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
                tilesToDisplay.put(key, fowPlayer.buildFogTile(key, fowPlayer));
            }
        }
    }

    private boolean isFowModeActive() {
        return game.isFowMode() && event != null &&
            (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL) ||
                event instanceof UserOverridenSlashCommandInteractionEvent);
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
            WebHelper.putMap(game.getName(), mainImage);
            WebHelper.putData(game.getName(), game);
            WebHelper.putOverlays(game.getID(), websiteOverlays);
        } else if (isFoWPrivate) {
            Player player = CommandHelper.getPlayerFromGame(game, event.getMember(), event.getUser().getId());
            WebHelper.putMap(game.getName(), mainImage, true, player);
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
        Coord coord;

        int deltaX = 0;
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        int yDelta = 0;

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
        coord = drawStrategyCards(coord(x, y));
        coord = coord(coord.x + 100, coord.y);

        // ROUND
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        String roundString = "ROUND: " + game.getRound();
        int roundLen = graphics.getFontMetrics().stringWidth(roundString);
        if (coord.x > mapWidth - roundLen - 100 * game.getRealPlayers().size()) {
            coord = coord(landscapeShift + 20, coord.y + 100);
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
            graphics.setFont(Storage.getFont32());
            int realX = x;
            Map<UnitKey, Integer> unitCount = new HashMap<>();

            // PLAYER AREAS
            for (Player player : players) {
                if (player == null) continue;
                int baseY = y;
                x = realX;

                boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                if (convertToGeneric) {
                    continue;
                }
                if ((game.isMinorFactionsMode() || player.getFaction() != null && player.getFaction().equalsIgnoreCase("neutral")) && player.isDummy()) {
                    continue;
                }

                // PAINT FACTION OR DISPLAY NAME
                List<String> teammateIDs = new ArrayList<>(player.getTeamMateIDs());
                teammateIDs.remove(player.getUserID());
                teammateIDs.addFirst(player.getUserID());

                // Faction/Colour/DisplayName
                // String factionText = player.getFactionModel() != null ? player.getFactionModel().getShortName() : player.getFaction(); //TODO use this but make it look better
                String factionText = StringUtils.capitalize(player.getFaction());
                if (player.getDisplayName() != null && !"null".equalsIgnoreCase(player.getDisplayName())) {
                    factionText = player.getDisplayName(); // overwrites faction
                }
                if (factionText != null && !"null".equalsIgnoreCase(factionText)) {
                    factionText = "[" + factionText + "]";
                }

                if (!"null".equals(player.getColor())) {
                    factionText += " (" + player.getColor() + ")"; // TODO: colour model display name
                }
                if ("null".equalsIgnoreCase(factionText)) {
                    factionText = "";
                }

                Color color = getColor(player.getColor());

                // Player/Teammate Names
                for (String teammateID : teammateIDs) {
                    User user = AsyncTI4DiscordBot.jda.getUserById(teammateID);

                    int leftJustified = x;
                    int topOfName = y + 10;

                    StringBuilder userName = new StringBuilder();
                    if (!game.hideUserNames() && game.getGuild() != null) {
                        Member member = game.getGuild().getMemberById(teammateID);
                        if (member == null) {
                            member = AsyncTI4DiscordBot.guildPrimary.getMemberById(teammateID);
                        }
                        userName.append(" ");

                        if (member != null) {
                            userName.append(member.getEffectiveName());
                        } else if (user != null) {
                            userName.append(user.getEffectiveName());
                        } else {
                            userName.append(player.getUserName());
                        }

                        leftJustified += 30; // to accommodate avater
                    }
                    if (AFKService.userIsAFK(teammateID)) {
                        userName.append(" -- AFK");
                    }

                    graphics.setFont(Storage.getFont32());
                    graphics.setColor(Color.WHITE);
                    int usernameWidth = graphics.getFontMetrics().stringWidth(userName.toString());
                    int factionTextWidth = graphics.getFontMetrics().stringWidth(factionText);
                    int maxWidthForPlayerNameBeforeLeaders = 715;

                    if (player.getUserID().equals(teammateID)) { // "real" player, first row
                        if (factionTextWidth + usernameWidth > maxWidthForPlayerNameBeforeLeaders) { // is a team, or too long, two lines
                            DrawingUtil.superDrawString(graphics, factionText, x, topOfName, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Top, stroke2, Color.BLACK);
                            y += 34;
                            DrawingUtil.superDrawString(graphics, userName.toString(), leftJustified, topOfName + 34, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Top, stroke2, Color.BLACK);
                        } else { // can one-line it
                            String fullText = userName.toString() + (factionText == null ? "" : " " + factionText);
                            DrawingUtil.superDrawString(graphics, fullText, leftJustified, topOfName, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Top, stroke2, Color.BLACK);
                        }
                    } else { // 2nd+ row, teammates - one-line it, just username
                        DrawingUtil.superDrawString(graphics, userName.toString(), leftJustified, topOfName, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Top, stroke2, Color.BLACK);
                    }

                    // Avatar
                    if (!game.hideUserNames()) {
                        graphics.drawImage(DrawingUtil.getUserDiscordAvatar(user), x, y + 5, null);
                    }

                    y += 34;
                }

                if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
                    y += 2;
                    continue;
                }

                // PAINT FACTION ICON
                y += 2;
                String faction = player.getFaction();
                if (faction != null) {
                    DrawingUtil.drawPlayerFactionIconImage(graphics, player, x, y, 95, 95);
                    if (!player.hasCustomFactionEmoji()) {
                        addWebsiteOverlay(player.getFactionModel(), x + 10, y + 10, 75, 75);
                    }
                }
                y += 4;

                // PAINT STRATEGY CARDS (SCs)
                Set<Integer> playerSCs = player.getSCs();
                if (playerSCs.size() == 1) {
                    int sc = playerSCs.stream().findFirst().get();
                    StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                    String scText = sc == 0 ? " " : Integer.toString(sc);
                    if (sc != 0) {
                        scText = game.getSCNumberIfNaaluInPlay(player, scText);
                    }
                    graphics.setColor(getSCColor(sc, game));
                    graphics.setFont(Storage.getFont64());

                    if (scText.contains("0/")) {
                        graphics.drawString("0", x + 90, y + 70 + yDelta);
                        graphics.setFont(Storage.getFont32());
                        graphics.setColor(Color.WHITE);
                        graphics.drawString(Integer.toString(sc), x + 120, y + 80 + yDelta);
                        if (getSCColor(sc, game).equals(Color.GRAY)) {
                            graphics.setFont(Storage.getFont24());
                            graphics.setColor(Color.RED);
                            graphics.drawString("X", x + 120, y + 80 + yDelta);
                        }
                    } else {
                        if (sc == ButtonHelper.getKyroHeroSC(game)) {
                            graphics.drawString("" + (game.getSCList().size() + 1), x + 90, y + 70 + yDelta);
                        } else {
                            DrawingUtil.superDrawString(graphics, scText, x + 120, y + 70, getSCColor(sc, game), HorizontalAlign.Center, VerticalAlign.Bottom, stroke2, Color.BLACK);
                            if (scModel != null) {
                                addWebsiteOverlay(scModel, x + 110, y + 20, 25, 50);
                                // graphics.drawRect(x + 110, y + 20, 25, 50); // debug
                            }
                            if (getSCColor(sc, game).equals(Color.GRAY)) {
                                graphics.setFont(Storage.getFont40());
                                DrawingUtil.superDrawString(graphics, "X", x + 120, y + 60, Color.RED, HorizontalAlign.Center, VerticalAlign.Bottom, stroke2, Color.BLACK);
                            }

                        }
                    }
                } else { // if (playerSCs.size() <= 4) {
                    int count = 1;
                    int row = 0;
                    int col = 0;
                    for (int sc : playerSCs) {
                        if (count == 5)
                            break;
                        switch (count) {
                            case 2 -> col = 1;
                            case 3 -> {
                                row = 1;
                                col = 0;
                            }
                            case 4 -> {
                                row = 1;
                                col = 1;
                            }
                        }
                        String scText = sc == 0 ? " " : Integer.toString(sc);
                        if (sc != 0) {
                            scText = game.getSCNumberIfNaaluInPlay(player, scText);
                        }
                        graphics.setColor(getSCColor(sc, game));

                        if (scText.contains("0/") && count == 1) {
                            graphics.setFont(Storage.getFont64());
                            graphics.drawString("0", x + 90, y + 70 + yDelta);
                            graphics.setFont(Storage.getFont32());
                            graphics.setColor(Color.WHITE);
                            graphics.drawString(Integer.toString(sc), x + 120, y + 80 + yDelta);
                        }

                        if (sc == ButtonHelper.getKyroHeroSC(game)) {
                            String kyroScNum = "" + (game.getSCList().size() + 1);
                            DrawingUtil.drawCenteredString(graphics, kyroScNum,
                                new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                Storage.getFont32());
                            if (getSCColor(sc, game).equals(Color.GRAY)) {
                                graphics.setColor(Color.RED);
                                DrawingUtil.drawCenteredString(graphics, "X",
                                    new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                    Storage.getFont24());
                            }
                        } else {
                            DrawingUtil.drawCenteredString(graphics, scText,
                                new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                Storage.getFont32());
                            if (getSCColor(sc, game).equals(Color.GRAY)) {
                                graphics.setColor(Color.RED);
                                DrawingUtil.drawCenteredString(graphics, "X",
                                    new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                    Storage.getFont24());
                            }
                        }

                        count++;
                    }
                }

                // Status
                String activePlayerID = game.getActivePlayerID();
                String phase = game.getPhaseOfGame();

                if (player.isEliminated()) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    DrawingUtil.superDrawString(g2, "ELIMINATED", 0, 0, EliminatedColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.isDummy()) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    DrawingUtil.superDrawString(g2, "DUMMY", 0, 0, EliminatedColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.isPassed()) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    DrawingUtil.superDrawString(g2, "PASSED", 0, 0, PassedColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    DrawingUtil.superDrawString(g2, "ACTIVE", 0, 0, ActiveColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                }

                // Eliminated Rectangle
                g2.setStroke(stroke5);
                if (player.isEliminated()) {
                    g2.setColor(color);
                    y += 95;
                    g2.drawRect(realX - 5, baseY, mapWidth - realX, y - baseY);
                    y += 15;
                    continue;
                }

                int xSpacer = 0;
                // Unfollowed SCs
                if (!player.getUnfollowedSCs().isEmpty()) {

                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(Color.RED);
                    graphics.drawString("Needs to Follow: ", x + 9, y + 125 + yDelta);
                    xSpacer = 165;
                    for (int sc : player.getUnfollowedSCs()) {
                        graphics.setColor(getSCColor(sc, game, true));
                        String drawText = String.valueOf(sc);
                        int len = graphics.getFontMetrics().stringWidth(drawText);
                        graphics.drawString(drawText, x + 9 + xSpacer, y + 125 + yDelta);
                        xSpacer += len + 8;
                    }
                }
                if (!game.isFowMode()) {
                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(Color.RED);
                    if (player.getNeighbouringPlayers().isEmpty()) {
                        graphics.drawString("No Neighbors", x + 9 + xSpacer, y + 125 + yDelta);
                        xSpacer = xSpacer + 115;
                    } else {
                        graphics.drawString("Neighbors: ", x + 9 + xSpacer, y + 125 + yDelta);
                        xSpacer = xSpacer + 115;
                        for (Player p2 : player.getNeighbouringPlayers()) {
                            String faction2 = p2.getFaction();
                            if (faction2 != null) {
                                DrawingUtil.drawPlayerFactionIconImage(graphics, p2, x + xSpacer, y + 125 + yDelta - 20, 26, 26);
                                xSpacer = xSpacer + 26;
                            }
                        }
                    }
                }

                // CCs
                graphics.setFont(Storage.getFont32());
                graphics.setColor(Color.WHITE);
                String ccCount = player.getCCRepresentation();
                x += 120;
                graphics.drawString(ccCount, x + 40, y + deltaY + 40);
                graphics.drawString("T/F/S", x + 40, y + deltaY);

                // Additional Fleet Supply
                int additionalFleetSupply = 0;
                if (player.hasAbility("edict")) {
                    additionalFleetSupply += player.getMahactCC().size();
                }
                if (player.hasAbility("armada")) {
                    additionalFleetSupply += 2;
                }
                if (additionalFleetSupply > 0) {
                    graphics.drawString("+" + additionalFleetSupply + " FS", x + 40, y + deltaY + 70);
                }

                // Cards
                String acImage = "pa_cardbacks_ac.png";
                String soImage = "pa_cardbacks_so.png";
                String pnImage = "pa_cardbacks_pn.png";
                String tradeGoodImage = "pa_cardbacks_tradegoods.png";
                String nomadCoinImage = "pa_cardbacks_nomadcoin.png";
                String commoditiesImage = "pa_cardbacks_commodities.png";
                drawPAImage(x + 150, y + yDelta, soImage);
                graphics.drawString(Integer.toString(player.getSo()), x + 170, y + deltaY + 50);

                drawPAImage(x + 215, y + yDelta, acImage);
                int ac = player.getAc();
                int acDelta = ac > 9 ? 0 : 10;
                graphics.drawString(Integer.toString(ac), x + 225 + acDelta, y + deltaY + 50);

                drawPAImage(x + 280, y + yDelta, pnImage);
                graphics.drawString(Integer.toString(player.getPnCount()), x + 300, y + deltaY + 50);

                // Trade Goods
                if (game.isNomadCoin()) {
                    drawPAImage(x + 345, y + yDelta, nomadCoinImage);
                } else {
                    drawPAImage(x + 345, y + yDelta, tradeGoodImage);
                }
                graphics.drawString(Integer.toString(player.getTg()), x + 360, y + deltaY + 50);

                // Comms
                drawPAImage(x + 410, y + yDelta, commoditiesImage);
                String comms = player.getCommodities() + "/" + player.getCommoditiesTotal();
                graphics.drawString(comms, x + 415, y + deltaY + 50);

                // Fragments
                int urf = player.getUrf();
                int irf = player.getIrf();
                String urfImage = "pa_fragment_urf.png";
                String irfImage = "pa_fragment_irf.png";
                int xDelta = 0;
                xDelta = drawFrags(y, x, yDelta, urf, urfImage, xDelta);
                xDelta += 25;
                xDelta = drawFrags(y, x, yDelta, irf, irfImage, xDelta);

                int xDelta2 = 0;
                int hrf = player.getHrf();
                int crf = player.getCrf();
                String hrfImage = "pa_fragment_hrf.png";
                String crfImage = "pa_fragment_crf.png";
                xDelta2 = drawFrags(y + 73, x, yDelta, hrf, hrfImage, xDelta2);
                xDelta2 += 25;
                xDelta2 = drawFrags(y + 73, x, yDelta, crf, crfImage, xDelta2);

                xDelta = x + 600;
                // xDelta = x + 550 + Math.max(xDelta, xDelta2); DISABLE AUTO-SCALE BASED ON
                // AMOUNT OF FRAGS - ALIGNS PLAYERS' LEADERS/PLANETS
                int yPlayArea = y - 30;
                y += 85;
                y += 200;

                // Secret Objectives
                int soCount = objectivesSO(yPlayArea + 170, player);

                int xDeltaSecondRow = xDelta;
                int yPlayAreaSecondRow = yPlayArea + 160;
                if (!player.getPlanets().isEmpty()) {
                    xDeltaSecondRow = planetInfo(player, xDeltaSecondRow, yPlayAreaSecondRow);
                }

                // FIRST ROW RIGHT SIDE
                int xDeltaFirstRowFromRightSide = 0;
                xDeltaFirstRowFromRightSide = unitValues(player, xDeltaFirstRowFromRightSide, yPlayArea);
                xDeltaFirstRowFromRightSide = nombox(player, xDeltaFirstRowFromRightSide, yPlayArea);
                xDeltaFirstRowFromRightSide = speakerToken(player, xDeltaFirstRowFromRightSide, yPlayArea);

                // SECOND ROW RIGHT SIDE
                int xDeltaSecondRowFromRightSide = 0;
                xDeltaSecondRowFromRightSide = reinforcements(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, unitCount);
                xDeltaSecondRowFromRightSide = sleeperTokens(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);
                xDeltaSecondRowFromRightSide = creussWormholeTokens(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);

                if (player.hasAbility("ancient_blueprints")) {
                    xDelta = bentorBluePrintInfo(player, xDelta, yPlayArea);
                }

                if (!player.getLeaders().isEmpty()) {
                    xDelta = leaderInfo(player, xDelta, yPlayArea, game);
                }

                if (player.getDebtTokens().values().stream().anyMatch(i -> i > 0)) {
                    xDelta = debtInfo(player, xDelta, yPlayArea, game);
                }

                if (!player.getRelics().isEmpty()) {
                    xDelta = relicInfo(player, xDelta, yPlayArea);
                }
                xDelta = omenDice(player, xDelta, yPlayArea);

                if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
                    xDelta = pnInfo(player, xDelta, yPlayArea, game);
                }

                // if (player.getTechs().isEmpty()) {
                xDelta = techInfo(player, xDelta, yPlayArea, game);
                // }

                if (!player.getNotResearchedFactionTechs().isEmpty()) {
                    xDelta = factionTechInfo(player, xDelta, yPlayArea);
                }

                if (!player.getAbilities().isEmpty()) {
                    xDelta = abilityInfo(player, xDelta, yPlayArea);
                }

                if (!player.getPromissoryNotesOwned().isEmpty()) {
                    xDelta = drawOwnedPromissoryNotes(player, xDelta, yPlayArea);
                }

                g2.setColor(color);
                if (soCount >= 4) {
                    y += 23;
                }
                if (soCount > 4) {
                    y += (soCount - 4) * 43;
                }

                // Draw Full Rect
                g2.drawRect(realX - 5, baseY, mapWidth - realX, y - baseY);
                y += 15;
            }
        }
    }

    private int speakerToken(Player player, int xDeltaFromRightSide, int yPlayAreaSecondRow) {
        if (player.getUserID().equals(game.getSpeakerUserID())) {
            xDeltaFromRightSide += 200;
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID(Constants.SPEAKER));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                graphics.drawImage(bufferedImage, width - xDeltaFromRightSide, yPlayAreaSecondRow + 25, null);
            }
        }
        return xDeltaFromRightSide;
    }

    private int displayRemainingFactionTokens(List<Point> points, BufferedImage img, int tokensRemaining, int xDeltaFromRight, int yDelta) {
        if (img != null) {
            int maxOffset = 0;
            for (int i = 0; i < tokensRemaining; i++)
                maxOffset = Math.max(maxOffset, points.get(i).x);

            xDeltaFromRight += maxOffset + img.getWidth() + 5;

            for (int i = 0; i < tokensRemaining; i++) {
                Point p = points.get(i);
                graphics.drawImage(img, width - xDeltaFromRight + p.x, yDelta + p.y, null);
            }
        }
        return xDeltaFromRight;
    }

    private int sleeperTokens(Player player, int xDeltaFromRightSide, int yDelta) {
        if (!player.hasAbility("awaken")) {
            return xDeltaFromRightSide;
        }
        String sleeperFile = ResourceHelper.getInstance().getTokenFile(Constants.TOKEN_SLEEPER_PNG);
        BufferedImage bufferedImage = ImageHelper.read(sleeperFile);

        List<Point> points = new ArrayList<>();
        points.add(new Point(0, 15));
        points.add(new Point(50, 0));
        points.add(new Point(50, 50));
        points.add(new Point(100, 25));
        points.add(new Point(10, 40));

        int numToDisplay = 5 - game.getSleeperTokensPlacedCount();
        return displayRemainingFactionTokens(points, bufferedImage, numToDisplay, xDeltaFromRightSide, yDelta);
    }

    private int creussWormholeTokens(Player player, int xDeltaSecondRowFromRightSide, int yPlayAreaSecondRow) {
        if (!player.getFaction().equalsIgnoreCase("ghost")) {
            return xDeltaSecondRowFromRightSide;
        }
        boolean alphaOnMap = false;
        boolean betaOnMap = false;
        boolean gammaOnMap = false;
        String alphaID = Mapper.getTokenID("creussalpha");
        String betaID = Mapper.getTokenID("creussbeta");
        String gammaID = Mapper.getTokenID("creussgamma");
        for (Tile tile : game.getTileMap().values()) {
            Set<String> tileTokens = tile.getUnitHolders().get("space").getTokenList();
            alphaOnMap |= tileTokens.contains(alphaID);
            betaOnMap |= tileTokens.contains(betaID);
            gammaOnMap |= tileTokens.contains(gammaID);
        }
        yPlayAreaSecondRow += 25;

        xDeltaSecondRowFromRightSide += (alphaOnMap && betaOnMap && gammaOnMap ? 0 : 90);
        boolean reconstruction = (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
        boolean travelBan = ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");

        if (!gammaOnMap) {
            String tokenFile = Mapper.getTokenPath(gammaID);
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            graphics.drawImage(bufferedImage, width - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            xDeltaSecondRowFromRightSide += 40;
            yPlayAreaSecondRow += alphaOnMap || betaOnMap ? 38 : 19;
        }

        if (!betaOnMap) {
            String tokenFile = Mapper.getTokenPath(betaID);
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            graphics.drawImage(bufferedImage, width - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            if (travelBan) {
                BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                graphics.drawImage(blockedWormholeImage, width - xDeltaSecondRowFromRightSide + 40, yPlayAreaSecondRow + 40, null);
            }
            if (reconstruction) {
                BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                graphics.drawImage(doubleWormholeImage, width - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            }
            xDeltaSecondRowFromRightSide += 40;
            yPlayAreaSecondRow += alphaOnMap || gammaOnMap ? 38 : 19;
        }

        if (!alphaOnMap) {
            String tokenFile = Mapper.getTokenPath(alphaID);
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            graphics.drawImage(bufferedImage, width - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            if (travelBan) {
                BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                graphics.drawImage(blockedWormholeImage, width - xDeltaSecondRowFromRightSide + 40, yPlayAreaSecondRow + 40, null);
            }
            if (reconstruction) {
                BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                graphics.drawImage(doubleWormholeImage, width - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            }
            xDeltaSecondRowFromRightSide += 40;
            yPlayAreaSecondRow += betaOnMap || gammaOnMap ? 38 : 19;
        }

        xDeltaSecondRowFromRightSide -= (alphaOnMap && betaOnMap && gammaOnMap ? 0 : 40);
        return xDeltaSecondRowFromRightSide;
    }

    private int omenDice(Player player, int x, int y) {
        int deltaX = 0;
        if (player.hasAbility("divination") && !ButtonHelperAbilities.getAllOmenDie(game).isEmpty()) {

            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(stroke2);

            for (int i = 0; i < ButtonHelperAbilities.getAllOmenDie(game).size(); i++) {
                String omen = "pa_ds_myko_omen_" + ButtonHelperAbilities.getAllOmenDie(game).get(i) + ".png";
                omen = omen.replace("10", "0");
                graphics.drawRect(x + deltaX - 2, y - 2, 52, 152);

                drawPAImage(x + deltaX, y, omen);
                deltaX += 56;
            }
            return x + deltaX + 20;
        }
        return x + deltaX;

    }

    private int bentorBluePrintInfo(Player player, int x, int y) {
        int deltaX = 0;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        graphics.setColor(Color.WHITE);
        String bluePrintFileNamePrefix = "pa_ds_bent_blueprint_";
        boolean hasFoundAny = false;
        if (player.hasFoundCulFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "crf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.hasFoundHazFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "hrf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.hasFoundIndFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "irf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.hasFoundUnkFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "urf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        return x + deltaX + (hasFoundAny ? 20 : 0);
    }

    private int pnInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        for (String pn : player.getPromissoryNotesInPlayArea()) {
            graphics.setColor(Color.WHITE);

            boolean commanderUnlocked = false;
            Player promissoryNoteOwner = game.getPNOwner(pn);
            if (promissoryNoteOwner == null) { // nobody owns this note - possibly eliminated player
                String error = game.getName() + " " + player.getUserName() + "  `GenerateMap.pnInfo` is trying to display a Promissory Note without an owner - possibly an eliminated player: " + pn;
                BotLogger.log(error);
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);

            if (pn.endsWith("_an")) { // Overlay for alliance commander
                if (promissoryNoteOwner.getLeader(Constants.COMMANDER).isPresent() &&
                    promissoryNoteOwner.getLeader(Constants.COMMANDER).get().getLeaderModel().isPresent()) {
                    LeaderModel leaderModel = promissoryNoteOwner.getLeader(Constants.COMMANDER).get().getLeaderModel().get();
                    drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, leaderModel);
                }
            } else {
                drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, promissoryNote);
            }

            for (Player player_ : player.getOtherRealPlayers()) {
                String playerColor = player_.getColor();
                String playerFaction = player_.getFaction();
                if (playerColor != null && playerColor.equals(promissoryNoteOwner.getColor())
                    || playerFaction != null && playerFaction.equals(promissoryNoteOwner.getFaction())) {
                    String pnColorFile = "pa_pn_color_" + Mapper.getColorID(playerColor) + ".png";
                    drawPAImage(x + deltaX, y, pnColorFile);
                    if (game.isFrankenGame()) {
                        drawFactionIconImage(graphics, promissoryNote.getFaction().orElse(""), x + deltaX - 1, y + 86, 42, 42);
                    }
                    DrawingUtil.drawPlayerFactionIconImage(graphics, promissoryNoteOwner, x + deltaX - 1, y + 108, 42, 42);
                    Leader leader = player_.unsafeGetLeader(Constants.COMMANDER);
                    if (leader != null) {
                        commanderUnlocked = !leader.isLocked();
                    }
                    break;
                }
            }

            graphics.setColor(Color.WHITE);
            if (pn.endsWith("_sftt")) {
                pn = "sftt";
            } else if (pn.endsWith("_an")) {
                pn = "alliance";
                if (!commanderUnlocked) {
                    pn += "_exh";
                    graphics.setColor(Color.GRAY);
                }
            }

            boolean isAttached = (promissoryNote != null && promissoryNote.getAttachment().isPresent()
                && !promissoryNote.getAttachment().get().isBlank());

            if (isAttached) {
                isAttached = false;
                String tokenID = promissoryNote.getAttachment().get();
                found: for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(tokenID))) {
                            isAttached = true;
                            PlanetModel p = Mapper.getPlanet(unitHolder.getName());
                            if (promissoryNote.getShrinkName() || p.getShrinkNamePNAttach()) {
                                graphics.setFont(Storage.getFont16());
                                drawOneOrTwoLinesOfTextVertically(graphics, "\n@" + p.getShortNamePNAttach(), x + deltaX + 9, y + 4, 120, true);
                            } else {
                                graphics.setFont(Storage.getFont18());
                                drawOneOrTwoLinesOfTextVertically(graphics, "\n@" + p.getShortNamePNAttach(), x + deltaX + 7, y + 4, 120, true);
                            }
                            break found;
                        }
                    }
                }
            }

            if (promissoryNote.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(graphics, promissoryNote.getShortName() + (isAttached ? "\n" : ""), x + deltaX + 9, y + 4, 120, true);
            } else {
                graphics.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(graphics, promissoryNote.getShortName() + (isAttached ? "\n" : ""), x + deltaX + 7, y + 4, 120, true);
            }
            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private int drawFrags(int y, int x, int yDelta, int urf, String urfImage, int xDelta) {
        for (int i = 0; i < urf; i++) {
            drawPAImage(x + 475 + xDelta, y + yDelta - 25, urfImage);
            xDelta += 15;
        }
        return xDelta;
    }

    private int relicInfo(Player player, int x, int y) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        List<String> relics = new ArrayList<>(player.getRelics());
        List<String> fakeRelics = relics.stream()
            .filter(relic -> Mapper.getRelic(relic).isFakeRelic())
            .filter(relic -> !relic.contains("axisorder"))
            .toList();
        List<String> axisOrderRelics = player.getRelics().stream()
            .filter(relic -> relic.contains("axisorder"))
            .toList();

        relics.removeAll(fakeRelics);
        relics.removeAll(axisOrderRelics);

        List<String> exhaustedRelics = player.getExhaustedRelics();

        int rectW = 44;
        int rectH = 152;
        int rectY = y - 2;
        for (String relicID : relics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            int rectX = x + deltaX - 2;
            drawRectWithOverlay(g2, rectX, rectY, rectW, rectH, relicModel);
            if (relicModel.getSource() == ComponentSource.absol) {
                drawPAImage(x + deltaX, y, "pa_source_absol.png");
            }
            drawPAImage(x + deltaX - 1, y - 2, "pa_relics_icon.png");

            String relicStatus = isExhausted ? "_exh" : "_rdy";

            // ABSOL QUANTUMCORE
            if (relicID.equals("absol_quantumcore")) {
                drawPAImage(x + deltaX, y, "pa_tech_techicons_cyberneticwarfare" + relicStatus + ".png");
            }
            if (relicID.equals("titanprototype") || relicModel.getHomebrewReplacesID().orElse("").equals("titanprototype")) {
                drawFactionIconImage(graphics, "relic", x + deltaX - 1, y + 108, 42, 42);
            }

            if (relicID.equals("emelpar") || relicModel.getHomebrewReplacesID().orElse("").equals("emelpar")) {
                StringBuilder empelar = new StringBuilder();
                List<Character> letters = Arrays.asList('m', 'e', 'l', 'p', 'a');
                Collections.shuffle(letters);
                for (Character c : letters) {
                    empelar.append(c);
                }
                empelar = new StringBuilder("Scepter of\nE" + empelar + "r");
                graphics.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(g2, empelar.toString(), x + deltaX + 7, y + 30, 120, true);
            } else if (relicModel.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(g2, relicModel.getShortName(), x + deltaX + 9, y + 30, 120, true);
            } else {
                graphics.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(g2, relicModel.getShortName(), x + deltaX + 7, y + 30, 120, true);
            }

            deltaX += 48;
        }

        // FAKE RELICS
        if (!fakeRelics.isEmpty()) {
            deltaX += 10;
        }
        for (String relicID : fakeRelics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, relicModel);

            drawPAImage(x + deltaX, y, "pa_relics_fakerelicicon.png");

            String relicStatus = isExhausted ? "_exh" : "_rdy";

            String relicFileName = "pa_relics_" + relicID + relicStatus + ".png";
            String resourcePath = ResourceHelper.getInstance().getPAResource(relicFileName);
            BufferedImage resourceBufferedImage;
            try {
                resourceBufferedImage = ImageHelper.read(resourcePath);
                if (resourceBufferedImage == null) {
                    g2.setFont(Storage.getFont20());
                    drawTwoLinesOfTextVertically(g2, relicModel.getShortName(), x + deltaX + 5, y + 140, 130);
                } else {
                    graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                }
            } catch (Exception e) {
                BotLogger.log("Bad file: " + relicFileName, e);
            }

            deltaX += 48;
        }

        // AXIS ORDER FAKE RELICS
        if (!axisOrderRelics.isEmpty()) {
            deltaX += 10;
        }
        for (String relicID : axisOrderRelics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 54, 152, relicModel);

            String relicStatus = isExhausted ? "_exh" : "_rdy";
            String relicFileName = "pa_relics_" + relicID + relicStatus + ".png";
            drawPAImage(x + deltaX, y, relicFileName);

            deltaX += 58;
        }
        return x + deltaX + 20;
    }

    private int leaderInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        Comparator<Leader> leaderComparator = (leader1, leader2) -> {
            int leaderRank1 = switch (leader1.getType()) {
                case Constants.AGENT -> 0;
                case Constants.ENVOY -> 1;
                case Constants.COMMANDER -> 2;
                case Constants.HERO -> 3;
                default -> -1;
            };
            int leaderRank2 = switch (leader2.getType()) {
                case Constants.AGENT -> 0;
                case Constants.ENVOY -> 1;
                case Constants.COMMANDER -> 2;
                case Constants.HERO -> 3;
                default -> -1;
            };
            if (leaderRank1 == leaderRank2) {
                return Mapper.getLeader(leader1.getId()).getName().compareToIgnoreCase(Mapper.getLeader(leader2.getId()).getName());
            }
            return leaderRank1 - leaderRank2;
        };
        List<Leader> allLeaders = new ArrayList<>(player.getLeaders());
        allLeaders.sort(leaderComparator);

        for (Leader leader : allLeaders) {
            boolean isExhaustedLocked = leader.isExhausted() || leader.isLocked();
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            String status = isExhaustedLocked ? "_exh" : "_rdy";
            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, Mapper.getLeader(leader.getId()));

            if (Mapper.isValidLeader(leader.getId())) {
                LeaderModel leaderModel = Mapper.getLeader(leader.getId());
                drawFactionIconImage(graphics, leaderModel.getFaction(), x + deltaX - 1, y + 108, 42, 42);
            }

            if (leader.getTgCount() != 0) {
                graphics.setColor(TradeGoodColor);
                graphics.setFont(Storage.getFont32());
                int offset = 20 - graphics.getFontMetrics().stringWidth("" + leader.getTgCount()) / 2;
                graphics.drawString(Integer.toString(leader.getTgCount()), x + deltaX + offset, y + 25);
            } else {
                String pipID;
                switch (leader.getType()) {
                    case Constants.AGENT -> pipID = "i";
                    case Constants.COMMANDER -> pipID = "ii";
                    case Constants.HERO -> pipID = "iii";
                    case Constants.ENVOY -> pipID = "agenda";
                    default -> pipID = "";
                }

                if (!pipID.isEmpty()) {
                    String leaderPipInfo = "pa_leaders_pips_" + pipID;
                    if (!isExhaustedLocked && leader.isActive()) {
                        leaderPipInfo += "_active" + ".png";
                    } else {
                        leaderPipInfo += status + ".png";
                    }
                    drawPAImage(x + deltaX, y, leaderPipInfo);
                }
            }

            LeaderModel leaderModel = Mapper.getLeader(leader.getId());
            if (leader.getId().equalsIgnoreCase("yssarilagent")) {
                drawTextVertically(g2, "Clever, Clever".toUpperCase(), x + deltaX + 8, y + 30, Storage.getFont14(), true);
                drawTextVertically(g2, "Ssruu".toUpperCase(), x + deltaX + 23, y + 30, Storage.getFont18(), true);
            } else if (leaderModel.getShrinkName()) {
                g2.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(g2, leaderModel.getShortName(), x + deltaX + 9, y + 30, 120, true);
            } else {
                g2.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(g2, leaderModel.getShortName(), x + deltaX + 7, y + 30, 120, true);
            }

            deltaX += 48;
        }

        if (player.hasAbility("imperia")) {
            deltaX += 5;
            List<String> mahactCCs = player.getMahactCC();
            Collection<Player> players = game.getRealPlayersNDummies();
            if (game.isMinorFactionsMode()) {
                players = game.getRealPlayers();
            }
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    if (mahactCCs.contains(playerColor)) {
                        Leader leader_ = player_.unsafeGetLeader(Constants.COMMANDER);
                        if (leader_ != null) {
                            String status_ = leader_.isLocked() ? "_exh" : "_rdy";
                            String imperiaColorFile = "pa_leaders_imperia" + status_ + ".png";
                            drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, Mapper.getLeader(leader_.getId()));
                            DrawingUtil.drawPlayerFactionIconImage(graphics, player_, x + deltaX - 1, y + 108, 42, 42);
                            drawPAImage(x + deltaX, y, imperiaColorFile);
                            String leaderPipInfo = "pa_leaders_pips_ii" + status_ + ".png";
                            drawPAImage(x + deltaX, y, leaderPipInfo);
                            deltaX += 48;
                        }
                    }
                }
            }
        }

        return x + deltaX + 20;
    }

    private int debtInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;
        int deltaY = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        String bankImage = "vaden".equalsIgnoreCase(player.getFaction()) ? "pa_ds_vaden_bank.png"
            : "pa_debtaccount.png";
        drawPAImage(x + deltaX, y, bankImage);

        deltaX += 24;
        deltaY += 2;

        int tokenDeltaY = 0;
        int playerCount = 0;
        int maxTokenDeltaX = 0;
        for (Entry<String, Integer> debtToken : player.getDebtTokens().entrySet()) {
            Player debtPlayer = game.getPlayerByColorID(Mapper.getColorID(debtToken.getKey())).orElse(null);
            boolean hideFactionIcon = isFoWPrivate && debtPlayer != null && !FoWHelper.canSeeStatsOfPlayer(game, debtPlayer, fowPlayer);

            int tokenDeltaX = 0;
            String controlID = hideFactionIcon ? Mapper.getControlID("gray") : Mapper.getControlID(debtToken.getKey());
            if (controlID.contains("null")) {
                continue;
            }

            float scale = 0.60f;
            BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);

            for (int i = 0; i < debtToken.getValue(); i++) {
                DrawingUtil.drawControlToken(graphics, controlTokenImage,
                    DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID), x + deltaX + tokenDeltaX,
                    y + deltaY + tokenDeltaY,
                    hideFactionIcon, scale);
                tokenDeltaX += 15;
            }

            tokenDeltaY += 29;
            maxTokenDeltaX = Math.max(maxTokenDeltaX, tokenDeltaX + 35);
            playerCount++;
            if (playerCount % 5 == 0) {
                tokenDeltaY = 0;
                deltaX += maxTokenDeltaX;
                maxTokenDeltaX = 0;
            }
        }
        deltaX = Math.max(deltaX + maxTokenDeltaX, 152);
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x - 2, y - 2, deltaX, 152);

        return x + deltaX + 10;
    }

    @SuppressWarnings("unused") // TODO (Jazz): figure out why I added this function
    private static void drawControlToken(Graphics graphics, Player p, int x, int y, boolean hideFactionIcon, float scale) {
        String colorID = p == null ? "gray" : p.getColor();
        BufferedImage controlToken = ImageHelper.readScaled(Mapper.getCCPath(Mapper.getControlID(colorID)), scale);
        DrawingUtil.drawControlToken(graphics, controlToken, p, x, y, hideFactionIcon, scale);
    }

    private int abilityInfo(Player player, int x, int y) {
        int deltaX = 10;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        boolean addedAbilities = false;
        Comparator<String> abilityComparator = (ability1, ability2) -> {
            AbilityModel abilityModel1 = Mapper.getAbility(ability1);
            AbilityModel abilityModel2 = Mapper.getAbility(ability2);
            return abilityModel1.getName().compareToIgnoreCase(abilityModel2.getName());
        };
        List<String> allAbilities = new ArrayList<>(player.getAbilities());
        allAbilities.sort(abilityComparator);

        for (String abilityID : allAbilities) {
            String abilityFileName = null;
            switch (abilityID) {
                case "grace" -> abilityFileName = "pa_ds_edyn_grace";
                case "policy_the_people_connect" -> abilityFileName = "pa_ds_olra_policy_cpos";
                case "policy_the_people_control" -> abilityFileName = "pa_ds_olra_policy_cneg";
                case "policy_the_environment_preserve" -> abilityFileName = "pa_ds_olra_policy_hpos";
                case "policy_the_environment_plunder" -> abilityFileName = "pa_ds_olra_policy_hneg";
                case "policy_the_economy_empower" -> abilityFileName = "pa_ds_olra_policy_ipos";
                case "policy_the_economy_exploit" -> abilityFileName = "pa_ds_olra_policy_ineg";
            }

            boolean isExhaustedLocked = player.getExhaustedAbilities().contains(abilityID);
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            AbilityModel abilityModel = Mapper.getAbility(abilityID);
            if (abilityFileName != null) {
                String status = isExhaustedLocked ? "_exh" : "_rdy";
                abilityFileName += status + ".png";
                String resourcePath = ResourceHelper.getInstance().getPAResource(abilityFileName);

                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, abilityModel);
            } else {
                drawFactionIconImage(g2, abilityModel.getFaction(), x + deltaX - 1, y, 42, 42);
                g2.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(g2, abilityModel.getShortName(), x + deltaX + 7, y + 144, 130);
                drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, abilityModel);
            }

            deltaX += 48;
            addedAbilities = true;
        }
        return x + deltaX + (addedAbilities ? 20 : 0);
    }

    private int drawOwnedPromissoryNotes(Player player, int x, int y) {
        int deltaX = 10;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        boolean addedPNs = false;
        Comparator<String> pnComparator = (id1, id2) -> {
            PromissoryNoteModel model1 = Mapper.getPromissoryNote(id1);
            PromissoryNoteModel model2 = Mapper.getPromissoryNote(id2);
            return model1.getName().compareToIgnoreCase(model2.getName());
        };
        List<String> ownedPNs = new ArrayList<>(player.getPromissoryNotesOwned());
        ownedPNs.sort(pnComparator);

        for (String pnID : ownedPNs) {
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnID);
            if (!game.isShowOwnedPNsInPlayerArea() && promissoryNote.getFaction().isEmpty() && !promissoryNote.getPlayImmediately()) {
                continue;
            }

            if (promissoryNote.getSource() == ComponentSource.promises_promises) {
                drawPAImageScaled(x + deltaX + 1, y + 1, "pa_promissory_light_pp.png", 38, 28);
            } else {
                drawPAImageScaled(x + deltaX + 1, y + 1, "pa_promissory_light.png", 38, 28);
            }
            if (game.isFrankenGame() && promissoryNote.getFaction().isPresent()) {
                drawFactionIconImage(graphics, promissoryNote.getFaction().get(), x + deltaX - 1, y + 108, 42, 42);
            }
            Player playerWhoHasIt = null;
            if (!game.isFowMode() && promissoryNote.getPlayArea()) {
                found: for (Player player_ : game.getRealPlayers()) {
                    for (String pn_ : player_.getPromissoryNotesInPlayArea()) {
                        if (pn_.equals(pnID)) {
                            playerWhoHasIt = player_;
                            break found;
                        }
                    }
                }
            }
            graphics.setColor(playerWhoHasIt != null ? Color.GRAY : Color.WHITE);

            if (pnID.equals("dspntnel")) { // for some reason "Plots Within Plots" gets cut off weirdly if handled normally
                graphics.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(graphics, "Plots Within Plots", x + deltaX + 9, y + 144, 150);
            } else if (promissoryNote.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(graphics, promissoryNote.getShortName(), x + deltaX + 9, y + 144, 120);
            } else {
                graphics.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(graphics, promissoryNote.getShortName(), x + deltaX + 7, y + 144, 120);
            }
            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, promissoryNote);
            DrawingUtil.drawPlayerFactionIconImageOpaque(g2, playerWhoHasIt, x + deltaX - 1, y + 25, 42, 42, 0.5f);

            deltaX += 48;
            addedPNs = true;
        }
        return x + deltaX + (addedPNs ? 20 : 0);
    }

    private int reinforcements(Player player, int xDeltaFromRightSide, int y, Map<UnitKey, Integer> unitMapCount) {
        Map<String, Tile> tileMap = game.getTileMap();
        int x = width - 450 - xDeltaFromRightSide;
        drawPAImage(x, y, "pa_reinforcements.png");
        if (unitMapCount.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    fillUnits(unitMapCount, unitHolder, false);
                }
            }
            for (Player player_ : game.getPlayers().values()) {
                UnitHolder nombox = player_.getNomboxTile().getSpaceUnitHolder();
                if (nombox == null) continue;
                fillUnits(unitMapCount, nombox, true);
            }
        }

        String playerColor = player.getColor();
        for (String unitID : Mapper.getUnitIDList()) {
            UnitKey unitKey = Mapper.getUnitKey(unitID, playerColor);
            if ("csd".equals(unitID)) continue;
            if ("cff".equals(unitID))
                unitKey = Mapper.getUnitKey("ff", playerColor);
            if ("cgf".equals(unitID))
                unitKey = Mapper.getUnitKey("gf", playerColor);

            int count = unitMapCount.getOrDefault(unitKey, 0);
            if ((player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) && "sd".equals(unitID)) {
                count += unitMapCount.getOrDefault(Mapper.getUnitKey("csd", playerColor), 0);
            }

            UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(unitID);

            if (reinforcementsPosition != null) {
                int unitCap = player.getUnitCap(unitID);
                boolean onlyPaintOneUnit = true;
                if (unitCap == 0) {
                    unitCap = reinforcementsPosition.getPositionCount(unitID);
                    onlyPaintOneUnit = false;
                }

                int numInReinforcements = unitCap - count;
                BufferedImage image = ImageHelper.read(getUnitPath(unitKey));
                String decal = player.getDecalFile(unitID);
                BufferedImage decalImage = null;
                if (decal != null) {
                    decalImage = ImageHelper.read(ResourceHelper.getInstance().getDecalFile(decal));
                }
                for (int i = 0; i < numInReinforcements; i++) {
                    Point position = reinforcementsPosition.getPosition(unitID);
                    graphics.drawImage(image, x + position.x, y + position.y, null);
                    if (decalImage != null) {
                        graphics.drawImage(decalImage, x + position.x, y + position.y, null);
                    }
                    if (onlyPaintOneUnit) break;
                }
                String unitName = unitKey.getUnitType().humanReadableName();
                if (numInReinforcements < 0 && game.isCcNPlasticLimit()) {
                    String warningMessage = player.getRepresentation() + " is exceeding unit plastic or cardboard limits for " + unitName + ". Use buttons to remove";
                    List<Button> removeButtons = ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, unitKey.asyncID());
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), warningMessage, removeButtons);
                }

                if (numInReinforcements > -10) {
                    paintNumber(unitID, x, y, numInReinforcements, playerColor);
                }
            }
        }

        int ccCount = Helper.getCCCount(game, playerColor);
        String CC_TAG = "cc";
        UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(CC_TAG);
        if (reinforcementsPosition != null && playerColor != null) {
            int positionCount = reinforcementsPosition.getPositionCount(CC_TAG);
            if (!game.getStoredValue("ccLimit").isEmpty()) {
                positionCount = Integer.parseInt(game.getStoredValue("ccLimit"));
            }
            int remainingReinforcements = positionCount - ccCount;
            if (remainingReinforcements > 0) {
                for (int i = 0; i < remainingReinforcements && i < 16; i++) {
                    try {
                        String ccID = Mapper.getCCID(playerColor);
                        Point position = reinforcementsPosition.getPosition(CC_TAG);
                        DrawingUtil.drawCCOfPlayer(graphics, ccID, x + position.x, y + position.y, 1, player, false);
                    } catch (Exception e) {
                        BotLogger.log("Could not parse file for CC: " + playerColor, e);
                    }
                }
            }
            if (-5 <= remainingReinforcements) {
                paintNumber(CC_TAG, x, y, remainingReinforcements, playerColor);
            }
        }
        return xDeltaFromRightSide + 450;
    }

    private static void fillUnits(Map<UnitKey, Integer> unitCount, UnitHolder unitHolder, boolean ignoreInfantryFighters) {
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey uk = unitEntry.getKey();
            int count = unitCount.getOrDefault(uk, 0);

            if (uk.getUnitType() == UnitType.Infantry || uk.getUnitType() == UnitType.Fighter) {
                if (ignoreInfantryFighters) continue;
                count++;
            } else {
                count += unitEntry.getValue();
            }
            unitCount.put(uk, count);
        }
    }

    public static boolean isWholeNumber(float number) {
        return number == Math.floor(number);
    }

    private int unitValues(Player player, int xDeltaFromRightSide, int y) {
        int widthOfSection = 180;
        int leftSide = width - widthOfSection - xDeltaFromRightSide;
        int verticalSpacing = 39;
        addWebsiteOverlay("Fleet Stats", "- Total Resources\n- Total Hit Points\n- Total Expected Hits", leftSide, y + 10, widthOfSection - 10, verticalSpacing * 4 - 10);
        int imageSize = verticalSpacing - 2;
        drawPAImageScaled(leftSide, y + verticalSpacing, "pa_resources.png", imageSize);
        drawPAImageScaled(leftSide, y + verticalSpacing * 2, "pa_health.png", imageSize);
        drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_hit.png", imageSize);
        //drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_hit.png", imageSize);
        //drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_unitimage.png", imageSize);
        graphics.setColor(Color.WHITE);
        leftSide += verticalSpacing + 10;
        DrawingUtil.drawCenteredString(graphics, "Space |", new Rectangle(leftSide - 4, y, 50, verticalSpacing), Storage.getFont18());
        DrawingUtil.drawCenteredString(graphics, "____________", new Rectangle(leftSide, y, 110, verticalSpacing), Storage.getFont24());
        float val = player.getTotalResourceValueOfUnits("space");
        if (isWholeNumber(val) || val > 10) {
            DrawingUtil.drawCenteredString(graphics, String.valueOf((int) val), new Rectangle(leftSide, y + verticalSpacing, 50, verticalSpacing), Storage.getFont24());
        } else {
            DrawingUtil.drawCenteredString(graphics, String.valueOf(val), new Rectangle(leftSide, y + verticalSpacing, 50, verticalSpacing), Storage.getFont24());
        }
        DrawingUtil.drawCenteredString(graphics, String.valueOf(player.getTotalHPValueOfUnits("space")), new Rectangle(leftSide, y + verticalSpacing * 2, 50, verticalSpacing), Storage.getFont24());
        DrawingUtil.drawCenteredString(graphics, String.valueOf(player.getTotalCombatValueOfUnits("space")), new Rectangle(leftSide, y + verticalSpacing * 3, 50, verticalSpacing), Storage.getFont24());
        leftSide += verticalSpacing + 20;
        DrawingUtil.drawCenteredString(graphics, "  Ground", new Rectangle(leftSide, y, 50, verticalSpacing), Storage.getFont18());
        // DrawingUtil.drawCenteredString(graphics, String.valueOf(player.getTotalResourceValueOfUnits("ground")),
        //     new Rectangle(leftSide, y + verticalSpacing * 1, 50, verticalSpacing), Storage.getFont24());
        val = player.getTotalResourceValueOfUnits("ground");
        if (isWholeNumber(val) || val > 10) {
            DrawingUtil.drawCenteredString(graphics, String.valueOf((int) val), new Rectangle(leftSide, y + verticalSpacing, 50, verticalSpacing), Storage.getFont24());
        } else {
            DrawingUtil.drawCenteredString(graphics, String.valueOf(val), new Rectangle(leftSide, y + verticalSpacing, 50, verticalSpacing), Storage.getFont24());
        }
        DrawingUtil.drawCenteredString(graphics, String.valueOf(player.getTotalHPValueOfUnits("ground")), new Rectangle(leftSide, y + verticalSpacing * 2, 50, verticalSpacing), Storage.getFont24());
        DrawingUtil.drawCenteredString(graphics, String.valueOf(player.getTotalCombatValueOfUnits("ground")), new Rectangle(leftSide, y + verticalSpacing * 3, 50, verticalSpacing), Storage.getFont24());
        //DrawingUtil.drawCenteredString(graphics, String.valueOf(player.getTotalUnitAbilityValueOfUnits()),
        //    new Rectangle(leftSide, y + verticalSpacing * 3, 50, verticalSpacing), Storage.getFont24());
        return xDeltaFromRightSide + widthOfSection;
    }

    private int nombox(Player player, int xDeltaFromRightSide, int y) {
        int widthOfNombox = 450;
        int x = width - widthOfNombox - xDeltaFromRightSide;
        UnitHolder unitHolder = player.getNomboxTile().getUnitHolders().get(Constants.SPACE);
        if (unitHolder == null || unitHolder.getUnits().isEmpty()) {
            return xDeltaFromRightSide;
        }

        Point infPoint = new Point(50, 75);
        Point fighterPoint = new Point(50, 125);
        Point mechPoint = new Point(100, 63);
        Point destroyerPoint = new Point(144, 63);
        Point cruiserPoint = new Point(185, 55);
        Point carrierPoint = new Point(235, 58);
        Point dreadnoughtPoint = new Point(284, 54);
        Point flagshipPoint = new Point(335, 47);
        Point warSunPoint = new Point(393, 56);

        String faction = player.getFaction();
        if (faction != null) {
            BufferedImage bufferedImage = DrawingUtil.getPlayerFactionIconImage(player);
            if (bufferedImage != null) {
                graphics.drawImage(bufferedImage, x + 178, y + 33, null);
            }
        }

        drawPAImage(x, y, "pa_nombox.png");

        Map<UnitKey, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        Map<UnitKey, Integer> units = new LinkedHashMap<>();

        for (Map.Entry<UnitKey, Integer> entry : tempUnits.entrySet()) {
            UnitKey id = entry.getKey();
            if (id.getUnitType() == UnitType.Mech) {
                units.put(id, entry.getValue());
            }
        }

        for (UnitKey key : units.keySet()) {
            tempUnits.remove(key);
        }
        units.putAll(tempUnits);

        BufferedImage image = null;

        List<UnitType> order = List.of(
            UnitType.Mech,
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun,
            UnitType.Fighter,
            UnitType.Infantry);

        Map<UnitType, List<Map.Entry<UnitKey, Integer>>> collect = units.entrySet().stream()
            .collect(Collectors.groupingBy(key -> key.getKey().getUnitType()));
        for (UnitType orderKey : order) {
            List<Map.Entry<UnitKey, Integer>> entry = collect.get(orderKey);
            if (entry == null) {
                continue;
            }

            int countOfUnits = 0;
            for (Map.Entry<UnitKey, Integer> entrySet : entry) {
                countOfUnits += entrySet.getValue();
            }
            int deltaY = 0;
            for (Map.Entry<UnitKey, Integer> unitEntry : entry) {
                UnitKey unitKey = unitEntry.getKey();
                Integer unitCount = unitEntry.getValue();
                Integer bulkUnitCount = null;
                Player p = game.getPlayerFromColorOrFaction(unitKey.getColor());

                try {
                    String unitPath = getUnitPath(unitKey);
                    if (unitPath != null) {
                        if (unitKey.getUnitType() == UnitType.Fighter) {
                            unitPath = unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
                            bulkUnitCount = unitCount;
                        } else if (unitKey.getUnitType() == UnitType.Infantry) {
                            unitPath = unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
                            bulkUnitCount = unitCount;
                        }
                    }
                    image = ImageHelper.read(unitPath);
                } catch (Exception e) {
                    BotLogger.log("Could not parse unit file for: " + unitKey + " in game " + game.getName(), e);
                }
                if (bulkUnitCount != null && bulkUnitCount > 0) {
                    unitCount = 1;
                }
                if (image == null) {
                    BotLogger.log("Could not find unit image for: " + unitKey);
                    continue;
                }

                if (unitCount == null) {
                    unitCount = 0;
                }

                Point position = new Point(x, y);
                boolean justNumber = false;
                switch (unitKey.getUnitType()) {
                    case Fighter -> {
                        position.translate(fighterPoint.x, fighterPoint.y);
                        justNumber = true;
                    }
                    case Infantry -> {
                        position.translate(infPoint.x, infPoint.y);
                        justNumber = true;
                    }
                    case Destroyer -> position.translate(destroyerPoint.x, destroyerPoint.y);
                    case Cruiser -> position.translate(cruiserPoint.x, cruiserPoint.y);
                    case Carrier -> position.translate(carrierPoint.x, carrierPoint.y);
                    case Dreadnought -> position.translate(dreadnoughtPoint.x, dreadnoughtPoint.y);
                    case Flagship -> position.translate(flagshipPoint.x, flagshipPoint.y);
                    case Warsun -> position.translate(warSunPoint.x, warSunPoint.y);
                    case Mech -> position.translate(mechPoint.x, mechPoint.y);
                    default -> {
                    }
                }

                BufferedImage spoopy = null;
                if (unitKey.getUnitType() == UnitType.Warsun) {
                    int chanceToSeeSpoop = CalendarHelper.isNearHalloween() ? 10 : 1000;
                    if (ThreadLocalRandom.current().nextInt(chanceToSeeSpoop) == 0) {
                        String spoopyPath = ResourceHelper.getInstance().getSpoopyFile();
                        spoopy = ImageHelper.read(spoopyPath);
                    }
                }

                if (justNumber) {
                    graphics.setFont(Storage.getFont40());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(Integer.toString(countOfUnits), position.x, position.y);
                    break;
                }
                position.y -= (countOfUnits * 7);

                Optional<BufferedImage> decal = Optional.ofNullable(p)
                    .map(player1 -> player1.getDecalFile(unitKey.asyncID()))
                    .map(decalFileName -> ImageHelper.read(ResourceHelper.getInstance().getDecalFile(decalFileName)));

                for (int i = 0; i < unitCount; i++) {
                    graphics.drawImage(image, position.x, position.y + deltaY, null);
                    if (decal.isPresent() && !List.of(UnitType.Fighter, UnitType.Infantry).contains(unitKey.getUnitType())) {
                        graphics.drawImage(decal.get(), position.x, position.y + deltaY, null);
                    }
                    if (spoopy != null) {
                        graphics.drawImage(spoopy, position.x, position.y + deltaY, null);
                    }

                    deltaY += 14;
                }
            }
        }
        return xDeltaFromRightSide + widthOfNombox;
    }

    private void paintNumber(String unitID, int x, int y, int reinforcementsCount, String color) {
        String id = "number_" + unitID;
        UnitTokenPosition textPosition = PositionMapper.getReinforcementsPosition(id);
        if (textPosition == null) {
            return;
        }
        Point position = textPosition.getPosition(id);

        graphics.setFont(Storage.getFont35());
        Integer offset = 20 - graphics.getFontMetrics().stringWidth("" + reinforcementsCount) / 2;
        if (reinforcementsCount <= 0) {
            graphics.setColor(Color.YELLOW);
        } else {
            String colorID = Mapper.getColorID(color);
            graphics.setColor("_blk.png".equalsIgnoreCase(DrawingUtil.getBlackWhiteFileSuffix(colorID)) ? Color.WHITE : Color.BLACK);
        }
        for (int i = -2; i <= 2; i++) {
            for (int j = (i == -2 || i == 2 ? -1 : -2); j <= (i == -2 || i == 2 ? 1 : 2); j++) {
                graphics.drawString("" + reinforcementsCount, x + position.x + offset + i, y + position.y + j + 28);
            }
        }
        if (reinforcementsCount <= 0) {
            graphics.setColor(Color.RED);
        } else {
            String colorID = Mapper.getColorID(color);
            graphics.setColor("_blk.png".equalsIgnoreCase(DrawingUtil.getBlackWhiteFileSuffix(colorID)) ? Color.BLACK : Color.WHITE);
        }
        graphics.drawString("" + reinforcementsCount, x + position.x + offset, y + position.y + 28);
    }

    private int planetInfo(Player player, int x, int y) {
        List<String> planets = player.getPlanets();
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        // RESOURCE/INFLUENCE TOTALS
        drawPAImage(x + deltaX - 2, y - 2, "pa_resinf_info.png");
        graphics.setColor(Color.WHITE);
        drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 152, 152, "Resource & Influence Summary", "This is an overview of your resources and influence. The left side is resources, and the right side is influence.\nThe top number how many you have available\nThe middle number is the total\nThe bottom number is the 'optimal' available\nThe bottom-centre number is the flex 'optimal' available");
        if (player.hasLeaderUnlocked("xxchahero")) { // XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
            if (Constants.gedsDeadId.equals(player.getUserID()) || RandomHelper.isOneInX(100)) {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha_gedsdead.png", 0.9f);
            } else {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha.png", 0.9f);
            }
            drawFactionIconImageOpaque(graphics, "xxcha", x + deltaX + 75 - 94 / 2, y + 75 - 94 / 2, 95, 95, 0.15f);
            graphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(graphics, String.valueOf(availablePlayerResources),
                new Rectangle(x + deltaX, y + 75 - 35 + 5, 150, 35), Storage.getFont35());
            graphics.setColor(Color.GRAY);
            DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerResources),
                new Rectangle(x + deltaX, y + 75 + 5, 150, 24), Storage.getFont24());
        } else { // NOT XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
            int availablePlayerResourcesOptimal = Helper.getPlayerOptimalResourcesAvailable(player, game);
            // int totalPlayerResourcesOptimal =
            // Helper.getPlayerOptimalResourcesTotal(player, map);
            int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, game);
            int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, game);
            int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, game);
            // int totalPlayerInfluenceOptimal =
            // Helper.getPlayerOptimalInfluenceTotal(player, map);
            int availablePlayerFlex = Helper.getPlayerFlexResourcesInfluenceAvailable(player, game);
            // int totalPlayerFlex = Helper.getPlayerFlexResourcesInfluenceTotal(player,
            // map);

            // RESOURCES
            graphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(graphics, String.valueOf(availablePlayerResources),
                new Rectangle(x + deltaX + 30, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerResources),
                new Rectangle(x + deltaX + 30, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#d5bd4f")); // greyish-yellow
            DrawingUtil.drawCenteredString(graphics, String.valueOf(availablePlayerResourcesOptimal),
                new Rectangle(x + deltaX + 30, y + 90, 32, 32), Storage.getFont18());
            // DrawingUtil.drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 30, y + 100,
            // 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerResourcesOptimal), new
            // Rectangle(x + deltaX + 34, y + 109, 32, 32), Storage.getFont32());

            // INFLUENCE
            graphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(graphics, String.valueOf(availablePlayerInfluence),
                new Rectangle(x + deltaX + 90, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerInfluence),
                new Rectangle(x + deltaX + 90, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#57b9d9")); // greyish-blue
            DrawingUtil.drawCenteredString(graphics, String.valueOf(availablePlayerInfluenceOptimal),
                new Rectangle(x + deltaX + 90, y + 90, 32, 32), Storage.getFont18());

            // FLEX
            graphics.setColor(Color.WHITE);
            if (Constants.cagesId.equals(player.getUserID()))
                graphics.setColor(Color.decode("#f616ce"));
            DrawingUtil.drawCenteredString(graphics, String.valueOf(availablePlayerFlex),
                new Rectangle(x + deltaX, y + 115, 150, 20), Storage.getFont18());
            // DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerFlex), new Rectangle(x
            // + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

        }

        deltaX += 156;

        boolean randomizeList = player != fowPlayer && isFoWPrivate;
        if (randomizeList) {
            Collections.shuffle(planets);
        }

        List<String> realPlanets = new ArrayList<>();
        List<String> nonTile = new ArrayList<>();
        List<String> fakePlanets = new ArrayList<>();
        for (String planet : planets) {
            PlanetModel model = Mapper.getPlanet(planet);
            Set<PlanetType> types = new HashSet<>();
            if (model.getPlanetTypes() != null) types.addAll(model.getPlanetTypes());

            if (types.contains(PlanetType.FAKE)) {
                fakePlanets.add(planet);
            } else if (game.getTileFromPlanet(planet) == null) {
                nonTile.add(planet);
            } else {
                realPlanets.add(planet);
            }
        }

        Tile homeTile = player.getHomeSystemTile();
        if (homeTile != null) {
            if (homeTile.getTileID().equals("51")) {
                Tile creussGate = game.getTile("17");
                if (creussGate != null) {
                    homeTile = creussGate;
                }
            }
            Point homePosition = PositionMapper.getTilePosition(homeTile.getPosition());
            Comparator<String> planetComparator = (planet1, planet2) -> {
                Tile tile1 = game.getTileFromPlanet(planet1);
                if (tile1.getTileID().equals("51")) {
                    Tile creussGate = game.getTile("17");
                    if (creussGate != null) {
                        tile1 = creussGate;
                    }
                }
                Point position1 = PositionMapper.getTilePosition(tile1.getPosition());
                int distance1 = ((homePosition.x - position1.x) * (homePosition.x - position1.x)
                    + (homePosition.y - position1.y) * (homePosition.y - position1.y)) / 4000;
                Tile tile2 = game.getTileFromPlanet(planet2);
                if (tile2.getTileID().equals("51")) {
                    Tile creussGate = game.getTile("17");
                    if (creussGate != null) {
                        tile2 = creussGate;
                    }
                }
                Point position2 = PositionMapper.getTilePosition(tile2.getPosition());
                int distance2 = ((homePosition.x - position2.x) * (homePosition.x - position2.x)
                    + (homePosition.y - position2.y) * (homePosition.y - position2.y)) / 4000;
                if (distance1 != distance2) {
                    return distance1 - distance2;
                }
                if (!tile1.getPosition().equalsIgnoreCase(tile2.getPosition())) {
                    return tile2.getPosition().compareToIgnoreCase(tile1.getPosition());
                }
                return planet1.compareToIgnoreCase(planet2);
            };
            realPlanets.sort(planetComparator);
        }

        for (String planet : realPlanets) {
            deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
        }
        if (!nonTile.isEmpty()) {
            deltaX += 30;
            for (String planet : nonTile) {
                deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
            }
        }
        if (!fakePlanets.isEmpty()) {
            deltaX += 30;
            for (String planet : fakePlanets) {
                deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
            }
        }

        return x + deltaX + 20;
    }

    private int drawPlanetInfo(Player player, String planetName, int x, int y, int deltaX) {
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        try {
            Planet planet = planetsInfo.get(planetName);
            PlanetModel planetModel = planet.getPlanetModel();

            boolean isExhausted = exhaustedPlanets.contains(planetName);
            graphics.setColor(isExhausted ? Color.GRAY : Color.WHITE);

            String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
            graphics.drawRect(x + deltaX - 2, y - 2, 52, 152);

            // Display planet traits
            String planetDisplayIcon = planet.getOriginalPlanetType();
            List<PlanetType> originalPlanetTypes = Mapper.getPlanet(planetName).getPlanetTypes();
            if (originalPlanetTypes == null || originalPlanetTypes.isEmpty()) {
                planetDisplayIcon = "none";
            }
            if (originalPlanetTypes != null && originalPlanetTypes.contains(PlanetType.FACTION)) {
                planetDisplayIcon = TileHelper.getPlanetById(planetName).getFactionHomeworld();
                if (planetDisplayIcon == null) // fallback to current player's faction
                    planetDisplayIcon = player.getFaction();
            }

            Set<String> planetTypes = planet.getPlanetTypes();
            if (!planetTypes.isEmpty() && planetTypes.size() > 1) {
                planetDisplayIcon = "combo_";
                if (planetTypes.contains("cultural")) planetDisplayIcon += "C";
                if (planetTypes.contains("hazardous")) planetDisplayIcon += "H";
                if (planetTypes.contains("industrial")) planetDisplayIcon += "I";
            }

            if (!planetDisplayIcon.isEmpty()) {
                if ("keleres".equals(player.getFaction()) && ("mentak".equals(planetDisplayIcon) || "xxcha".equals(planetDisplayIcon) || "argent".equals(planetDisplayIcon))) {
                    planetDisplayIcon = "keleres";
                }

                if (Mapper.isValidFaction(planetDisplayIcon)) {
                    drawFactionIconImage(graphics, planetDisplayIcon, x + deltaX - 2, y - 2, 52, 52);
                } else {
                    String planetTypeName = "pc_attribute_" + planetDisplayIcon + ".png";
                    drawPlanetCardDetail(x + deltaX + 1, y + 2, planetTypeName);
                }
            }

            // GLEDGE CORE
            if (planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                String tokenPath = ResourceHelper.getInstance().getTokenFile(Constants.GLEDGE_CORE_PNG);
                BufferedImage image = ImageHelper.readScaled(tokenPath, 0.25f);
                graphics.drawImage(image, x + deltaX + 15, y + 112, null);
            }

            boolean hasAttachment = planet.hasAttachment();
            if (hasAttachment) {
                String planetChevrons = "pc_upgrade.png";
                if (planet.getTokenList().contains("attachment_tombofemphidia.png")) {
                    planetChevrons = "pc_upgrade_tomb.png";
                    ExploreModel tomb = Mapper.getExplore("toe");
                    addWebsiteOverlay(tomb, x + deltaX + 26, y + 40, 20, 20);
                }
                drawPlanetCardDetail(x + deltaX + 26, y + 40, planetChevrons);
            }

            if (planet.isLegendary()) {
                if (planetModel != null) {
                    addWebsiteOverlay(planetModel, x + deltaX + 26, y + 60, 20, 20);
                }
                String statusOfAbility = exhaustedPlanetsAbilities.contains(planetName) ? "_exh" : "_rdy";
                String planetLegendaryCresent = "pc_legendary" + statusOfAbility + ".png";
                drawPlanetCardDetail(x + deltaX + 26, y + 60, planetLegendaryCresent);
            }

            boolean hasBentorEncryptionKey = planet.getTokenList().stream()
                .anyMatch(token -> token.contains("encryptionkey"));
            // BENTOR ENCRYPTION KEY
            if (hasBentorEncryptionKey) {
                String imageFileName = "pc_tech_bentor_encryptionkey.png";
                addWebsiteOverlay("Bentor Encryption Key", null, x + deltaX + 26, y + 82, 20, 20);
                drawPlanetCardDetail(x + deltaX + 26, y + 82, imageFileName);
            }

            String originalTechSpeciality = planet.getOriginalTechSpeciality();
            if (isNotBlank(originalTechSpeciality) && !hasBentorEncryptionKey) {
                String planetTechSkip = "pc_tech_" + originalTechSpeciality + statusOfPlanet + ".png";
                drawPlanetCardDetail(x + deltaX + 26, y + 82, planetTechSkip);
            } else if (!hasBentorEncryptionKey) {
                List<String> techSpeciality = planet.getTechSpeciality();
                for (String techSpec : techSpeciality) {
                    if (techSpec.isEmpty()) {
                        continue;
                    }
                    String planetTechSkip = "pc_tech_" + techSpec + statusOfPlanet + ".png";
                    drawPlanetCardDetail(x + deltaX + 26, y + 82, planetTechSkip);
                }
            }

            String resFileName = "pc_res" + statusOfPlanet + ".png";
            String infFileName = "pc_inf" + statusOfPlanet + ".png";
            int resources = planet.getResources();
            int influence = planet.getInfluence();
            if (planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                resFileName = "pc_res_khrask" + statusOfPlanet + ".png";
                addWebsiteOverlay("Garden World", null, x + deltaX, y, 20, 20);
            }

            drawPlanetCardDetail(x + deltaX + 26, y + 103, resFileName);
            drawPlanetCardDetail(x + deltaX + 26, y + 125, infFileName);

            graphics.setFont(Storage.getFont16());
            int offset = 11 - graphics.getFontMetrics().stringWidth("" + resources) / 2;
            if (planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                graphics.setColor(Color.BLACK);
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        graphics.drawString("" + resources, x + deltaX + 26 + offset + i, y + 119 + j);
                    }
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString("" + resources, x + deltaX + 26 + offset, y + 119);
            offset = 10 - graphics.getFontMetrics().stringWidth("" + influence) / 2;
            graphics.drawString("" + influence, x + deltaX + 27 + offset, y + 141);

            graphics.setColor(isExhausted ? Color.GRAY : Color.WHITE);
            if (planetModel.getShrinkNamePNAttach()) {
                drawTextVertically(graphics, planetModel.getShortName().toUpperCase(), x + deltaX + 9, y + 144, Storage.getFont16());
            } else {
                drawTextVertically(graphics, planetModel.getShortName().toUpperCase(), x + deltaX + 7, y + 144, Storage.getFont18());
            }

            return deltaX + 56;
        } catch (Exception e) {
            BotLogger.log("could not print out planet: " + planetName.toLowerCase(), e);
        }
        return deltaX;
    }

    private void drawPlanetCardDetail(int x, int y, String resourceName) {
        String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
        BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
        graphics.drawImage(resourceBufferedImage, x, y, null);
    }

    private int techInfo(Player player, int x, int y, Game game) {
        List<String> techs = player.getTechs();
        List<String> exhaustedTechs = player.getExhaustedTechs();
        List<String> purgedTechs = player.getPurgedTechs();

        Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : techs) {
            TechnologyModel techModel = Mapper.getTech(tech);
            String techType = techModel.getFirstType().toString();
            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techType = game.getStoredValue("colorChange" + tech);
            }
            List<String> techList = techsFiltered.get(techType);
            if (techList == null) {
                techList = new ArrayList<>();
            }
            techList.add(tech);
            techsFiltered.put(techType, techList);
        }
        Comparator<String> techComparator = (tech1, tech2) -> {
            TechnologyModel tech1Info = Mapper.getTech(tech1);
            TechnologyModel tech2Info = Mapper.getTech(tech2);
            return TechnologyModel.sortTechsByRequirements(tech1Info, tech2Info);
        };
        for (Map.Entry<String, List<String>> entry : techsFiltered.entrySet()) {
            List<String> list = entry.getValue();
            list.sort(techComparator);
        }
        purgedTechs.sort(techComparator);

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = 0;
        deltaX = techField(x, y, techsFiltered.get(Constants.PROPULSION), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.WARFARE), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.CYBERNETIC), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.BIOTIC), exhaustedTechs, deltaX, player);
        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), deltaX, player, game);
        deltaX = techGenSynthesis(x, y, deltaX, player, techsFiltered.get(Constants.UNIT_UPGRADE));
        deltaX = techField(x, y, purgedTechs, Collections.emptyList(), deltaX, player);
        return x + deltaX + 20;
    }

    private int factionTechInfo(Player player, int x, int y) {
        List<String> techs = player.getNotResearchedFactionTechs();
        if (techs.isEmpty()) {
            return y;
        }

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = 20;
        deltaX = factionTechField(player, x, y, techs, deltaX);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, int deltaX, Player player) {
        if (techs == null) {
            return deltaX;
        }
        boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
        List<String> zealotsTechs = Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
        for (String tech : techs) {
            boolean isExhausted = exhaustedTechs.contains(tech);
            boolean isPurged = player.getPurgedTechs().contains(tech);
            String techStatus = isExhausted ? "_exh.png" : "_rdy.png";

            TechnologyModel techModel = Mapper.getTech(tech);

            String techIcon = techModel.getImageFileModifier();

            // Handle Homebrew techs with modded colours
            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techIcon = game.getStoredValue("colorChange" + tech);
            }

            // Draw Background Colour
            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + techStatus;
                drawPAImage(x + deltaX, y, techSpec);
            }

            // Zealots Hero Active
            if (zealotsHeroActive && zealotsTechs.contains(tech)) {
                String path = "pa_tech_techicons_zealots.png";
                drawPAImage(x + deltaX, y, path);
            }

            if (techModel.getSource() == ComponentSource.absol) {
                drawPAImage(x + deltaX, y, "pa_source_absol" + (isExhausted ? "_exh" : "") + ".png");
            }

            // Draw Faction Tech Icon
            if (techModel.getFaction().isPresent()) {
                drawFactionIconImage(graphics, techModel.getFaction().get(), x + deltaX - 1, y + 108, 42, 42);
            } else {
                Color foreground = Color.WHITE;
                int types = 0;
                if (techModel.isPropulsionTech()) {
                    foreground = Color.decode("#509dce");
                    types++;
                }
                if (techModel.isCyberneticTech()) {
                    foreground = Color.decode("#e2da6a");
                    types++;
                }
                if (techModel.isBioticTech()) {
                    foreground = Color.decode("#7cba6b");
                    types++;
                }
                if (techModel.isWarfareTech()) {
                    foreground = Color.decode("#dc6569");
                    types++;
                }
                if (types != 1) {
                    foreground = Color.WHITE;
                }
                if (isExhausted || isPurged) {
                    foreground = Color.GRAY;
                }

                String initials = techModel.getInitials();
                if (initials.length() == 2) {
                    String left = initials.substring(0, 1);
                    String right = initials.substring(1, 2);
                    graphics.setFont(Storage.getFont32());
                    int offsetLeft = Math.max(0, 10 - graphics.getFontMetrics().stringWidth(left) / 2);
                    int offsetRight = Math.min(40 - graphics.getFontMetrics().stringWidth(right),
                        30 - graphics.getFontMetrics().stringWidth(right) / 2);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(right, x + i + deltaX + offsetRight, y + j + 148);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(right, x + deltaX + offsetRight, y + 148);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(left, x + i + deltaX + offsetLeft, y + j + 139);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(left, x + deltaX + offsetLeft, y + 139);
                } else if (initials.length() == 3) {
                    String left = initials.substring(0, 1);
                    String middle = initials.substring(1, 2);
                    String right = initials.substring(2, 3);
                    graphics.setFont(Storage.getFont24());
                    int offsetLeft = Math.max(0, 7 - graphics.getFontMetrics().stringWidth(left) / 2);
                    int offsetMiddle = 20 - graphics.getFontMetrics().stringWidth(middle) / 2;
                    int offsetRight = Math.min(40 - graphics.getFontMetrics().stringWidth(right),
                        33 - graphics.getFontMetrics().stringWidth(right) / 2);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(right, x + i + deltaX + offsetRight, y + j + 148);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(right, x + deltaX + offsetRight, y + 148);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(middle, x + i + deltaX + offsetMiddle, y + j + 141);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(middle, x + deltaX + offsetMiddle, y + 141);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(left, x + i + deltaX + offsetLeft, y + j + 134);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(left, x + deltaX + offsetLeft, y + 134);
                } else {
                    initials = initials.substring(0, 1);
                    graphics.setFont(Storage.getFont48());
                    int offset = 20 - graphics.getFontMetrics().stringWidth(initials) / 2;
                    graphics.setColor(Color.BLACK);
                    for (int i = -2; i <= 2; i++) {
                        for (int j = -2; j <= 2; j++) {
                            graphics.drawString(initials, x + i + deltaX + offset, y + j + 148);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(initials, x + deltaX + offset, y + 148);
                }
            }

            graphics.setColor(isExhausted ? Color.GRAY : Color.WHITE);
            if (isPurged) graphics.setColor(Color.RED);

            if (techModel.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(graphics, techModel.getShortName(), x + deltaX + 9, y + 116, 116);
            } else {
                graphics.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(graphics, techModel.getShortName(), x + deltaX + 7, y + 116, 116);
            }
            if ("dslaner".equalsIgnoreCase(tech)) {
                drawTextVertically(graphics, "" + player.getAtsCount(), x + deltaX + 15, y + 140, Storage.getFont16());
            }

            drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, techModel);
            deltaX += 48;
        }
        return deltaX;
    }

    private int factionTechField(Player player, int x, int y, List<String> techs, int deltaX) {
        if (techs == null) {
            return deltaX;
        }

        for (String tech : techs) {
            graphics.setColor(Color.DARK_GRAY);

            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel.isUnitUpgrade()) {
                continue;
            }

            int types = 0;
            String techIcon = "";
            if (techModel.isPropulsionTech()) {
                types++;
                techIcon += "propulsion";
            }
            if (techModel.isCyberneticTech()) {
                types++;
                techIcon += "cybernetic";
            }
            if (techModel.isBioticTech() && types++ < 2) techIcon += "biotic";
            if (techModel.isWarfareTech() && types++ < 2) techIcon += "warfare";

            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techIcon = game.getStoredValue("colorChange" + tech);
            }

            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + "_exh.png";
                drawPAImage(x + deltaX, y, techSpec);
            }

            if (techModel.getFaction().isPresent()) {
                drawFactionIconImageOpaque(graphics, techModel.getFaction().get(), x + deltaX + 1, y + 108, 42, 42, 0.5f);
            }

            if (techModel.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                drawOneOrTwoLinesOfTextVertically(graphics, techModel.getShortName(), x + deltaX + 9, y + 116, 116);
            } else {
                graphics.setFont(Storage.getFont18());
                drawOneOrTwoLinesOfTextVertically(graphics, techModel.getShortName(), x + deltaX + 7, y + 116, 116);
            }

            drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, techModel);
            deltaX += 48;
        }
        return deltaX;
    }

    private int techGenSynthesis(int x, int y, int deltaX, Player player, List<String> techs) {
        int genSynthesisInfantry = player.getGenSynthesisInfantry();
        if ((techs == null && genSynthesisInfantry == 0) || !hasInfantryII(techs) && genSynthesisInfantry == 0) {
            return deltaX;
        }
        String techSpec = "pa_tech_techname_stasiscapsule.png";
        drawPAImage(x + deltaX, y, techSpec);
        if (genSynthesisInfantry < 20) {
            graphics.setFont(Storage.getFont35());
        } else {
            graphics.setFont(Storage.getFont30());
        }
        int centerX = 0;
        if (genSynthesisInfantry < 10) {
            centerX += 5;
        }
        graphics.drawString(String.valueOf(genSynthesisInfantry), x + deltaX + 3 + centerX, y + 148);
        drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, "Gen Synthesis (Infantry II)", "Number of infantry to revive: " + genSynthesisInfantry);
        deltaX += 48;
        return deltaX;
    }

    private boolean hasInfantryII(List<String> techs) {
        if (techs == null) {
            return false;
        }
        for (String tech : techs) {
            TechnologyModel techInformation = Mapper.getTech(tech);
            if ("inf2".equals(techInformation.getBaseUpgrade().orElse("")) || "inf2".equals(tech)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        mainImage.flush();
        graphics.dispose();
        logDebug();
    }

    private record Coord(int x, int y) {
        public Coord translate(int dx, int dy) {
            return coord(x + dx, y + dy);
        }
    }

    private static Coord coord(int x, int y) {
        return new Coord(x, y);
    }

    private static Coord getUnitTechOffsets(String asyncId, boolean getFactionIconOffset) {
        asyncId = AliasHandler.resolveUnit(asyncId);
        switch (asyncId) {
            case "gf" -> {
                if (getFactionIconOffset)
                    return new Coord(3, 17);
                return new Coord(3, 2);
            }
            case "fs" -> {
                if (getFactionIconOffset)
                    return new Coord(185, 101);
                return new Coord(151, 67);
            }
            case "ff" -> {
                if (getFactionIconOffset)
                    return new Coord(5, 72);
                return new Coord(7, 59);
            }
            case "dn" -> {
                if (getFactionIconOffset)
                    return new Coord(116, 99);
                return new Coord(93, 72);
            }
            case "dd" -> {
                if (getFactionIconOffset)
                    return new Coord(62, 106);
                return new Coord(52, 99);
            }
            case "cv" -> {
                if (getFactionIconOffset)
                    return new Coord(105, 38);
                return new Coord(82, 11);
            }
            case "ca" -> {
                if (getFactionIconOffset)
                    return new Coord(149, 24);
                return new Coord(126, 1);
            }
            case "ws" -> {
                if (getFactionIconOffset)
                    return new Coord(204, 21);
                return new Coord(191, 4);
            }
            case "sd", "csd" -> {
                if (getFactionIconOffset)
                    return new Coord(52, 65);
                return new Coord(46, 49);
            }
            case "pd" -> {
                if (getFactionIconOffset)
                    return new Coord(51, 15);
                return new Coord(47, 2);
            }
            case "mf" -> {
                if (getFactionIconOffset)
                    return new Coord(5, 110);
                return new Coord(3, 102);
            }
            default -> {
                return new Coord(0, 0);
            }
        }
    }

    private int techFieldUnit(int x, int y, List<String> techs, int deltaX, Player player, Game game) {
        drawPAImage(x + deltaX, y, "pa_tech_unitupgrade_outlines.png");

        boolean brokenWarSun = false;
        if (ButtonHelper.isLawInPlay(game, "schematics")) {
            for (Player p2 : game.getPlayers().values()) {
                brokenWarSun |= p2.hasWarsunTech();
            }
        }

        // Add unit upgrade images
        if (techs != null) {
            boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
            List<String> zealotsTechs = Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
            for (String tech : techs) {
                TechnologyModel techInformation = Mapper.getTech(tech);
                if (!techInformation.isUnitUpgrade()) {
                    continue;
                }

                UnitModel unit = Mapper.getUnitModelByTechUpgrade(techInformation.getAlias());
                if (unit == null) {
                    BotLogger.log(game.getName() + " " + player.getUserName() + " Could not load unit associated with tech: " + techInformation.getAlias());
                    continue;
                }

                Coord unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
                UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
                drawPAUnitUpgrade(deltaX + x + unitOffset.x, y + unitOffset.y, unitKey);

                if (zealotsHeroActive && zealotsTechs.contains(tech)) {
                    String path = "pa_tech_unitsnew_zealots_" + tech + ".png";
                    try {
                        path = ResourceHelper.getInstance().getPAResource(path);
                        BufferedImage img = ImageHelper.read(path);
                        graphics.drawImage(img, deltaX + x + unitOffset.x, y + unitOffset.y, null);
                    } catch (Exception e) {
                        // Do Nothing
                        BotLogger.log("Could not display active zealot tech", e);
                    }
                }
            }
        }

        boolean zealotsHeroPurged = game.getStoredValue("zealotsHeroPurged").equals("true");
        if (zealotsHeroPurged) {
            for (String tech : player.getPurgedTechs()) {
                TechnologyModel techInformation = Mapper.getTech(tech);
                if (!techInformation.isUnitUpgrade()) {
                    continue;
                }

                UnitModel unit = Mapper.getUnitModelByTechUpgrade(techInformation.getAlias());
                if (unit == null) {
                    BotLogger.log(game.getName() + " " + player.getUserName() + " Could not load unit associated with tech: " + techInformation.getAlias());
                    continue;
                }

                Coord unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
                UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
                String path = "pa_tech_unitsnew_zealotspurged_" + tech + ".png";
                try {
                    path = ResourceHelper.getInstance().getPAResource(path);
                    BufferedImage img = ImageHelper.read(path);
                    graphics.drawImage(img, deltaX + x + unitOffset.x, y + unitOffset.y, null);
                } catch (Exception e) {
                    // Do Nothing
                    BotLogger.log("Could not display purged zealot tech", e);
                }
            }
        }

        if (brokenWarSun) {
            UnitModel unit = Mapper.getUnitModelByTechUpgrade("ws");
            Coord unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
            UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
            BufferedImage wsCrackImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_publicize_weapon_schematics" + (player.hasWarsunTech() ? DrawingUtil.getBlackWhiteFileSuffix(unitKey.getColorID()) : "_blk.png")));
            graphics.drawImage(wsCrackImage, deltaX + x + unitOffset.x, y + unitOffset.y, null);
        }

        // Add the blank warsun if player has no warsun
        List<UnitModel> playerUnitModels = new ArrayList<>(player.getUnitModels());
        if (player.getUnitsByAsyncID("ws").isEmpty()) {
            playerUnitModels.add(Mapper.getUnit("nowarsun"));
        }
        // Add faction icons on top of upgraded or upgradable units
        for (UnitModel unit : playerUnitModels) {
            boolean isPurged = unit.getRequiredTechId().isPresent() && player.getPurgedTechs().contains(unit.getRequiredTechId().get());
            Coord unitFactionOffset = getUnitTechOffsets(unit.getAsyncId(), true);
            if (unit.getFaction().isPresent()) {
                boolean unitHasUpgrade = unit.getUpgradesFromUnitId().isPresent() || unit.getUpgradesToUnitId().isPresent();
                if (game.isFrankenGame() || unitHasUpgrade || player.getFactionModel().getAlias().equals("echoes")) {
                    // Always paint the faction icon in franken
                    drawFactionIconImage(graphics, unit.getFaction().get().toLowerCase(), deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32);
                }
            }

            if (isPurged) {
                DrawingUtil.superDrawString(graphics, "X", deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, Color.RED, null, null, stroke2, Color.BLACK);
            }

            // Unit Overlays
            addWebsiteOverlay(unit, deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32);
            // graphics.drawRect(deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32); //debug
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 252, 152);
        deltaX += 270;
        return deltaX;
    }

    private void drawFactionIconImage(Graphics graphics, String faction, int x, int y, int width, int height) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null);
    }

    private static void drawFactionIconImageOpaque(Graphics g, String faction, int x, int y, int width, int height, Float opacity) {
        try {
            BufferedImage resourceBufferedImage = DrawingUtil.getFactionIconImageScaled(faction, width, height);
            Graphics2D g2 = (Graphics2D) g;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.log("Could not display faction icon image: " + faction, e);
        }
    }

    private void drawPlanetImage(int x, int y, String resourceName, String planetName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
            if (Optional.ofNullable(resourcePath).isPresent()) {
                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x, y, null);
            } else {
                String name = Optional.ofNullable(Mapper.getPlanet(planetName).getShortName())
                    .orElse(Mapper.getPlanet(planetName).getName());
                String text = StringUtils.left(StringUtils.substringBefore(name, "\n"), 10).toUpperCase();
                drawTextVertically(graphics, text, x + 6, y + 146, Storage.getFont20());
            }
        } catch (Exception e) {
            BotLogger.log("Could not display planet: " + resourceName, e);
        }
    }

    private void drawGeneralImageScaled(int x, int y, String resourceName, int width, int height) {
        try {
            String resourcePath = ResourceHelper.getInstance().getGeneralFile(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.readScaled(resourcePath, width, height);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private void drawGeneralImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getGeneralFile(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private static void drawPAImage(Graphics g, int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            g.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        drawPAImage(graphics, x, y, resourceName);
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int size) {
        drawPAImageScaled(x, y, resourceName, size, size);
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int width, int height) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.readScaled(resourcePath, width, height);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private String getUnitPath(UnitKey unit) {
        return allEyesOnMe ? ResourceHelper.getInstance().getUnitFile(unit, true) : ResourceHelper.getInstance().getUnitFile(unit);
    }

    private void drawPAUnitUpgrade(int x, int y, UnitKey unitKey) {
        try {
            String path = getUnitPath(unitKey);
            BufferedImage img = ImageHelper.read(path);
            graphics.drawImage(img, x, y, null);
        } catch (Exception e) {
            // Do Nothing
            BotLogger.log("Could not display UU", e);
        }
    }

    private void drawPAImageOpaque(int x, int y, String resourceName, float opacity) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2.drawImage(resourceBufferedImage, x, y, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
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
                    int tokenWidth = controlTokenImage == null ? 51 : controlTokenImage.getWidth(); //51
                    int tokenHeight = controlTokenImage == null ? 33 : controlTokenImage.getHeight(); //33
                    int centreHorizontally = Math.max(0, (availableSpacePerColumn - tokenWidth) / 2);
                    int centreVertically = Math.max(0, (availableSpacePerRow - tokenHeight) / 2);

                    int vpCount = player.getTotalVictoryPoints();
                    int tokenX = vpCount * boxWidth + Math.min(boxBuffer + (availableSpacePerColumn * col) + centreHorizontally, boxWidth - tokenWidth - boxBuffer) + landscapeShift;
                    int tokenY = y + boxBuffer + (availableSpacePerRow * row) + centreVertically;
                    DrawingUtil.drawControlToken(graphics, controlTokenImage, DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID), tokenX, tokenY, convertToGeneric, scale);
                } catch (Exception e) {
                    // nothing
                    BotLogger.log("Could not display player: " + player.getUserName(), e);
                }
                row++;
            }
            row = 0;
            col++;
        }
        y += 180;
        return y;
    }

    private Coord drawStrategyCards(Coord coord) {
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
                graphics.setColor(getSCColor(sc, game));
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

        return coord(x, deltaY);
    }

    private Coord drawTurnOrderTracker(int x, int y) {
        boolean convertToGenericSC = isFoWPrivate;
        String activePlayerUserID = game.getActivePlayerID();
        if (!convertToGenericSC && activePlayerUserID != null && "action".equals(game.getPhaseOfGame())) {
            graphics.setFont(Storage.getFont20());
            graphics.setColor(ActiveColor);
            graphics.drawString("ACTIVE", x + 10, y + 35);
            graphics.setFont(Storage.getFont16());
            graphics.setColor(Color.LIGHT_GRAY);
            graphics.drawString("NEXT UP", x + 112, y + 34);

            Player activePlayer = game.getPlayer(activePlayerUserID);
            List<Player> allPlayers = new ArrayList<>(game.getRealPlayers());

            Comparator<Player> comparator = Comparator.comparing(game::getPlayersTurnSCInitiative);
            allPlayers.sort(comparator);

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
        return coord(x, y);
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
                    // if we can see a players stats, but we cannot see their home system - move their stats somewhere random
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

        // It might be better to actually just use the anchor position and not give a fk. Will test on PBD 10
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
            players.stream().filter(player -> FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)).forEach(statOrder::add);
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
            if (p.equals(statTiles.get(1))) dir1 = j;
            if (p.equals(statTiles.get(2))) dir2 = j;
            j++;
        }

        ColorModel playerColor = Mapper.getColor(player.getColor());
        for (String pos : statTiles) {
            Point p = points.get(pos);
            List<Integer> adjDir = new ArrayList<>();
            List<String> adjPos = PositionMapper.getAdjacentTilePositions(pos);
            for (int i = 0; i < 6; i++)
                if (statTiles.contains(adjPos.get(i)))
                    adjDir.add(i);
            BufferedImage hex = DrawingUtil.hexBorder(game.getHexBorderStyle(), playerColor, adjDir);
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
            DrawingUtil.drawPlayerFactionIconImageOpaque(graphics, player, point.x, point.y, size, size, 0.40f);
        }

        { // PAINT USERNAME
            graphics.setFont(Storage.getFont32());
            String userName = player.getUserName();
            point = PositionMapper.getPlayerStats("newuserName");
            if (!game.hideUserNames()) {
                String name = userName.substring(0, Math.min(userName.length(), 15));
                DrawingUtil.superDrawString(graphics, name, statTileMid.x + point.x, statTileMid.y + point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
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

            // BufferedImage img = ImageHelper.readEmojiImageScaled(Emojis.getColorEmoji(player.getColor()), 30);
            // int offset = graphics.getFontMetrics().stringWidth(factionText) / 2 + 10;
            // point.translate(0, -25);
            // graphics.drawImage(img, point.x - offset - 30, point.y, null);
            // graphics.drawImage(img, point.x + offset, point.y, null);
        }

        { // PAINT VICTORY POINTS
            graphics.setFont(Storage.getFont32());
            String vpCount = "VP: " + player.getTotalVictoryPoints() + " / " + game.getVp();
            point = PositionMapper.getPlayerStats("newvp");
            point.translate(statTileMid.x, statTileMid.y);
            DrawingUtil.superDrawString(graphics, vpCount, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
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
            if (player.hasTheZeroToken()) playerSCs.add(0);
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
                    DrawingUtil.superDrawString(graphics, Integer.toString(sc), point.x, point.y + fontYoffset, getSCColor(sc, game), center, bottom, stroke6, Color.BLACK);
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

            DrawingUtil.drawCCOfPlayer(graphics, ccID, point.x, point.y, player.getTacticalCC(), player, false, rightAlign);
            drawFleetCCOfPlayer(graphics, fleetCCID, point.x, point.y + 65, player, rightAlign);
            DrawingUtil.drawCCOfPlayer(graphics, ccID, point.x, point.y + 130, player.getStrategicCC(), player, false, rightAlign);

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
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(17000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 - 30,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30 + (player.isSpeaker() ? 30 : 0),
                    null);
                bufferedImage = ImageHelper.read(heroFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(17000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 + 30,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + 30 + (player.isSpeaker() ? 30 : 0),
                    null);
                offBoardHighlighting += 2;
            } else if (hasStellar) {
                bufferedImage = ImageHelper.read(relicFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                    null);
                offBoardHighlighting++;
            } else if (hasHero) {
                bufferedImage = ImageHelper.read(heroFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
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
            boolean reconstruction = (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
            boolean travelBan = ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");

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
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                }
                if (reconstruction) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                    graphics.drawImage(doubleWormholeImage, x, y, null);
                }
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
            if (!alphaOnMap) {
                String tokenFile = Mapper.getTokenPath(alphaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                if (travelBan) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                }
                if (reconstruction) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
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
                            traitFile = ResourceHelper.getInstance().getFactionFile(custodiaVigilia.getFactionHomeworld() + ".png");
                        }
                    } else if (traits.size() == 1) {
                        String t = planetReal.getPlanetType().getFirst();
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
                    traitFiles.add(traitFile);
                    offBoardHighlighting++;
                }
            }
            if (offBoardHighlighting >= 2) {
                for (int i = 0; i < offBoardHighlighting; i++) {
                    BufferedImage bufferedImage = ImageHelper.read(traitFiles.get(i));
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                        null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(traitFiles.getFirst());
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (TILE_WIDTH - bufferedImage.getWidth()) / 2,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
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
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                        null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(techFiles.getFirst());
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
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
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30,
                        miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 - 30 + (player.isSpeaker() ? 30 : 0),
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
                        DrawingUtil.drawCenteredString(graphics, "" + attachCount.get(planet),
                            new Rectangle(
                                miscTile.x + (345 - 80) / 2 - 30,
                                miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 - 30 + (player.isSpeaker() ? 30 : 0),
                                80, 80),
                            Storage.getFont48());
                    }
                }
            } else if (offBoardHighlighting == 1) {
                String planet = attachFiles.keySet().iterator().next();

                BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
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
                    DrawingUtil.drawCenteredString(graphics, "" + attachCount.get(planet),
                        new Rectangle(
                            miscTile.x + (345 - 80) / 2,
                            miscTile.y + (SPACE_FOR_TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                            80, 80),
                        Storage.getFont48());
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
                DrawingUtil.superDrawString(graphics, "PASSED", point.x, point.y, PassedColor, center, null, stroke4, Color.BLACK);
            } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                point = PositionMapper.getPlayerStats("newpassed");
                point.translate(miscTile.x, miscTile.y);
                DrawingUtil.superDrawString(graphics, "ACTIVE", point.x, point.y, ActiveColor, center, null, stroke4, Color.BLACK);
            }
            if (player.isAFK()) {
                point = PositionMapper.getPlayerStats("newafk");
                point.translate(miscTile.x, miscTile.y);
                DrawingUtil.superDrawString(graphics, "AFK", point.x, point.y, Color.gray, center, null, stroke4, Color.BLACK);
            }
            graphics.setColor(Color.WHITE);
        }
    }

    private void paintPlayerInfoOld(Game game, Player player, int ringCount) {
        int deltaX = 0, deltaSplitX = 0;
        int deltaY = 0, deltaSplitY = 0;

        String playerStatsAnchor = player.getPlayerStatsAnchorPosition();
        if (playerStatsAnchor != null) {
            // String anchorProjectedOnOutsideRing = PositionMapper.getEquivalentPositionAtRing(ringCount, playerStatsAnchor);
            Point anchorProjectedPoint = PositionMapper.getTilePosition(playerStatsAnchor);
            if (anchorProjectedPoint != null) {
                Point playerStatsAnchorPoint = getTilePosition(playerStatsAnchor, anchorProjectedPoint.x, anchorProjectedPoint.y);
                Integer anchorLocationIndex = PositionMapper.getRingSideNumberOfTileID(player.getPlayerStatsAnchorPosition());
                anchorLocationIndex = anchorLocationIndex == null ? 0 : anchorLocationIndex - 1;
                boolean isCorner = playerStatsAnchor.equals(PositionMapper.getTileIDAtCornerPositionOfRing(ringCount, anchorLocationIndex + 1));
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
            graphics.drawString(userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX,
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
            graphics.setColor(getSCColor(sc, game));
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

        DrawingUtil.drawCCOfPlayer(graphics, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player, false, false);
        drawFleetCCOfPlayer(graphics, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player, false);
        DrawingUtil.drawCCOfPlayer(graphics, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), player, false, false);

        // PAINT SPEAKER
        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                graphics.drawImage(bufferedImage, point.x + deltaX + deltaSplitX + negativeDelta,
                    point.y + deltaY - deltaSplitY, null);
                graphics.setColor(Color.WHITE);
            }
        }
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(PassedColor);
            graphics.drawString("PASSED", point.x + deltaX, point.y + deltaY);
            graphics.setColor(Color.WHITE);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(ActiveColor);
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

    private static void drawFleetCCOfPlayer(Graphics graphics, String ccID, int x, int y, Player player, boolean rightAlign) {
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
                BufferedImage armadaLowerCCImage = ImageHelper.read(Mapper.getCCPath(Mapper.getCCID(player.getColor())));
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
            BotLogger.log("Ignored exception during map generation", e);
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
            graphics.setColor(LawColor);
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
            graphics.drawString(agendaTitle, x + 95, y + 30);
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
                    for (String debtToken : game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue()).split("_")) {

                        boolean hideFactionIcon = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, game.getPlayerFromColorOrFaction(debtToken), fowPlayer);
                        String controlID = hideFactionIcon ? Mapper.getControlID("gray") : Mapper.getControlID(debtToken);
                        if (controlID.contains("null")) {
                            continue;
                        }
                        float scale = 0.80f;
                        BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                        DrawingUtil.drawControlToken(graphics, controlTokenImage,
                            game.getPlayerFromColorOrFaction(debtToken), x + (count / 3) * 55,
                            y + tokenDeltaY - (count / 3) * 90,
                            hideFactionIcon, scale);
                        tokenDeltaY += 30;
                        count = count + 1;
                    }
                }

            } catch (Exception e) {
                BotLogger.log("Could not paint agenda icon", e);
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
                BotLogger.log("Could not paint event icon", e);
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

    private int objectivesSO(int y, Player player) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke3);

        Map<String, Player> players = game.getPlayers();
        Map<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        Map<String, Integer> customPublicVP = game.getCustomPublicVP();
        Set<String> secret = secretObjectives.keySet();
        graphics.setFont(Storage.getFont26());
        graphics.setColor(Stage1RevealedColor);

        Map<String, List<String>> scoredSecretObjectives = new LinkedHashMap<>();
        Map<String, Integer> secrets = new LinkedHashMap<>(player.getSecrets());

        for (String id : secrets.keySet()) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            scoredSecretObjectives.put(id, List.of(player.getUserID()));
        }
        if (player.isSearchWarrant()) {
            graphics.setColor(Color.LIGHT_GRAY);
            Map<String, Integer> revealedSecrets = new LinkedHashMap<>(secrets);
            y = displaySecretObjectives(y, new LinkedHashMap<>(), revealedSecrets, players, secretObjectives, secret, customPublicVP);
        }
        Map<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            secretsScored.remove(id);
        }
        Map<String, Integer> revealedSecretObjectives = new LinkedHashMap<>(secretsScored);
        for (String id : secretsScored.keySet()) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            scoredSecretObjectives.put(id, List.of(player.getUserID()));
        }
        graphics.setColor(Color.RED);
        y = displaySecretObjectives(y, scoredSecretObjectives, revealedSecretObjectives, players, secretObjectives, secret, customPublicVP);
        if (player.isSearchWarrant()) {
            return secretsScored.size() + player.getSecrets().size();
        }
        return secretsScored.size();
    }

    private int displaySecretObjectives(
        int y,
        Map<String, List<String>> scoredPublicObjectives,
        Map<String, Integer> revealedPublicObjectives,
        Map<String, Player> players,
        Map<String, String> publicObjectivesState,
        Set<String> po,
        Map<String, Integer> customPublicVP) {

        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            int x = 50;

            String key = revealed.getKey();
            if (!po.contains(key)) {
                continue;
            }
            String name = publicObjectivesState.get(key);
            Integer index = revealedPublicObjectives.get(key);
            if (index == null) {
                continue;
            }
            keysToRemove.add(key);

            graphics.drawString("(" + index + ") " + name, x, y + 23);

            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            if (scoredPlayerID != null) {
                drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, false, true);
            }
            drawRectWithOverlay(graphics, x - 4, y - 5, 600, 38, Mapper.getSecretObjective(key));

            y += 43;
        }
        keysToRemove.forEach(revealedPublicObjectives::remove);

        return y;
    }

    private void drawScoreControlMarkers(
        int x,
        int y,
        Map<String, Player> players,
        List<String> scoredPlayerID,
        boolean multiScoring,
        boolean fixedColumn) {
        try {
            int tempX = 0;
            for (Map.Entry<String, Player> playerEntry : players.entrySet()) {
                Player player = playerEntry.getValue();
                String userID = player.getUserID();

                boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                if (scoredPlayerID.contains(userID)) {
                    String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());
                    if (controlID.contains("null")) {
                        continue;
                    }

                    float scale = 0.55f;

                    BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);

                    if (multiScoring) {
                        int frequency = Collections.frequency(scoredPlayerID, userID);
                        for (int i = 0; i < frequency; i++) {
                            DrawingUtil.drawControlToken(graphics, controlTokenImage, player, x + tempX, y, convertToGeneric, scale);
                            tempX += scoreTokenSpacing;
                        }
                    } else {
                        DrawingUtil.drawControlToken(graphics, controlTokenImage, player, x + tempX, y, convertToGeneric, scale);
                    }
                }
                if (!multiScoring && !fixedColumn) {
                    tempX += scoreTokenSpacing;
                }
            }
        } catch (Exception e) {
            BotLogger.log("Error drawing score control token markers", e);
        }
    }

    private Color getSCColor(Integer sc, Game game) {
        return getSCColor(sc, game, false);
    }

    private Color getSCColor(Integer sc, Game game, boolean ignorePlayed) {
        Map<Integer, Boolean> scPlayed = game.getScPlayed();
        if (!ignorePlayed && scPlayed.get(sc) != null && scPlayed.get(sc)) {
            return Color.GRAY;
        }

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (scModel != null) {
            return scModel.getColour();
        }
        String scString = sc.toString();
        int scGroup = Integer.parseInt(StringUtils.left(scString, 1));
        return switch (scGroup) {
            case 1 -> new Color(255, 38, 38);
            case 2 -> new Color(253, 168, 24);
            case 3 -> new Color(247, 237, 28);
            case 4 -> new Color(46, 204, 113);
            case 5 -> new Color(26, 188, 156);
            case 6 -> new Color(52, 152, 171);
            case 7 -> new Color(155, 89, 182);
            case 8 -> new Color(124, 0, 192);
            case 9 -> new Color(251, 96, 213);
            case 10 -> new Color(165, 211, 34);
            default -> Color.WHITE;
        };
    }

    public static Color getColor(String color) {
        if (color == null) {
            return Color.WHITE;
        }
        if (color.startsWith("split")) {
            color = color.replace("split", "");
        }
        return switch (color) {
            case "black" -> Color.DARK_GRAY;
            case "blue" -> Color.BLUE;
            case "green" -> Color.GREEN;
            case "gray", "grey" -> new Color(113, 126, 152);
            case "orange" -> Color.ORANGE;
            case "pink" -> new Color(246, 153, 205);
            case "purple" -> new Color(166, 85, 247);
            case "red" -> Color.RED;
            case "yellow" -> Color.YELLOW;
            case "petrol" -> new Color(62, 128, 133);
            case "brown" -> new Color(112, 78, 42);
            case "tan" -> new Color(180, 168, 121);
            case "forest" -> new Color(93, 151, 102);
            case "chrome" -> new Color(186, 193, 195);
            case "sunset" -> new Color(173, 106, 248);
            case "turquoise" -> new Color(37, 255, 232);
            case "gold" -> new Color(215, 1, 247);
            case "lightgray" -> new Color(213, 213, 213);
            case "bloodred" -> Color.decode("#70001a");
            case "chocolate" -> Color.decode("#3a1d19");
            case "teal" -> Color.decode("#00deff");
            case "emerald" -> Color.decode("#004018");
            case "navy" -> Color.decode("#03004b");
            case "lime" -> Color.decode("#ace3a0");
            case "lavender" -> Color.decode("#9796df");
            case "rose" -> Color.decode("#d59de2");
            case "spring" -> Color.decode("#cedd8e");
            case "ethereal" -> Color.decode("#31559e");
            case "orca" -> getColor("gray");
            default -> Color.WHITE;
        };
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
            BotLogger.log("Tile Error, when building map `" + game.getName() + "`, tile: " + tile.getTileID(), exception);
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

    private static void drawTextVertically(Graphics graphics, String text, int x, int y, Font font) {
        drawTextVertically(graphics, text, x, y, font, false);
    }

    private static void drawTextVertically(Graphics graphics, String text, int x, int y, Font font, boolean rightAlign) {
        Graphics2D graphics2D = (Graphics2D) graphics;
        AffineTransform originalTransform = graphics2D.getTransform();
        graphics2D.rotate(Math.toRadians(-90));
        graphics2D.setFont(font);

        if (rightAlign) {
            y += graphics.getFontMetrics().stringWidth(text);
        }

        // DRAW A 1px BLACK BORDER AROUND TEXT
        Color originalColor = graphics2D.getColor();
        graphics2D.setColor(Color.BLACK);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                graphics2D.drawString(text,
                    (y + j) * -1, // See https://www.codejava.net/java-se/graphics/how-to-draw-text-vertically-with-graphics2d
                    x + graphics2D.getFontMetrics().getHeight() / 2 + i);
            }
        }
        graphics2D.setColor(originalColor);

        graphics2D.drawString(text,
            (y) * -1, // See https://www.codejava.net/java-se/graphics/how-to-draw-text-vertically-with-graphics2d
            x + graphics2D.getFontMetrics().getHeight() / 2);
        graphics2D.setTransform(originalTransform);
    }

    private static void drawTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth) {
        drawTwoLinesOfTextVertically(graphics, text, x, y, maxWidth, false);

    }

    private static void drawTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth, boolean rightAlign) {
        int spacing = graphics.getFontMetrics().getAscent() + graphics.getFontMetrics().getLeading();
        text = text.toUpperCase();
        String firstRow = StringUtils.substringBefore(text, "\n");
        firstRow = trimTextToPixelWidth(graphics, firstRow, maxWidth);
        String secondRow = text.substring(firstRow.length()).replace("\n", "");
        secondRow = trimTextToPixelWidth(graphics, secondRow, maxWidth);
        drawTextVertically(graphics, firstRow, x, y, graphics.getFont(), rightAlign);
        if (isNotBlank(secondRow)) {
            drawTextVertically(graphics, secondRow, x + spacing, y, graphics.getFont(), rightAlign);
        }
    }

    private static void drawOneOrTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth) {
        drawOneOrTwoLinesOfTextVertically(graphics, text, x, y, maxWidth, false);
    }

    private static void drawOneOrTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth, boolean rightAlign) {
        // vertically prints text on one line, centred horizontally, if it fits,
        // otherwise prints it over two lines

        // if the text contains a linebreak, print it over two lines
        if (text.contains("\n")) {
            drawTwoLinesOfTextVertically(graphics, text, x, y, maxWidth, rightAlign);
            return;
        }

        int spacing = graphics.getFontMetrics().getAscent() + graphics.getFontMetrics().getLeading();
        text = text.toUpperCase();

        // if the text is short enough to fit on one line, print it on one
        if (text.equals(trimTextToPixelWidth(graphics, text, maxWidth))) {
            drawTextVertically(graphics, text, x + spacing / 2, y, graphics.getFont(), rightAlign);
            return;
        }

        // if there's a space in the text, try to split it
        // as close to the centre as possible
        if (text.contains(" ")) {
            float center = text.length() / 2.0f + 0.5f;
            String front = text.substring(0, (int) center);
            String back = text.substring((int) (center - 0.5f));
            int before = front.lastIndexOf(" ");
            int after = text.indexOf(" ", (int) (center - 0.5f));

            // if there's only a space in the back half, replace the first space with a newline
            if (before == -1) {
                text = text.substring(0, after) + "\n" + text.substring(after + 1);
            }
            // if there's only a space in the front half, or if the last space in the
            // front half is closer to the centre than the first space in the back half,
            // replace the last space in the front half with a newline
            else if (after == -1 || (center - before - 1 <= after - center + 1)) {
                text = text.substring(0, before) + "\n" + text.substring(before + 1);
            }
            // otherwise, the first space in the back half is closer to the centre
            // than the last space in the front half, so replace
            // the first space in the back half with a newline
            else {
                text = text.substring(0, after) + "\n" + text.substring(after + 1);
            }
        }
        drawTwoLinesOfTextVertically(graphics, text, x, y, maxWidth, rightAlign);
    }

    private static String trimTextToPixelWidth(Graphics graphics, String text, int pixelLength) {
        for (int i = 0; i < text.length(); i++) {
            if (graphics.getFontMetrics().stringWidth(text.substring(0, i + 1)) > pixelLength) {
                return text.substring(0, i);
            }
        }
        return text;
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
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1; //make it thick if it's a 3-ring? why? player areas?
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

    // The first parameter is the scale factor (contrast), the second is the offset (brightness)
    private static BufferedImage makeGrayscale(BufferedImage image) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorConvertOp op = new ColorConvertOp(cs, null);
        return op.filter(image, null);
    }

    private void drawRectWithOverlay(Graphics g, int x, int y, int width, int height, String overlayTitle, String overlayText) {
        g.drawRect(x, y, width, height);
        addWebsiteOverlay(overlayTitle, overlayText, x, y, width, height);
    }

    private void drawRectWithOverlay(Graphics g, int x, int y, int width, int height, ModelInterface dataModel) {
        g.drawRect(x, y, width, height);
        if (dataModel == null) {
            addWebsiteOverlay("missingDataModel", "missingDataModel", x, y, width, height);
        } else {
            addWebsiteOverlay(dataModel, x, y, width, height);
        }
    }

    void addWebsiteOverlay(String overlayTitle, String overlayText, int x, int y, int width, int height) {
        websiteOverlays.add(new WebsiteOverlay(overlayTitle, overlayText, List.of(x, y, width, height)));
    }

    void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        websiteOverlays.add(new WebsiteOverlay(dataModel, List.of(x, y, width, height)));
    }

    String getGameName() {
        return game.getName();
    }
}
