package ti4.commands.milty;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.commands.milty.MiltyDraftManager.PlayerDraft;
import ti4.generator.DrawingUtil;
import ti4.generator.MapGenerator;
import ti4.generator.MapGenerator.HorizontalAlign;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
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
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.model.WormholeModel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MiltyDraftHelper {

    public static void generateAndPostSlices(Game game) {
        MessageChannel mainGameChannel = game.getMainGameChannel();
        FileUpload fileUpload = generateImage(game);

        if (fileUpload == null) {
            MessageHelper.sendMessageToChannel(mainGameChannel, "There was an error building the slices image.");
        } else {
            MessageHelper.sendFileUploadToChannel(mainGameChannel, fileUpload);
        }
    }

    private static FileUpload generateImage(Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        List<MiltyDraftSlice> slices = manager.getSlices();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(manager.getMapTemplate());

        int sliceCount = slices.size();
        int spanW = (int) (Math.ceil(Math.sqrt(sliceCount)) + 0.01);
        int spanH = (sliceCount + spanW - 1) / spanW;

        float scale = 1f;
        int scaled = (int) (mapTemplate.squareSliceImageSize() * scale);
        int width = scaled * spanW;
        int height = scaled * spanH;
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        int index = 0;
        int deltaX = 0;
        int deltaY = 0;

        StringBuilder desc = new StringBuilder();
        for (MiltyDraftSlice slice : slices) {
            Player playerPicked = game.getPlayers().values().stream()
                .filter(player -> manager.getPlayerDraft(player) != null)
                .filter(player -> slice.equals(manager.getPlayerDraft(player).getSlice()))
                .findFirst().orElse(null);

            BufferedImage sliceImage;
            if (game.isFowMode()) {
                sliceImage = partialSliceImage(slice, mapTemplate, playerPicked != null);
            } else {
                sliceImage = sliceImageWithPlayerInfo(slice, manager, playerPicked);
            }

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

            if (!desc.toString().isBlank()) desc.append(";\n");
            desc.append(slice.ttsString());
        }

        FileUpload fileUpload = MapGenerator.createFileUpload(mainImage, 1.0f, game.getName() + "_miltydraft");
        fileUpload.setDescription(desc.toString());
        return fileUpload;
    }

    private static final BasicStroke innerStroke = new BasicStroke(4.0f);
    private static final BasicStroke outlineStroke = new BasicStroke(9.0f);

    private static BufferedImage partialSliceImage(MiltyDraftSlice slice, MapTemplateModel template, boolean taken) {
        List<Point> tilePositions = template.tileDisplayCoords();
        int imageSize = template.squareSliceImageSize();
        Point hs = tilePositions.getFirst();

        List<String> tileStrings = new ArrayList<>();
        tileStrings.add(ResourceHelper.getInstance().getTileFile("00_green.png"));
        tileStrings.addAll(slice.getTiles().stream().map(t -> t.getTile().getTilePath()).toList());

        String fow = slice.getTiles().getFirst().getTile().getFowTilePath(null);
        BufferedImage fogFilter = ImageHelper.read(fow);

        BufferedImage sliceImage = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = sliceImage.getGraphics();

        int index = 0;
        for (String tilePath : tileStrings) {
            BufferedImage img = ImageHelper.read(tilePath);
            Point p = tilePositions.get(index);
            graphics.drawImage(img, p.x, p.y, null);
            if (taken) graphics.drawImage(fogFilter, p.x, p.y, null);
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

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont50());
        DrawingUtil.superDrawString(graphics, totalsString, hs.x + 172, hs.y + 110, Color.white, hCenter, null, outlineStroke, Color.black);
        DrawingUtil.superDrawString(graphics, optimalString, hs.x + 172, hs.y + 165, Color.white, hCenter, null, outlineStroke, Color.black);

        return sliceImage;
    }

    private static BufferedImage sliceImageWithPlayerInfo(MiltyDraftSlice slice, MiltyDraftManager manager, Player player) {
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(manager.getMapTemplate());
        List<Point> tilePositions = mapTemplate.tileDisplayCoords();
        Point hs = tilePositions.getFirst();

        BufferedImage sliceImage = partialSliceImage(slice, mapTemplate, player != null);
        Graphics graphics = sliceImage.getGraphics();

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(graphics, slice.getName(), hs.x + 172, hs.y + 50, Color.white, hCenter, null, outlineStroke, Color.black);

        if (player != null) {
            graphics.setColor(Color.white);
            graphics.setFont(Storage.getFont50());

            PlayerDraft pd = manager.getPlayerDraft(player);
            String playerName = player.getUserName();
            String faction = pd.getFaction() == null ? "no faction" : pd.getFaction();
            DrawingUtil.superDrawString(graphics, playerName, hs.x + 172, hs.y + 230, Color.red, hCenter, null, outlineStroke, Color.black);

            if (pd.getFaction() != null) {
                FactionModel factionModel = Mapper.getFaction(faction);
                String factionName = factionModel.getFactionName();
                if (factionModel.getAlias().startsWith("keleres"))
                    factionName = "The Council Keleres";
                graphics.setFont(Storage.getFont35());
                DrawingUtil.superDrawString(graphics, factionName, hs.x + 172, hs.y + 270, Color.orange, hCenter, null, outlineStroke, Color.black);
                BufferedImage img = getEmojiImage(factionModel.getFactionEmoji());
                int offset = graphics.getFontMetrics().stringWidth(factionName) / 2 + 10;
                graphics.drawImage(img, hs.x + 172 - offset - 40, hs.y + 240, null);
                graphics.drawImage(img, hs.x + 172 + offset, hs.y + 240, null);
            }
        }

        return sliceImage;
    }

    private static BufferedImage getEmojiImage(String emojiString) {
        return ImageHelper.readEmojiImageScaled(emojiString, 40);
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
            if (isInvalid(tileModel)) {
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

            boolean sourceAllowed = sources.contains(tileModel.getSource());

            // leaving these as a stop-gap for now until I can verify all sources are setup
            if (tileID.length() <= 2) sourceAllowed = true;
            if (tileID.matches("d\\d{1,3}") && sources.contains(ComponentSource.ds)) sourceAllowed = true;

            if (!sourceAllowed) continue;

            Tile tile = new Tile(tileID, "none");
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
            } else if (tile.getPlanetUnitHolders().isEmpty()) {
                draftTile.setTierList(TierList.red);
            } else {
                draftTile.setTierList(TierList.high);
            }

            draftManager.addDraftTile(draftTile);
        }
    }

    private static boolean isInvalid(TileModel tileModel) {
        if (tileModel.getTileBackOption().isPresent()) {
            String back = tileModel.getTileBackOption().orElse("");
            if (back.equals("red") || back.equals("blue")) {
                //good
            } else {
                return true;
            }
        }

        String id = tileModel.getId().toLowerCase();
        String path = tileModel.getImagePath() == null ? "" : tileModel.getImagePath().toLowerCase();
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

    // TODO (Jazz): add map template
    public static List<MiltyDraftSlice> parseSlicesFromString(String sliceString, List<ComponentSource> allowedSources) {
        try {
            sliceString = sliceString.replace("|", ";");
            MiltyDraftManager manager = new MiltyDraftManager();
            manager.init(allowedSources);
            manager.loadSlicesFromString(sliceString);
            return manager.getSlices();
        } catch (Exception e) {
            BotLogger.log("invalid slice string", e);
            return null;
        }
    }
}
