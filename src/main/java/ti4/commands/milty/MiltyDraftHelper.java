package ti4.commands.milty;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.commands.milty.MiltyDraftManager.PlayerDraft;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.generator.MapGenerator.HorizontalAlign;
import ti4.helpers.Emojis;
import ti4.helpers.ImageHelper;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.TileModel;
import ti4.model.WormholeModel;
import ti4.model.Source.ComponentSource;

public class MiltyDraftHelper {

    public static void generateAndPostSlices(Game game) {
        MessageChannel mainGameChannel = game.getMainGameChannel();
        FileUpload fileUpload = generateImage(game);

        if (fileUpload == null) {
            MessageHelper.sendMessageToChannel(mainGameChannel, "There was an error building the slices image.");
        } else {
            MessageHelper.sendFileUploadToChannel(mainGameChannel, fileUpload, true);
        }
    }

    private static FileUpload generateImage(Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        List<MiltyDraftSlice> slices = manager.getSlices();

        int sliceCount = slices.size();
        int spanW = (int) (Math.ceil(Math.sqrt(sliceCount)) + 0.01);
        int spanH = (sliceCount + spanW - 1) / spanW;

        float scale = 1f;
        int scaled = (int) (900 * scale);
        int width = scaled * spanW;
        int height = scaled * spanH;
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        int index = 0;
        int deltaX = 0;
        int deltaY = 0;
        for (MiltyDraftSlice slice : slices) {
            Player playerPicked = game.getPlayers().values().stream().filter(player -> slice.equals(manager.getPlayerDraft(player).getSlice())).findFirst().orElse(null);
            BufferedImage sliceImage = generateSliceImage(slice, manager, playerPicked);
            BufferedImage resizedSlice = ImageHelper.scale(sliceImage, scale);
            graphicsMain.drawImage(resizedSlice, deltaX, deltaY, null);
            index++;

            int heightSlice = resizedSlice.getHeight();
            int widthSlice = resizedSlice.getWidth();

            deltaX += widthSlice;
            if (index % spanW == 0) {
                deltaY += heightSlice;
                deltaX = 0;
            }
        }

        FileUpload fileUpload = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            boolean jpg = true;
            String ext = jpg ? "jpg" : "png";
            BufferedImage jpgImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            jpgImage.createGraphics().drawImage(mainImage, 0, 0, Color.black, null);
            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName(ext).next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(out));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(1.0f);
            }
            imageWriter.write(null, new IIOImage(jpg ? jpgImage : mainImage, null, null), defaultWriteParam);
            String fileName = game.getName() + "_miltydraft_" + MapGenerator.getTimeStamp() + "." + ext;
            fileUpload = FileUpload.fromData(out.toByteArray(), fileName);
        } catch (IOException e) {
            BotLogger.log("Could not create FileUpload for milty draft", e);
        }

        return fileUpload;
    }

    private static final BasicStroke innerStroke = new BasicStroke(4.0f);
    private static final BasicStroke outlineStroke = new BasicStroke(9.0f);

    private static BufferedImage generateSliceImage(MiltyDraftSlice slice, MiltyDraftManager manager, Player player) {
        Point left = new Point(0, 450);
        Point front = new Point(260, 300);
        Point right = new Point(520, 450);
        Point equidistant = new Point(0, 150);
        Point farFront = new Point(260, 0);
        Point hs = new Point(260, 600);

        List<String> tileStrings = new ArrayList<>();
        tileStrings.addAll(slice.getTiles().stream().map(t -> t.getTile().getTilePath()).toList());
        tileStrings.add(ResourceHelper.getInstance().getTileFile("00_green.png"));
        List<Point> tilePositions = Arrays.asList(left, front, right, equidistant, farFront, hs);

        String fow = slice.getFront().getTile().getFowTilePath(null);
        BufferedImage fogFilter = ImageHelper.read(fow);

        BufferedImage sliceImage = new BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = sliceImage.getGraphics();

        int index = 0;
        BufferedImage img = null;
        for (String tilePath : tileStrings) {
            img = ImageHelper.read(tilePath);
            Point p = tilePositions.get(index);
            graphics.drawImage(img, p.x, p.y, null);
            if (player != null) graphics.drawImage(fogFilter, p.x, p.y, null);

            index++;
        }
        List<BufferedImage> whs = new ArrayList<>();
        List<BufferedImage> legendary = new ArrayList<>();
        List<BufferedImage> blueSkips = new ArrayList<>();
        List<BufferedImage> greenSkips = new ArrayList<>();
        List<BufferedImage> yellowSkips = new ArrayList<>();
        List<BufferedImage> redSkips = new ArrayList<>();
        for (MiltyDraftTile tile : slice.getTiles()) {
            if (tile.isHasAlphaWH()) whs.add(getEmojiImage(Emojis.WHalpha));
            if (tile.isHasBetaWH()) whs.add(getEmojiImage(Emojis.WHbeta));
            if (tile.isHasOtherWH()) whs.add(getEmojiImage(Emojis.WHgamma));

            if (tile.isLegendary()) legendary.add(getEmojiImage(Emojis.LegendaryPlanet));

            for (UnitHolder uh : tile.getTile().getPlanetUnitHolders()) {
                if (uh instanceof Planet p) {
                    for (String spec : p.getTechSpecialities()) {
                        switch (spec) {
                            case "propulsion" -> blueSkips.add(getEmojiImage(Emojis.PropulsionTech));
                            case "biotic" -> greenSkips.add(getEmojiImage(Emojis.BioticTech));
                            case "cybernetic" -> yellowSkips.add(getEmojiImage(Emojis.CyberneticTech));
                            case "warfare" -> redSkips.add(getEmojiImage(Emojis.WarfareTech));
                        }
                    }
                }
            }
        }
        List<BufferedImage> features = new ArrayList<>();
        features.addAll(yellowSkips);
        features.addAll(blueSkips);
        features.addAll(greenSkips);
        features.addAll(redSkips);
        features.addAll(whs);
        features.addAll(legendary);

        List<Point> featurePoints = Arrays.asList(
            new Point(83, 3), new Point(220, 3),
            new Point(60, 43), new Point(243, 43),
            new Point(37, 83), new Point(266, 83),
            new Point(14, 123), new Point(289, 123));

        int resources = slice.getTotalRes();
        int influence = slice.getTotalInf();
        String totalsString = resources + "/" + influence;

        int resourcesMilty = slice.getOptimalRes();
        int influenceMilty = slice.getOptimalInf();
        int flexMilty = slice.getOptimalFlex();
        String optimalString = "(" + resourcesMilty + "/" + influenceMilty + "+" + flexMilty + ")";

        ((Graphics2D) graphics).setStroke(innerStroke);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        MapGenerator.superDrawString(graphics, slice.getName(), hs.x + 172, hs.y + 50, Color.white, hCenter, null, outlineStroke, Color.black);

        index = 0;
        graphics.setColor(Color.black);
        for (BufferedImage featureImage : features) {
            Point fPoint = featurePoints.get(index);
            featureImage.getGraphics();
            graphics.setColor(Color.black);
            graphics.fillRoundRect(fPoint.x + hs.x, fPoint.y + hs.y, 40, 40, 40, 40);

            graphics.drawImage(featureImage, fPoint.x + hs.x, fPoint.y + hs.y, null);
            index++;
            if (index >= featurePoints.size()) break;
        }

        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont50());
        MapGenerator.superDrawString(graphics, totalsString, hs.x + 172, hs.y + 110, Color.white, hCenter, null, outlineStroke, Color.black);
        MapGenerator.superDrawString(graphics, optimalString, hs.x + 172, hs.y + 165, Color.white, hCenter, null, outlineStroke, Color.black);
        if (player != null) {
            PlayerDraft pd = manager.getPlayerDraft(player);
            String playerName = player.getUserName();
            String faction = pd.getFaction() == null ? "no faction" : pd.getFaction();
            graphics.setFont(Storage.getFont50());
            MapGenerator.superDrawString(graphics, playerName, hs.x + 172, hs.y + 230, Color.red, hCenter, null, outlineStroke, Color.black);
            if (pd.getFaction() != null) {
                FactionModel factionModel = Mapper.getFaction(faction);
                graphics.setFont(Storage.getFont35());
                MapGenerator.superDrawString(graphics, factionModel.getFactionName(), hs.x + 172, hs.y + 270, Color.orange, hCenter, null, outlineStroke, Color.black);
                img = getEmojiImage(factionModel.getFactionEmoji());
                int offset = graphics.getFontMetrics().stringWidth(factionModel.getFactionName()) / 2 + 10;
                graphics.drawImage(img, hs.x + 172 - offset - 40, hs.y + 240, null);
                graphics.drawImage(img, hs.x + 172 + offset, hs.y + 240, null);
            }
        }

        return sliceImage;
    }

    private static BufferedImage getEmojiImage(String emojiString) {
        int size = 40;
        if (Emoji.fromFormatted(emojiString) instanceof CustomEmoji e)
            return ImageHelper.readURLScaled(e.getImageUrl(), size, size);
        return null;
    }

    public static void initDraftTiles(MiltyDraftManager manager, Game game) {
        List<ComponentSource> sources = new ArrayList<>(Arrays.asList(
            ComponentSource.base,
            ComponentSource.codex1,
            ComponentSource.codex2,
            ComponentSource.codex3,
            ComponentSource.pok));
        if (game.isDiscordantStarsMode()) sources.add(ComponentSource.ds);
        initDraftTiles(manager, sources);
    }

    public static void initDraftTiles(MiltyDraftManager draftManager, List<ComponentSource> sources) {
        List<TileModel> allTiles = new ArrayList<>(TileHelper.getAllTiles().values());
        for (TileModel tileModel : allTiles) {
            String tileID = tileModel.getId();
            if (isInvalid(tileModel, tileID)) {
                continue;
            }
            Set<WormholeModel.Wormhole> wormholes = tileModel.getWormholes();
            MiltyDraftTile draftTile = new MiltyDraftTile();
            if (wormholes != null) {
                for (WormholeModel.Wormhole wormhole : wormholes) {
                    if (WormholeModel.Wormhole.ALPHA == wormhole) {
                        draftTile.setHasAlphaWH(true);
                    } else if (WormholeModel.Wormhole.BETA == wormhole) {
                        draftTile.setHasBetaWH(true);
                    } else {
                        draftTile.setHasOtherWH(true);
                    }
                }
            }

            Tile tile = new Tile(tileID, "none");
            boolean sourceAllowed = false;
            if (sources.contains(tile.getTileModel().getSource())) sourceAllowed = true;

            // leaving these as a stop-gap for now until I can verify all sources are setup
            if (tileID.length() <= 2) sourceAllowed = true;
            if (tileID.matches("d\\d{1,3}") && sources.contains(ComponentSource.ds)) sourceAllowed = true;

            if (!sourceAllowed) continue;

            if (tile.isHomeSystem() || tile.getRepresentation().contains("Hyperlane") || tile.getRepresentation().contains("Keleres")) {
                continue;
            }

            draftTile.setTile(tile);
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet planet) {
                    draftTile.addPlanet(planet);
                }
            }

            if (tile.isAnomaly()) {
                draftTile.setTierList(TierList.anomaly);
            } else if (tile.getPlanetUnitHolders().size() == 0) {
                draftTile.setTierList(TierList.red);
            } else {
                draftTile.setTierList(TierList.high);
            }

            draftManager.addDraftTile(draftTile);
        }
    }

    private static boolean isInvalid(TileModel tileModel, String tileID) {
        if (tileModel.getTileBackOption().isPresent()) {
            String back = tileModel.getTileBackOption().orElse("");
            if (back.equals("red") || back.equals("blue")) {
                //good
            } else {
                return true;
            }
        }

        String id = tileID.toLowerCase();
        String path = tileModel.getTilePath() == null ? "" : tileModel.getTilePath().toLowerCase();
        List<String> disallowedTerms = List.of("corner", "lane", "mecatol", "blank", "border", "fow", "anomaly", "deltawh",
            "seed", "mr", "mallice", "ethan", "prison", "kwon", "home", "hs", "red", "blue", "green", "gray", "gate", "setup");
        return disallowedTerms.stream().anyMatch(term -> id.contains(term) || path.contains(term));
    }

    public static void buildPartialMap(Game game, GenericInteractionCreateEvent event) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();

        String mapTemplate = manager.getMapTemplate();
        if (mapTemplate == null) {
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(manager.getPlayers().size());
            if (defaultTemplate == null) {
                throw new Exception("idk how to build this map yet");
            }
            mapTemplate = defaultTemplate.getAlias();
        }

        MapTemplateHelper.buildPartialMapFromMiltyData(game, event, mapTemplate);
    }

    public static void buildMap(Game game) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();

        String mapTemplate = manager.getMapTemplate();
        if (mapTemplate == null) {
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(manager.getPlayers().size());
            if (defaultTemplate == null) {
                throw new Exception("idk how to build this map yet");
            }
            mapTemplate = defaultTemplate.getAlias();
        }

        MapTemplateHelper.buildMapFromMiltyData(game, mapTemplate);
    }
}
