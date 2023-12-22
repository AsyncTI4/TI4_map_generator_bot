package ti4.generator;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.helpers.ImageHelper;
import ti4.helpers.Storage;
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
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.model.EventModel;
import ti4.model.LeaderModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.UnitModel;

public class MapGenerator {

    public static final int DELTA_Y = 26;
    public static final int RING_MAX_COUNT = 8;
    public static final int RING_MIN_COUNT = 3;
    public static final int PLAYER_STATS_HEIGHT = 650;
    public static final int TILE_PADDING = 100;
    private static final int EXTRA_X = 300;
    private static final int EXTRA_Y = 200;
    private static final Point tilePositionPoint = new Point(230, 295);
    private static final Point labelPositionPoint = new Point(90, 295);
    private static final Point numberPositionPoint = new Point(40, 27);

    private final Graphics graphics;
    private final BufferedImage mainImage;
    private final int scoreTokenWidth;
    private final Game game;
    private final DisplayType displayType;
    private final boolean uploadToDiscord;
    private final boolean debug;
    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final boolean extraRow;

    private int mapWidth;
    private int minX = -1;
    private int minY = -1;
    private int maxX = -1;
    private int maxY = -1;
    private Boolean isFoWPrivate;
    private Player fowPlayer;
    private long debugAbsoluteStartTime;
    private long debugStartTime;
    private long debugFowTime;
    private long debugTileTime;
    private long debugGameInfoTime;
    private long debugDiscordTime;

    private MapGenerator(Game game) {
        this(game, null, true);
    }

    private MapGenerator(Game game, DisplayType displayType) {
        this(game, displayType, true);
    }

    private MapGenerator(Game game, DisplayType displayType, boolean uploadToDiscord) {
        this.game = game;
        this.displayType = defaultIfNull(displayType);
        this.uploadToDiscord = uploadToDiscord;

        debug = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);

        String controlID = Mapper.getControlID("red");
        BufferedImage bufferedImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), 0.45f);
        if (bufferedImage != null)
            scoreTokenWidth = bufferedImage.getWidth();
        else
            scoreTokenWidth = 14;

        int stage1 = game.getRevealedPublicObjectives().keySet().stream().filter(Mapper.getPublicObjectivesStage1()::containsKey).toList().size();
        int stage2 = game.getRevealedPublicObjectives().keySet().stream().filter(Mapper.getPublicObjectivesStage2()::containsKey).toList().size();
        int other = game.getRevealedPublicObjectives().size() - stage1 - stage2;
        int mostObjs = Math.max(Math.max(stage1, stage2), other);
        int objectivesY = Math.max((mostObjs - 5) * 43, 0);

        int playerCountForMap = game.getRealPlayers().size() + game.getDummies().size();
        int playerY = playerCountForMap * 340;

        int lawsY = (game.getLaws().keySet().size() / 2 + 1) * 115;
        int heightStats = playerY + lawsY + objectivesY + 600;

        int ringCount = game.getRingCount();
        ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);
        int mapHeight = (ringCount + 1) * 600 + EXTRA_Y * 2;
        mapWidth = (ringCount + 1) * 520 + EXTRA_X * 2;
        extraRow = (mapHeight - EXTRA_Y) < (playerCountForMap / 2 * PLAYER_STATS_HEIGHT + EXTRA_Y);
        if (extraRow) {
            mapWidth += EXTRA_X;
        }

        width = mapWidth;
        if (displayType == DisplayType.stats) {
            heightForGameInfo = 40;
            height = heightStats;
        } else if (displayType == DisplayType.map) {
            heightForGameInfo = mapHeight - 400;
            height = mapHeight + 600;
        } else {
            heightForGameInfo = mapHeight;
            height = mapHeight + heightStats;
        }

        ImageIO.setUseCache(false);
        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
    }

    private DisplayType defaultIfNull(DisplayType displayType) {
        if (game.getDisplayTypeForced() != null) {
            return game.getDisplayTypeForced();
        } else if (displayType == null) {
            displayType = game.getDisplayTypeForced();
            if (displayType == null) {
                return DisplayType.all;
            }
        }
        return displayType;
    }

    public static void saveImageToWebsiteOnly(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) {
            saveImage(game, null, event, false);
        }
    }

    public static CompletableFuture<FileUpload> saveImage(Game game, @Nullable SlashCommandInteractionEvent event) {
        return saveImage(game, null, event);
    }

    public static CompletableFuture<FileUpload> saveImage(Game game, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event) {
        return AsyncTI4DiscordBot.completeAsync(() -> new MapGenerator(game, displayType).saveImage(event));
    }

    public static CompletableFuture<FileUpload> saveImage(Game game, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event, boolean uploadToDiscord) {
        return AsyncTI4DiscordBot.completeAsync(() -> new MapGenerator(game, displayType, uploadToDiscord).saveImage(event));
    }

    private FileUpload saveImage(@Nullable GenericInteractionCreateEvent event) {
        if (debug) debugAbsoluteStartTime = System.nanoTime();

        AsyncTI4DiscordBot.jda.getPresence().setActivity(Activity.playing(game.getName()));
        game.incrementMapImageGenerationCount();

        drawGame(event);
        AsyncTI4DiscordBot.THREAD_POOL.submit(() -> sendToWebsite(event));
        FileUpload fileUpload = uploadToDiscord();
        logDebug(event);
        return fileUpload;
    }

    private void setupTilesForDisplayTypeAllAndMap(Map<String, Tile> tilesToDisplay) {
        if (displayType != DisplayType.all && displayType != DisplayType.map) {
            return;
        }
        if (debug) debugStartTime = System.nanoTime();
        Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
        String setup = tileMap.keySet().stream()
            .filter("0"::equals)
            .findFirst()
            .orElse(null);
        if (setup != null) {
            if ("setup".equals(tileMap.get(setup).getTileID())) {
                int ringCount = game.getRingCount();
                ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);
                minX = 10000;
                minY = 10000;
                maxX = -1;
                maxY = -1;
                Set<String> filledPositions = new HashSet<>();
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
                        // Do nothing
                    }

                    if (tileRingNumber > -1 && tileRingNumber <= ringCount && !tileMap.containsKey(position)) {
                        addTile(new Tile("0gray", position), TileStep.Tile);
                        filledPositions.add(position);
                    }
                }
                for (String position : PositionMapper.getTilePositions()) {
                    if (!tileMap.containsKey(position) || !filledPositions.contains(position)) {
                        addTile(new Tile("0border", position), TileStep.Tile, true);
                    }
                }

            } else {
                addTile(tileMap.get(setup), TileStep.Tile);
            }
            tileMap.remove(setup);
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
        if (!game.getTileDistances().isEmpty()) tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), TileStep.Distance));
        if (debug) debugTileTime = System.nanoTime() - debugStartTime;
    }

    private void setupFow(GenericInteractionCreateEvent event, Map<String, Tile> tilesToDisplay) {
        if (debug) debugStartTime = System.nanoTime();
        if (game.isFoWMode() && event != null) {
            if (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)) {
                isFoWPrivate = true;
                Player player = getFowPlayer(event);

                // IMPORTANT NOTE : This method used to be local and was refactored to extract
                // any references to tilesToDisplay
                fowPlayer = Helper.getGamePlayer(game, player, event, null);

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
        }
        if (debug) debugFowTime = System.nanoTime() - debugStartTime;
    }

    private void logDebug(GenericInteractionCreateEvent event) {
        if (!debug) return;
        long total = System.nanoTime() - debugAbsoluteStartTime;
        String sb = " Total time to generate map " + game.getName() + ": " + Helper.getTimeRepresentationNanoSeconds(total) + "\n" +
            "    Frog time: " + Helper.getTimeRepresentationNanoSeconds(debugFowTime) + String.format(" (%2.2f%%)", (double) debugFowTime / (double) total * 100.0) + "\n" +
            "    Tile time: " + Helper.getTimeRepresentationNanoSeconds(debugTileTime) + String.format(" (%2.2f%%)", (double) debugTileTime / (double) total * 100.0) +
            "\n" +
            "    Info time: " + Helper.getTimeRepresentationNanoSeconds(debugGameInfoTime) + String.format(" (%2.2f%%)", (double) debugGameInfoTime / (double) total * 100.0) +
            "\n" +
            "     Discord time: " + Helper.getTimeRepresentationNanoSeconds(debugDiscordTime) + String.format(" (%2.2f%%)", (double) debugDiscordTime / (double) total * 100.0) +
            "\n";
        MessageHelper.sendMessageToBotLogChannel(event, "```\nDEBUG - GenerateMap Timing:\n" + sb + "\n```");
        ImageHelper.getCacheStats().ifPresent(stats -> MessageHelper.sendMessageToBotLogChannel("```\n" + stats + "\n```"));
    }

    private void sendToWebsite(GenericInteractionCreateEvent event) {
        String testing = System.getenv("TESTING");
        if (testing == null && displayType == DisplayType.all && (isFoWPrivate == null || !isFoWPrivate)) {
            WebHelper.putMap(game.getName(), mainImage);
            WebHelper.putData(game.getName(), game);
        } else if (isFoWPrivate != null && isFoWPrivate) {
            Player player = getFowPlayer(event);
            WebHelper.putMap(game.getName(), mainImage, true, player);
        }
    }

    private FileUpload uploadToDiscord() {
        if (!uploadToDiscord) {
            return null;
        }
        if (debug) debugStartTime = System.nanoTime();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // CONVERT PNG TO JPG
            BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(mainImage, 0, 0, Color.black, null);
            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(out));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(0.15f);
            }

            imageWriter.write(null, new IIOImage(convertedImage, null, null), defaultWriteParam);

            String fileName = game.getName() + "_" + getTimeStamp() + ".jpg";
            return FileUpload.fromData(out.toByteArray(), fileName);
        } catch (IOException e) {
            BotLogger.log("Could not create FileUpload", e);
        }
        if (debug) debugDiscordTime = System.nanoTime() - debugStartTime;
        return null;
    }

    private Player getFowPlayer(@Nullable GenericInteractionCreateEvent event) {
        if (event == null)
            return null;
        String user = event.getUser().getId();
        return game.getPlayer(user);
    }

    @NotNull
    public static String getTimeStamp() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd - HH.mm.ss");
        return ZonedDateTime.now(ZoneOffset.UTC).format(fmt);
    }

    @Nullable
    private static String getFactionIconPath(String factionID) {
        if ("null".equals(factionID) || StringUtils.isBlank(factionID)) {
            return null;
        }
        String factionFile = ResourceHelper.getInstance().getFactionFile(factionID + ".png");
        if (factionFile == null) {
            // Handle homebrew factions based on real factions
            if (Mapper.getFaction(factionID).getHomebrewReplacesID().isPresent()) {
                factionFile = ResourceHelper.getInstance().getFactionFile(Mapper.getFaction(factionID).getHomebrewReplacesID().get() + ".png");
            }
        }
        if (factionFile == null) {
            BotLogger.log("Could not find image file for faction icon: " + factionID);
        }
        return factionFile;
    }

    private static BufferedImage getPlayerFactionIconImage(Player player) {
        return getPlayerFactionIconImageScaled(player, 95, 95);
    }

    private static BufferedImage getPlayerFactionIconImageScaled(Player player, float scale) {
        int scaledWidth = (int) (95 * scale);
        int scaledHeight = (int) (95 * scale);
        return getPlayerFactionIconImageScaled(player, scaledWidth, scaledHeight);
    }

    @Nullable
    private static BufferedImage getPlayerFactionIconImageScaled(Player player, int width, int height) {
        if (player == null) return null;
        Emoji factionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
        if (player.hasCustomFactionEmoji() && factionEmoji instanceof CustomEmoji factionCustomEmoji) {
            int urlImagePadding = 5;
            return ImageHelper.readURLScaled(factionCustomEmoji.getImageUrl(), width - urlImagePadding, height - urlImagePadding);
        }

        return getFactionIconImageScaled(player.getFaction(), width, height);
    }

    @Nullable
    private static BufferedImage getFactionIconImageScaled(String factionID, int width, int height) {
        String factionPath = getFactionIconPath(factionID);
        if (factionPath == null) return null;

        if (width == 95 && height == 95) { //default faction image size is 95x95
            return ImageHelper.read(factionPath);
        }

        return ImageHelper.readScaled(factionPath, width, height);
    }

    private static Image getPlayerDiscordAvatar(Player player) {
        try {
            Member member = AsyncTI4DiscordBot.guildPrimary.getMemberById(player.getUserID());
            if (member == null) return null;

            return ImageHelper.readURLScaled(member.getEffectiveAvatar().getUrl(), 32, 32);
        } catch (Exception e) {
            BotLogger.log("Could not get Avatar", e);
        }
        return null;
    }

    private void drawGame(GenericInteractionCreateEvent event) {
        if (debug) debugStartTime = System.nanoTime();
        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        setupFow(event, tilesToDisplay);
        setupTilesForDisplayTypeAllAndMap(tilesToDisplay);

        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        String timeStamp = getTimeStamp();
        graphics.drawString(game.getName() + " " + game.getCreationDate() + " - " + timeStamp, 0, 34);

        int widthOfLine = width - 50;
        int y = heightForGameInfo + 60;
        int x = 10;
        int deltaX = 0;
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        int yDelta = 0;

        // GAME MODES
        int deltaY = -150;
        if (game.isCompetitiveTIGLGame()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_TIGL.png");
            deltaX += 100;
        }
        if (game.isAbsolMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_Absol.png");
            deltaX += 100;
        }
        if (game.isMiltyModMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_MiltyMod.png");
            deltaX += 100;
        }
        if (game.isDiscordantStarsMode()) {
            drawGeneralImage(x + deltaX, y + deltaY, "GameMode_DiscordantStars.png");
        }

        // GAME FUN NAME
        deltaY = 35;
        graphics.setFont(Storage.getFont50());
        graphics.setColor(Color.WHITE);
        graphics.drawString(game.getCustomName(), 0, y);

        // STRATEGY CARDS
        y = strategyCards(y);

        int tempY = y;
        y = objectives(y + 180);
        y = laws(y);
        y = events(y);
        tempY = scoreTrack(tempY + 20);
        if (displayType != DisplayType.stats) {
            playerInfo(game);
        }

        if (displayType == DisplayType.all || displayType == DisplayType.stats) {
            graphics.setFont(Storage.getFont32());
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(new BasicStroke(5));
            int realX = x;
            HashMap<UnitKey, Integer> unitCount = new HashMap<>();
            for (Player player : players) {
                int baseY = y;
                x = realX;

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                if (convertToGeneric) {
                    continue;
                }

                graphics.drawImage(getPlayerDiscordAvatar(player), x, y + 5, null);
                y += 34;
                graphics.setFont(Storage.getFont32());
                Color color = getColor(player.getColor());
                graphics.setColor(Color.WHITE);
                String userName = player.getUserName() + ("null".equals(player.getColor()) ? "" : " (" + player.getColor() + ")");
                if (player.isAFK()) {
                    userName = userName + " -- AFK";
                }
                graphics.drawString(userName, x + 34, y);
                if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
                    continue;
                }

                y += 2;
                String faction = player.getFaction();
                if (faction != null) {
                    drawPlayerFactionIconImage(graphics, player, x, y, 95, 95);
                }
                y += 4;

                // PAINT SCs
                Set<Integer> playerSCs = player.getSCs();
                if (playerSCs.size() == 1) {
                    int sc = playerSCs.stream().findFirst().get();
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
                    } else {
                        graphics.drawString(scText, x + 90, y + 70 + yDelta);
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

                        if (scText.contains("0/")) {
                            graphics.setFont(Storage.getFont64());
                            graphics.drawString("0", x + 90, y + 70 + yDelta);
                            graphics.setFont(Storage.getFont32());
                            graphics.setColor(Color.WHITE);
                            graphics.drawString(Integer.toString(sc), x + 120, y + 80 + yDelta);
                        } else {
                            drawCenteredString(graphics, scText, new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32), Storage.getFont32());
                        }
                        count++;
                    }
                }

                String activePlayerID = game.getActivePlayer();
                String phase = game.getCurrentPhase();
                if (player.isPassed()) {
                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(new Color(238, 58, 80));
                    graphics.drawString("PASSED", x + 5, y + 95 + yDelta);
                } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(new Color(50, 230, 80));
                    graphics.drawString("ACTIVE", x + 9, y + 95 + yDelta);
                }

                graphics.setFont(Storage.getFont32());
                graphics.setColor(Color.WHITE);
                String ccCount = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                x += 120;
                graphics.drawString(ccCount, x + 40, y + deltaY + 40);

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
                graphics.drawString("T/F/S", x + 40, y + deltaY);

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

                if (game.getNomadCoin()) {
                    drawPAImage(x + 345, y + yDelta, nomadCoinImage);
                } else {
                    drawPAImage(x + 345, y + yDelta, tradeGoodImage);
                }
                graphics.drawString(Integer.toString(player.getTg()), x + 360, y + deltaY + 50);

                drawPAImage(x + 410, y + yDelta, commoditiesImage);
                String comms = player.getCommodities() + "/" + player.getCommoditiesTotal();
                graphics.drawString(comms, x + 415, y + deltaY + 50);

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
                // xDelta = x + 550 + Math.max(xDelta, xDelta2); DISABLE AUTO-SCALE BASED ON AMOUNT OF FRAGS - ALIGNS PLAYERS' LEADERS/PLANETS
                int yPlayArea = y - 30;
                y += 85;
                y += 200;

                int soCount = objectivesSO(yPlayArea + 150, player);

                int xDeltaSecondRow = xDelta;
                int yPlayAreaSecondRow = yPlayArea + 160;
                if (!player.getPlanets().isEmpty()) {
                    xDeltaSecondRow = planetInfo(player, xDeltaSecondRow, yPlayAreaSecondRow);
                }

                int xDeltaFirstRowFromRightSide = 0;
                int xDeltaSecondRowFromRightSide = 0;
                // FIRST ROW RIGHT SIDE
                xDeltaFirstRowFromRightSide = nombox(player, xDeltaFirstRowFromRightSide, yPlayArea);

                // SECOND ROW RIGHT SIDE
                xDeltaSecondRowFromRightSide = reinforcements(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, unitCount);
                xDeltaSecondRowFromRightSide = sleeperTokens(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);

                xDeltaSecondRowFromRightSide = speakerToken(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);

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
                //  }

                if (!player.getNotResearchedFactionTechs().isEmpty()) {
                    xDelta = factionTechInfo(player, xDelta, yPlayArea);
                }

                if (!player.getAbilities().isEmpty()) {
                    xDelta = abilityInfo(player, xDelta, yPlayArea);
                }

                g2.setColor(color);
                if (soCount > 4) {
                    y += (soCount - 4) * 43;
                }
                g2.drawRect(realX - 5, baseY, x + widthOfLine, y - baseY);
                y += 15;

            }
        }
        if (debug) debugGameInfoTime = System.nanoTime() - debugStartTime;
    }

    private int speakerToken(Player player, int xDeltaSecondRowFromRightSide, int yPlayAreaSecondRow) {
        if (player.getUserID().equals(game.getSpeaker())) {
            xDeltaSecondRowFromRightSide += 200;
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID(Constants.SPEAKER));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                graphics.drawImage(bufferedImage, width - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow + 25, null);
            }
        }
        return xDeltaSecondRowFromRightSide;
    }

    private int sleeperTokens(Player player, int xDeltaSecondRowFromRightSide, int yPlayAreaSecondRow) {
        if (player.hasAbility("awaken")) {
            xDeltaSecondRowFromRightSide += 200;
            // paintNumber(unitID, x, y, remainingReinforcements, playerColor);

            String sleeperFile = ResourceHelper.getInstance().getTokenFile(Constants.TOKEN_SLEEPER_PNG);
            BufferedImage bufferedImage = ImageHelper.read(sleeperFile);
            if (bufferedImage != null) {
                List<Point> points = new ArrayList<>() {
                    {
                        add(new Point(0, 15));
                        add(new Point(50, 0));
                        add(new Point(100, 25));
                        add(new Point(50, 50));
                        add(new Point(10, 40));
                    }
                };
                for (int i = 0; i < 5 - game.getSleeperTokensPlacedCount(); i++) {
                    Point point = points.get(i);
                    graphics.drawImage(bufferedImage, width - xDeltaSecondRowFromRightSide + point.x, yPlayAreaSecondRow + point.y, null);
                }
            }
        }
        return xDeltaSecondRowFromRightSide;
    }

    private int omenDice(Player player, int x, int y) {
        int deltaX = 0;
        if (player.hasAbility("divination") && ButtonHelperAbilities.getAllOmenDie(game).size() > 0) {

            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(new BasicStroke(2));

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
        g2.setStroke(new BasicStroke(2));
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
        g2.setStroke(new BasicStroke(2));
        Collection<Player> players = game.getPlayers().values();
        for (String pn : player.getPromissoryNotesInPlayArea()) {
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);

            boolean commanderUnlocked = false;
            Player promissoryNoteOwner = game.getPNOwner(pn);
            if (promissoryNoteOwner == null) { // nobody owns this note - possibly eliminated player
                String error = game.getName() + " " + player.getUserName();
                error += "  `GenerateMap.pnInfo` is trying to display a Promissory Note without an owner - possibly an eliminated player: " + pn;
                BotLogger.log(error);
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    String playerFaction = player_.getFaction();
                    if (playerColor != null && playerColor.equals(promissoryNoteOwner.getColor()) || playerFaction != null && playerFaction.equals(promissoryNoteOwner.getFaction())) {
                        String pnColorFile = "pa_pn_color_" + Mapper.getColorID(playerColor) + ".png";
                        drawPAImage(x + deltaX, y, pnColorFile);

                        if (game.isFrankenGame()) drawFactionIconImage(graphics, promissoryNote.getFaction().orElse(""), x + deltaX - 1, y + 86, 42, 42);
                        drawPlayerFactionIconImage(graphics, promissoryNoteOwner, x + deltaX - 1, y + 108, 42, 42);
                        Leader leader = player_.unsafeGetLeader(Constants.COMMANDER);
                        if (leader != null) {
                            commanderUnlocked = !leader.isLocked();
                        }
                        break;
                    }
                }
            }

            if (pn.endsWith("_sftt")) {
                pn = "sftt";
            } else if (pn.endsWith("_an")) {
                pn = "alliance";
                if (!commanderUnlocked) {
                    pn += "_exh";
                }
            }

            String pnName = "pa_pn_name_" + pn + ".png";
            drawPAImage(x + deltaX, y, pnName);
            if (promissoryNote != null && promissoryNote.getAttachment().isPresent() && !promissoryNote.getAttachment().get().isBlank()) {
                String tokenID = promissoryNote.getAttachment().get();
                found: for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(tokenID))) {
                            drawPlanetImage(x + deltaX + 17, y, "pc_planetname_" + unitHolder.getName() + "_rdy.png", unitHolder.getName());
                            break found;
                        }
                    }
                }
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
        g2.setStroke(new BasicStroke(2));

        List<String> exhaustedRelics = player.getExhaustedRelics();
        for (String relicID : player.getRelics()) {

            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }
            String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
            String relicFileName = "pa_relics_" + relicID + statusOfPlanet + ".png";
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            if (!relicID.contains("axisorder")) {
                drawPAImage(x + deltaX, y, "pa_relics_icon.png");
            }

            drawPAImage(x + deltaX, y, relicFileName);
            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private int leaderInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        for (Leader leader : player.getLeaders()) {
            boolean isExhaustedLocked = leader.isExhausted() || leader.isLocked();
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            String status = isExhaustedLocked ? "_exh" : "_rdy";
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);

            if (Mapper.isValidLeader(leader.getId())) {
                LeaderModel leaderModel = Mapper.getLeader(leader.getId());
                drawFactionIconImage(graphics, leaderModel.getFaction(), x + deltaX - 1, y + 108, 42, 42);
            }

            if (leader.getTgCount() != 0) {
                graphics.setColor(new Color(241, 176, 0));
                graphics.setFont(Storage.getFont32());
                graphics.drawString(Integer.toString(leader.getTgCount()), x + deltaX + 3, y + 32);
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

            String leaderInfoFileName = "pa_leaders_" + leader.getId() + status + ".png";
            String resourcePath = ResourceHelper.getInstance().getPAResource(leaderInfoFileName);
            BufferedImage resourceBufferedImage;
            try {
                resourceBufferedImage = ImageHelper.read(resourcePath);
                if (resourceBufferedImage == null) {
                    LeaderModel leaderModel = Mapper.getLeader(leader.getId());
                    drawTwoLinesOfTextVertically(g2, leaderModel.getShortName(), x + deltaX + 10, y + 148, Storage.getFont16(), 16);
                } else {
                    graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                }
            } catch (Exception e) {
                BotLogger.log("Bad file: " + leaderInfoFileName, e);
            }

            deltaX += 48;
        }
        if (player.hasAbility("imperia")) {
            deltaX += 5;
            List<String> mahactCCs = player.getMahactCC();
            Collection<Player> players = game.getRealPlayersNDummies();
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    if (mahactCCs.contains(playerColor)) {
                        Leader leader_ = player_.unsafeGetLeader(Constants.COMMANDER);
                        if (leader_ != null) {
                            boolean locked = leader_.isLocked();
                            String imperiaColorFile = "pa_leaders_imperia";
                            if (locked) {
                                imperiaColorFile += "_exh";
                            } else {
                                imperiaColorFile += "_rdy";
                            }
                            imperiaColorFile += ".png";
                            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
                            drawPlayerFactionIconImage(graphics, player_, x + deltaX - 1, y + 108, 42, 42);
                            drawPAImage(x + deltaX, y, imperiaColorFile);
                            String status_ = locked ? "_exh" : "_rdy";
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
        g2.setStroke(new BasicStroke(2));

        String bankImage = "vaden".equalsIgnoreCase(player.getFaction()) ? "pa_ds_vaden_bank.png" : "pa_debtaccount.png";
        drawPAImage(x + deltaX, y, bankImage);

        deltaX += 24;
        deltaY += 2;

        boolean hideFactionIcon = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);

        int tokenDeltaY = 0;
        int playerCount = 0;
        int maxTokenDeltaX = 0;
        for (Entry<String, Integer> debtToken : player.getDebtTokens().entrySet()) {
            int tokenDeltaX = 0;
            String controlID = hideFactionIcon ? Mapper.getControlID("gray") : Mapper.getControlID(debtToken.getKey());
            if (controlID.contains("null")) {
                continue;
            }

            float scale = 0.60f;

            BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);

            for (int i = 0; i < debtToken.getValue(); i++) {
                drawControlToken(graphics, controlTokenImage, getPlayerByControlMarker(game.getPlayers().values(), controlID), x + deltaX + tokenDeltaX, y + deltaY + tokenDeltaY,
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

    private static void drawControlToken(Graphics graphics, BufferedImage bottomTokenImage, Player player, int x, int y, boolean hideFactionIcon, float scale) {
        graphics.drawImage(bottomTokenImage, x, y, null);

        if (hideFactionIcon) return;
        scale = scale * 0.50f;
        BufferedImage factionImage = getPlayerFactionIconImageScaled(player, scale);
        if (factionImage == null) return;

        int centreCustomTokenHorizontally = bottomTokenImage.getWidth() / 2 - factionImage.getWidth() / 2;
        int centreCustomTokenVertically = bottomTokenImage.getHeight() / 2 - factionImage.getHeight() / 2;

        graphics.drawImage(factionImage, x + centreCustomTokenHorizontally, y + centreCustomTokenVertically, null);
    }

    private int abilityInfo(Player player, int x, int y) {
        int deltaX = 10;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        boolean addedAbilities = false;
        for (String abilityID : player.getAbilities()) {

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

            String status = isExhaustedLocked ? "_exh" : "_rdy";
            abilityFileName = abilityFileName + status + ".png";
            String resourcePath = ResourceHelper.getInstance().getPAResource(abilityFileName);
            if (resourcePath != null) {
                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            } else if (game.isFrankenGame()) {
                AbilityModel abilityModel = Mapper.getAbility(abilityID);
                drawFactionIconImage(g2, abilityModel.getFaction(), x + deltaX - 1, y, 42, 42);
                drawTwoLinesOfTextVertically(g2, abilityModel.getShortName(), x + deltaX + 6, y + 144, Storage.getFont16(), 20);
                graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            }

            deltaX += 48;
            addedAbilities = true;
        }
        return x + deltaX + (addedAbilities ? 20 : 0);
    }

    private int reinforcements(Player player, int xDeltaFromRightSide, int y, HashMap<UnitKey, Integer> unitCount) {
        HashMap<String, Tile> tileMap = game.getTileMap();
        int x = width - 450 - xDeltaFromRightSide;
        drawPAImage(x, y, "pa_reinforcements.png");
        if (unitCount.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    fillUnits(unitCount, unitHolder, false);
                }
            }
            for (Player player_ : game.getPlayers().values()) {
                UnitHolder unitHolder = player_.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                if (unitHolder == null) {
                    continue;
                }
                fillUnits(unitCount, unitHolder, true);
            }
        }

        String playerColor = player.getColor();
        for (String unitID : Mapper.getUnitIDList()) {
            UnitKey unitColorID = Mapper.getUnitKey(unitID, playerColor);
            if ("cff".equals(unitID)) {
                unitColorID = Mapper.getUnitKey("ff", playerColor);
            }
            if ("cgf".equals(unitID)) {
                unitColorID = Mapper.getUnitKey("gf", playerColor);
            }

            Integer count = unitCount.get(unitColorID);
            if ("csd".equals(unitID)) {
                // if (!(player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2"))) {
                //     continue;
                // }
                // unitColorID = Mapper.getUnitKey("sd", playerColor);
                continue;
            }

            if (count == null) {
                count = 0;
            }
            if ((player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) && "sd".equals(unitID)) {
                if (unitCount.get(Mapper.getUnitKey("csd", playerColor)) != null) {
                    count = count + unitCount.get(Mapper.getUnitKey("csd", playerColor));
                }

            }
            UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(unitID);

            if (reinforcementsPosition != null) {
                int positionCount = player.getUnitCap(unitID);
                boolean aboveCap = true;
                if (positionCount == 0) {
                    positionCount = reinforcementsPosition.getPositionCount(unitID);
                    aboveCap = false;
                }
                int remainingReinforcements = positionCount - count;
                if (remainingReinforcements > 0) {
                    for (int i = 0; i < remainingReinforcements; i++) {
                        Point position = reinforcementsPosition.getPosition(unitID);
                        BufferedImage image = null;
                        try {
                            String unitPath = ResourceHelper.getInstance().getUnitFile(unitColorID);
                            image = ImageHelper.read(unitPath);
                        } catch (Exception e) {
                            BotLogger.log("Could not parse unit file for reinforcements: " + unitID, e);
                        }
                        BufferedImage decal = null;
                        try {
                            if (image != null && !"null".equals(player.getDecalSet()) && Mapper.isValidDecalSet(player.getDecalSet())) {
                                String decalFileName = String.format("%s_%s%s", player.getDecalSet(), unitID, getBlackWhiteFileSuffix(Mapper.getColorID(player.getColor())));
                                String decalPath = ResourceHelper.getInstance().getDecalFile(decalFileName);
                                decal = ImageHelper.read(decalPath);
                            }
                        } catch (Exception e) {
                            BotLogger.log("Could not parse decal file for reinforcements: " + player.getDecalSet(), e);
                        }
                        graphics.drawImage(image, x + position.x, y + position.y, null);
                        graphics.drawImage(decal, x + position.x, y + position.y, null);
                        if (aboveCap) {
                            i = remainingReinforcements;
                        }
                    }
                } else {
                    if (remainingReinforcements < 0 && !game.isDiscordantStarsMode() && game.getCCNPlasticLimit()) {
                        String warningMessage = playerColor + " is exceeding unit plastic or cardboard limits for " + ButtonHelper.getUnitName(AliasHandler.resolveUnit(unitID));
                        if (game.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), warningMessage, ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, ButtonHelper.getUnitName(AliasHandler.resolveUnit(unitID))));
                        } else {
                            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), warningMessage,  ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, ButtonHelper.getUnitName(AliasHandler.resolveUnit(unitID))));
                        }
                    }
                }
                if (-5 <= remainingReinforcements)
                    paintNumber(unitID, x, y, remainingReinforcements, playerColor);
            }
        }

        int ccCount = Helper.getCCCount(game, playerColor);
        String CC_TAG = "cc";
        UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(CC_TAG);
        if (reinforcementsPosition != null && playerColor != null) {
            int positionCount = reinforcementsPosition.getPositionCount(CC_TAG);
            int remainingReinforcements = positionCount - ccCount;
            if (remainingReinforcements > 0) {
                for (int i = 0; i < remainingReinforcements; i++) {
                    try {
                        String ccID = Mapper.getCCID(playerColor);
                        Point position = reinforcementsPosition.getPosition(CC_TAG);
                        drawCCOfPlayer(graphics, ccID, x + position.x, y + position.y, 1, player, false);
                    } catch (Exception e) {
                        BotLogger.log("Could not parse file for CC: " + playerColor, e);
                    }
                }
            }
            if (-5 <= remainingReinforcements)
                paintNumber(CC_TAG, x, y, remainingReinforcements, playerColor);
        }
        return xDeltaFromRightSide + 450;
    }

    private static void fillUnits(HashMap<UnitKey, Integer> unitCount, UnitHolder unitHolder, boolean ignoreInfantryFighters) {
        HashMap<UnitKey, Integer> units = unitHolder.getUnits();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            Integer count = unitCount.get(unitKey);
            if (count == null) {
                count = 0;
            }
            if (unitKey.getUnitType() == UnitType.Infantry || unitKey.getUnitType() == UnitType.Fighter) {
                if (ignoreInfantryFighters) {
                    continue;
                }
                count++;
            } else {
                count += unitEntry.getValue();
            }
            unitCount.put(unitKey, count);
        }
    }

    private int nombox(Player player, int xDeltaFromRightSide, int y) {
        int x = width - 450 - xDeltaFromRightSide;
        UnitHolder unitHolder = player.getNomboxTile().getUnitHolders().get(Constants.SPACE);
        if (unitHolder == null || unitHolder.getUnits().isEmpty()) {
            return 0;
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
            BufferedImage bufferedImage = getPlayerFactionIconImage(player);
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

                try {
                    String unitPath = Tile.getUnitPath(unitKey);
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
                    BotLogger.log("Could not parse unit file for: " + unitKey, e);
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

                BufferedImage decal = null;
                try {
                    String color = AliasHandler.resolveColor(unitKey.getColorID());
                    Player decalPlayer = player.getGame().getPlayerFromColorOrFaction(color);
                    if (decalPlayer != null && !"null".equals(decalPlayer.getDecalSet()) && Mapper.isValidDecalSet(player.getDecalSet())) {
                        String decalFileName = String.format("%s_%s%s", decalPlayer.getDecalSet(), unitKey.asyncID(), getBlackWhiteFileSuffix(
                            Mapper.getColorID(decalPlayer.getColor())));
                        String decalPath = ResourceHelper.getInstance().getDecalFile(decalFileName);
                        decal = ImageHelper.read(decalPath);
                    }
                } catch (Exception e) {
                    // BotLogger.log("Could not parse decal file for: " + player.getDecalSet(), e);
                }

                BufferedImage spoopy = null;
                if ((unitKey.getUnitType() == UnitType.Warsun) && (ThreadLocalRandom.current().nextInt(1000) == 0)) {

                    String spoopypath = ResourceHelper.getInstance().getSpoopyFile();
                    spoopy = ImageHelper.read(spoopypath);
                    //     BotLogger.log("SPOOPY TIME: " + spoopypath);
                }

                if (justNumber) {
                    graphics.setFont(Storage.getFont40());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(Integer.toString(countOfUnits), position.x, position.y);
                    break;
                }
                position.y -= (countOfUnits * 7);
                for (int i = 0; i < unitCount; i++) {
                    graphics.drawImage(image, position.x, position.y + deltaY, null);
                    graphics.drawImage(decal, position.x, position.y + deltaY, null);
                    if (spoopy != null) {
                        graphics.drawImage(spoopy, position.x, position.y + deltaY, null);
                    }

                    deltaY += 14;
                }
            }
        }
        return xDeltaFromRightSide + 450;
    }

    private void paintNumber(String unitID, int x, int y, int reinforcementsCount, String color) {
        String id = "number_" + unitID;
        UnitTokenPosition textPosition = PositionMapper.getReinforcementsPosition(id);
        if (textPosition == null) return;

        String text = "pa_reinforcements_numbers_" + reinforcementsCount;
        String colorID = Mapper.getColorID(color);
        text += getBlackWhiteFileSuffix(colorID);
        Point position = textPosition.getPosition(id);
        drawPAImage(x + position.x, y + position.y, text);
    }

    private int planetInfo(Player player, int x, int y) {
        HashMap<String, UnitHolder> planetsInfo = game.getPlanetsInfo();
        List<String> planets = player.getPlanets();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        // RESOURCE/INFLUENCE TOTALS
        drawPAImage(x + deltaX - 2, y - 2, "pa_resinf_info.png");
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 152, 152);
        if (player.hasLeaderUnlocked("xxchahero")) { // XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
            if ("586504147746947090".equals(player.getUserID())) {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha_gedsdead.png", 0.9f);
            } else {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha.png", 0.9f);
            }
            drawFactionIconImageOpaque(graphics, "xxcha", x + deltaX + 75 - 94 / 2, y + 75 - 94 / 2, 95, 95, 0.15f);
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerResources), new Rectangle(x + deltaX, y + 75 - 35 + 5, 150, 35), Storage.getFont35());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerResources), new Rectangle(x + deltaX, y + 75 + 5, 150, 24), Storage.getFont24());
        } else { // NOT XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
            int availablePlayerResourcesOptimal = Helper.getPlayerOptimalResourcesAvailable(player, game);
            // int totalPlayerResourcesOptimal = Helper.getPlayerOptimalResourcesTotal(player, map);
            int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, game);
            int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, game);
            int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, game);
            // int totalPlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceTotal(player, map);
            int availablePlayerFlex = Helper.getPlayerFlexResourcesInfluenceAvailable(player, game);
            // int totalPlayerFlex = Helper.getPlayerFlexResourcesInfluenceTotal(player, map);

            // RESOURCES
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerResources), new Rectangle(x + deltaX + 30, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerResources), new Rectangle(x + deltaX + 30, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#d5bd4f")); // greyish-yellow
            drawCenteredString(graphics, String.valueOf(availablePlayerResourcesOptimal), new Rectangle(x + deltaX + 30, y + 90, 32, 32), Storage.getFont18());
            // drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 30, y + 100, 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // drawCenteredString(graphics, String.valueOf(totalPlayerResourcesOptimal), new Rectangle(x + deltaX + 34, y + 109, 32, 32), Storage.getFont32());

            // INFLUENCE
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerInfluence), new Rectangle(x + deltaX + 90, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerInfluence), new Rectangle(x + deltaX + 90, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#57b9d9")); // greyish-blue
            drawCenteredString(graphics, String.valueOf(availablePlayerInfluenceOptimal), new Rectangle(x + deltaX + 90, y + 90, 32, 32), Storage.getFont18());
            // drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 90, y + 100, 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // drawCenteredString(graphics, String.valueOf(totalPlayerInfluenceOptimal), new Rectangle(x + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

            // FLEX
            graphics.setColor(Color.WHITE);
            // drawCenteredString(graphics, "FLEX", new Rectangle(x + deltaX, y + 130, 150, 8), Storage.getFont8());
            if ("203608548440014848".equals(player.getUserID()))
                graphics.setColor(Color.decode("#f616ce"));
            drawCenteredString(graphics, String.valueOf(availablePlayerFlex), new Rectangle(x + deltaX, y + 115, 150, 20), Storage.getFont18());
            // drawCenteredString(graphics, String.valueOf(totalPlayerFlex), new Rectangle(x + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

        }

        deltaX += 156;

        boolean randomizeList = player != fowPlayer && isFoWPrivate != null && isFoWPrivate;
        if (randomizeList) {
            Collections.shuffle(planets);
        }
        for (String planet : planets) {
            try {
                UnitHolder unitHolder = planetsInfo.get(planet);
                if (!(unitHolder instanceof Planet planetHolder)) {
                    BotLogger.log(game.getName() + ": Planet unitHolder not found: " + planet);
                    continue;
                }

                boolean isExhausted = exhaustedPlanets.contains(planet);
                if (isExhausted) {
                    graphics.setColor(Color.GRAY);
                } else {
                    graphics.setColor(Color.WHITE);
                }

                int resources = planetHolder.getResources();
                int influence = planetHolder.getInfluence();
                String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
                String planetFileName = "pc_planetname_" + planet + statusOfPlanet + ".png";
                String resFileName = "pc_res_" + resources + statusOfPlanet + ".png";
                String infFileName = "pc_inf_" + influence + statusOfPlanet + ".png";

                graphics.drawRect(x + deltaX - 2, y - 2, 52, 152);

                if (unitHolder.getTokenList().contains(Constants.ATTACHMENT_TITANSPN_PNG)) {
                    String planetTypeName = "pc_attribute_titanspn.png";
                    drawPlanetImage(x + deltaX + 2, y + 2, planetTypeName, planet);
                } else {
                    String originalPlanetType = planetHolder.getOriginalPlanetType();
                    if ("none".equals(originalPlanetType) && "mr".equals(planet))
                        originalPlanetType = "mr";
                    if ("none".equals(originalPlanetType))
                        originalPlanetType = TileHelper.getAllPlanets().get(planet).getFactionHomeworld();
                    if (Optional.ofNullable(originalPlanetType).isEmpty()) {
                        originalPlanetType = "none";
                    }
                    if ("none".equals(originalPlanetType))
                        originalPlanetType = player.getFaction();

                    if (!originalPlanetType.isEmpty()) {
                        if ("keleres".equals(player.getFaction()) && ("mentak".equals(originalPlanetType) ||
                            "xxcha".equals(originalPlanetType) ||
                            "argent".equals(originalPlanetType))) {
                            originalPlanetType = "keleres";
                        }

                        if (Mapper.isValidFaction(originalPlanetType)) {
                            drawFactionIconImage(graphics, originalPlanetType, x + deltaX - 2, y - 2, 52, 52);
                        } else {
                            String planetTypeName = "pc_attribute_" + originalPlanetType + ".png";
                            drawPlanetImage(x + deltaX + 1, y + 2, planetTypeName, planet);
                        }
                    }
                }

                // GLEDGE CORE
                if (unitHolder.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                    String tokenPath = ResourceHelper.getInstance().getTokenFile(Constants.GLEDGE_CORE_PNG);
                    BufferedImage image = ImageHelper.readScaled(tokenPath, 0.25f);
                    graphics.drawImage(image, x + deltaX + 15, y + 112, null);
                }

                boolean hasAttachment = planetHolder.hasAttachment();
                if (hasAttachment) {
                    String planetTypeName = "pc_upgrade.png";
                    drawPlanetImage(x + deltaX + 26, y + 40, planetTypeName, planet);
                }

                if (planetHolder.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                    String khraskGardenWorlds = "pc_ds_khraskbonus.png";
                    drawPlanetImage(x + deltaX, y, khraskGardenWorlds, planet);
                }

                boolean hasAbility = planetHolder.isHasAbility() ||
                    planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge") || token.contains("legendary") || token.contains("consulate"));
                if (hasAbility) {
                    String statusOfAbility = exhaustedPlanetsAbilities.contains(planet) ? "_exh" : "_rdy";
                    String planetTypeName = "pc_legendary" + statusOfAbility + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 60, planetTypeName, planet);
                }

                boolean hasBentorEncryptionKey = unitHolder.getTokenList().stream().anyMatch(token -> token.contains("encryptionkey"));
                // BENTOR ENCRYPTION KEY
                if (hasBentorEncryptionKey) {
                    String imageFileName = "pc_tech_bentor_encryptionkey.png";
                    drawPlanetImage(x + deltaX + 26, y + 82, imageFileName, planet);
                }

                String originalTechSpeciality = planetHolder.getOriginalTechSpeciality();
                if (!originalTechSpeciality.isEmpty() && !hasBentorEncryptionKey) {
                    String planetTypeName = "pc_tech_" + originalTechSpeciality + statusOfPlanet + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 82, planetTypeName, planet);
                } else if (!hasBentorEncryptionKey) {
                    List<String> techSpeciality = planetHolder.getTechSpeciality();
                    for (String techSpec : techSpeciality) {
                        if (techSpec.isEmpty()) {
                            continue;
                        }
                        String planetTypeName = "pc_tech_" + techSpec + statusOfPlanet + ".png";
                        drawPlanetImage(x + deltaX + 26, y + 82, planetTypeName, planet);
                    }
                }

                drawPlanetImage(x + deltaX + 26, y + 103, resFileName, planet);
                drawPlanetImage(x + deltaX + 26, y + 125, infFileName, planet);
                drawPlanetImage(x + deltaX, y, planetFileName, planet);

                deltaX += 56;
            } catch (Exception e) {
                BotLogger.log("could not print out planet: " + planet.toLowerCase(), e);
            }
        }

        return x + deltaX + 20;
    }

    private int techInfo(Player player, int x, int y, Game game) {
        List<String> techs = player.getTechs();
        List<String> exhaustedTechs = player.getExhaustedTechs();
        //    if (techs.isEmpty()) {
        //       return y;
        //  }

        Map<String, TechnologyModel> techInfo = Mapper.getTechs();
        Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : techs) {
            String techType = Mapper.getTechType(tech).toString().toLowerCase();
            List<String> techList = techsFiltered.get(techType);
            if (techList == null) {
                techList = new ArrayList<>();
            }
            techList.add(tech);
            techsFiltered.put(techType, techList);
        }
        for (Map.Entry<String, List<String>> entry : techsFiltered.entrySet()) {
            List<String> list = entry.getValue();
            list.sort((tech1, tech2) -> {
                TechnologyModel tech1Info = techInfo.get(tech1);
                TechnologyModel tech2Info = techInfo.get(tech2);
                return TechnologyModel.sortTechsByRequirements(tech1Info, tech2Info);
            });
        }
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        deltaX = techField(x, y, techsFiltered.get(Constants.PROPULSION), exhaustedTechs, techInfo, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.WARFARE), exhaustedTechs, techInfo, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.CYBERNETIC), exhaustedTechs, techInfo, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.BIOTIC), exhaustedTechs, techInfo, deltaX, player);
        deltaX = techStasisCapsule(x, y, deltaX, player, techsFiltered.get(Constants.UNIT_UPGRADE), techInfo);
        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), techInfo, deltaX, player, game);
        return x + deltaX + 20;
    }

    private int factionTechInfo(Player player, int x, int y) {
        List<String> techs = player.getNotResearchedFactionTechs();
        if (techs.isEmpty()) {
            return y;
        }

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        int deltaX = 20;
        deltaX = factionTechField(x, y, techs, deltaX);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, Map<String, TechnologyModel> techInfo, int deltaX, Player player) {
        if (techs == null) {
            return deltaX;
        }
        for (String tech : techs) {
            boolean isExhausted = exhaustedTechs.contains(tech);
            String techStatus;
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
                techStatus = "_exh.png";
            } else {
                graphics.setColor(Color.WHITE);
                techStatus = "_rdy.png";
            }

            TechnologyModel techInformation = techInfo.get(tech);

            String techIcon;
            switch (techInformation.getType()) {
                case WARFARE -> techIcon = Constants.WARFARE;
                case PROPULSION -> techIcon = Constants.PROPULSION;
                case CYBERNETIC -> techIcon = Constants.CYBERNETIC;
                case BIOTIC -> techIcon = Constants.BIOTIC;
                case UNITUPGRADE -> techIcon = Constants.UNIT_UPGRADE;
                default -> techIcon = "";
            }

            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + techStatus;
                drawPAImage(x + deltaX, y, techSpec);
            }

            if (techInformation.getFaction().isPresent()) {
                drawFactionIconImage(graphics, techInformation.getFaction().get(), x + deltaX - 1, y + 108, 42, 42);
            }

            String techName = "pa_tech_techname_" + tech + techStatus;
            String resourcePath = ResourceHelper.getInstance().getPAResource(techName);
            if (resourcePath != null) {
                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                if ("dslaner".equalsIgnoreCase(tech)) {
                    drawTextVertically(graphics, "" + player.getAtsCount(), x + deltaX + 15, y + 140, Storage.getFont16());
                }
            } else {
                TechnologyModel techModel = Mapper.getTech(tech);
                drawTwoLinesOfTextVertically(graphics, techModel.getName(), x + deltaX + 20, y + 148, Storage.getFont16(), 16);
            }

            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }

    private int factionTechField(int x, int y, List<String> techs, int deltaX) {
        Map<String, TechnologyModel> techInfo = Mapper.getTechs();

        if (techs == null) {
            return deltaX;
        }

        for (String tech : techs) {
            graphics.setColor(Color.DARK_GRAY);

            TechnologyModel techInformation = techInfo.get(tech);
            if (techInformation.getType() == TechnologyType.UNITUPGRADE) continue;

            String techIcon;
            switch (techInformation.getType()) {
                case WARFARE -> techIcon = Constants.WARFARE;
                case PROPULSION -> techIcon = Constants.PROPULSION;
                case CYBERNETIC -> techIcon = Constants.CYBERNETIC;
                case BIOTIC -> techIcon = Constants.BIOTIC;
                default -> techIcon = "";
            }

            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + "_exh.png";
                drawPAImage(x + deltaX, y, techSpec);
            }

            if (techInformation.getFaction().isPresent()) {
                drawFactionIconImageOpaque(graphics, techInformation.getFaction().get(), x + deltaX + 1, y + 108, 42, 42, 0.5f);
            }

            String techName = "pa_tech_techname_" + tech + "_exh.png";
            String resourcePath = ResourceHelper.getInstance().getPAResource(techName);
            if (resourcePath != null) {
                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
            } else {
                TechnologyModel techModel = Mapper.getTech(tech);
                drawTwoLinesOfTextVertically(graphics, techModel.getName(), x + deltaX + 10, y + 148, Storage.getFont16(), 16);
            }

            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }

    private int techStasisCapsule(int x, int y, int deltaX, Player player, List<String> techs, Map<String, TechnologyModel> techInfo) {
        int stasisInfantry = player.getStasisInfantry();
        if ((techs == null && stasisInfantry == 0) || !hasInfantryII(techs, techInfo) && stasisInfantry == 0) {
            return deltaX;
        }
        String techSpec = "pa_tech_techname_stasiscapsule.png";
        drawPAImage(x + deltaX, y, techSpec);
        if (stasisInfantry < 20) {
            graphics.setFont(Storage.getFont35());
        } else {
            graphics.setFont(Storage.getFont30());
        }
        int centerX = 0;
        if (stasisInfantry < 10) {
            centerX += 5;
        }
        graphics.drawString(String.valueOf(stasisInfantry), x + deltaX + 3 + centerX, y + 148);
        graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
        deltaX += 48;
        return deltaX;
    }

    private boolean hasInfantryII(List<String> techs, Map<String, TechnologyModel> techInfo) {
        if (techs == null) {
            return false;
        }
        for (String tech : techs) {
            TechnologyModel techInformation = techInfo.get(tech);
            if ("inf2".equals(techInformation.getBaseUpgrade().orElse("")) || "inf2".equals(tech)) {
                return true;
            }
        }
        return false;
    }

    private record Coord(int x, int y) {
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

    private int techFieldUnit(int x, int y, List<String> techs, Map<String, TechnologyModel> techInfo, int deltaX, Player player, Game game) {
        String outline = "pa_tech_unitupgrade_outlines.png";

        drawPAImage(x + deltaX, y, outline);
        // Add faction icons for base units
        for (String u : player.getUnitsOwned()) {
            UnitModel unit = Mapper.getUnit(u);
            if (unit == null) {
                System.out.println("error:" + u);
            } else if (unit.getFaction().isPresent()) {
                // ONLY PAINT FACTION IF IS FRANKEN OR IS NOT A UNIT THAT UPGRADES OR WAS UPGRADED TO (indicating faction tech)
                if (game.isFrankenGame()
                    || ((unit.getUpgradesFromUnitId().isPresent() && unit.getUpgradesFromUnitId().isPresent()) || (unit.getUpgradesToUnitId().isPresent() && unit.getUpgradesToUnitId().isPresent()))) {
                    Coord unitFactionOffset = getUnitTechOffsets(unit.getAsyncId(), true);
                    drawFactionIconImage(graphics, unit.getFaction().get().toLowerCase(), deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32);
                }
            }
        }
        if (techs != null) {
            for (String tech : techs) {
                TechnologyModel techInformation = techInfo.get(tech);
                if (techInformation.getType() != TechnologyModel.TechnologyType.UNITUPGRADE) {
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

                if (techInformation.getFaction().isPresent()) {
                    Coord unitFactionOffset = getUnitTechOffsets(unit.getAsyncId(), true);
                    drawFactionIconImage(graphics, techInformation.getFaction().get(), deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 30, 30);
                }
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 252, 152);
        deltaX += 228;
        return deltaX;
    }

    private void drawFactionIconImage(Graphics graphics, String faction, int x, int y, int width, int height) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null);
    }

    private void drawFactionIconImageOpaque(Graphics graphics, String faction, int x, int y, int width, int height, Float opacity) {
        try {
            BufferedImage resourceBufferedImage = getFactionIconImageScaled(faction, width, height);
            Graphics2D g2 = (Graphics2D) graphics;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.log("Could not display faction icon image: " + faction, e);
        }
    }

    private static void drawPlayerFactionIconImage(Graphics graphics, Player player, int x, int y, int width, int height) {
        drawPlayerFactionIconImageOpaque(graphics, player, x, y, width, height, null);
    }

    private static void drawPlayerFactionIconImageOpaque(Graphics graphics, Player player, int x, int y, int width, int height, Float opacity) {
        if (player == null) return;
        try {
            BufferedImage resourceBufferedImage = getPlayerFactionIconImageScaled(player, width, height);
            Graphics2D g2 = (Graphics2D) graphics;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.log("Could not display player's faction icon image", e);
        }
    }

    private void drawPlanetImage(int x, int y, String resourceName, String planetName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
            if (Optional.ofNullable(resourcePath).isPresent()) {
                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x, y, null);
            } else {
                String name = Optional.ofNullable(Mapper.getPlanet(planetName).getShortName()).orElse(Mapper.getPlanet(planetName).getName());
                String text = StringUtils.left(StringUtils.substringBefore(name, "\n"), 10).toUpperCase();
                drawTextVertically(graphics, text, x + 6, y + 146, Storage.getFont20());
            }
        } catch (Exception e) {
            BotLogger.log("Could not display planet: " + resourceName, e);
        }
    }

    private void drawGeneralImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getGeneralFile(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            // BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            // BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAUnitUpgrade(int x, int y, UnitKey unitKey) {
        try {
            String path = Tile.getUnitPath(unitKey);
            BufferedImage img = ImageHelper.read(path);
            graphics.drawImage(img, x, y, null);
        } catch (Exception e) {
            // Do Nothing
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

    private int scoreTrack(int y) {
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(5));
        graphics.setFont(Storage.getFont50());
        int height = 140;
        int width = 150;
        if (14 < game.getVp()) {
            width = 120;
        }
        for (int i = 0; i <= game.getVp(); i++) {
            graphics.setColor(Color.WHITE);
            graphics.drawString(Integer.toString(i), i * width + 55, y + (height / 2) + 25);
            g2.setColor(Color.RED);
            g2.drawRect(i * width, y, width, height);
        }

        List<Player> players = new ArrayList<>(game.getPlayers().values());
        int tempCounter = 0;
        int tempX = 0;
        int tempWidth = 0;
        int tempHeight;

        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }
        for (Player player : players) {
            if (!player.isRealPlayer())
                continue;
            try {
                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());

                float scale = 0.7f;

                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                tempWidth = controlTokenImage == null ? 20 : controlTokenImage.getWidth();
                tempHeight = controlTokenImage == null ? 20 : controlTokenImage.getHeight();

                int vpCount = player.getTotalVictoryPoints();
                int x = vpCount * width + 5 + tempX;

                drawControlToken(graphics, controlTokenImage, getPlayerByControlMarker(game.getPlayers().values(), controlID), x, y + (tempCounter * tempHeight),
                    convertToGeneric, scale);
            } catch (Exception e) {
                // nothing
                // LoggerHandler.log("Could not display player: " + player.getUserName() + " VP count", e);
            }
            tempCounter++;
            if (tempCounter >= 4) {
                tempCounter = 0;
                tempX = tempWidth;
            }
        }
        y += 180;
        return y;
    }

    private int strategyCards(int y) {
        boolean convertToGenericSC = isFoWPrivate != null && isFoWPrivate;
        int deltaY = y + 80;
        LinkedHashMap<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        Collection<Player> players = game.getPlayers().values();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player : players) {
            scPicked.addAll(player.getSCs());
        }
        HashMap<Integer, Boolean> scPlayed = game.getScPlayed();
        int x = 20;
        int horizontalSpacingIncrement = 70;
        for (Map.Entry<Integer, Integer> scTGs : scTradeGoods.entrySet()) {
            Integer sc = scTGs.getKey();
            if (sc == 0) {
                continue;
            }
            if (sc > 9)
                horizontalSpacingIncrement = 80;
            if (sc > 19)
                horizontalSpacingIncrement = 100;
            if (!convertToGenericSC && !scPicked.contains(sc)) {
                graphics.setColor(getSCColor(sc));
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, deltaY);
                Integer tg = scTGs.getValue();
                if (tg > 0) {
                    graphics.setFont(Storage.getFont26());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString("TG:" + tg, x, deltaY + 30);
                }
            }
            if (convertToGenericSC && scPlayed.getOrDefault(sc, false)) {
                graphics.setColor(Color.GRAY);
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, deltaY);
            }
            x += horizontalSpacingIncrement;
        }

        //NEXTLINE IF LOTS OF SC CARDS
        if (game.getScTradeGoods().size() > 32) {
            x = 20;
            deltaY += 100;
        }

        //ROUND
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        x += 100;
        graphics.drawString("ROUND: " + game.getRound(), x, deltaY);

        //TURN ORDER
        String activePlayerUserID = game.getActivePlayer();
        if (!convertToGenericSC && activePlayerUserID != null && "action".equals(game.getCurrentPhase())) {
            x += 450;

            graphics.setFont(Storage.getFont20());
            graphics.setColor(new Color(50, 230, 80));
            graphics.drawString("ACTIVE", x + 10, deltaY + 35);
            graphics.setFont(Storage.getFont16());
            graphics.setColor(Color.LIGHT_GRAY);
            graphics.drawString("NEXT UP", x + 112, deltaY + 34);

            Player activePlayer = game.getPlayer(activePlayerUserID);
            List<Player> allPlayers = new ArrayList<>(game.getRealPlayers());

            Comparator<Player> comparator = Comparator.comparing(game::getPlayersTurnSCInitiative);
            allPlayers.sort(comparator);

            int rotationDistance = allPlayers.size() - allPlayers.indexOf(activePlayer);
            Collections.rotate(allPlayers, rotationDistance);
            for (Player player : allPlayers) {
                if (player.isPassed() || player.getSCs().size() == 0) continue;
                String faction = player.getFaction();
                if (faction != null) {
                    BufferedImage bufferedImage = getPlayerFactionIconImage(player);
                    if (bufferedImage != null) {
                        graphics.drawImage(bufferedImage, x, deltaY - 70, null);
                        x += 100;
                    }
                }
            }
        }
        return deltaY + 40;
    }

    private void playerInfo(Game game) {
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        Player speaker = game.getPlayer(game.getSpeaker());
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }

        int deltaX = mapWidth - EXTRA_X - (extraRow ? EXTRA_X : 0);
        int deltaY = EXTRA_Y;

        int ringCount = game.getRingCount();
        ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);

        for (Player player : players) {
            if (player.getFaction() == null || !player.isRealPlayer()) {
                continue;
            }

            int deltaSplitX = 0;
            int deltaSplitY = 0;

            String playerStatsAnchor = player.getPlayerStatsAnchorPosition();
            if (playerStatsAnchor != null) {
                String anchorProjectedOnOutsideRing = PositionMapper.getEquivalentPositionAtRing(ringCount, playerStatsAnchor);
                Point anchorProjectedPoint = PositionMapper.getTilePosition(anchorProjectedOnOutsideRing);
                if (anchorProjectedPoint != null) {
                    Point playerStatsAnchorPoint = getTilePosition(anchorProjectedOnOutsideRing, anchorProjectedPoint.x, anchorProjectedPoint.y);
                    int anchorLocationIndex = PositionMapper.getRingSideNumberOfTileID(player.getPlayerStatsAnchorPosition()) - 1;
                    boolean isCorner = anchorProjectedOnOutsideRing.equals(PositionMapper.getTileIDAtCornerPositionOfRing(ringCount, anchorLocationIndex + 1));
                    if (anchorLocationIndex == 0 && isCorner) { // North Corner
                        deltaX = playerStatsAnchorPoint.x + EXTRA_X + 80;
                        deltaY = playerStatsAnchorPoint.y - 80;
                        deltaSplitX = 200;
                    } else if (anchorLocationIndex == 0) { // North East
                        deltaX = playerStatsAnchorPoint.x + EXTRA_X + 300;
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
                    }
                } else
                    continue;
            } else
                continue;

            String userName = player.getUserName();

            boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
            if (convertToGeneric) {
                continue;
            }

            // PAINT USERNAME
            Point point = PositionMapper.getPlayerStats(Constants.STATS_USERNAME);
            graphics.drawString(userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX, point.y + deltaY);

            // PAINT FACTION
            point = PositionMapper.getPlayerStats(Constants.STATS_FACTION);
            graphics.drawString(StringUtils.capitalize(player.getFaction()), point.x + deltaX, point.y + deltaY);

            // PAIN VICTORY POINTS
            int vpCount = player.getTotalVictoryPoints();
            point = PositionMapper.getPlayerStats(Constants.STATS_VP);
            graphics.drawString("VP: " + vpCount, point.x + deltaX, point.y + deltaY);

            // PAINT SO ICONS
            int totalSecrets = player.getSecrets().keySet().size();
            Set<String> soSet = player.getSecretsScored().keySet();
            int soOffset = 0;
            String soHand = "pa_so-icon_hand.png";
            String soScored = "pa_so-icon_scored.png";
            point = PositionMapper.getPlayerStats(Constants.STATS_SO);
            for (int i = 0; i < totalSecrets; i++) {
                drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soHand);
                soOffset += 25;
            }
            ArrayList<String> soToPoList = game.getSoToPoList();
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

            drawCCOfPlayer(graphics, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player, false);
            drawFleetCCOfPlayer(graphics, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player);
            drawCCOfPlayer(graphics, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), player, false);

            // PAINT SPEAKER
            if (player == speaker) {
                String speakerID = Mapper.getTokenID(Constants.SPEAKER);
                String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
                if (speakerFile != null) {
                    BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                    point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                    int negativeDelta = 0;
                    graphics.drawImage(bufferedImage, point.x + deltaX + deltaSplitX + negativeDelta, point.y + deltaY - deltaSplitY, null);
                    graphics.setColor(Color.WHITE);
                }
            }
            String activePlayerID = game.getActivePlayer();
            String phase = game.getCurrentPhase();
            if (player.isPassed()) {
                point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
                graphics.setColor(new Color(238, 58, 80));
                graphics.drawString("PASSED", point.x + deltaX, point.y + deltaY);
                graphics.setColor(Color.WHITE);
            } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
                graphics.setColor(new Color(50, 230, 80));
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
            deltaY += PLAYER_STATS_HEIGHT;
        }
    }

    private static void drawCCOfPlayer(Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon) {
        String ccPath = Mapper.getCCPath(ccID);
        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);

            BufferedImage factionImage = null;
            int centreCustomTokenHorizontally = 0;
            if (!hideFactionIcon) {
                factionImage = getPlayerFactionIconImageScaled(player, 45, 45);
                centreCustomTokenHorizontally = ccImage != null ? ccImage.getWidth() / 2 - factionImage.getWidth() / 2 : 0;
            }

            int delta = 20;
            for (int i = 0; i < ccCount; i++) {
                graphics.drawImage(ccImage, x + (delta * i), y, null);
                if (!hideFactionIcon) graphics.drawImage(factionImage, x + (delta * i) + centreCustomTokenHorizontally, y + DELTA_Y, null);
            }
        } catch (Exception e) {
            BotLogger.log("Ignored error during map generation", e);
        }
    }

    private static void drawFleetCCOfPlayer(Graphics graphics, String ccID, int x, int y, Player player) {
        String ccPath = Mapper.getCCPath(ccID);
        int ccCount = player.getFleetCC();
        boolean hasArmada = player.hasAbility("armada");
        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            int delta = 20;
            int lastCCPosition = -1;
            if (hasArmada) {
                String armadaLowerCCID = Mapper.getCCID(player.getColor());
                String armadaLowerCCPath = Mapper.getCCPath(armadaLowerCCID);
                BufferedImage armadaLowerCCImage = ImageHelper.read(armadaLowerCCPath);
                String armadaCCID = "fleet_armada.png";
                String armadaCCPath = Mapper.getCCPath(armadaCCID);
                BufferedImage armadaCCImage = ImageHelper.read(armadaCCPath);

                //DRAW TWO ARMADA TOKENS
                for (int i = 0; i < 2; i++) {
                    graphics.drawImage(armadaLowerCCImage, x + (delta * i), y, null);
                    graphics.drawImage(armadaCCImage, x + (delta * i), y, null);
                }
                x += 30;

                //DRAW FLEET TOKENS
                for (int i = 2; i < ccCount + 2; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                    lastCCPosition = i;
                }
            } else {
                for (int i = 0; i < ccCount; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                    lastCCPosition = i;
                }
            }
            List<String> mahactCC = player.getMahactCC();
            if (!mahactCC.isEmpty() && player.hasAbility("edict")) {
                x += 10;
                for (String ccColor : mahactCC) {
                    lastCCPosition++;
                    String fleetCCID = Mapper.getCCPath(Mapper.getFleetCCID(ccColor));
                    BufferedImage ccImageExtra = ImageHelper.readScaled(fleetCCID, 1.0f);
                    graphics.drawImage(ccImageExtra, x + (delta * lastCCPosition), y, null);

                }
            }
        } catch (Exception e) {
            BotLogger.log("Ignored exception during map generation", e);
        }
    }

    private int objectives(int y) {
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));
        Map<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>(game.getScoredPublicObjectives());
        Map<String, Integer> revealedPublicObjectives = new LinkedHashMap<>(game.getRevealedPublicObjectives());
        LinkedHashMap<String, Player> players = game.getPlayers();
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        LinkedHashMap<String, Integer> customPublicVP = game.getCustomPublicVP();
        LinkedHashMap<String, String> customPublics = customPublicVP.keySet().stream().collect(Collectors.toMap(key -> key, name -> {
            String nameOfPO = Mapper.getSecretObjectivesJustNames().get(name);
            return nameOfPO != null ? nameOfPO : name;
        }, (key1, key2) -> key1, LinkedHashMap::new));
        Set<String> po1 = publicObjectivesState1.keySet();
        Set<String> po2 = publicObjectivesState2.keySet();
        Set<String> customVP = customPublicVP.keySet();

        graphics.setFont(Storage.getFont26());
        graphics.setColor(new Color(230, 126, 34));
        int x = 5;
        int y1 = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState1, po1, 1, null, false);

        x = 801;
        graphics.setColor(new Color(93, 173, 226));
        int y2 = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState2, po2, 2, null, false);

        x = 1598;
        graphics.setColor(Color.WHITE);
        int y3 = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, customPublics, customVP, null, customPublicVP, false);

        return Math.max(y3, Math.max(y1, y2)) + 15;
    }

    private int laws(int y) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));

        LinkedHashMap<String, Integer> laws = game.getLaws();
        LinkedHashMap<String, String> lawsInfo = game.getLawsInfo();
        boolean secondColumn = false;
        for (Map.Entry<String, Integer> lawEntry : laws.entrySet()) {
            String lawID = lawEntry.getKey();
            String lawNumberID = "(" + lawEntry.getValue() + ") ";
            String optionalText = lawsInfo.get(lawID);
            graphics.setFont(Storage.getFont35());
            graphics.setColor(new Color(228, 255, 0));

            graphics.drawRect(x, y, 1178, 110);
            String agendaTitle = Mapper.getAgendaTitle(lawID);
            if (agendaTitle == null) {
                agendaTitle = Mapper.getAgendaJustNames().get(lawID);
            }
            if (optionalText != null && !optionalText.isEmpty() && game.getPlayerFromColorOrFaction(optionalText) == null) {
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
                graphics.drawString(agendaText.substring(0, agendaTextLength - index), x + 95, y + 70);
                graphics.drawString(agendaText.substring(agendaTextLength - index), x + 95, y + 96);
            } else {
                graphics.drawString(agendaText, x + 95, y + 70);
            }
            try {
                String agendaType = Mapper.getAgendaType(lawID);

                if ("1".equals(agendaType) || optionalText == null || optionalText.isEmpty()) {
                    paintAgendaIcon(y, x);
                } else if ("0".equals(agendaType)) {
                    Player electedPlayer = null;
                    boolean convertToGeneric = false;
                    for (Player player : game.getPlayers().values()) {
                        if (optionalText.equals(player.getFaction()) || optionalText.equals(player.getColor())) {
                            if (isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)) {
                                convertToGeneric = true;
                            }
                            electedPlayer = player;
                            break;
                        }
                    }
                    if (convertToGeneric || electedPlayer == null) {
                        paintAgendaIcon(y, x);
                    } else {
                        drawPlayerFactionIconImage(graphics, electedPlayer, x + 2, y + 2, 95, 95);
                    }
                }

            } catch (Exception e) {
                BotLogger.log("Could not paint agenda icon", e);
            }

            if (!secondColumn) {
                secondColumn = true;
                x += 1178 + 8;
            } else {
                secondColumn = false;
                y += 112;
                x = 5;
            }
        }
        return secondColumn ? y + 115 : y + 3;
    }

    private int events(int y) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));

        LinkedHashMap<String, Integer> events = game.getEventsInEffect();
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
                x = 5;
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
        g2.setStroke(new BasicStroke(3));

        LinkedHashMap<String, Player> players = game.getPlayers();
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        LinkedHashMap<String, Integer> customPublicVP = game.getCustomPublicVP();
        Set<String> secret = secretObjectives.keySet();
        graphics.setFont(Storage.getFont26());
        graphics.setColor(new Color(230, 126, 34));

        Map<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> secrets = new LinkedHashMap<>(player.getSecrets());

        for (String id : secrets.keySet()) {
            scoredPublicObjectives.put(id, List.of(player.getUserID()));
        }
        if (player.isSearchWarrant()) {
            graphics.setColor(Color.LIGHT_GRAY);
            Map<String, Integer> revealedSecrets = new LinkedHashMap<>(secrets);
            y = displayObjectives(y, x, new LinkedHashMap<>(), revealedSecrets, players, secretObjectives, secret, 0, customPublicVP, true);
        }
        LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            secretsScored.remove(id);
        }
        Map<String, Integer> revealedPublicObjectives = new LinkedHashMap<>(secretsScored);
        for (String id : secretsScored.keySet()) {
            scoredPublicObjectives.put(id, List.of(player.getUserID()));
        }
        graphics.setColor(Color.RED);
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, secretObjectives, secret, 1, customPublicVP, true);
        if (player.isSearchWarrant()) {
            return secretsScored.keySet().size() + player.getSecrets().keySet().size();
        }
        return secretsScored.keySet().size();
    }

    private int displayObjectives(
        int y,
        int x,
        Map<String, List<String>> scoredPublicObjectives,
        Map<String, Integer> revealedPublicObjectives,
        Map<String, Player> players,
        HashMap<String, String> publicObjectivesState,
        Set<String> po,
        Integer objectiveWorth,
        Map<String, Integer> customPublicVP,
        boolean fixedColumn) {
        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            if (fixedColumn) {
                x = 50;
            }

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
            if (customPublicVP != null) {
                objectiveWorth = customPublicVP.get(key);
                if (objectiveWorth == null) {
                    objectiveWorth = 1;
                }
            }
            if (fixedColumn) {
                graphics.drawString("(" + index + ") " + name, x, y + 23);
            } else {
                graphics.drawString("(" + index + ") " + name + " - " + objectiveWorth + " VP", x, y + 23);
            }
            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            boolean multiScoring = Constants.CUSTODIAN.equals(key) || (isFoWPrivate != null && isFoWPrivate);
            if (scoredPlayerID != null) {
                if (fixedColumn) {
                    drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, false, true);
                } else {
                    drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, multiScoring);
                }
            }
            if (fixedColumn) {
                graphics.drawRect(x - 4, y - 5, 600, 38);
            } else {
                graphics.drawRect(x - 4, y - 5, 785, 38);
            }
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
        boolean multiScoring) {
        drawScoreControlMarkers(x, y, players, scoredPlayerID, multiScoring, false);
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

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
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
                            drawControlToken(graphics, controlTokenImage, player, x + tempX, y, convertToGeneric, scale);
                            tempX += scoreTokenWidth;
                        }
                    } else {
                        drawControlToken(graphics, controlTokenImage, player, x + tempX, y, convertToGeneric, scale);
                    }
                }
                if (!multiScoring && !fixedColumn) {
                    tempX += scoreTokenWidth;
                }
            }
        } catch (Exception e) {
            BotLogger.log("Could not parse custodian CV token file", e);
        }
    }

    private static Player getPlayerByControlMarker(Iterable<Player> players, String controlID) {
        Player player = null;
        for (Player player_ : players) {
            if (player_.getColor() != null && player_.getFaction() != null) {
                String playerControlMarker = Mapper.getControlID(player_.getColor());
                String playerCC = Mapper.getCCID(player_.getColor());
                String playerSweep = Mapper.getSweepID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC) || controlID.equals(playerSweep)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    private Color getSCColor(int sc, Game game) {
        HashMap<Integer, Boolean> scPlayed = game.getScPlayed();
        if (scPlayed.get(sc) != null) {
            if (scPlayed.get(sc)) {
                return Color.GRAY;
            }
        }
        return getSCColor(sc);
    }

    private Color getSCColor(Integer sc) {
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

    enum TileStep {
        Setup, Tile, Extras, Units, Distance
    }

    private void addTile(Tile tile, TileStep step) {
        addTile(tile, step, false);
    }

    private void addTile(Tile tile, TileStep step, boolean setupCheck) {
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
            if (!setupCheck) {
                if (!"tl".equalsIgnoreCase(position) &&
                    !"tr".equalsIgnoreCase(position) &&
                    !"bl".equalsIgnoreCase(position) &&
                    !"br".equalsIgnoreCase(position)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            } else if (x < minX || x > maxX || y < minY || y > maxY) {
                return;
            }

            positionPoint = getTilePosition(position, x, y);
            int tileX = positionPoint.x + EXTRA_X - TILE_PADDING;
            int tileY = positionPoint.y + EXTRA_Y - TILE_PADDING;

            BufferedImage tileImage = partialTileImage(tile, step, fowPlayer, isFoWPrivate);
            graphics.drawImage(tileImage, tileX, tileY, null);
        } catch (Exception exception) {
            BotLogger.log("Tile Error, when building map `" + game.getName() + "`, tile: " + tile.getTileID(), exception);
        }
    }

    public static BufferedImage partialTileImage(Tile tile, Game game, TileStep step, Player frogPlayer, Boolean isFrogPrivate) {
        return new MapGenerator(game).partialTileImage(tile, step, frogPlayer, isFrogPrivate);
    }

    private BufferedImage partialTileImage(Tile tile, TileStep step, Player frogPlayer, Boolean isFrogPrivate) {
        BufferedImage tileOutput = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics tileGraphics = tileOutput.createGraphics();

        switch (step) {
            case Setup -> {
            } // do nothing
            case Tile -> {
                BufferedImage image = ImageHelper.read(tile.getTilePath());
                tileGraphics.drawImage(image, TILE_PADDING, TILE_PADDING, null);

                // ADD ANOMALY BORDER IF HAS ANOMALY PRODUCING TOKENS OR UNITS
                if (tile.isAnomaly(game)) {
                    BufferedImage anomalyImage = ImageHelper.read(ResourceHelper.getInstance().getTileFile("tile_anomaly.png"));
                    tileGraphics.drawImage(anomalyImage, TILE_PADDING, TILE_PADDING, null);
                }

                int textOffset = 0;
                if ("large".equals(game.getTextSize())) {
                    tileGraphics.setFont(Storage.getFont50());
                    textOffset = 90;
                } else if ("medium".equals(game.getTextSize())) {
                    tileGraphics.setFont(Storage.getFont35());
                    textOffset = 50;
                } else if ("tiny".equals(game.getTextSize())) {
                    tileGraphics.setFont(Storage.getFont12());
                } else { //"small"
                    tileGraphics.setFont(Storage.getFont20());
                    textOffset = 20;
                }
                tileGraphics.setColor(Color.WHITE);
                if (isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer)) {
                    BufferedImage frogOfWar = ImageHelper.read(tile.getFowTilePath(frogPlayer));
                    tileGraphics.drawImage(frogOfWar, TILE_PADDING, TILE_PADDING, null);
                    tileGraphics.drawString(tile.getFogLabel(frogPlayer), TILE_PADDING + labelPositionPoint.x, TILE_PADDING + labelPositionPoint.y);
                }
                tileGraphics.drawString(tile.getPosition(), TILE_PADDING + tilePositionPoint.x - textOffset, TILE_PADDING + tilePositionPoint.y);
            }
            case Extras -> {
                if (isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer))
                    return tileOutput;

                List<String> adj = game.getAdjacentTileOverrides(tile.getPosition());
                int direction = 0;
                for (String secondaryTile : adj) {
                    if (secondaryTile != null) {
                        addBorderDecoration(direction, secondaryTile, tileGraphics, BorderAnomalyModel.BorderAnomalyType.ARROW);
                    }
                    direction++;
                }
                game.getBorderAnomalies().forEach(borderAnomalyHolder -> {
                    if (borderAnomalyHolder.getTile().equals(tile.getPosition()))
                        addBorderDecoration(borderAnomalyHolder.getDirection(), null, tileGraphics, borderAnomalyHolder.getType());
                });
            }
            case Units -> {
                if (isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer))
                    return tileOutput;

                List<Rectangle> rectangles = new ArrayList<>();
                Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
                UnitHolder spaceUnitHolder = unitHolders.stream().filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);

                if (spaceUnitHolder != null) {
                    addSleeperToken(tile, tileGraphics, spaceUnitHolder, MapGenerator::isValidCustodianToken);
                    addToken(tile, tileGraphics, spaceUnitHolder);
                    unitHolders.remove(spaceUnitHolder);
                    unitHolders.add(spaceUnitHolder);
                }
                for (UnitHolder unitHolder : unitHolders) {
                    addSleeperToken(tile, tileGraphics, unitHolder, MapGenerator::isValidToken);
                    addControl(tile, tileGraphics, unitHolder, rectangles, frogPlayer, isFrogPrivate);
                }
                if (spaceUnitHolder != null) {
                    addCC(tile, tileGraphics, spaceUnitHolder, frogPlayer, isFrogPrivate);
                }
                int degree = 180;
                int degreeChange = 5;
                for (UnitHolder unitHolder : unitHolders) {
                    int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                    if (unitHolder != spaceUnitHolder) {
                        addPlanetToken(tile, tileGraphics, unitHolder, rectangles);
                    }
                    addUnits(tile, tileGraphics, rectangles, degree, degreeChange, unitHolder, radius, frogPlayer);
                }
            }
            case Distance -> {
                if (game.isFoWMode()) break;
                Integer distance = game.getTileDistances().get(tile.getPosition());
                if (distance == null) break;

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) break;

                BufferedImage distanceColor = ImageHelper.read(ResourceHelper.getInstance().getTileFile(getColorFilterForDistance(distance)));
                tileGraphics.drawImage(distanceColor, TILE_PADDING, TILE_PADDING, null);
                tileGraphics.setColor(Color.WHITE);
                drawCenteredString(tileGraphics, distance.toString(), new Rectangle(TILE_PADDING, TILE_PADDING, tileImage.getWidth(), tileImage.getHeight()), Storage.getFont100());
            }
        }
        return tileOutput;
    }

    public static String getColorFilterForDistance(int distance) {
        return "Distance" + distance + ".png";
    }

    private Point getTilePosition(String position, int x, int y) {
        int ringCount = game.getRingCount();
        ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);
        if (ringCount < RING_MAX_COUNT) {
            int lower = RING_MAX_COUNT - ringCount;

            if ("tl".equalsIgnoreCase(position)) {
                y -= 150;
            } else if ("bl".equalsIgnoreCase(position)) {
                y -= lower * 600 - 150;
            } else if ("tr".equalsIgnoreCase(position)) {
                x -= lower * 520;
                y -= 150;
            } else if ("br".equalsIgnoreCase(position)) {
                x -= lower * 520;
                y -= lower * 600 - 150;
            } else {
                x -= lower * 260;
                y -= lower * 300;
            }
            return new Point(x, y);
        }
        return new Point(x, y);
    }

    private static void addBorderDecoration(int direction, String secondaryTile, Graphics tileGraphics, BorderAnomalyModel.BorderAnomalyType decorationType) {
        Graphics2D tileGraphics2d = (Graphics2D) tileGraphics;

        BufferedImage borderDecorationImage;
        try {
            borderDecorationImage = ImageHelper.read(decorationType.getImageFilePath());
        } catch (Exception e) {
            BotLogger.log("Could not find border decoration image! Decoration was " + decorationType.toString());
            return;
        }
        if (borderDecorationImage == null) return;

        int imageCenterX = borderDecorationImage.getWidth() / 2;
        int imageCenterY = borderDecorationImage.getHeight() / 2;

        AffineTransform originalTileTransform = tileGraphics2d.getTransform();
        // Translate the graphics so that a rectangle drawn at 0,0 with same size as the tile (345x299) is centered
        tileGraphics2d.translate(100, 100);
        int centerX = 173;
        int centerY = 150;

        if (decorationType == BorderAnomalyModel.BorderAnomalyType.ARROW) {
            int textOffsetX = 11;
            int textOffsetY = 40;
            Graphics2D arrow = (Graphics2D) borderDecorationImage.getGraphics();
            AffineTransform arrowTextTransform = arrow.getFont().getTransform();

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

    private void addCC(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Player frogPlayer, Boolean isFrogPrivate) {
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

                Player player = getPlayerByControlMarker(game.getPlayers().values(), ccID);
                boolean convertToGeneric = isFrogPrivate != null && isFrogPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);

                boolean generateImage = true;
                if (ccID.startsWith("sweep")) {
                    if (player != frogPlayer) {
                        generateImage = false;
                    }
                }

                if (generateImage) {
                    int imgX = TILE_PADDING + 10 + deltaX;
                    int imgY = TILE_PADDING + centerPosition.y - 40 + deltaY;
                    drawCCOfPlayer(tileGraphics, ccID, imgX, imgY, 1, player, convertToGeneric);
                }
            } catch (Exception ignored) {
            }

            if (image != null) {
                deltaX += image.getWidth() / 5;
                deltaY += image.getHeight() / 4;
            }
        }
    }

    private void addControl(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<Rectangle> rectangles, Player frogPlayer, Boolean isFrogPrivate) {
        List<String> controlList = new ArrayList<>(unitHolder.getControlList());
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String controlID : controlList) {
                if (controlID.contains(Constants.SLEEPER)) {
                    continue;
                }

                Player player = getPlayerByControlMarker(game.getPlayers().values(), controlID);

                boolean convertToGeneric = isFrogPrivate != null && isFrogPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);

                boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
                Point position = unitTokenPosition.getPosition(controlID);
                if (isMirage) {
                    if (position == null) {
                        position = new Point(Constants.MIRAGE_POSITION.x, Constants.MIRAGE_POSITION.y);
                    } else {
                        position.x += Constants.MIRAGE_POSITION.x;
                        position.y += Constants.MIRAGE_POSITION.y;
                    }
                }

                float scale = 1.0f;

                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                if (controlTokenImage == null) continue;

                if (position != null) {
                    int imgX = TILE_PADDING + position.x;
                    int imgY = TILE_PADDING + position.y;
                    drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                } else {
                    int imgX = TILE_PADDING + centerPosition.x + xDelta;
                    int imgY = TILE_PADDING + centerPosition.y;
                    drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    xDelta += 10;
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, controlList);
        }
    }

    private static void addSleeperToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Function<String, Boolean> isValid) {
        BufferedImage tokenImage;
        Point centerPosition = unitHolder.getHolderCenterPosition();
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
                    scale = 0.6f;
                } else if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                    scale = 1.32f;
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    scale = 0.5f; // didnt previous get changed for custodians
                }
                tokenImage = ImageHelper.readScaled(tokenPath, scale);
                if (tokenImage == null) continue;
                Point position = new Point(centerPosition.x - (tokenImage.getWidth() / 2), centerPosition.y - (tokenImage.getHeight() / 2));
                if (tokenID.contains(Constants.CUSTODIAN_TOKEN)) {
                    position = new Point(125, 115); // 70, 45
                } else if (tokenID.contains(Constants.SLEEPER) && containsDMZ) {
                    position = new Point(position.x + 10, position.y + 10);
                } else if (tokenID.contains(Constants.WORLD_DESTROYED)) {
                    position = new Point(position.x + 4, position.y + 13);
                }
                tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y - 10, null);
            }
        }
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

    private static void addPlanetToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<Rectangle> rectangles) {
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
                    BotLogger.log("Could not parse token file for: " + tokenID + " on tile: " + tile.getAutoCompleteName());
                    continue;
                }
                BufferedImage tokenImage = ImageHelper.read(tokenPath);
                if (tokenImage == null) continue;

                if (tokenPath.contains(Constants.DMZ_LARGE) ||
                    tokenPath.contains(Constants.WORLD_DESTROYED) ||
                    tokenPath.contains(Constants.CONSULATE_TOKEN) ||
                    tokenPath.contains(Constants.GLEDGE_CORE) || tokenPath.contains("freepeople")) {
                    if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2), TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
                    } else {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2), TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
                    }
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + 70, TILE_PADDING + 45, null);
                } else {
                    Point position = unitTokenPosition.getPosition(tokenID);
                    if (position != null) {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y, null);
                        rectangles.add(new Rectangle(TILE_PADDING + position.x, TILE_PADDING + position.y, tokenImage.getWidth(), tokenImage.getHeight()));
                    } else {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x + xDelta, TILE_PADDING + centerPosition.y, null);
                        rectangles.add(new Rectangle(TILE_PADDING + centerPosition.x + xDelta, TILE_PADDING + centerPosition.y, tokenImage.getWidth(), tokenImage.getHeight()));
                        xDelta += 10;
                    }
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, tokenList);
        }
    }

    private static void oldFormatPlanetTokenAdd(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<String> tokenList) {
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
            if (image == null) continue;
            tileGraphics.drawImage(image, TILE_PADDING + x - (image.getWidth() / 2), TILE_PADDING + y + offSet + deltaY - (image.getHeight() / 2), null);
            y += image.getHeight();
        }
    }

    private static void addToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder) {
        HashSet<String> tokenList = unitHolder.getTokenList();
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = 0;
        int y = 0;
        int deltaX = 80;
        int deltaY = 0;
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
            if (tokenImage == null) return;

            if (tokenPath.contains(Constants.MIRAGE)) {
                tileGraphics.drawImage(tokenImage, TILE_PADDING + Constants.MIRAGE_POSITION.x, TILE_PADDING + Constants.MIRAGE_POSITION.y, null);
            } else if (tokenPath.contains(Constants.SLEEPER)) {
                tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2), TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
            } else {
                if (spaceTokenPositions.size() > index) {
                    Point point = spaceTokenPositions.get(index);
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + x + point.x, TILE_PADDING + y + point.y, null);
                    index++;
                } else {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + x + deltaX, TILE_PADDING + y + deltaY, null);
                    deltaX += 30;
                    deltaY += 30;
                }
            }
        }
    }

    private void addUnits(Tile tile, Graphics tileGraphics, List<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius, Player frogPlayer) {
        BufferedImage unitImage;
        Map<UnitKey, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        Map<UnitKey, Integer> units = new LinkedHashMap<>();
        HashMap<String, Point> unitOffset = new HashMap<>();
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);

        boolean isCabalJail = "s11".equals(tile.getTileID());
        boolean isNekroJail = "s12".equals(tile.getTileID());
        boolean isYssarilJail = "s13".equals(tile.getTileID());

        boolean isJail = isCabalJail || isNekroJail || isYssarilJail;
        boolean showJail = frogPlayer == null
            || (isCabalJail && FoWHelper.canSeeStatsOfFaction(game, "cabal", frogPlayer))
            || (isNekroJail && FoWHelper.canSeeStatsOfFaction(game, "nekro", frogPlayer))
            || (isYssarilJail && FoWHelper.canSeeStatsOfFaction(game, "yssaril", frogPlayer));

        Point unitOffsetValue = game.isAllianceMode() ? PositionMapper.getAllianceUnitOffset() : PositionMapper.getUnitOffset();
        int spaceX = unitOffsetValue != null ? unitOffsetValue.x : 10;
        int spaceY = unitOffsetValue != null ? unitOffsetValue.y : -7;
        for (Map.Entry<UnitKey, Integer> entry : tempUnits.entrySet()) {
            UnitKey id = entry.getKey();
            if (id != null && id.getUnitType() == UnitType.Mech) {
                units.put(id, entry.getValue());
            }
        }
        for (UnitKey key : units.keySet()) {
            tempUnits.remove(key);
        }
        units.putAll(tempUnits);
        HashMap<UnitKey, Integer> unitDamage = unitHolder.getUnitDamage();
        // float scaleOfUnit = 1.0f;
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            unitTokenPosition = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
        }
        BufferedImage dmgImage = ImageHelper.readScaled(Helper.getDamagePath(), 0.8f);

        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);

        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            Integer unitCount = unitEntry.getValue();

            if (isJail && frogPlayer != null) {
                String colorID = Mapper.getColorID(frogPlayer.getColor());
                if (!showJail && !unitKey.getColorID().equals(colorID)) {
                    continue;
                }
            }

            Integer unitDamageCount = unitDamage.get(unitKey);

            Color groupUnitColor = Color.WHITE;
            Integer bulkUnitCount = null;
            Set<String> lightColors = Set.of("ylw", "org", "pnk", "tan", "crm", "sns", "tqs", "gld", "lme", "lvn", "rse", "spr", "tea", "lgy", "eth");
            if (lightColors.contains(unitKey.getColorID())) {
                groupUnitColor = Color.BLACK;
            }

            try {
                String unitPath = Tile.getUnitPath(unitKey);
                if (unitPath != null) {
                    if (unitKey.getUnitType() == UnitType.Fighter) {
                        unitPath = unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
                        bulkUnitCount = unitCount;
                    } else if (unitKey.getUnitType() == UnitType.Infantry) {
                        unitPath = unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
                        bulkUnitCount = unitCount;
                    }
                }
                if (game.getPlayerByColorID(unitKey.getColorID()).orElse(null) != null) {
                    Player p = game.getPlayerByColorID(unitKey.getColorID()).get();
                    if ((p.ownsUnit("cabal_spacedock") || p.ownsUnit("cabal_spacedock2")) && unitKey.getUnitType() == UnitType.Spacedock) {
                        unitPath = unitPath.replace("sd", "csd");
                    }
                    if (unitKey.getUnitType() == UnitType.TyrantsLament) {
                        unitPath = unitPath.replace("tyrantslament", "fs");
                        String name = "tyrant.png";
                        unitPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);     
                        //spoopy = ImageHelper.read(spoopyPath);
                    }
                    if (unitKey.getUnitType() == UnitType.Lady) {
                        unitPath = unitPath.replace("lady", "fs");
                    }
                }

                unitImage = ImageHelper.read(unitPath);
            } catch (Exception e) {
                BotLogger.log("Could not parse unit file for: " + unitKey, e);
                continue;
            }
            if (unitImage == null) continue;

            BufferedImage decal = null;
            String color = AliasHandler.resolveColor(unitKey.getColorID());
            Player player = game.getPlayerFromColorOrFaction(color);
            try {
                if (player != null && !"null".equals(player.getDecalSet()) && Mapper.isValidDecalSet(player.getDecalSet())) {
                    String decalFileName = String.format("%s_%s%s", player.getDecalSet(), unitKey.asyncID(), getBlackWhiteFileSuffix(Mapper.getColorID(player.getColor())));
                    String decalPath = ResourceHelper.getInstance().getDecalFile(decalFileName);
                    decal = ImageHelper.read(decalPath);
                }
            } catch (Exception e) {
                String str = player.getDecalSet();
                BotLogger.log("Could not parse decal file for reinforcements: " + str, e);
            }

            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;
            }

            BufferedImage spoopy = null;
            if ((unitKey.getUnitType() == UnitType.Warsun) && (ThreadLocalRandom.current().nextInt(1000) == 0)) {

                String spoopypath = ResourceHelper.getInstance().getSpoopyFile();
                spoopy = ImageHelper.read(spoopypath);
                //  BotLogger.log("SPOOPY TIME: " + spoopypath);
            }
            // if(unitKey.getUnitType() == UnitType.TyrantsLament){
            //     String name = "tyrant.png";
            //     String spoopyPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);     
            //     spoopy = ImageHelper.read(spoopyPath);
            //     BotLogger.log("SPOOPY TIME: " + spoopyPath);
            // }
            if (unitKey.getUnitType() == UnitType.Lady) {
                String name = "units_ds_ghemina_lady_wht.png";
                String spoopyPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);
                spoopy = ImageHelper.read(spoopyPath);
            }
            if (unitKey.getUnitType() == UnitType.Flagship && player.ownsUnit("ghemina_flagship_lord")) {
                String name = "units_ds_ghemina_lord_wht.png";
                String spoopyPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);
                spoopy = ImageHelper.read(spoopyPath);
            }
            Point centerPosition = unitHolder.getHolderCenterPosition();
            // DRAW UNITS
            for (int i = 0; i < unitCount; i++) {
                String id = unitKey.asyncID();
                boolean fighterOrInfantry = Set.of(UnitType.Infantry, UnitType.Fighter).contains(unitKey.getUnitType());
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
                while (searchPosition && position == null) {
                    x = (int) (radius * Math.sin(degree));
                    y = (int) (radius * Math.cos(degree));
                    int possibleX = centerPosition.x + x - (unitImage.getWidth() / 2);
                    int possibleY = centerPosition.y + y - (unitImage.getHeight() / 2);
                    BufferedImage finalImage = unitImage;
                    if (rectangles.stream().noneMatch(rectangle -> rectangle.intersects(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()))) {
                        searchPosition = false;
                    } else if (degree > 360) {
                        searchPosition = false;
                        degree += 3;// To change degree if we did not find place, might be better placement then
                    }
                    degree += degreeChange;
                    if (!searchPosition) {
                        rectangles.add(new Rectangle(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()));
                    }
                }
                int xOriginal = centerPosition.x + x;
                int yOriginal = centerPosition.y + y;
                int imageX = position != null ? position.x : xOriginal - (unitImage.getWidth() / 2);
                int imageY = position != null ? position.y : yOriginal - (unitImage.getHeight() / 2);
                if (isMirage) {
                    imageX += Constants.MIRAGE_POSITION.x;
                    imageY += Constants.MIRAGE_POSITION.y;
                }

                tileGraphics.drawImage(unitImage, TILE_PADDING + imageX, TILE_PADDING + imageY, null);
                tileGraphics.drawImage(decal, TILE_PADDING + imageX, TILE_PADDING + imageY, null);
                if (spoopy != null) {
                    tileGraphics.drawImage(spoopy, TILE_PADDING + imageX, TILE_PADDING + imageY, null);
                }

                if (bulkUnitCount != null) {
                    tileGraphics.setFont(Storage.getFont24());
                    tileGraphics.setColor(groupUnitColor);
                    int scaledNumberPositionX = numberPositionPoint.x;
                    int scaledNumberPositionY = numberPositionPoint.y;
                    tileGraphics.drawString(Integer.toString(bulkUnitCount), TILE_PADDING + imageX + scaledNumberPositionX, TILE_PADDING + imageY + scaledNumberPositionY);
                }

                // UNIT TAGS
                if (i == 0 && !(UnitType.Infantry.equals(unitKey.getUnitType())) && game.isShowUnitTags()) { //DRAW TAG
                    UnitModel unitModel = game.getUnitFromUnitKey(unitKey);
                    if (player != null && unitModel != null && unitModel.getIsShip()) {
                        //TODO: Only paint the tag of the most expensive ship per player, or if no ships, the "bottom most" unit on a planet
                        String factionTag = player.getFactionModel().getShortTag();
                        BufferedImage plaquette = ImageHelper.read(ResourceHelper.getInstance().getUnitFile("unittags_plaquette.png"));
                        Point plaquetteOffset = getUnitTagLocation(id);

                        tileGraphics.drawImage(plaquette, TILE_PADDING + imageX + plaquetteOffset.x, TILE_PADDING + imageY + plaquetteOffset.y, null);
                        drawPlayerFactionIconImage(tileGraphics, player, TILE_PADDING + imageX + plaquetteOffset.x, TILE_PADDING + imageY + plaquetteOffset.y, 32, 32);

                        tileGraphics.setColor(Color.WHITE);
                        drawCenteredString(tileGraphics, factionTag, new Rectangle(TILE_PADDING + imageX + plaquetteOffset.x + 25, TILE_PADDING + imageY + plaquetteOffset.y + 17, 40, 13),
                            Storage.getFont13());
                    }
                }

                if (unitDamageCount != null && unitDamageCount > 0 && dmgImage != null) {
                    if (isSpace && position != null) {
                        position.x = position.x - 7;
                    }
                    int imageDmgX = position != null ? position.x + (unitImage.getWidth() / 2) - (dmgImage.getWidth() / 2) : xOriginal - (dmgImage.getWidth() / 2);
                    int imageDmgY = position != null ? position.y + (unitImage.getHeight() / 2) - (dmgImage.getHeight() / 2) : yOriginal - (dmgImage.getHeight() / 2);
                    if (isMirage) {
                        imageDmgX = imageX;
                        imageDmgY = imageY;
                    } else if (unitKey.getUnitType() == UnitType.Mech) {
                        imageDmgX = position != null ? position.x : xOriginal - (dmgImage.getWidth());
                        imageDmgY = position != null ? position.y : yOriginal - (dmgImage.getHeight());

                    }
                    tileGraphics.drawImage(dmgImage, TILE_PADDING + imageDmgX, TILE_PADDING + imageDmgY, null);
                    unitDamageCount--;
                }
            }
        }
    }

    /**
     * Draw a String centered in the middle of a Rectangle.
     *
     * @param g The Graphics instance.
     * @param text The String to draw.
     * @param rect The Rectangle to center the text in.
     */
    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        // Get the FontMetrics
        FontMetrics metrics = g.getFontMetrics(font);
        // Determine the X coordinate for the text
        int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        // Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        // Set the font
        g.setFont(font);
        // Draw the String
        g.drawString(text, x, y);
    }

    private static String getBlackWhiteFileSuffix(String colorID) {
        Set<String> lightColors = Set.of("ylw", "org", "pnk", "tan", "crm", "sns", "tqs", "gld", "lme", "lvn", "rse", "spr", "tea", "lgy", "eth");
        if (lightColors.contains(colorID)) {
            return "_blk.png";
        }
        return "_wht.png";
    }

    private static Point getUnitTagLocation(String unitID) {
        return switch (unitID) {
            case "ws" -> new Point(-10, 45); //War Sun
            case "fs", "lord", "lady", "tyrantslament" -> new Point(10, 55); //Flagship
            case "dn" -> new Point(10, 50); //Dreadnought
            case "ca" -> new Point(0, 40); //Cruiser
            case "cv" -> new Point(0, 40); //Carrier
            case "gf", "ff" -> new Point(-15, 12); //Infantry/Fighter
            case "dd" -> new Point(-10, 30); //Destroyer
            case "mf" -> new Point(-10, 20); //Mech
            case "pd" -> new Point(-10, 20); //PDS
            case "sd", "csd", "plenaryorbital" -> new Point(-10, 20); //Space Dock
            default -> new Point(0, 0);
        };
    }

    /**
     * @param graphics
     * @param text text to draw vertically
     * @param x left
     * @param y bottom
     * @param font
     */
    private static void drawTextVertically(Graphics graphics, String text, int x, int y, Font font) {
        Graphics2D graphics2D = (Graphics2D) graphics;
        AffineTransform originalTransform = graphics2D.getTransform();
        graphics2D.rotate(Math.toRadians(-90));
        graphics2D.setFont(font);

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

    private static void drawTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, Font font, int offset) {
        String firstRow = StringUtils.left(StringUtils.substringBefore(text, "\n"), 12).toUpperCase();
        String secondRow = StringUtils.left(StringUtils.substringAfter(text, "\n"), 12).toUpperCase();
        drawTextVertically(graphics, firstRow, x, y, font);
        if (StringUtils.isNotBlank(secondRow)) {
            drawTextVertically(graphics, secondRow, x + offset, y, font);
        }
    }

}
