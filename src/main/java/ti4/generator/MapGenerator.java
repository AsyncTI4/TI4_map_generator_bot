package ti4.generator;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.commands.fow.FOWOptions;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.helpers.ImageHelper;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
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
import ti4.model.ColorModel;
import ti4.model.EventModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class MapGenerator {

    public static final int DELTA_Y = 26;
    public static final int RING_MAX_COUNT = 8;
    public static final int RING_MIN_COUNT = 3;
    public static final int PLAYER_STATS_HEIGHT = 650;
    public static final int TILE_PADDING = 100;
    private static final int EXTRA_X = 300;
    private static final int EXTRA_Y = 200;
    private static final Point tilePositionPoint = new Point(255, 295);
    private static final Point labelPositionPoint = new Point(90, 295);
    private static final Point numberPositionPoint = new Point(40, 27);

    private final Graphics graphics;
    private final BufferedImage mainImage;
    private final int scoreTokenWidth;
    private final Game game;
    private final DisplayType displayType;
    private final DisplayType displayTypeBasic;
    private final boolean uploadToDiscord;
    private final boolean debug;
    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final boolean extraRow;
    private final Map<String, Player> playerControlMap;

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

    //private static final BasicStroke stroke1 = new BasicStroke(1.0f);
    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke3 = new BasicStroke(3.0f);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke5 = new BasicStroke(5.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);
    private static final BasicStroke stroke7 = new BasicStroke(7.0f);
    private static final BasicStroke stroke8 = new BasicStroke(8.0f);

    private static Color EliminatedColor = new Color(150, 0, 24); // Carmine
    private static Color ActiveColor = new Color(80, 200, 120); // Emerald
    private static Color PassedColor = new Color(220, 20, 60); // Crimson
    private static Color DummyColor = new Color(0, 128, 255); // Azure
    private static Color Stage1RevealedColor = new Color(230, 126, 34);
    private static Color Stage1HiddenColor = new Color(130, 70, 0);
    private static Color Stage2RevealedColor = new Color(93, 173, 226);
    private static Color Stage2HiddenColor = new Color(30, 60, 128);
    private static Color LawColor = new Color(228, 255, 0);
    private static Color TradeGoodColor = new Color(241, 176, 0);

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
        this.playerControlMap = game.getPlayerControlMap();

        debug = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);

        String controlID = Mapper.getControlID("red");
        BufferedImage bufferedImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), 0.45f);
        if (bufferedImage != null)
            scoreTokenWidth = bufferedImage.getWidth();
        else
            scoreTokenWidth = 14;

        int stage1 = game.getRevealedPublicObjectives().keySet().stream()
            .filter(Mapper.getPublicObjectivesStage1()::containsKey).toList().size();
        int stage2 = game.getRevealedPublicObjectives().keySet().stream()
            .filter(Mapper.getPublicObjectivesStage2()::containsKey).toList().size();
        int other = game.getRevealedPublicObjectives().size() - stage1 - stage2;
        stage1 = game.getPublicObjectives1Peakable().size() + stage1;
        stage2 = game.getPublicObjectives2Peakable().size() + stage2;

        int mostObjs = Math.max(Math.max(stage1, stage2), other);
        int objectivesY = Math.max((mostObjs - 5) * 43, 0);

        int playerCountForMap = game.getRealPlayers().size() + game.getDummies().size();
        int playerY = playerCountForMap * 340;
        int unrealPlayers = game.getNotRealPlayers().size();
        playerY += unrealPlayers * 20;
        for (Player player : game.getPlayers().values()) {
            if (player.isEliminated()) {
                playerY -= 190;
            } else if (player.getSecretsScored().size() > 4) {
                playerY += (player.getSecretsScored().size() - 4) * 43;
            }
        }

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
        switch (this.displayType) {
            case stats:
                heightForGameInfo = 40;
                height = heightStats;
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
                height = mapHeight + 600;
                displayTypeBasic = DisplayType.map;
                width = mapWidth;
                break;
            case landscape:
                heightForGameInfo = 40;
                height = Math.max(heightStats, mapHeight);
                displayTypeBasic = DisplayType.all;
                width = mapWidth + 4 * 520 + EXTRA_X * 2;
                break;
            default:
                heightForGameInfo = mapHeight;
                height = mapHeight + heightStats;
                displayTypeBasic = DisplayType.all;
                width = mapWidth;
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
        if (GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(),
            Boolean.class, false)) {
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
        if (debug) {
            debugAbsoluteStartTime = System.nanoTime();
        }

        AsyncTI4DiscordBot.jda.getPresence().setActivity(Activity.playing(game.getName()));
        game.incrementMapImageGenerationCount();

        drawGame(event);
        AsyncTI4DiscordBot.THREAD_POOL.submit(() -> sendToWebsite(event));
        FileUpload fileUpload = uploadToDiscord();
        logDebug(event);
        return fileUpload;
    }

    private void setupTilesForDisplayTypeAllAndMap(Map<String, Tile> tilesToDisplay) {
        if (displayTypeBasic != DisplayType.all && displayTypeBasic != DisplayType.map) {
            return;
        }
        if (debug) {
            debugStartTime = System.nanoTime();
        }
        Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
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
        if (debug)
            debugTileTime = System.nanoTime() - debugStartTime;
    }

    private void setupFow(GenericInteractionCreateEvent event, Map<String, Tile> tilesToDisplay) {
        if (debug)
            debugStartTime = System.nanoTime();
        if (game.isFowMode() && event != null) {
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
                    playerControlMap.remove(key);
                    if (fowPlayer != null) {
                        tilesToDisplay.put(key, fowPlayer.buildFogTile(key, fowPlayer));
                    }
                }
            }
        }
        if (debug)
            debugFowTime = System.nanoTime() - debugStartTime;
    }

    private void logDebug(GenericInteractionCreateEvent event) {
        ImageHelper.getCacheStats().ifPresent(stats -> MessageHelper.sendMessageToBotLogChannel("```\n" + stats + "\n```"));
        if (!debug)
            return;
        long total = System.nanoTime() - debugAbsoluteStartTime;
        String sb = " Total time to generate map " + game.getName() + ": " + Helper.getTimeRepresentationNanoSeconds(total) +
            "\n" + debugString("    Frog time: ", debugFowTime, total) +
            "\n" + debugString("    Tile time: ", debugTileTime, total) +
            "\n" + debugString("    Info time: ", debugGameInfoTime, total) +
            "\n" + debugString(" Discord time: ", debugDiscordTime, total) +
            "\n";
        MessageHelper.sendMessageToBotLogChannel(event, "```\nDEBUG - GenerateMap Timing:\n" + sb + "\n```");
    }

    private static String debugString(String prefix, long time, long total) {
        return prefix + Helper.getTimeRepresentationNanoSeconds(time) + String.format(" (%2.2f%%)", (double) time / (double) total * 100.0);
    }

    private void sendToWebsite(GenericInteractionCreateEvent event) {
        String testing = System.getenv("TESTING");
        if (testing == null && displayTypeBasic == DisplayType.all && (isFoWPrivate == null || !isFoWPrivate)) {
            WebHelper.putMap(game.getName(), mainImage);
            WebHelper.putData(game.getName(), game);
        } else if (isFoWPrivate != null && isFoWPrivate) {
            Player player = getFowPlayer(event);
            WebHelper.putMap(game.getName(), mainImage, true, player);
        }
    }

    private FileUpload uploadToDiscord() {
        if (!uploadToDiscord) return null;
        if (debug) debugStartTime = System.nanoTime();
        float quality = 1 / 6.0f;
        switch (displayType) {
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
            case landscape:
                quality = 1 / 4.0f;
        }

        FileUpload fileUpload = uploadToDiscord(mainImage, quality, game.getName());

        if (debug) debugDiscordTime = System.nanoTime() - debugStartTime;
        return fileUpload;
    }

    public static FileUpload uploadToDiscord(BufferedImage imageToUpload, float compressionQuality, String filenamePrefix) {
        return uploadToDiscord(imageToUpload, compressionQuality, filenamePrefix, false);
    }

    public static FileUpload uploadToDiscord(BufferedImage imageToUpload, float compressionQuality, String filenamePrefix, boolean saveLocalCopy) {
        if (imageToUpload == null) return null;

        FileUpload fileUpload = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // CONVERT PNG TO JPG
            BufferedImage convertedImage = new BufferedImage(imageToUpload.getWidth(), imageToUpload.getHeight(), BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(imageToUpload, 0, 0, Color.BLACK, null);
            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(out));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(compressionQuality);
            }
            imageWriter.write(null, new IIOImage(convertedImage, null, null), defaultWriteParam);

            // SAVE LOCAL & UPLOAD TO DISCORD
            if (saveLocalCopy) {
                File f = new File(filenamePrefix + ".png");
                ImageIO.write(imageToUpload, "png", f);
            }
            String fileName = filenamePrefix + "_" + getTimeStamp() + ".jpg";
            fileUpload = FileUpload.fromData(out.toByteArray(), fileName);
        } catch (IOException e) {
            BotLogger.log("Could not create FileUpload for " + filenamePrefix, e);
        }
        return fileUpload;
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
            if (Mapper.getFaction(factionID) != null && Mapper.getFaction(factionID).getHomebrewReplacesID().isPresent()) {
                factionFile = ResourceHelper.getInstance()
                    .getFactionFile(Mapper.getFaction(factionID).getHomebrewReplacesID().get() + ".png");
            }
        }
        if (factionFile == null) {
            if (factionID.equalsIgnoreCase("fogalliance")) {
                return null;
            }
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
        if (player == null)
            return null;
        Emoji factionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
        if (player.hasCustomFactionEmoji() && factionEmoji instanceof CustomEmoji factionCustomEmoji) {
            int urlImagePadding = 5;
            return ImageHelper.readURLScaled(factionCustomEmoji.getImageUrl(), width - urlImagePadding, height - urlImagePadding);
        } else if (player.hasCustomFactionEmoji() && factionEmoji instanceof UnicodeEmoji uni) {
            return ImageHelper.readUnicodeScaled(uni.getFormatted(), width, height);
        }

        return getFactionIconImageScaled(player.getFaction(), width, height);
    }

    @Nullable
    private static BufferedImage getFactionIconImageScaled(String factionID, int width, int height) {
        String factionPath = getFactionIconPath(factionID);
        if (factionPath == null)
            return null;

        return ImageHelper.readScaled(factionPath, width, height);
    }

    private static Image getPlayerDiscordAvatar(Player player) {
        try {
            Member member = AsyncTI4DiscordBot.guildPrimary.getMemberById(player.getUserID());
            if (member == null)
                return null;

            return ImageHelper.readURLScaled(member.getEffectiveAvatar().getUrl(), 32, 32);
        } catch (Exception e) {
            BotLogger.log("Could not get Avatar", e);
        }
        return null;
    }

    public static void drawBanner(Player player) {
        Graphics bannerG;
        BufferedImage bannerImage = new BufferedImage(325, 50, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("factionbanner_background.png"), 325, 50);
        String pnColorFile = "pa_pn_color_" + Mapper.getColorID(player.getColor()) + ".png";
        BufferedImage colorImage = ImageHelper.readScaled(ResourceHelper.getInstance().getPAResource(pnColorFile), 1.5f);
        BufferedImage gradientImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("factionbanner_gradient.png"));
        BufferedImage smallFactionImage = getPlayerFactionIconImageScaled(player, 0.26f);
        BufferedImage largeFactionImage = getPlayerFactionIconImageScaled(player, 1.4f);
        bannerG = bannerImage.getGraphics();

        bannerG.drawImage(backgroundImage, 0, 0, null);
        Graphics2D bannerG2d = (Graphics2D) bannerG;
        bannerG2d.rotate(Math.toRadians(-90));
        bannerG2d.drawImage(colorImage, -60, 0, null);
        bannerG2d.rotate(Math.toRadians(90));
        bannerG2d.drawImage(gradientImage, 0, 0, null);
        bannerG2d.drawImage(smallFactionImage, 2, 24, null);
        bannerG.drawImage(largeFactionImage, 180, -42, null);
        bannerG.setFont(Storage.getFont16());
        bannerG.setColor(Color.WHITE);
        String name = Mapper.getFaction(player.getFaction()).getFactionName().toUpperCase();
        if (name.contains("KELERES")) {
            name = "THE COUNCIL KELERES";
        }
        if (name.contains("FRANKEN") && player.getDisplayName() != null && !player.getDisplayName().isEmpty() && !player.getDisplayName().equalsIgnoreCase("null")) {
            name = player.getDisplayName().toUpperCase();
        }
        superDrawString(bannerG, name, 29, 44, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Bottom, stroke2, Color.BLACK);

        String turnOrdinal = StringHelper.ordinal(player.getTurnCount());
        String descr = player.getFlexibleDisplayName() + "'s " + turnOrdinal + " turn";
        FileUpload fileUpload = uploadToDiscord(bannerImage, 1.0f, player.getFaction() + player.getColor() + "banner").setDescription(descr);
        MessageHelper.sendFileUploadToChannel(player.getCorrectChannel(), fileUpload);
    }

    public static void drawAgendaBanner(int num, Game game) {
        Graphics bannerG;
        BufferedImage bannerImage = new BufferedImage(225, 50, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("factionbanner_background.png"), 325, 50);
        BufferedImage agendaImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("agenda.png"), 50, 50);
        String pnColorFile = "pa_pn_color_" + Mapper.getColorID("blue") + ".png";
        BufferedImage colorImage = ImageHelper.readScaled(ResourceHelper.getInstance().getPAResource(pnColorFile), 1.5f);
        BufferedImage gradientImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("factionbanner_gradient.png"));
        bannerG = bannerImage.getGraphics();

        bannerG.drawImage(backgroundImage, 0, 0, null);

        Graphics2D bannerG2d = (Graphics2D) bannerG;
        bannerG2d.rotate(Math.toRadians(-90));
        bannerG2d.drawImage(colorImage, -60, 0, null);
        bannerG2d.rotate(Math.toRadians(90));
        bannerG2d.drawImage(gradientImage, 0, 0, null);
        bannerG.drawImage(agendaImage, 0, 0, null);
        bannerG.setFont(Storage.getFont28());
        bannerG.setColor(Color.WHITE);

        superDrawString(bannerG, "Agenda #" + num, 55, 35, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Bottom, stroke2, Color.BLACK);

        FileUpload fileUpload = uploadToDiscord(bannerImage, 1.0f, "agenda" + num + "banner");
        MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload);
    }

    public static void drawPhaseBanner(String phase, int round, TextChannel channel) {
        BufferedImage bannerImage = new BufferedImage(511, 331, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile(phase + "banner.png"), 511, 331);

        Graphics bannerG = bannerImage.getGraphics();
        bannerG.drawImage(backgroundImage, 0, 0, null);
        bannerG.setFont(Storage.getFont48());
        bannerG.setColor(Color.WHITE);
        superDrawString(bannerG, phase.toUpperCase() + " PHASE", 255, 110, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Center, stroke8, Color.BLACK);
        bannerG.setFont(Storage.getFont32());

        String roundText = "ROUND " + StringHelper.numberToWords(round).toUpperCase();
        superDrawString(bannerG, roundText, 255, 221, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Center, stroke6, Color.BLACK);

        String descr = "Start of " + phase + " phase, round " + round + ".";
        FileUpload fileUpload = uploadToDiscord(bannerImage, 1.0f, phase + round + "banner").setDescription(descr);
        MessageHelper.sendFileUploadToChannel(channel, fileUpload);
    }

    private void drawGame(GenericInteractionCreateEvent event) {

        boolean finRun = false;
        int checkpoint = 1;
        if (event instanceof SlashCommandInteractionEvent slash) {
            if (slash.getUser().getIdLong() == 488681163146133504l) {
                finRun = true;
            }
        }
        if (finRun) {
            // BotLogger.log("Show game made it past checkpoint #" + checkpoint);
            checkpoint++;
        }
        if (debug) {
            debugStartTime = System.nanoTime();
        }
        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        setupFow(event, tilesToDisplay);
        setupTilesForDisplayTypeAllAndMap(tilesToDisplay);

        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        String timeStamp = getTimeStamp();
        graphics.drawString(game.getName() + " " + game.getCreationDate() + " - " + timeStamp, 0, 34);
        if (finRun) {
            //BotLogger.log("Show game made it past checkpoint #" + checkpoint);
            checkpoint++;
        }
        int landscapeShift = (displayType == DisplayType.landscape ? mapWidth : 0);
        int y = heightForGameInfo + 60;
        int x = landscapeShift + 10;
        Coord coord = coord(x, y);

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
        if (finRun) {
            // BotLogger.log("Show game made it past checkpoint #" + checkpoint);
            checkpoint++;
        }

        if (displayTypeBasic == DisplayType.all || displayTypeBasic == DisplayType.stats) {
            graphics.setFont(Storage.getFont32());
            Graphics2D g2 = (Graphics2D) graphics;
            int realX = x;
            Map<UnitKey, Integer> unitCount = new HashMap<>();
            for (Player player : players) {
                if (player == null) continue;
                int baseY = y;
                x = realX;

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate
                    && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                if (convertToGeneric) {
                    continue;
                }
                if ((game.isMinorFactionsMode() || (player != null && player.getFaction() != null && player.getFaction().equalsIgnoreCase("neutral"))) && player.isDummy()) {
                    continue;
                }

                // PAINT AVATAR AND USERNAME
                StringBuilder userName = new StringBuilder();
                String playerName = player.getUserName();
                boolean fowHidePlayerNames = Boolean.parseBoolean(game.getFowOption(FOWOptions.HIDE_NAMES));
                if (!fowHidePlayerNames) {
                    graphics.drawImage(getPlayerDiscordAvatar(player), x, y + 5, null);
                    userName.append(" ").append(playerName.substring(0, Math.min(playerName.length(), 20)));
                }
                y += 34;
                graphics.setFont(Storage.getFont32());
                Color color = getColor(player.getColor());
                graphics.setColor(Color.WHITE);

                // PAINT FACTION OR DISPLAY NAME
                String factionText = player.getFaction();
                if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
                    factionText = player.getDisplayName();
                }
                if (factionText != null && !"null".equals(factionText)) {
                    userName.append(" [").append(StringUtils.capitalize(factionText)).append("]");
                }

                if (!"null".equals(player.getColor())) {
                    userName.append(" (").append(player.getColor()).append(")");
                }
                if (player.isAFK()) {
                    userName.append(" -- AFK");
                }

                graphics.drawString(userName.toString(), !fowHidePlayerNames ? x + 34 : x, y);
                if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
                    continue;
                }

                // PAINT FACTION ICON
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
                        if (getSCColor(sc, game).equals(Color.GRAY)) {
                            graphics.setFont(Storage.getFont24());
                            graphics.setColor(Color.RED);
                            graphics.drawString("X", x + 120, y + 80 + yDelta);
                        }
                    } else {
                        if (sc == ButtonHelper.getKyroHeroSC(game)) {
                            graphics.drawString("" + (game.getSCList().size() + 1), x + 90, y + 70 + yDelta);
                        } else {
                            if (sc == 1) {
                                graphics.drawString(scText, x + 102, y + 70 + yDelta);
                            } else {
                                graphics.drawString(scText, x + 90, y + 70 + yDelta);
                            }
                            if (getSCColor(sc, game).equals(Color.GRAY)) {
                                graphics.setFont(Storage.getFont40());
                                graphics.setColor(Color.RED);

                                graphics.drawString("X", x + 100, y + 60 + yDelta);
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
                            drawCenteredString(graphics, kyroScNum,
                                new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                Storage.getFont32());
                            if (getSCColor(sc, game).equals(Color.GRAY)) {
                                graphics.setColor(Color.RED);
                                drawCenteredString(graphics, "X",
                                    new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                    Storage.getFont24());
                            }
                        } else {
                            drawCenteredString(graphics, scText,
                                new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32),
                                Storage.getFont32());
                            if (getSCColor(sc, game).equals(Color.GRAY)) {
                                graphics.setColor(Color.RED);
                                drawCenteredString(graphics, "X",
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
                    superDrawString(g2, "ELIMINATED", 0, 0, EliminatedColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.isDummy()) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    superDrawString(g2, "ELIMINATED", 0, 0, EliminatedColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.isDummy()) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    superDrawString(g2, "DUMMY", 0, 0, DummyColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.isPassed()) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    superDrawString(g2, "PASSED", 0, 0, PassedColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                    AffineTransform transform = g2.getTransform();
                    g2.translate(x + 47 - 3, y + 47 - 6);
                    g2.rotate(-Math.PI / 4);
                    g2.setFont(Storage.getFont20());
                    superDrawString(g2, "ACTIVE", 0, 0, ActiveColor, HorizontalAlign.Center, VerticalAlign.Center, stroke4, Color.BLACK);
                    g2.setTransform(transform);
                }

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
                    if (player.getNeighbouringPlayers().size() == 0) {
                        graphics.drawString("No Neighbors", x + 9 + xSpacer, y + 125 + yDelta);
                        xSpacer = xSpacer + 115;
                    } else {
                        graphics.drawString("Neighbors: ", x + 9 + xSpacer, y + 125 + yDelta);
                        xSpacer = xSpacer + 115;
                        for (Player p2 : player.getNeighbouringPlayers()) {
                            String faction2 = p2.getFaction();
                            if (faction2 != null) {
                                drawPlayerFactionIconImage(graphics, p2, x + xSpacer, y + 125 + yDelta - 20, 26, 26);
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

                // Additional FS
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

                if (game.isNomadCoin()) {
                    drawPAImage(x + 345, y + yDelta, nomadCoinImage);
                } else {
                    drawPAImage(x + 345, y + yDelta, tradeGoodImage);
                }
                graphics.drawString(Integer.toString(player.getTg()), x + 360, y + deltaY + 50);

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
                int soCount = objectivesSO(yPlayArea + 165, player);

                int xDeltaSecondRow = xDelta;
                int yPlayAreaSecondRow = yPlayArea + 160;
                if (!player.getPlanets().isEmpty()) {
                    xDeltaSecondRow = planetInfo(player, xDeltaSecondRow, yPlayAreaSecondRow);
                }

                int xDeltaFirstRowFromRightSide = 0;
                int xDeltaSecondRowFromRightSide = 0;
                // FIRST ROW RIGHT SIDE
                xDeltaFirstRowFromRightSide = unitValues(player, xDeltaFirstRowFromRightSide, yPlayArea);
                xDeltaFirstRowFromRightSide = nombox(player, xDeltaFirstRowFromRightSide, yPlayArea);

                // SECOND ROW RIGHT SIDE
                xDeltaSecondRowFromRightSide = reinforcements(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, unitCount);
                xDeltaSecondRowFromRightSide = sleeperTokens(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);
                xDeltaSecondRowFromRightSide = creussWormholeTokens(player, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);
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
                // }

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
                g2.drawRect(realX - 5, baseY, mapWidth - realX, y - baseY);
                y += 15;

            }
        }
        if (finRun) {
            // BotLogger.log("Show game made it past checkpoint #" + checkpoint);
            checkpoint++;
            if (checkpoint > 1000) System.out.println(checkpoint);
        }
        if (debug)
            debugGameInfoTime = System.nanoTime() - debugStartTime;
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

    private int sleeperTokens(Player player, int xDeltaSecondRowFromRightSide, int yPlayAreaSecondRow) {
        if (!player.hasAbility("awaken")) {
            return xDeltaSecondRowFromRightSide;
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
        return displayRemainingFactionTokens(points, bufferedImage, numToDisplay, xDeltaSecondRowFromRightSide, yPlayAreaSecondRow);
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
            yPlayAreaSecondRow += (alphaOnMap || betaOnMap || gammaOnMap) ? 38 : 19;
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
            yPlayAreaSecondRow += (alphaOnMap || betaOnMap || gammaOnMap) ? 38 : 19;
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
            yPlayAreaSecondRow += (alphaOnMap || betaOnMap || gammaOnMap) ? 38 : 19;
        }

        xDeltaSecondRowFromRightSide -= (alphaOnMap && betaOnMap && gammaOnMap ? 0 : 40);
        return xDeltaSecondRowFromRightSide;
    }

    private int omenDice(Player player, int x, int y) {
        int deltaX = 0;
        if (player.hasAbility("divination") && ButtonHelperAbilities.getAllOmenDie(game).size() > 0) {

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
        Collection<Player> players = game.getPlayers().values();
        for (String pn : player.getPromissoryNotesInPlayArea()) {
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);

            boolean commanderUnlocked = false;
            Player promissoryNoteOwner = game.getPNOwner(pn);
            if (promissoryNoteOwner == null) { // nobody owns this note - possibly eliminated player
                String error = game.getName() + " " + player.getUserName();
                error += "  `GenerateMap.pnInfo` is trying to display a Promissory Note without an owner - possibly an eliminated player: "
                    + pn;
                BotLogger.log(error);
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    String playerFaction = player_.getFaction();
                    if (playerColor != null && playerColor.equals(promissoryNoteOwner.getColor())
                        || playerFaction != null && playerFaction.equals(promissoryNoteOwner.getFaction())) {
                        String pnColorFile = "pa_pn_color_" + Mapper.getColorID(playerColor) + ".png";
                        drawPAImage(x + deltaX, y, pnColorFile);

                        if (game.isFrankenGame())
                            drawFactionIconImage(graphics, promissoryNote.getFaction().orElse(""), x + deltaX - 1,
                                y + 86, 42, 42);
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
            if (promissoryNote != null && promissoryNote.getAttachment().isPresent()
                && !promissoryNote.getAttachment().get().isBlank()) {
                String tokenID = promissoryNote.getAttachment().get();
                found: for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(tokenID))) {
                            drawPlanetImage(x + deltaX + 17, y, "pc_planetname_" + unitHolder.getName() + "_rdy.png",
                                unitHolder.getName());
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
        g2.setStroke(stroke2);

        List<String> relics = new ArrayList<>(player.getRelics());
        List<String> fakeRelics = relics.stream()
            .filter(relic -> Mapper.getRelic(relic).isFakeRelic())
            .filter(relic -> !relic.contains("axisorder"))
            .collect(Collectors.toList());
        List<String> axisOrderRelics = player.getRelics().stream()
            .filter(relic -> relic.contains("axisorder"))
            .collect(Collectors.toList());

        relics.removeAll(fakeRelics);
        relics.removeAll(axisOrderRelics);

        List<String> exhaustedRelics = player.getExhaustedRelics();

        for (String relicID : relics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, "pa_relics_icon.png");

            String relicStatus = isExhausted ? "_exh" : "_rdy";

            // ABSOL QUANTUMCORE
            if (relicID.contains("quantumcore")) {
                drawPAImage(x + deltaX, y, "pa_tech_techicons_cyberneticwarfare" + relicStatus + ".png");
            }

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

            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);

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
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            graphics.drawRect(x + deltaX - 2, y - 2, 54, 152);

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
                graphics.setColor(TradeGoodColor);
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
                    g2.setFont(Storage.getFont16());
                    drawTwoLinesOfTextVertically(g2, leaderModel.getShortName(), x + deltaX + 10, y + 148, 130);
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
            if (game.isMinorFactionsMode()) {
                players = game.getRealPlayers();
            }
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
        g2.setStroke(stroke2);

        String bankImage = "vaden".equalsIgnoreCase(player.getFaction()) ? "pa_ds_vaden_bank.png"
            : "pa_debtaccount.png";
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
                drawControlToken(graphics, controlTokenImage,
                    getPlayerByControlMarker(game.getPlayers().values(), controlID), x + deltaX + tokenDeltaX,
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
    private static void getAndDrawControlToken(Graphics graphics, Player p, int x, int y, boolean hideFactionIcon, float scale) {
        String colorID = p == null ? "gray" : p.getColor();
        BufferedImage controlToken = ImageHelper.readScaled(Mapper.getCCPath(Mapper.getControlID(colorID)), scale);
        drawControlToken(graphics, controlToken, p, x, y, hideFactionIcon, scale);
    }

    private static void drawControlToken(Graphics graphics, BufferedImage bottomTokenImage, Player player, int x, int y, boolean hideFactionIcon, float scale) {
        graphics.drawImage(bottomTokenImage, x, y, null);

        if (hideFactionIcon)
            return;
        scale = scale * 0.50f;
        BufferedImage factionImage = getPlayerFactionIconImageScaled(player, scale);
        if (factionImage == null)
            return;

        int centreCustomTokenHorizontally = bottomTokenImage.getWidth() / 2 - factionImage.getWidth() / 2;
        int centreCustomTokenVertically = bottomTokenImage.getHeight() / 2 - factionImage.getHeight() / 2;

        graphics.drawImage(factionImage, x + centreCustomTokenHorizontally, y + centreCustomTokenVertically, null);
    }

    private int abilityInfo(Player player, int x, int y) {
        int deltaX = 10;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
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
                g2.setFont(Storage.getFont16());
                drawTwoLinesOfTextVertically(g2, abilityModel.getShortName(), x + deltaX + 6, y + 144, 130);
                graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            }

            deltaX += 48;
            addedAbilities = true;
        }
        return x + deltaX + (addedAbilities ? 20 : 0);
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
                UnitHolder unitHolder = player_.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                if (unitHolder == null) {
                    continue;
                }
                fillUnits(unitMapCount, unitHolder, true);
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
                BufferedImage image = ImageHelper.read(ResourceHelper.getInstance().getUnitFile(unitKey));
                BufferedImage decal = null;
                if (player != null) decal = ImageHelper.read(ResourceHelper.getInstance().getDecalFile(player.getDecalFile(unitID)));
                for (int i = 0; i < numInReinforcements; i++) {
                    Point position = reinforcementsPosition.getPosition(unitID);
                    graphics.drawImage(image, x + position.x, y + position.y, null);
                    graphics.drawImage(decal, x + position.x, y + position.y, null);
                    if (onlyPaintOneUnit) break;
                }

                String unitName = unitKey.getUnitType().humanReadableName();
                if (numInReinforcements < 0 && !game.isDiscordantStarsMode() && game.isCcNPlasticLimit()) {
                    String warningMessage = playerColor + " is exceeding unit plastic or cardboard limits for " + unitName + ". Use buttons to remove";
                    List<Button> removeButtons = ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, unitKey.asyncID());
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), warningMessage, removeButtons);
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
                        drawCCOfPlayer(graphics, ccID, x + position.x, y + position.y, 1, player, false);
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
        int imageSize = verticalSpacing - 2;
        drawPAImageScaled(leftSide, y + verticalSpacing * 1, "pa_resources.png", imageSize);
        drawPAImageScaled(leftSide, y + verticalSpacing * 2, "pa_health.png", imageSize);
        drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_hit.png", imageSize);
        //drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_hit.png", imageSize);
        //drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_unitimage.png", imageSize);
        graphics.setColor(Color.WHITE);
        leftSide += verticalSpacing + 10;
        drawCenteredString(graphics, "Space |",
            new Rectangle(leftSide - 4, y + verticalSpacing * 0, 50, verticalSpacing), Storage.getFont18());
        drawCenteredString(graphics, "____________",
            new Rectangle(leftSide, y + verticalSpacing * 0, 110, verticalSpacing), Storage.getFont24());
        float val = player.getTotalResourceValueOfUnits("space");
        if (isWholeNumber(val) || val > 10) {
            drawCenteredString(graphics, String.valueOf((int) val),
                new Rectangle(leftSide, y + verticalSpacing * 1, 50, verticalSpacing), Storage.getFont24());
        } else {
            drawCenteredString(graphics, String.valueOf(val),
                new Rectangle(leftSide, y + verticalSpacing * 1, 50, verticalSpacing), Storage.getFont24());
        }
        drawCenteredString(graphics, String.valueOf(player.getTotalHPValueOfUnits("space")),
            new Rectangle(leftSide, y + verticalSpacing * 2, 50, verticalSpacing), Storage.getFont24());
        drawCenteredString(graphics, String.valueOf(player.getTotalCombatValueOfUnits("space")),
            new Rectangle(leftSide, y + verticalSpacing * 3, 50, verticalSpacing), Storage.getFont24());
        leftSide += verticalSpacing + 20;
        drawCenteredString(graphics, "  Ground",
            new Rectangle(leftSide, y + verticalSpacing * 0, 50, verticalSpacing), Storage.getFont18());
        // drawCenteredString(graphics, String.valueOf(player.getTotalResourceValueOfUnits("ground")),
        //     new Rectangle(leftSide, y + verticalSpacing * 1, 50, verticalSpacing), Storage.getFont24());
        val = player.getTotalResourceValueOfUnits("ground");
        if (isWholeNumber(val) || val > 10) {
            drawCenteredString(graphics, String.valueOf((int) val),
                new Rectangle(leftSide, y + verticalSpacing * 1, 50, verticalSpacing), Storage.getFont24());
        } else {
            drawCenteredString(graphics, String.valueOf(val),
                new Rectangle(leftSide, y + verticalSpacing * 1, 50, verticalSpacing), Storage.getFont24());
        }
        drawCenteredString(graphics, String.valueOf(player.getTotalHPValueOfUnits("ground")),
            new Rectangle(leftSide, y + verticalSpacing * 2, 50, verticalSpacing), Storage.getFont24());
        drawCenteredString(graphics, String.valueOf(player.getTotalCombatValueOfUnits("ground")),
            new Rectangle(leftSide, y + verticalSpacing * 3, 50, verticalSpacing), Storage.getFont24());
        //drawCenteredString(graphics, String.valueOf(player.getTotalUnitAbilityValueOfUnits()),
        //    new Rectangle(leftSide, y + verticalSpacing * 3, 50, verticalSpacing), Storage.getFont24());
        return xDeltaFromRightSide + widthOfSection;
    }

    private int nombox(Player player, int xDeltaFromRightSide, int y) {
        int widthOfNombox = 450;
        int x = width - widthOfNombox - xDeltaFromRightSide;
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
                Player p = game.getPlayerFromColorOrFaction(unitKey.getColor());

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

                String decalFile = p != null ? p.getDecalFile(unitKey.asyncID()) : null;
                BufferedImage decal = ImageHelper.read(ResourceHelper.getInstance().getDecalFile(decalFile));
                BufferedImage spoopy = null;
                if ((unitKey.getUnitType() == UnitType.Warsun) && (ThreadLocalRandom.current().nextInt(1000) == 0)) {
                    String spoopypath = ResourceHelper.getInstance().getSpoopyFile();
                    spoopy = ImageHelper.read(spoopypath);
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
                    if (!List.of(UnitType.Fighter, UnitType.Infantry).contains(unitKey.getUnitType())) {
                        graphics.drawImage(decal, position.x, position.y + deltaY, null);
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
        if (textPosition == null)
            return;

        String text = "pa_reinforcements_numbers_" + reinforcementsCount;
        String colorID = Mapper.getColorID(color);
        text += getBlackWhiteFileSuffix(colorID);
        Point position = textPosition.getPosition(id);
        drawPAImage(x + position.x, y + position.y, text);
    }

    private int planetInfo(Player player, int x, int y) {
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        List<String> planets = player.getPlanets();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

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
            drawCenteredString(graphics, String.valueOf(availablePlayerResources),
                new Rectangle(x + deltaX, y + 75 - 35 + 5, 150, 35), Storage.getFont35());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerResources),
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
            drawCenteredString(graphics, String.valueOf(availablePlayerResources),
                new Rectangle(x + deltaX + 30, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerResources),
                new Rectangle(x + deltaX + 30, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#d5bd4f")); // greyish-yellow
            drawCenteredString(graphics, String.valueOf(availablePlayerResourcesOptimal),
                new Rectangle(x + deltaX + 30, y + 90, 32, 32), Storage.getFont18());
            // drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 30, y + 100,
            // 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // drawCenteredString(graphics, String.valueOf(totalPlayerResourcesOptimal), new
            // Rectangle(x + deltaX + 34, y + 109, 32, 32), Storage.getFont32());

            // INFLUENCE
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerInfluence),
                new Rectangle(x + deltaX + 90, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerInfluence),
                new Rectangle(x + deltaX + 90, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#57b9d9")); // greyish-blue
            drawCenteredString(graphics, String.valueOf(availablePlayerInfluenceOptimal),
                new Rectangle(x + deltaX + 90, y + 90, 32, 32), Storage.getFont18());
            // drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 90, y + 100,
            // 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // drawCenteredString(graphics, String.valueOf(totalPlayerInfluenceOptimal), new
            // Rectangle(x + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

            // FLEX
            graphics.setColor(Color.WHITE);
            // drawCenteredString(graphics, "FLEX", new Rectangle(x + deltaX, y + 130, 150,
            // 8), Storage.getFont8());
            if ("203608548440014848".equals(player.getUserID()))
                graphics.setColor(Color.decode("#f616ce"));
            drawCenteredString(graphics, String.valueOf(availablePlayerFlex),
                new Rectangle(x + deltaX, y + 115, 150, 20), Storage.getFont18());
            // drawCenteredString(graphics, String.valueOf(totalPlayerFlex), new Rectangle(x
            // + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

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

                // Display planet traits
                String originalPlanetType = planetHolder.getOriginalPlanetType();
                if (Optional.ofNullable(originalPlanetType).isEmpty()) {
                    originalPlanetType = "none";
                }
                if ("faction".equals(originalPlanetType)) {
                    originalPlanetType = TileHelper.getAllPlanets().get(planet).getFactionHomeworld();
                    if (originalPlanetType == null) // fallback to current player's faction
                        originalPlanetType = player.getFaction();
                }

                Set<String> planetTypes = planetHolder.getPlanetTypes();
                if (!planetTypes.isEmpty() && planetTypes.size() > 1) {
                    originalPlanetType = "combo_";
                    if (planetTypes.contains("cultural")) originalPlanetType += "C";
                    if (planetTypes.contains("hazardous")) originalPlanetType += "H";
                    if (planetTypes.contains("industrial")) originalPlanetType += "I";
                }

                if (!originalPlanetType.isEmpty()) {
                    if ("keleres".equals(player.getFaction()) && ("mentak".equals(originalPlanetType) || "xxcha".equals(originalPlanetType) || "argent".equals(originalPlanetType))) {
                        originalPlanetType = "keleres";
                    }

                    if (Mapper.isValidFaction(originalPlanetType)) {
                        drawFactionIconImage(graphics, originalPlanetType, x + deltaX - 2, y - 2, 52, 52);
                    } else {
                        String planetTypeName = "pc_attribute_" + originalPlanetType + ".png";
                        drawPlanetImage(x + deltaX + 1, y + 2, planetTypeName, planet);
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
                    if (planetHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        planetTypeName = "pc_upgrade_tomb.png";
                    }
                    drawPlanetImage(x + deltaX + 26, y + 40, planetTypeName, planet);
                }

                if (planetHolder.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                    String khraskGardenWorlds = "pc_ds_khraskbonus.png";
                    drawPlanetImage(x + deltaX, y, khraskGardenWorlds, planet);
                }

                if (planetHolder.isLegendary()) {
                    String statusOfAbility = exhaustedPlanetsAbilities.contains(planet) ? "_exh" : "_rdy";
                    String planetTypeName = "pc_legendary" + statusOfAbility + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 60, planetTypeName, planet);
                }

                boolean hasBentorEncryptionKey = unitHolder.getTokenList().stream()
                    .anyMatch(token -> token.contains("encryptionkey"));
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
        // if (techs.isEmpty()) {
        // return y;
        // }

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

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = 0;
        deltaX = techField(x, y, techsFiltered.get(Constants.PROPULSION), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.WARFARE), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.CYBERNETIC), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.BIOTIC), exhaustedTechs, deltaX, player);
        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), deltaX, player, game);
        deltaX = techStasisCapsule(x, y, deltaX, player, techsFiltered.get(Constants.UNIT_UPGRADE));
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
        deltaX = factionTechField(x, y, techs, deltaX);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, int deltaX, Player player) {
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

            // Draw Faction Tech Icon
            if (techModel.getFaction().isPresent()) {
                drawFactionIconImage(graphics, techModel.getFaction().get(), x + deltaX - 1, y + 108, 42, 42);
            }

            // Draw Tech Name
            String techName = "pa_tech_techname_" + tech + techStatus;
            String resourcePath = ResourceHelper.getInstance().getPAResource(techName);
            if (resourcePath != null) {
                BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                if ("dslaner".equalsIgnoreCase(tech)) {
                    drawTextVertically(graphics, "" + player.getAtsCount(), x + deltaX + 15, y + 140, Storage.getFont16());
                }
            } else { //no special image, so draw the text
                graphics.setFont(Storage.getFont20());
                drawTwoLinesOfTextVertically(graphics, techModel.getName(), x + deltaX + 5, y + 148, 130);
            }

            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }

    private int factionTechField(int x, int y, List<String> techs, int deltaX) {
        if (techs == null) {
            return deltaX;
        }

        for (String tech : techs) {
            graphics.setColor(Color.DARK_GRAY);

            TechnologyModel techInformation = Mapper.getTech(tech);
            if (techInformation.isUnitUpgrade()) {
                continue;
            }

            int types = 0;
            String techIcon = "";
            if (techInformation.isPropulsionTech() && types++ < 2) techIcon += "propulsion";
            if (techInformation.isCyberneticTech() && types++ < 2) techIcon += "cybernetic";
            if (techInformation.isBioticTech() && types++ < 2) techIcon += "biotic";
            if (techInformation.isWarfareTech() && types++ < 2) techIcon += "warfare";

            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techIcon = game.getStoredValue("colorChange" + tech);
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
                graphics.setFont(Storage.getFont20());
                drawTwoLinesOfTextVertically(graphics, techModel.getName(), x + deltaX + 5, y + 130, 110);
            }

            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }

    private int techStasisCapsule(int x, int y, int deltaX, Player player, List<String> techs) {
        int stasisInfantry = player.getStasisInfantry();
        if ((techs == null && stasisInfantry == 0) || !hasInfantryII(techs) && stasisInfantry == 0) {
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

    private record Coord(int x, int y) {
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
            }
        }
        if (brokenWarSun) {
            UnitModel unit = Mapper.getUnitModelByTechUpgrade("ws");
            Coord unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
            UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
            BufferedImage wsCrackImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile(
                "agenda_publicize_weapon_schematics" + (player.hasWarsunTech() ? getBlackWhiteFileSuffix(unitKey.getColorID()) : "_blk.png")));
            graphics.drawImage(wsCrackImage, deltaX + x + unitOffset.x, y + unitOffset.y, null);
        }

        // Add faction icons on top of upgraded or upgradable units
        for (String u : player.getUnitsOwned()) {
            UnitModel unit = Mapper.getUnit(u);
            if (unit == null) {
                System.out.println("Invalid unitID found:" + u);
            } else if (unit.getFaction().isPresent()) {
                boolean unitHasUpgrade = unit.getUpgradesFromUnitId().isPresent() || unit.getUpgradesToUnitId().isPresent();
                if (game.isFrankenGame() || unitHasUpgrade || player.getFactionModel().getAlias().equals("echoes")) {
                    // Always paint the faction icon in franken
                    Coord unitFactionOffset = getUnitTechOffsets(unit.getAsyncId(), true);
                    drawFactionIconImage(graphics, unit.getFaction().get().toLowerCase(), deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32);
                }
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 252, 152);
        deltaX += 270;
        return deltaX;
    }

    private void drawFactionIconImage(Graphics graphics, String faction, int x, int y, int width, int height) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null);
    }

    private void drawFactionIconImageOpaque(Graphics graphics, String faction, int x, int y, int width, int height,
        Float opacity) {
        try {
            BufferedImage resourceBufferedImage = getFactionIconImageScaled(faction, width, height);
            Graphics2D g2 = (Graphics2D) graphics;
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

    private static void drawPlayerFactionIconImage(Graphics graphics, Player player, int x, int y, int width, int height) {
        drawPlayerFactionIconImageOpaque(graphics, player, x, y, width, height, null);
    }

    private static void drawPlayerFactionIconImageOpaque(Graphics graphics, Player player, int x, int y, int width, int height, Float opacity) {
        if (player == null)
            return;
        try {
            BufferedImage resourceBufferedImage = getPlayerFactionIconImageScaled(player, width, height);
            Graphics2D g2 = (Graphics2D) graphics;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
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
                String name = Optional.ofNullable(Mapper.getPlanet(planetName).getShortName())
                    .orElse(Mapper.getPlanet(planetName).getName());
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
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
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

    private void drawPAUnitUpgrade(int x, int y, UnitKey unitKey) {
        try {
            String path = Tile.getUnitPath(unitKey);
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

    public static void superDrawString(Graphics g, String txt, int x, int y, Color textColor, HorizontalAlign h, VerticalAlign v, Stroke outlineSize, Color outlineColor) {
        superDrawString((Graphics2D) g, txt, x, y, textColor, h, v, outlineSize, outlineColor);
    }

    public enum HorizontalAlign {
        Left, Center, Right;
    };

    public enum VerticalAlign {
        Top, Center, Bottom;
    };

    /**
     *
     * @param g graphics object
     * @param txt string to print
     * @param x x-position of the string (Left side, unless horizontalAlignment is set)
     * @param y y-position of the string (Bottom side, unless verticalAlignment is set)
     * @param textColor
     * @param horizontalAlignment location of the provided x relative to the (default = Left)
     * @param verticalAlignment location of the provided y relative to the text (default = Bottom)
     * @param outlineSize use global variable "strokeX" where X = outline size e.g. stroke1 for 1px outline
     * @param outlineColor
     */
    private static void superDrawString(Graphics2D g, String txt, int x, int y, Color textColor, HorizontalAlign horizontalAlignment, VerticalAlign verticalAlignment, Stroke outlineSize, Color outlineColor) {
        if (txt == null) return;
        if (horizontalAlignment != null) {
            double width = g.getFontMetrics().stringWidth(txt);
            switch (horizontalAlignment) {
                case Center -> x -= width / 2.0;
                case Right -> x -= width;
                case Left -> {
                }
            }
        }
        if (verticalAlignment != null) {
            double height = g.getFontMetrics().getStringBounds(txt, g).getHeight();
            switch (verticalAlignment) {
                case Center -> y += height / 2.0;
                case Top -> y += height;
                case Bottom -> {
                }
            }
        }
        if (outlineSize == null) outlineSize = stroke2;
        if (outlineColor == null && textColor == null) {
            outlineColor = Color.BLACK;
            textColor = Color.WHITE;
        }
        if (outlineSize == null || outlineColor == null) {
            g.drawString(txt, x, y);
        } else {
            drawStringOutlined(g, txt, x, y, outlineSize, outlineColor, textColor);
        }
    }

    private static void drawStringOutlined(Graphics2D g2, String text, int x, int y, Stroke outlineStroke, Color outlineColor, Color fillColor) {
        if (text == null) return;
        Color origColor = g2.getColor();
        AffineTransform originalTileTransform = g2.getTransform();
        Stroke origStroke = g2.getStroke();
        RenderingHints origHints = g2.getRenderingHints();

        GlyphVector gv = g2.getFont().createGlyphVector(g2.getFontRenderContext(), text);
        Shape textShape = gv.getOutline();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.translate(x, y);
        g2.setColor(outlineColor);
        g2.setStroke(outlineStroke);
        g2.draw(textShape);

        g2.setColor(fillColor);
        g2.fill(textShape);

        g2.setColor(origColor);
        g2.setStroke(origStroke);
        g2.setTransform(originalTileTransform);
        g2.setRenderingHints(origHints);
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
            drawCenteredString(g2, Integer.toString(i), rect, Storage.getFont50());
            g2.setColor(Color.RED);
            g2.drawRect(i * boxWidth + landscapeShift, y, boxWidth, boxHeight);
        }

        List<Player> players = new ArrayList<>(game.getRealPlayers());
        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }

        int row = 0;
        int col = 0;
        int tokenWidth = 0;
        int tokenHeight = 0;
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
                    boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                    String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());

                    BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                    tokenWidth = controlTokenImage == null ? 51 : controlTokenImage.getWidth(); //51
                    tokenHeight = controlTokenImage == null ? 33 : controlTokenImage.getHeight(); //33
                    int centreHorizontally = Math.max(0, (availableSpacePerColumn - tokenWidth) / 2);
                    int centreVertically = Math.max(0, (availableSpacePerRow - tokenHeight) / 2);

                    int vpCount = player.getTotalVictoryPoints();
                    int tokenX = vpCount * boxWidth + Math.min(boxBuffer + (availableSpacePerColumn * col) + centreHorizontally, boxWidth - tokenWidth - boxBuffer) + landscapeShift;
                    int tokenY = y + boxBuffer + (availableSpacePerRow * row) + centreVertically;
                    drawControlToken(graphics, controlTokenImage, getPlayerByControlMarker(game.getPlayers().values(), controlID), tokenX, tokenY, convertToGeneric, scale);
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
        boolean convertToGenericSC = isFoWPrivate != null && isFoWPrivate;
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
            graphics.setFont(Storage.getFont64());
            int textWidth = graphics.getFontMetrics().stringWidth(Integer.toString(sc));

            if (!convertToGenericSC && !scPicked.contains(sc)) {
                graphics.setColor(getSCColor(sc, game));
                if (!game.getStoredValue("exhaustedSC" + sc).isEmpty()) {
                    graphics.setColor(Color.GRAY);
                }
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, deltaY);
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
        boolean convertToGenericSC = isFoWPrivate != null && isFoWPrivate;
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
                    BufferedImage bufferedImage = getPlayerFactionIconImage(player);
                    if (bufferedImage != null) {
                        graphics.drawImage(bufferedImage, x, y - 70, null);
                        x += 100;
                    }
                }
            }
        }
        return coord(x, y);
    }

    private int drawCardDecks(int x, int y) {
        if (!game.isFowMode()) {
            int cardWidth = 60;
            int cardHeight = 90;
            int horSpacing = cardWidth + 15;
            int textY = y + cardHeight - 10;
            Stroke outline = stroke2;

            graphics.setFont(Storage.getFont24());

            drawPAImageScaled(x, y, "cardback_secret.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getSecretObjectiveDeckSize()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;

            drawPAImageScaled(x, y, "cardback_action.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getActionCards().size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;

            drawPAImageScaled(x, y, "cardback_cultural.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getExploreDeck("cultural").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;

            drawPAImageScaled(x, y, "cardback_industrial.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getExploreDeck("industrial").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;

            drawPAImageScaled(x, y, "cardback_hazardous.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getExploreDeck("hazardous").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;

            drawPAImageScaled(x, y, "cardback_frontier.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getExploreDeck("frontier").size()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;
            drawPAImageScaled(x, y, "cardback_relic.jpg", cardWidth, cardHeight);
            superDrawString(graphics, Integer.toString(game.getRelicDeckSize()), x + cardWidth / 2, textY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, outline, Color.BLACK);
            x += horSpacing;
        }
        return x;
    }

    private int tileRing(String pos) {
        if (pos.replaceAll("\\d", "").isEmpty())
            return Integer.parseInt(pos) / 100;
        return 100;
    }

    private List<String> findThreeNearbyStatTiles(Game game, Player player, Set<String> taken) {
        boolean fow = isFoWPrivate != null && isFoWPrivate;
        boolean randomizeLocation = false;
        if (fow && player != fowPlayer) {
            if (FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)) {
                if (!FoWHelper.hasHomeSystemInView(game, player, fowPlayer)) {
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
        boolean fow = isFoWPrivate != null && isFoWPrivate;
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        List<Player> statOrder = new ArrayList<>(game.getRealPlayers());
        if (fow) {
            Collections.shuffle(players);
            statOrder.clear();
            // always build fowplayer's stat location first
            statOrder.add(fowPlayer);
            // then build the stats of players we can see home systems
            players.stream().filter(player -> FoWHelper.hasHomeSystemInView(game, player, fowPlayer)).forEach(statOrder::add);
            // then build the stats of everyone else
            players.stream().filter(player -> FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)).forEach(statOrder::add);
        }

        int ringCount = game.getRingCount();
        ringCount = Math.max(Math.min(ringCount, RING_MAX_COUNT), RING_MIN_COUNT);

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

    private BufferedImage hexBorder(ColorModel color, List<Integer> openSides, boolean solidLines) {
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        boolean rainbow = color.getName().endsWith("rainbow");

        float inlineSize = 3.0f;
        float outlineSize = 6.0f;
        // on, off, on, off, ....
        float[] dash = { solidLines ? 85.0f : 30.0f, solidLines ? 1000.0f : 17.0f, 30.0f, 1000.0f };
        float[] sparse = { 11.0f, 1000.0f };
        Stroke line = new BasicStroke(inlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
        Stroke outline = new BasicStroke(outlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
        Stroke lineSparse = new BasicStroke(inlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, sparse, 0.0f);
        Stroke outlineSparse = new BasicStroke(outlineSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, sparse, 0.0f);

        Color primary = color.primaryColor();
        Color secondary = color.secondaryColor();
        if (secondary == null) secondary = primary;
        if (color.getName().equals("black"))
            primary = secondary = Color.darkGray;

        List<Point> corners = List.of(new Point(88, 2), new Point(256, 2), new Point(342, 149), new Point(256, 296), new Point(88, 296), new Point(2, 149));
        //corners.forEach(c -> c.translate(10, 10)); // offset by 10 pixels so that our border can slightly overlap the bounds of the hex

        // Draw outlines
        g2.setColor(Color.BLACK);
        for (int i = 0; i < 6; i++) {
            if (openSides.contains(i)) g2.setStroke(outlineSparse);
            if (!openSides.contains(i)) g2.setStroke(outline);
            Point c1 = corners.get(i);
            Point c2 = corners.get((i + 1) % 6);
            g2.drawLine(c1.x, c1.y, c2.x, c2.y);
            g2.drawLine(c2.x, c2.y, c1.x, c1.y);
        }

        // Draw Real Colors
        g2.setStroke(line);
        for (int i = 0; i < 6; i++) {
            if (openSides.contains(i)) g2.setStroke(lineSparse);
            if (!openSides.contains(i)) g2.setStroke(line);

            Point c1 = corners.get(i);
            Point c2 = corners.get((i + 1) % 6);

            GradientPaint gpOne = null, gpTwo = null;
            if (rainbow) { // special handling for rainbow
                Point mid = new Point((c1.x + c2.x) / 2, (c1.y + c2.y) / 2);
                if (i % 2 == 0) {
                    gpOne = new GradientPaint(c1, Color.red, mid, Color.yellow);
                    gpTwo = new GradientPaint(mid, Color.yellow, c2, Color.green);
                } else {
                    gpOne = new GradientPaint(c1, Color.green, mid, Color.blue);
                    gpTwo = new GradientPaint(mid, Color.blue, c2, Color.red);
                }
            } else {
                if (i % 2 == 0) {
                    gpOne = new GradientPaint(c1, primary, c2, secondary);
                } else {
                    gpOne = new GradientPaint(c1, secondary, c2, primary);
                }
            }

            // Draw lines both directions so the dash is symmetrical
            g2.setPaint(gpOne);
            g2.drawLine(c1.x, c1.y, c2.x, c2.y);
            g2.setPaint(gpTwo);
            g2.drawLine(c2.x, c2.y, c1.x, c1.y);
        }

        return img;
    }

    private BufferedImage hexBorderCache(ColorModel color, List<Integer> openSides) {
        String style = game.getHexBorderStyle();
        // don't cache these, it's not worth it
        if (openSides.size() > 2) return hexBorder(color, openSides, style.equals("solid"));

        // Otherwise, cache it
        Function<String, BufferedImage> loader = (name) -> hexBorder(color, openSides, style.equals("solid"));
        Collections.sort(openSides);
        String key = color.getName() + "-HexBorder-" + style;
        for (int x : openSides)
            key += "_" + x;
        return ImageHelper.createOrLoadCalculatedImage(key, loader);
    }

    private void paintPlayerInfo(Game game, Player player, List<String> statTiles) {
        boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
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
            BufferedImage hex = hexBorderCache(playerColor, adjDir);
            graphics.drawImage(hex, p.x, p.y, null);
        }

        Point miscTile; // To be used for speaker and other stuff
        Point point, tile = statTileMid;
        HorizontalAlign center = HorizontalAlign.Center;
        VerticalAlign bottom = VerticalAlign.Bottom;
        { // PAINT FACTION ICON
            point = PositionMapper.getPlayerStats("factionicon");
            int size = 275;
            point.translate(statTileMid.x - (size / 2), statTileMid.y - (size / 2));
            drawPlayerFactionIconImageOpaque(graphics, player, point.x, point.y, size, size, 0.40f);
        }

        { // PAINT USERNAME
            graphics.setFont(Storage.getFont32());
            String userName = player.getUserName();
            point = PositionMapper.getPlayerStats("newuserName");
            if (!Boolean.parseBoolean(game.getFowOption(FOWOptions.HIDE_NAMES))) {
                String name = userName.substring(0, Math.min(userName.length(), 15));
                superDrawString(graphics, name, tile.x + point.x, tile.y + point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
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
            superDrawString(graphics, factionText, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);

            BufferedImage img = ImageHelper.readEmojiImageScaled(Emojis.getColorEmoji(player.getColor()), 30);
            int offset = graphics.getFontMetrics().stringWidth(factionText) / 2 + 10;
            point.translate(0, -25);
            graphics.drawImage(img, point.x - offset - 30, point.y, null);
            graphics.drawImage(img, point.x + offset, point.y, null);
        }

        { // PAINT VICTORY POINTS
            graphics.setFont(Storage.getFont32());
            String vpCount = "VP: " + player.getTotalVictoryPoints() + " / " + game.getVp();
            point = PositionMapper.getPlayerStats("newvp");
            point.translate(statTileMid.x, statTileMid.y);
            superDrawString(graphics, vpCount, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
        }

        { // PAINT SO ICONS
            List<String> soToPoList = game.getSoToPoList();
            int unscoredSOs = player.getSecrets().keySet().size();
            int scoredSOs = (int) player.getSecretsScored().keySet().stream().filter(so -> !soToPoList.contains(so)).count();
            int secretsEmpty = player.getMaxSOCount() - unscoredSOs - scoredSOs;
            int soOffset = (15 + 35 * player.getMaxSOCount()) / 2 - 50;

            String soHand = "pa_so-icon_hand.png";
            String soScored = "pa_so-icon_scored.png";
            String soEmpty = "pa_so-icon_empty.png";
            point = PositionMapper.getPlayerStats("newso");
            for (int i = 0; i < secretsEmpty; i++) {
                drawPAImage((point.x + tile.x + soOffset), point.y + tile.y, soEmpty);
                soOffset -= 35;
            }
            for (int i = 0; i < unscoredSOs; i++) {
                drawPAImage((point.x + tile.x + soOffset), point.y + tile.y, soHand);
                soOffset -= 35;
            }
            for (int i = 0; i < scoredSOs; i++) {
                drawPAImage((point.x + tile.x + soOffset), point.y + tile.y, soScored);
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
                if (sc == 0) {
                    drawPAImageScaled(point.x, point.y, "pa_telepathic.png", scsize);
                    point.translate(scsize, 0);
                } else {
                    int fontYoffset = (scsize / 2) + 25;
                    superDrawString(graphics, Integer.toString(sc), point.x, point.y + fontYoffset, getSCColor(sc, game), center, bottom, stroke6, Color.BLACK);
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

            drawCCOfPlayer(graphics, ccID, point.x, point.y, player.getTacticalCC(), player, false, rightAlign);
            drawFleetCCOfPlayer(graphics, fleetCCID, point.x, point.y + 65, player, rightAlign);
            drawCCOfPlayer(graphics, ccID, point.x, point.y + 130, player.getStrategicCC(), player, false, rightAlign);

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
            String fleetCCs = Integer.toString(player.getFleetCC() + additionalFleetSupply) + addFS;
            superDrawString(graphics, reps.get(0), point.x, point.y, Color.WHITE, align, null, stroke4, Color.BLACK);
            superDrawString(graphics, fleetCCs, point.x, point.y + 65, Color.WHITE, align, null, stroke4, Color.BLACK);
            superDrawString(graphics, reps.get(2), point.x, point.y + 130, Color.WHITE, align, null, stroke4, Color.BLACK);
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
                            miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1),
                            miscTile.y + (300 - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1) + (player.isSpeaker() ? 30 : 0),
                            null);
                    }
                } else {
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                        null);
                }
            }
            if (hasNanoForge) {
                String relicFile = ResourceHelper.getInstance().getGeneralFile("Relic.png");
                BufferedImage bufferedImage = ImageHelper.read(relicFile);
                if (offBoardHighlighting >= 1) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / (offBoardHighlighting + (hasNanoForge ? 1 : 0)) / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 + 30,
                        miscTile.y + (300 - bufferedImage.getHeight()) / 2 + 30 + (player.isSpeaker() ? 30 : 0),
                        null);
                } else {
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                        null);
                }
                offBoardHighlighting++;
            }
        } else if (displayType == DisplayType.empties) {
            boolean hasStellar = player.hasRelic("stellarconverter") || player.hasRelic("absol_stellarconverter");
            String relicFile = ResourceHelper.getInstance().getGeneralFile("Relic.png");
            boolean hasHero = player.hasLeaderUnlocked("muaathero") || player.hasLeaderUnlocked("zelianhero");
            String heroFile = ResourceHelper.getInstance().getResourceFromFolder("emojis/leaders/", "Hero.png", "Could not find command token file");
            if (player.hasLeaderUnlocked("muaathero")) {
                heroFile = ResourceHelper.getInstance().getResourceFromFolder("emojis/leaders/pok/Emoji Farm 4/", "MuaatHero.png", "Could not find command token file");
            }
            BufferedImage bufferedImage;
            if (hasStellar && hasHero) {
                bufferedImage = ImageHelper.read(relicFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(17000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 - 30 + (player.isSpeaker() ? 30 : 0),
                    null);
                bufferedImage = ImageHelper.read(heroFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(17000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2 + 30,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 + 30 + (player.isSpeaker() ? 30 : 0),
                    null);
                offBoardHighlighting += 2;
            } else if (hasStellar) {
                bufferedImage = ImageHelper.read(relicFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                    null);
                offBoardHighlighting++;
            } else if (hasHero) {
                bufferedImage = ImageHelper.read(heroFile);
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
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
            int x = miscTile.x + (345 - 80) / 2;
            x += (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 30 : 0);
            int y = miscTile.y + (300 - 80) / 2 + (player.isSpeaker() ? 30 : 0);
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
        } else if (displayType == DisplayType.anomalies && (player.hasTech("dt2") || player.getUnitsOwned().contains("cabal_spacedock"))) {
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
                int x = miscTile.x + (345 - 95) / 2;
                x += (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 30 : 0);
                int y = miscTile.y + (300 - 95) / 2 + (player.isSpeaker() ? 30 : 0);
                String tokenFile = Mapper.getTokenPath("token_gravityrift.png");
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                for (int i = 0; i < unitNum; i++) {
                    graphics.drawImage(bufferedImage, x, y, null);
                    x -= (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 60 : 0);
                }
                offBoardHighlighting += unitNum;
            }
        } else if (displayType == DisplayType.traits) {
            List<String> traitFiles = new ArrayList<String>();
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    String traitFile = "";
                    List<String> traits = planetReal.getPlanetType();

                    if (planetReal.getOriginalPlanetType().equals("faction") && traits.size() == 0) {
                        if (custodiaVigilia.getFactionHomeworld() == null) {
                            traitFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                        } else {
                            traitFile = ResourceHelper.getInstance().getFactionFile(custodiaVigilia.getFactionHomeworld() + ".png");
                        }
                    } else if (traits.size() == 1) {
                        String t = planetReal.getPlanetType().get(0);
                        traitFile = ResourceHelper.getInstance().getGeneralFile(("" + t.charAt(0)).toUpperCase() + t.substring(1).toLowerCase() + ".png");
                    } else if (traits.size() == 0) {
                    } else {
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
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                        miscTile.y + (300 - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                        null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(traitFiles.get(0));
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                    null);
            }
        } else if (displayType == DisplayType.techskips) {
            List<String> techFiles = new ArrayList<String>();
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    List<String> skips = planetReal.getTechSpeciality();
                    skips.removeAll(Collections.singleton(null));
                    skips.removeAll(Collections.singleton(""));
                    if (skips.size() == 0) {
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
                        miscTile.y + (300 - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                        null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(techFiles.get(0));
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                    null);
            }
        } else if (displayType == DisplayType.attachments) {
            Map<String, String> attachFiles = new HashMap<>();
            Map<String, Integer> attachCount = new HashMap<>();
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    List<String> attach = new ArrayList<String>(planetReal.getAttachments());
                    attach.removeAll(Collections.singleton(null));
                    attach.removeAll(Collections.singleton(""));
                    if (attach.size() == 0) {
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
                int i = 0;
                for (String planet : attachFiles.keySet()) {
                    BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                        miscTile.y + (300 - bufferedImage.getHeight()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                        null);
                    if (attachCount.get(planet) > 1) {
                        graphics.setColor(Color.WHITE);
                        graphics.fillOval(
                            miscTile.x + (345 - 80) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y + (300 - 16) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                            80, 80);
                        graphics.setColor(Color.BLACK);
                        graphics.fillOval(
                            miscTile.x + (345 - 72) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y + (300 - 16) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0) + 4,
                            72, 72);
                        graphics.setColor(Color.WHITE);
                        drawCenteredString(graphics, "" + attachCount.get(planet),
                            new Rectangle(
                                miscTile.x + (345 - 80) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                                miscTile.y + (300 - 16) / 2 - 30 + i * 60 / (offBoardHighlighting - 1) + (player.isSpeaker() ? 30 : 0),
                                80, 80),
                            Storage.getFont48());
                    }
                }
                i++;
            } else if (offBoardHighlighting == 1) {
                String planet = attachFiles.keySet().iterator().next();

                BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y + (300 - bufferedImage.getHeight()) / 2 + (player.isSpeaker() ? 30 : 0),
                    null);
                if (attachCount.get(planet) > 1) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillOval(
                        miscTile.x + (345 - 80) / 2,
                        miscTile.y + (300 - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                        80, 80);
                    graphics.setColor(Color.BLACK);
                    graphics.fillOval(
                        miscTile.x + (345 - 72) / 2,
                        miscTile.y + (300 - 16) / 2 + (player.isSpeaker() ? 30 : 0) + 4,
                        72, 72);
                    graphics.setColor(Color.WHITE);
                    drawCenteredString(graphics, "" + attachCount.get(planet),
                        new Rectangle(
                            miscTile.x + (345 - 80) / 2,
                            miscTile.y + (300 - 16) / 2 + (player.isSpeaker() ? 30 : 0),
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
                superDrawString(graphics, "PASSED", point.x, point.y, PassedColor, center, null, stroke4, Color.BLACK);
            } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
                point = PositionMapper.getPlayerStats("newpassed");
                point.translate(miscTile.x, miscTile.y);
                superDrawString(graphics, "ACTIVE", point.x, point.y, ActiveColor, center, null, stroke4, Color.BLACK);
            }
            if (player.isAFK()) {
                point = PositionMapper.getPlayerStats("newafk");
                point.translate(miscTile.x, miscTile.y);
                superDrawString(graphics, "AFK", point.x, point.y, Color.gray, center, null, stroke4, Color.BLACK);
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
                } else
                    return;
            } else
                return;
        } else
            return;

        String userName = player.getUserName();

        boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate
            && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
        if (convertToGeneric) {
            return;
        }

        // PAINT USERNAME
        Point point = PositionMapper.getPlayerStats(Constants.STATS_USERNAME);
        if (!Boolean.parseBoolean(game.getFowOption(FOWOptions.HIDE_NAMES))) {
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

        drawCCOfPlayer(graphics, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player, false);
        drawFleetCCOfPlayer(graphics, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player);
        drawCCOfPlayer(graphics, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), player, false);

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

    private static void drawCCOfPlayer(Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon) {
        drawCCOfPlayer(graphics, ccID, x, y, ccCount, player, hideFactionIcon, true);
    }

    private static void drawCCOfPlayer(Graphics graphics, String ccID, int x, int y, int ccCount, Player player, boolean hideFactionIcon, boolean rightAlign) {
        String ccPath = Mapper.getCCPath(ccID);
        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            BufferedImage blankCC = ImageHelper.read(Mapper.getCCPath("command_blank.png"));

            BufferedImage factionImage = null;
            int centreCustomTokenHorizontally = 0;
            if (!hideFactionIcon) {
                factionImage = getPlayerFactionIconImageScaled(player, 45, 45);
                if (factionImage == null) {
                    hideFactionIcon = true;
                } else {
                    centreCustomTokenHorizontally = ccImage != null ? ccImage.getWidth() / 2 - factionImage.getWidth() / 2 : 0;
                }
            }

            int delta = rightAlign ? -20 : 20;
            if (ccCount == 0) {
                ccCount = 1;
                ccImage = blankCC;
                hideFactionIcon = true;
            }
            for (int i = 0; i < ccCount; i++) {
                graphics.drawImage(ccImage, x + (delta * i), y, null);
                if (!hideFactionIcon)
                    graphics.drawImage(factionImage, x + (delta * i) + centreCustomTokenHorizontally, y + DELTA_Y, null);
            }
        } catch (Exception e) {
            String gameName = "";
            if (player == null) gameName = "Null Player";
            if (player != null && player.getGame() == null) gameName = "Null Game";
            if (player != null && player.getGame() != null) gameName = player.getGame().getName();
            BotLogger.log("Ignored error during map generation for `" + gameName + "`", e);
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
                if (hasArmada || ccCount >= 1)
                    x += delta;
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
        Map<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>(game.getScoredPublicObjectives());
        Map<String, Integer> revealedPublicObjectives = new LinkedHashMap<>(game.getRevealedPublicObjectives());
        Map<String, Player> players = game.getPlayers();

        graphics.setFont(Storage.getFont26());

        int spacingBetweenObjectiveTypes = 10;
        int maxObjWidth = (mapWidth - spacingBetweenObjectiveTypes * 4) / 3;
        int maxYFound = 0;

        int top = y;
        int left = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
        int boxWidth = 0;
        // STAGE 1
        Map<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        Set<String> po1 = publicObjectivesState1.keySet();
        graphics.setColor(Stage1RevealedColor);
        Coord coord = coord(left, y);
        coord = displayObjectives(top, coord.x, maxObjWidth, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState1, po1, 1, null);
        boxWidth = coord.x;
        graphics.setColor(Stage1HiddenColor);
        int y1b = displayUnrevealedObjectives(coord.y, left, boxWidth, game.getPublicObjectives1Peakable(), 1, game);
        maxYFound = Math.max(maxYFound, y1b);

        // STAGE 2
        Map<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        Set<String> po2 = publicObjectivesState2.keySet();
        left += coord.x + spacingBetweenObjectiveTypes;
        coord = coord(left, top);
        graphics.setColor(Stage2RevealedColor);
        coord = displayObjectives(top, left, maxObjWidth, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState2, po2, 2, null);
        boxWidth = coord.x;
        graphics.setColor(Stage2HiddenColor);
        int y2b = displayUnrevealedObjectives(coord.y, left, boxWidth, game.getPublicObjectives2Peakable(), 2, game);
        maxYFound = Math.max(maxYFound, y2b);

        // CUSTOM
        Map<String, Integer> customPublicVP = game.getCustomPublicVP();
        Map<String, String> customPublics = customPublicVP.keySet().stream()
            .collect(Collectors.toMap(key -> key, name -> {
                name = name.replace("extra1", "");
                name = name.replace("extra2", "");
                String nameOfPO = Mapper.getSecretObjectivesJustNames().get(name);
                return nameOfPO != null ? nameOfPO : name;
            }, (key1, key2) -> key1, LinkedHashMap::new));
        Set<String> customVP = customPublicVP.keySet();
        left += coord.x + spacingBetweenObjectiveTypes;
        coord = coord(left, top);
        graphics.setColor(Color.WHITE);
        coord = displayObjectives(top, coord.x, maxObjWidth, scoredPublicObjectives, revealedPublicObjectives, players, customPublics, customVP, null, customPublicVP);
        maxYFound = Math.max(maxYFound, coord.y) + 15;
        return maxYFound;
    }

    private int laws(int y) {
        if (displayTypeBasic == displayType.map) {
            return y;
        }
        int x = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
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

            graphics.drawRect(x, y, 1178, 110);
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
                            if (isFoWPrivate != null && isFoWPrivate
                                && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)) {
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
                if (!game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue()).isEmpty()) {
                    int tokenDeltaY = 0;
                    int count = 0;
                    for (String debtToken : game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue()).split("_")) {

                        boolean hideFactionIcon = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, game.getPlayerFromColorOrFaction(debtToken), fowPlayer);
                        String controlID = hideFactionIcon ? Mapper.getControlID("gray") : Mapper.getControlID(debtToken);
                        if (controlID.contains("null")) {
                            continue;
                        }
                        float scale = 0.80f;
                        BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                        drawControlToken(graphics, controlTokenImage,
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
                x += 1178 + 8;
            } else {
                secondColumn = false;
                y += 112;
                x = 5 + (displayType == DisplayType.landscape ? mapWidth : 0);
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
            y = displaySecretObjectives(y, x, new LinkedHashMap<>(), revealedSecrets, players, secretObjectives, secret, 0, customPublicVP);
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
        y = displaySecretObjectives(y, x, scoredSecretObjectives, revealedSecretObjectives, players, secretObjectives, secret, 1, customPublicVP);
        if (player.isSearchWarrant()) {
            return secretsScored.keySet().size() + player.getSecrets().keySet().size();
        }
        return secretsScored.keySet().size();
    }

    private Coord displayObjectives(
        int y,
        int x,
        int maximumBoxWidth,
        Map<String, List<String>> scoredObjectives,
        Map<String, Integer> revealedObjectives,
        Map<String, Player> players,
        Map<String, String> publicObjectivesState,
        Set<String> po,
        Integer objectiveWorth,
        Map<String, Integer> customPublicVP) {

        Set<String> keysToRemove = new HashSet<>();
        int objectiveBoxHeight = 38;
        int spacingBetweenBoxes = 5;
        int bufferBetweenTextAndTokens = 15;
        int playerCount = players.size();
        int minimumBoxWidth = 400;
        if (game.isRedTapeMode()) { //TODO: move and handle method displayUnrevealedObjectives into this method and calc boxWidth properly
            minimumBoxWidth += 400;
        }

        int maxTextWidth = 0;

        // BUILD KEY / TEXT PAIRS & CALC LONGEST STRING
        Map<String, String> objectivesKeyDisplayName = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> revealed : revealedObjectives.entrySet()) {
            String key = revealed.getKey();
            if (!po.contains(key)) {
                continue;
            }
            String name = publicObjectivesState.get(key);
            Integer index = revealedObjectives.get(key);
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
            String text = "(" + index + ") " + name + " - " + objectiveWorth + " VP";
            int textWidth = graphics.getFontMetrics().stringWidth(text);
            maxTextWidth = Math.max(maxTextWidth, textWidth);
            objectivesKeyDisplayName.put(key, text);
        }

        int maxLengthOfAllTokens = maxTextWidth + bufferBetweenTextAndTokens * 2 + playerCount * scoreTokenWidth;
        int boxWidth = Math.max(minimumBoxWidth, Math.min(maximumBoxWidth, maxLengthOfAllTokens));

        // DRAW STRINGS & BOXES
        for (Map.Entry<String, String> revealed : objectivesKeyDisplayName.entrySet()) {
            String key = revealed.getKey();
            String text = revealed.getValue();
            graphics.drawString(text, x, y + 23);

            List<String> scoredPlayerID = scoredObjectives.get(key);
            boolean multiScoring = Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key) || (isFoWPrivate != null && isFoWPrivate);
            if (scoredPlayerID != null) {
                int tokenX = x + maxTextWidth + bufferBetweenTextAndTokens;
                int tokenY = y;
                drawScoreControlMarkers(tokenX, tokenY, players, scoredPlayerID, multiScoring);
            }
            graphics.drawRect(x - 4, y - spacingBetweenBoxes, boxWidth, objectiveBoxHeight);

            y += objectiveBoxHeight + spacingBetweenBoxes;
        }
        keysToRemove.forEach(revealedObjectives::remove);

        return coord(boxWidth, y);
    }

    private int displaySecretObjectives(
        int y,
        int x,
        Map<String, List<String>> scoredPublicObjectives,
        Map<String, Integer> revealedPublicObjectives,
        Map<String, Player> players,
        Map<String, String> publicObjectivesState,
        Set<String> po,
        Integer objectiveWorth,
        Map<String, Integer> customPublicVP) {

        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            x = 50;

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

            graphics.drawString("(" + index + ") " + name, x, y + 23);

            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            if (scoredPlayerID != null) {
                drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, false, true);
            }
            graphics.drawRect(x - 4, y - 5, 600, 38);

            y += 43;
        }
        keysToRemove.forEach(revealedPublicObjectives::remove);

        return y;
    }

    private int displayUnrevealedObjectives(
        int y,
        int x,
        int boxWidth,
        List<String> unrevealedPublicObjectives,
        Integer objectiveWorth,
        Game game) {

        Integer index = 1;

        for (String unRevealed : unrevealedPublicObjectives) {

            String name = Mapper.getPublicObjective(unRevealed).getName();

            if (game.isRedTapeMode()) {
                graphics.drawString(String.format("(%d) <Unrevealed> %s - %d VP", index, name, objectiveWorth), x, y + 23);
            } else {
                graphics.drawString(String.format("(%d) <Unrevealed> - %d VP", index, objectiveWorth), x, y + 23);
            }

            graphics.drawRect(x - 4, y - 5, boxWidth, 38);

            if (game.getPublicObjectives1Peeked().containsKey(unRevealed) || game.getPublicObjectives2Peeked().containsKey(unRevealed)) {
                List<String> playerIDs;
                if (game.getPublicObjectives1Peeked().containsKey(unRevealed)) {
                    playerIDs = game.getPublicObjectives1Peeked().get(unRevealed);
                } else {
                    playerIDs = game.getPublicObjectives2Peeked().get(unRevealed);
                }
                drawPeekedMarkers(x + 515, y, game.getPlayers(), playerIDs);
            }

            y += 43;
            index++;
        }
        return y;
    }

    private void drawPeekedMarkers(int x, int y, Map<String, Player> players, List<String> peekedPlayerID) {
        try {
            int tempX = 0;

            for (Map.Entry<String, Player> playerEntry : players.entrySet()) {
                Player player = playerEntry.getValue();
                String userID = player.getUserID();

                if (peekedPlayerID.contains(userID)) {
                    boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer);
                    String ccColor = convertToGeneric ? "gray" : player.getColor();
                    String controlID = Mapper.getControlID(ccColor);

                    if (controlID.contains("null")) {
                        continue;
                    }

                    float scale = 0.55f;

                    BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                    if (controlTokenImage == null)
                        return;

                    String peekID = "peek" + getBlackWhiteFileSuffix(Mapper.getColorID(ccColor));
                    BufferedImage peekMarkerImage = ImageHelper.readScaled(Mapper.getPeekMarkerPath(peekID), scale);
                    if (peekMarkerImage == null)
                        return;

                    drawControlToken(graphics, controlTokenImage, player, x + tempX, y, true, scale);

                    int centreCustomTokenHorizontally = controlTokenImage.getWidth() / 2 - peekMarkerImage.getWidth() / 2;
                    int centreCustomTokenVertically = controlTokenImage.getHeight() / 2 - peekMarkerImage.getHeight() / 2;

                    graphics.drawImage(peekMarkerImage, x + centreCustomTokenHorizontally + tempX, y + centreCustomTokenVertically, null);
                }

                // This needs to be updated regardless if the player peeked or not to maintain marker alignment with PO score markers.
                tempX += scoreTokenWidth;
            }
        } catch (Exception e) {
            BotLogger.log("Could not draw peek markers", e);
        }
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
            BotLogger.log("Error drawing score control token markers", e);
        }
    }

    private static Player getPlayerByControlMarker(Iterable<Player> players, String controlID) {
        Player player = null;
        for (Player player_ : players) {
            if (player_.getColor() != null && player_.getFaction() != null) {
                String playerControlMarker = Mapper.getControlID(player_.getColor());
                String playerCC = Mapper.getCCID(player_.getColor());
                String playerSweep = Mapper.getSweepID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC)
                    || controlID.equals(playerSweep)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
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

    enum TileStep {
        Setup, Tile, Extras, Units, Distance, Wormholes, Anomalies, Aetherstream, Legendaries, Empties, SpaceCannon, Traits, TechSkips, Attachments
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
                Point wormholeLocation = TileHelper.getAllTiles().get(tile.getTileID()).getShipPositionsType().getWormholeLocation();
                if (wormholeLocation == null) {
                    graphics.drawImage(icon, TILE_PADDING + offset + 86, TILE_PADDING + 260, null);
                } else {
                    graphics.drawImage(icon, TILE_PADDING + offset + wormholeLocation.x, TILE_PADDING + offset + wormholeLocation.y, null);
                }

        }
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
                    int offset = 0;
                    switch (TileHelper.getAllTiles().get(tile.getTileID()).getShipPositionsType().toString().toUpperCase()) {
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
                Player controllingPlayer = playerControlMap.get(tile.getPosition());
                boolean fow = isFrogPrivate != null && isFrogPrivate;
                if (!game.getHexBorderStyle().equals("off") && controllingPlayer != null && !tile.getTileModel().getShipPositionsType().isSpiral()) {
                    int sideNum = 0;
                    List<Integer> openSides = new ArrayList<>();
                    for (String adj : PositionMapper.getAdjacentTilePositions(tile.getPosition())) {
                        if (playerControlMap.get(adj) == controllingPlayer) {
                            openSides.add(sideNum);
                        }
                        sideNum++;
                    }
                    if (fow && this.fowPlayer == null) openSides.clear();
                    BufferedImage border = hexBorderCache(Mapper.getColor(controllingPlayer.getColor()), openSides);
                    tileGraphics.drawImage(border, TILE_PADDING, TILE_PADDING, null);
                }

                if ("large".equals(game.getTextSize())) {
                    tileGraphics.setFont(Storage.getFont40());
                } else if ("medium".equals(game.getTextSize())) {
                    tileGraphics.setFont(Storage.getFont30());
                } else if ("tiny".equals(game.getTextSize())) {
                    tileGraphics.setFont(Storage.getFont12());
                } else { // "small"
                    tileGraphics.setFont(Storage.getFont20());
                }

                if (isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer)) {
                    BufferedImage frogOfWar = ImageHelper.read(tile.getFowTilePath(frogPlayer));
                    tileGraphics.drawImage(frogOfWar, TILE_PADDING, TILE_PADDING, null);
                    int labelX = TILE_PADDING + labelPositionPoint.x;
                    int labelY = TILE_PADDING + labelPositionPoint.y;
                    superDrawString(tileGraphics, tile.getFogLabel(frogPlayer), labelX, labelY, Color.WHITE, null, null, null, null);
                }

                int textX = TILE_PADDING + tilePositionPoint.x;
                int textY = TILE_PADDING + tilePositionPoint.y;
                superDrawString(tileGraphics, tile.getPosition(), textX, textY, Color.WHITE, HorizontalAlign.Right, VerticalAlign.Bottom, stroke7, Color.BLACK);

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
                        superDrawString(tileGraphics, draftNum, numX, numY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Center, stroke8, Color.BLACK);
                    }
                    tileGraphics.setFont(Storage.getFont24());
                    int numX = TILE_PADDING + 172; //172 //320
                    int numY = TILE_PADDING + 228; //50  //161
                    superDrawString(tileGraphics, draftColor, numX, numY, Color.WHITE, HorizontalAlign.Center, VerticalAlign.Bottom, stroke6, Color.BLACK);
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
                    if (TileHelper.getAllTiles().get(tile.getTileID()).getShipPositionsType().isSpiral()) {
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
                if (isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer))
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
                if (isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer))
                    return tileOutput;

                List<Rectangle> rectangles = new ArrayList<>();
                Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
                UnitHolder spaceUnitHolder = unitHolders.stream()
                    .filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);

                if (spaceUnitHolder != null) {
                    addSleeperToken(tile, tileGraphics, spaceUnitHolder, MapGenerator::isValidCustodianToken, game);
                    addToken(tile, tileGraphics, spaceUnitHolder, game);
                    unitHolders.remove(spaceUnitHolder);
                    unitHolders.add(spaceUnitHolder);
                }
                int prodInSystem = 0;
                for (Player player : game.getRealPlayers()) {
                    prodInSystem = Math.max(prodInSystem, Helper.getProductionValue(player, game, tile, false));
                }
                for (UnitHolder unitHolder : unitHolders) {
                    addSleeperToken(tile, tileGraphics, unitHolder, MapGenerator::isValidToken, game);
                    addControl(tile, tileGraphics, unitHolder, rectangles, frogPlayer, isFrogPrivate);
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
                    List<String> problematicTiles = List.of("25", "26", "64"); // quann, lodor, atlas
                    BufferedImage gearImage = ImageHelper.readScaled(ResourceHelper.getInstance().getTileFile("production_representation.png"), 64, 64);
                    if (tile.getUnitHolders().size() != 4 || problematicTiles.contains(tile.getTileID())) {
                        int xMod = -15;
                        int yMod = -290;
                        tileGraphics.drawImage(gearImage, TILE_PADDING + tilePositionPoint.x + xMod - 29, TILE_PADDING + tilePositionPoint.y + yMod - 4, null);
                        tileGraphics.setFont(Storage.getFont35());
                        tileGraphics.drawString(prodInSystem + "", TILE_PADDING + tilePositionPoint.x + xMod + 15 + textModifer - 25, TILE_PADDING + tilePositionPoint.y + yMod + 40);
                    } else {
                        int xMod = -155;
                        int yMod = -290;
                        tileGraphics.drawImage(gearImage, TILE_PADDING + tilePositionPoint.x + xMod - 29, TILE_PADDING + tilePositionPoint.y + yMod - 4, null);
                        tileGraphics.setFont(Storage.getFont35());
                        tileGraphics.drawString(prodInSystem + "", TILE_PADDING + tilePositionPoint.x + xMod + 15 + textModifer - 25, TILE_PADDING + tilePositionPoint.y + yMod + 40);
                    }
                }

                if (spaceUnitHolder != null) {
                    addCC(tile, tileGraphics, spaceUnitHolder, frogPlayer, isFrogPrivate);
                }
                int degree = 180;
                int degreeChange = 5;
                for (UnitHolder unitHolder : unitHolders) {
                    int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS
                        : Constants.RADIUS;
                    if (unitHolder != spaceUnitHolder) {
                        addPlanetToken(tile, tileGraphics, unitHolder, rectangles);
                    }
                    addUnits(tile, tileGraphics, rectangles, degree, degreeChange, unitHolder, radius, frogPlayer);
                }
            }
            case Distance -> {
                if (game.isFowMode()) {
                    break;
                }
                Integer distance = game.getTileDistances().get(tile.getPosition());
                if (distance == null) {
                    break;
                }

                BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
                if (tileImage == null) {
                    break;
                }

                int x = TILE_PADDING + (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                int y = TILE_PADDING + (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);

                BufferedImage distanceColor = ImageHelper
                    .read(ResourceHelper.getInstance().getTileFile(getColorFilterForDistance(distance)));
                tileGraphics.drawImage(distanceColor, x, y, null);
                tileGraphics.setColor(Color.WHITE);
                drawCenteredString(tileGraphics, distance.toString(),
                    new Rectangle(TILE_PADDING, TILE_PADDING, tileImage.getWidth(), tileImage.getHeight()),
                    Storage.getFont100());
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
                    x += (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                    y += (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);
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
                    x += (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                    y += (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);
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
                    x += (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                    y += (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);
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
                    x += (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                    y += (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);
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
                boolean isEmpty = tile.getPlanetUnitHolders().size() == 0;

                if (!isEmpty) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    Graphics graph = tileImage.getGraphics();
                    x += (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                    y += (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);
                    x += 93;
                    y += 70;
                    tileGraphics.setColor(Color.RED);
                    tileGraphics.fillOval(x, y, 159, 159);
                    tileGraphics.setColor(Color.WHITE);
                    drawCenteredString(tileGraphics, "!", new Rectangle(x, y, 159, 159), Storage.getFont80());
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
                HashMap<Player, List<Integer>> pdsDice = new HashMap<Player, List<Integer>>();

                for (Player player : game.getRealPlayers()) {
                    List<Integer> diceCount = new ArrayList<Integer>();
                    List<Integer> diceCountMirveda = new ArrayList<Integer>();
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
                            for (Map.Entry<UnitKey, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                                if (unitEntry.getValue() == 0) {
                                    continue;
                                }

                                UnitKey unitKey = unitEntry.getKey();
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

                    if (diceCountMirveda.size() > 0) {
                        Collections.sort(diceCountMirveda);
                        diceCount.add(diceCountMirveda.get(0));
                    }

                    if (diceCount.size() > 0) {
                        Collections.sort(diceCount);
                        if (player.getTechs().contains("ps")) {
                            diceCount.add(0, diceCount.get(0));
                        }
                        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
                            diceCount.add(0, diceCount.get(0));
                        }
                        pdsDice.put(player, diceCount);
                    }
                }

                if (pdsDice.size() == 0) {
                    BufferedImage fogging = ImageHelper.read(tile.getFowTilePath(null));
                    tileGraphics.drawImage(fogging, x, y, null);
                } else {
                    x += (tile.getTileModel().getShipPositionsType().isSpiral() ? 36 : 0);
                    y += (tile.getTileModel().getShipPositionsType().isSpiral() ? 43 : 0);
                    float scale = pdsDice.size() >= 3 ? 6.0f / pdsDice.size() : 3.0f;

                    Font bigFont = Storage.getFont64();
                    Font smallFont = Storage.getFont32();
                    switch (pdsDice.size()) {
                        case 1:
                        case 2:
                            bigFont = Storage.getFont64();
                            smallFont = Storage.getFont32();
                            break;
                        case 3:
                            bigFont = Storage.getFont40();
                            smallFont = Storage.getFont20();
                            break;
                        case 4:
                            bigFont = Storage.getFont32();
                            smallFont = Storage.getFont16();
                            break;
                        case 5:
                            bigFont = Storage.getFont24();
                            smallFont = Storage.getFont12();
                            break;
                        default:
                            bigFont = Storage.getFont16();
                            smallFont = Storage.getFont8();
                            break;
                    }

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
                        if (getBlackWhiteFileSuffix(player.getColorID()).equals("_wht.png")) {
                            tileGraphics.setColor(Color.WHITE);
                        } else {
                            tileGraphics.setColor(Color.BLACK);
                        }
                        BufferedImage bufferedImage = ImageHelper.readScaled(Mapper.getCCPath(Mapper.getControlID(player.getColor())), scale);
                        drawControlToken(tileGraphics, bufferedImage, player, x, y, false, scale / 2);
                        drawCenteredString(tileGraphics, "" + numberOfDice + (rerolls ? "*" : ""),
                            new Rectangle(Math.round(x + 6 * scale), Math.round(y + 12 * scale), Math.round(61 * scale / 2), Math.round(24 * scale * 2 / 3)),
                            bigFont);
                        drawCenteredString(tileGraphics, "(" + expectedHits + ")",
                            new Rectangle(Math.round(x + 6 * scale), Math.round(y + 12 * scale + 24 * scale * 2 / 3), Math.round(61 * scale / 2), Math.round(24 * scale / 3)),
                            smallFont);
                        if (numberOfDice >= 5) {
                            drawCenteredString(tileGraphics, pdsDice.get(player).subList(0, numberOfDice / 3).stream().map(i -> i.toString()).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                            drawCenteredString(tileGraphics, pdsDice.get(player).subList(numberOfDice / 3, 2 * numberOfDice / 3).stream().map(i -> i.toString()).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale + 36 * scale / 3), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                            drawCenteredString(tileGraphics, pdsDice.get(player).subList(2 * numberOfDice / 3, numberOfDice).stream().map(i -> i.toString()).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 6 * scale + 36 * scale * 2 / 3), Math.round(73 * scale / 2), Math.round(36 * scale / 3)),
                                smallFont);
                        } else if (numberOfDice >= 3) {
                            drawCenteredString(tileGraphics, pdsDice.get(player).subList(0, numberOfDice / 2).stream().map(i -> i.toString()).collect(Collectors.joining(",")) + ",",
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 12 * scale), Math.round(73 * scale / 2), Math.round(24 * scale / 2)),
                                smallFont);
                            drawCenteredString(tileGraphics, pdsDice.get(player).subList(numberOfDice / 2, numberOfDice).stream().map(i -> i.toString()).collect(Collectors.joining(",")),
                                new Rectangle(Math.round(x + 73 * scale / 2), Math.round(y + 12 * scale + 24 * scale / 2), Math.round(73 * scale / 2), Math.round(24 * scale / 2)),
                                smallFont);
                        } else {
                            drawCenteredString(tileGraphics, pdsDice.get(player).stream().map(i -> i.toString()).collect(Collectors.joining(",")),
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

                int planets = tile.getPlanetUnitHolders().size();
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    String traitFile = "";
                    List<String> traits = planet.getPlanetType();
                    if (traits.size() == 0) {
                        traits.add(planet.getOriginalPlanetType());
                    }

                    if (tile.isMecatol()) {
                        traitFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
                    } else if (planet.getOriginalPlanetType().equals("faction")) {
                        traitFile = ResourceHelper.getInstance().getFactionFile(Mapper.getPlanet(planet.getName()).getFactionHomeworld() + ".png");
                    } else if (traits.size() == 1) {
                        String t = planet.getPlanetType().get(0);
                        traitFile = ResourceHelper.getInstance().getGeneralFile(("" + t.charAt(0)).toUpperCase() + t.substring(1).toLowerCase() + ".png");
                    } else if (traits.size() == 0) {
                    } else {
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

                    if (!traitFile.equals("")) {
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
                        String skipFile;
                        switch (skip.toLowerCase()) {
                            case "biotic":
                                skipFile = ResourceHelper.getInstance().getGeneralFile("Biotic light.png");
                                break;
                            case "cybernetic":
                                skipFile = ResourceHelper.getInstance().getGeneralFile("Cybernetic light.png");
                                break;
                            case "propulsion":
                                skipFile = ResourceHelper.getInstance().getGeneralFile("Propulsion_light.png");
                                break;
                            case "warfare":
                                skipFile = ResourceHelper.getInstance().getGeneralFile("Warfare_light.png");
                                break;
                            default:
                                skipFile = ResourceHelper.getInstance().getGeneralFile("Generic_Technology.png");
                        }

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
                    List<String> attachments = new ArrayList<String>(planet.getAttachments());
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
                        drawCenteredString(tileGraphics, "" + number,
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
            BotLogger.log("Could not find border decoration image! Decoration was " + decorationType.toString());
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

    private void addCC(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Player frogPlayer,
        Boolean isFrogPrivate) {
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
                boolean convertToGeneric = isFrogPrivate != null && isFrogPrivate
                    && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);

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
                BotLogger.log("Could not addCC", ignored);
            }

            if (image != null) {
                deltaX += image.getWidth() / 5;
                deltaY += image.getHeight() / 4;
            }
        }
    }

    private void addControl(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, List<Rectangle> rectangles,
        Player frogPlayer, Boolean isFrogPrivate) {
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

                boolean convertToGeneric = isFrogPrivate != null && isFrogPrivate
                    && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);

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
                    drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(
                        new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    if (player != null && player.isRealPlayer() && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                        BufferedImage exhaustedTokenImage = ImageHelper.readScaled(ResourceHelper.getInstance().getResourceFromFolder("command_token/", "exhaustedControl.png", "Could not find command token file"), scale);
                        drawControlToken(tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
                        rectangles.add(
                            new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    }

                } else {
                    int imgX = TILE_PADDING + centerPosition.x + xDelta;
                    int imgY = TILE_PADDING + centerPosition.y;
                    drawControlToken(tileGraphics, controlTokenImage, player, imgX, imgY, convertToGeneric, scale);
                    rectangles.add(
                        new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
                    if (player != null && player.isRealPlayer() && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                        BufferedImage exhaustedTokenImage = ImageHelper.readScaled(ResourceHelper.getInstance().getResourceFromFolder("command_token/", "exhaustedControl", "Could not find command token file"), scale);
                        drawControlToken(tileGraphics, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, scale);
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
        if (game.isShowBubbles() && unitHolder instanceof Planet planetHolder
            && shouldPlanetHaveShield(unitHolder, game)) {
            String tokenPath;
            switch (planetHolder.getContrastColor()) {
                case "orange":
                    tokenPath = ResourceHelper.getInstance().getTokenFile("token_planetaryShield_orange.png");
                    break;
                case "blue":
                default:
                    tokenPath = ResourceHelper.getInstance().getTokenFile("token_planetaryShield.png");
                    break;
            }
            float scale = 0.95f;
            if (Mapper.getPlanet(unitHolder.getName()).getLegendaryAbilityText() != null
                && !unitHolder.getName().equalsIgnoreCase("mirage") && !unitHolder.getName().equalsIgnoreCase("eko")
                && !unitHolder.getName().toLowerCase().contains("mallice")
                && !unitHolder.getName().equalsIgnoreCase("domna")) {
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
                    if (Mapper.getPlanet(unitHolder.getName()).getLegendaryAbilityText() != null
                        && !unitHolder.getName().equalsIgnoreCase("mirage") && !unitHolder.getName().equalsIgnoreCase("eko")
                        && !unitHolder.getName().toLowerCase().contains("mallice") // includes locked
                        && !unitHolder.getName().equalsIgnoreCase("domna")) {
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
                    scale = 0.5f; // didnt previous get changed for custodians
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

    private static boolean shouldPlanetHaveShield(UnitHolder unitHolder, Game game) {

        if (unitHolder.getTokenList().contains(Constants.WORLD_DESTROYED_PNG)) {
            return false;
        }

        Map<UnitKey, Integer> units = unitHolder.getUnits();

        if (ButtonHelper.isLawInPlay(game, "conventions")) {
            String planet = unitHolder.getName();
            if (ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural")) {
                return true;
            }
        }
        Map<UnitKey, Integer> planetUnits = new HashMap<>(units);
        for (Player player : game.getRealPlayers()) {
            for (Map.Entry<UnitKey, Integer> unitEntry : planetUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) {
                    continue;
                }
                if (unitModel.getPlanetaryShield()) {
                    if (unitModel.getBaseType().equalsIgnoreCase("mech") && ButtonHelper.isLawInPlay(game, "articles_war")) {
                        return false;
                    }
                    return true;
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
                    if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                        tileGraphics.drawImage(tokenImage,
                            TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                            TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
                    } else {
                        tileGraphics.drawImage(tokenImage,
                            TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2),
                            TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
                    }
                } else if (tokenPath.contains(Constants.DMZ_LARGE)) {
                    float scale = 0.3f;
                    if (Mapper.getPlanet(unitHolder.getName()).getLegendaryAbilityText() != null
                        && !unitHolder.getName().equalsIgnoreCase("mirage") && !unitHolder.getName().equalsIgnoreCase("eko")
                        && !unitHolder.getName().toLowerCase().contains("mallice") // includes locked
                        && !unitHolder.getName().equalsIgnoreCase("domna")) {
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
        int mirageDragX = Math.round((345 / 8 + TILE_PADDING) * (1 - mirageDragRatio));
        int mirageDragY = Math.round((3 * 300 / 4 + TILE_PADDING) * (1 - mirageDragRatio));
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

    private void addUnits(Tile tile, Graphics tileGraphics, List<Rectangle> rectangles, int degree, int degreeChange,
        UnitHolder unitHolder, int radius, Player frogPlayer) {
        BufferedImage unitImage;
        Map<UnitKey, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        Map<UnitKey, Integer> units = new LinkedHashMap<>();
        HashMap<String, Point> unitOffset = new HashMap<>();
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);
        if (isSpace && displayType == DisplayType.shipless) {
            return;
        }

        float mirageDragRatio = 2.0f / 3;
        int mirageDragX = Math.round((345 / 8 + TILE_PADDING) * (1 - mirageDragRatio));
        int mirageDragY = Math.round((3 * 300 / 4 + TILE_PADDING) * (1 - mirageDragRatio));
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
        boolean showJail = frogPlayer == null
            || (isCabalJail && FoWHelper.canSeeStatsOfFaction(game, "cabal", frogPlayer))
            || (isNekroJail && FoWHelper.canSeeStatsOfFaction(game, "nekro", frogPlayer))
            || (isYssarilJail && FoWHelper.canSeeStatsOfFaction(game, "yssaril", frogPlayer));

        Point unitOffsetValue = game.isAllianceMode() ? PositionMapper.getAllianceUnitOffset()
            : PositionMapper.getUnitOffset();
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
        Map<UnitKey, Integer> unitDamage = unitHolder.getUnitDamage();
        // float scaleOfUnit = 1.0f;
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            unitTokenPosition = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
        }
        BufferedImage dmgImage = ImageHelper.readScaled(Helper.getDamagePath(), 0.8f);

        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
        int multInf = 2;
        int multFF = 2;
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            if (unitKey != null && !Mapper.isValidColor(unitKey.getColor())) {
                continue;
            }
            Integer unitCount = unitEntry.getValue();

            if (isJail && frogPlayer != null) {
                String colorID = Mapper.getColorID(frogPlayer.getColor());
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
                    if ((p.ownsUnit("cabal_spacedock") || p.ownsUnit("cabal_spacedock2"))
                        && unitKey.getUnitType() == UnitType.Spacedock) {
                        unitPath = unitPath.replace("sd", "csd");
                    }
                    if (unitKey.getUnitType() == UnitType.TyrantsLament) {
                        unitPath = unitPath.replace("tyrantslament", "fs");
                        String name = "TyrantNew.png";
                        unitPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);
                        // spoopy = ImageHelper.read(spoopyPath);
                    }
                    if (unitKey.getUnitType() == UnitType.Lady) {
                        unitPath = unitPath.replace("lady", "fs");
                    }
                    if (unitKey.getUnitType() == UnitType.Cavalry) {
                        unitPath = unitPath.replace("cavalry", "fs");
                        String name = "Memoria_1.png";
                        if (game.getPNOwner("cavalry") != null && game.getPNOwner("cavalry").hasTech("m2")) {
                            name = "Memoria_2.png";
                        }
                        unitPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);
                    }
                    if (unitKey.getUnitType() == UnitType.PlenaryOrbital) {
                        unitPath = unitPath.replace("plenaryorbital", "sd");
                        String name = "PlenaryNew.png";
                        unitPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);
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

            Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
            BufferedImage decal = null;
            if (player != null) decal = ImageHelper.read(ResourceHelper.getInstance().getDecalFile(player.getDecalFile(unitKey.asyncID())));

            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;

            }

            BufferedImage spoopy = null;
            if ((unitKey.getUnitType() == UnitType.Warsun) && (ThreadLocalRandom.current().nextInt(1000) == 0)) {

                String spoopypath = ResourceHelper.getInstance().getSpoopyFile();
                spoopy = ImageHelper.read(spoopypath);
                // BotLogger.log("SPOOPY TIME: " + spoopypath);
            }
            // if(unitKey.getUnitType() == UnitType.TyrantsLament){
            // String name = "tyrant.png";
            // String spoopyPath = ResourceHelper.getInstance().getNonSpoopyFinFile(name);
            // spoopy = ImageHelper.read(spoopyPath);
            // BotLogger.log("SPOOPY TIME: " + spoopyPath);
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
                int mult = 0;
                if (fighterOrInfantry && isSpace) {
                    if (unitKey.getUnitType() == UnitType.Infantry) {
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
                if (unitKey.getUnitType() == UnitType.Infantry && position == null) {
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
                if (unitKey.getUnitType() == UnitType.Mech && (ButtonHelper.isLawInPlay(game, "articles_war") || ButtonHelper.isLawInPlay(game, "absol_articleswar"))) {
                    BufferedImage mechTearImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_articles_of_war" + getBlackWhiteFileSuffix(unitKey.getColorID())));
                    tileGraphics.drawImage(mechTearImage, imageX, imageY, null);
                } else if (unitKey.getUnitType() == UnitType.Warsun && ButtonHelper.isLawInPlay(game, "schematics")) {
                    BufferedImage wsCrackImage = ImageHelper.read(ResourceHelper.getInstance().getTokenFile("agenda_publicize_weapon_schematics" + getBlackWhiteFileSuffix(unitKey.getColorID())));
                    tileGraphics.drawImage(wsCrackImage, imageX, imageY, null);
                }
                if (!List.of(UnitType.Fighter, UnitType.Infantry).contains(unitKey.getUnitType())) {
                    tileGraphics.drawImage(decal, imageX, imageY, null);
                }
                if (spoopy != null) {
                    tileGraphics.drawImage(spoopy, imageX, imageY, null);
                }

                // UNIT TAGS
                if (i == 0 && !(UnitType.Infantry.equals(unitKey.getUnitType())) && game.isShowUnitTags()) { // DRAW TAG
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
                        drawPlayerFactionIconImage(tileGraphics, player, imageX + plaquetteOffset.x,
                            imageY + plaquetteOffset.y, 32, 32);

                        tileGraphics.setColor(Color.WHITE);
                        drawCenteredString(tileGraphics, factionTag,
                            new Rectangle(imageX + plaquetteOffset.x + 25,
                                imageY + plaquetteOffset.y + 17, 40, 13),
                            Storage.getFont13());
                    }
                }
                if (bulkUnitCount != null) {
                    tileGraphics.setFont(Storage.getFont24());
                    tileGraphics.setColor(groupUnitColor);

                    int scaledNumberPositionX = numberPositionPoint.x;
                    int scaledNumberPositionY = numberPositionPoint.y;
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
        // Determine the Y coordinate for the text (note we add the ascent, as in java
        // 2d 0 is top of the screen)
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        // Set the font
        g.setFont(font);
        // Draw the String
        g.drawString(text, x, y);
    }

    public static String getBlackWhiteFileSuffix(String colorID) {
        Set<String> lightColors = Set.of("ylw", "org", "pnk", "tan", "crm", "sns", "tqs", "gld", "lme", "lvn", "rse",
            "spr", "tea", "lgy", "eth");
        if (lightColors.contains(colorID)) {
            return "_blk.png";
        }
        return "_wht.png";
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

    private static void drawTwoLinesOfTextVertically(Graphics graphics, String text, int x, int y, int maxWidth) {
        int spacing = graphics.getFontMetrics().getAscent() + graphics.getFontMetrics().getLeading();
        text = text.toUpperCase();
        String firstRow = StringUtils.substringBefore(text, "\n");
        firstRow = trimTextToPixelWidth(graphics, firstRow, maxWidth);
        String secondRow = text.replace(firstRow, "").replace("\n", "");
        secondRow = trimTextToPixelWidth(graphics, secondRow, maxWidth);
        drawTextVertically(graphics, firstRow, x, y, graphics.getFont());
        if (StringUtils.isNotBlank(secondRow)) {
            drawTextVertically(graphics, secondRow, x + spacing, y, graphics.getFont());
        }
    }

    private static String trimTextToPixelWidth(Graphics graphics, String text, int pixelLength) {
        int currentPixels = 0;
        for (int i = 0; i < text.length(); i++) {
            currentPixels += graphics.getFontMetrics().charWidth(text.charAt(i));
            if (currentPixels > pixelLength) {
                return text.substring(0, i);
            }
        }
        return text;
    }

}
