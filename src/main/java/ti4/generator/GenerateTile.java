package ti4.generator;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.generator.MapGenerator.TileStep;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.ImageHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.MapFileDeleter;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GenerateTile {

    private static final int TILE_PADDING = 100;

    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;

    private int offsetX;
    private int offsetY;

    private Boolean isFoWPrivate;
    private Player fowPlayer;

    private static GenerateTile instance;

    private GenerateTile() {
        init(0, "000");
        reset();
    }

    private void init(int context, String focusTile) {
        int tileExtraWidth = 260;
        int tileWidth = 345;
        int extraX = 100;
        width = tileWidth + (tileExtraWidth * 2 * context) + extraX;
        int tileHeight = 300;
        int extraY = 100;
        height = tileHeight * (2 * context + 1) + extraY;

        if (focusTile == null) {
            offsetX = 0;
            offsetY = 0;
            return;
        }

        Point p = PositionMapper.getTilePosition(focusTile);
        if (p != null) {
            offsetX = -1 * p.x + (extraX / 2) + (context * tileExtraWidth);
            offsetY = -1 * p.y + (extraY / 2) + (context * tileHeight);
        } else {
            offsetX = 0;
            offsetY = 0;
        }
    }

    private void reset() {
        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
        isFoWPrivate = null;
        fowPlayer = null;
    }

    public static GenerateTile getInstance() {
        if (instance == null) {
            instance = new GenerateTile();
        }
        return instance;
    }

    public FileUpload saveImage(Game game, @Nullable SlashCommandInteractionEvent event) {
        return saveImage(game, 1, "000", event);
    }

    public FileUpload saveImage(Game game, int context, String focusTile, @Nullable GenericInteractionCreateEvent event) {
        return saveImage(game, context, focusTile, event, null);
    }

    public FileUpload saveImage(Game game, int context, String focusTile, @Nullable GenericInteractionCreateEvent event, @Nullable Player p1) {
        init(context, focusTile);
        reset();

        Map<String, Tile> tilesToDisplay = new HashMap<>(game.getTileMap());
        Set<String> systemsInRange = getTilesToShow(game, context, focusTile);
        Set<String> keysToRemove = new HashSet<>(tilesToDisplay.keySet());
        keysToRemove.removeAll(systemsInRange);
        for (String tile_ : keysToRemove) {
            tilesToDisplay.remove(tile_);
        }

        // Resolve fog of war vision limitations
        if (game.isFowMode() && event != null) {
            isFoWPrivate = false;
            if (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)) {
                isFoWPrivate = true;
                Player player = getFowPlayer(game, event);
                if (p1 != null) {
                    player = p1;
                }
                fowPlayer = Helper.getGamePlayer(game, player, event, null);
                if (p1 != null) {
                    fowPlayer = p1;
                }
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

        try {
            Map<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
            tileMap.remove(null);

            Set<String> tiles = tileMap.keySet();
            Set<String> tilesWithExtra = new HashSet<>(game.getAdjacentTileOverrides().values());
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), game, TileStep.Tile));
            tilesWithExtra.forEach(key -> addTile(tileMap.get(key), game, TileStep.Extras));
            tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), game, TileStep.Units));

            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            String timeStamp = getTimeStamp();
            graphics.drawString(game.getName() + " " + timeStamp, 0, 34);
        } catch (Exception e) {
            BotLogger.log(game.getName() + ": Could not save generated system info image");
        }

        String timeStamp = getTimeStamp();
        String absolutePath = Storage.getMapImageDirectory() + "/" + game.getName() + "_" + timeStamp + ".jpg";
        try (FileOutputStream fileOutputStream = new FileOutputStream(absolutePath)) {
            BufferedImage convertedImage = ImageHelper.redrawWithoutAlpha(mainImage);
            boolean canWrite = ImageIO.write(convertedImage, "jpg", fileOutputStream);
            if (!canWrite) {
                throw new IllegalStateException("Failed to write image.");
            }
        } catch (IOException e) {
            BotLogger.log("Could not save jpg file", e);
        }
        File jpgFile = new File(absolutePath);
        MapFileDeleter.addFileToDelete(jpgFile);
        return FileUpload.fromData(jpgFile, jpgFile.getName());
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

    private static Player getFowPlayer(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (event == null)
            return null;
        String user = event.getUser().getId();
        return game.getPlayer(user);
    }

    @NotNull
    public static String getTimeStamp() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd - HH.mm.ss.SSS");
        return ZonedDateTime.now(ZoneOffset.UTC).format(fmt);
    }

    private void addTile(Tile tile, Game game, TileStep step) {
        if (tile == null || tile.getTileID() == null) {
            return;
        }
        try {
            String position = tile.getPosition();
            Point positionPoint = PositionMapper.getTilePosition(position);
            if (positionPoint == null) {
                throw new Exception("Could not map tile to a position on the map: " + game.getName());
            }

            int tileX = positionPoint.x + offsetX - TILE_PADDING;
            int tileY = positionPoint.y + offsetY - TILE_PADDING;

            BufferedImage tileImage = MapGenerator.partialTileImage(tile, game, step, fowPlayer, isFoWPrivate);
            graphics.drawImage(tileImage, tileX, tileY, null);

        } catch (Exception exception) {
            BotLogger.log("Tile Error, when building map: " + tile.getTileID(), exception);
        }
    }
}
